/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.detail;

import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.lucyapps.prompt.R;
import com.lucyapps.prompt.detail.CalendarWrapper.EventSummary;
import com.lucyapps.prompt.util.ClassUtilities;
import com.lucyapps.prompt.util.LocationUtil;

/**
 * 
 * @author Thor
 */
public class PromptService extends Service {

	/** Maximum time to next event to consider in milliseconds. */
	public static final long LOOK_AHEAD_TIME = 8 * 3600 * 1000;
	/** Lead time ahead of when one needs to leave. */
	public static final long LEAD_TIME = 15 * 60 * 1000;
	/** Time between running service checks */
	public static final long UPDATE_INTERVAL = 30000;
	/** If a last known location is older than STALE_GPS_DURATION, don't use it. */
	public static final long STALE_GPS_DURATION = LOOK_AHEAD_TIME / 2;
	/** Minimum distance in meters between updates indexed by travelMode. */
	public static final long[] TRAVEL_MODE_MIN_UPDATE_DISTANCE = { 1000, 200, 1000 };
	/** Maximum time to wait for a location fix. */
	public static final long LOCATION_FIX_TIMEOUT = 3 * 60000;
	// optimize network usage by not performing redundant queries
	// Persist state to avoid excessive notification and preserve battery.
	// private Location lastUpdatedLocation; //not saved
	private Location locationAtLastTravelTimeCalculation;
	private CalendarWrapper.EventSummary lastKnownEvent;
	private CalendarWrapper.EventSummary lastEventNotified;
	/** Last known travel time in milliseconds */
	private long lastTravelTime = -1;
	/** If isScheduledToRun has not yet been set, assume it should be run. */
	private static final boolean IS_SCHEDULED_TO_RUN_DEFAULT_VALUE = true;
	private MyLocation myLocation;
	private Timer timer = null;
	private int lastTravelMode = 0;
	private int travelMode = 0;
	private int leadTimeInMinutes = 15;
	/** Name to use for accessing preferences. */
	public static final String PREFERENCES_ID = "PromptServicePrefs";
	private static final String LAST_KNOWN_EVENT_ID = ".lastKnownEvent";
	private static final String LAST_EVENT_NOTIFIED_ID = ".lastEventNotified";
	private static final String LOCATION_AT_LAST_TRAVEL_TIME_CALC_ID = ".locLTTCalc";
	private static final String LAST_TRAVEL_TIME_ID = ".lastTravelTime";
	private static final String LAST_TRAVEL_MODE_ID = ".lastTravelMode";
	private static final String IS_SCHEDULED_TO_RUN_ID = ".isScheduledToRun";
	private static final String TRAVEL_MODE_ID = ".travelMode";
	private static final String LEAD_TIME_ID = ".leadTime";
	
	private SharedPreferences getServiceSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(getBaseContext());
//		return getSharedPreferences(PromptService.PREFERENCES_ID, MODE_PRIVATE);
	}
	
	private synchronized void saveState() {
		SharedPreferences.Editor editor = getServiceSharedPreferences().edit();
		if (lastKnownEvent != null) {
			lastKnownEvent.saveState(editor, LAST_KNOWN_EVENT_ID);
		}
		if (lastEventNotified != null) {
			lastEventNotified.saveState(editor, LAST_EVENT_NOTIFIED_ID);
		}
		if (locationAtLastTravelTimeCalculation != null) {
			LocationUtil.saveState(locationAtLastTravelTimeCalculation, editor,
					LOCATION_AT_LAST_TRAVEL_TIME_CALC_ID);
			editor.putLong(LAST_TRAVEL_TIME_ID, lastTravelTime);
		}
		editor.putInt(LAST_TRAVEL_MODE_ID, lastTravelMode);
		// Don't need to explicitly save travelMode or leadTimeInMinutes.
		editor.commit();
	}

	private synchronized void loadState() {
		SharedPreferences prefs = getServiceSharedPreferences();
		lastKnownEvent = new EventSummary();
		lastKnownEvent.loadState(prefs, LAST_KNOWN_EVENT_ID);
		lastEventNotified = new EventSummary();
		lastEventNotified.loadState(prefs, LAST_EVENT_NOTIFIED_ID);
		locationAtLastTravelTimeCalculation = LocationUtil.loadState(prefs,
				LOCATION_AT_LAST_TRAVEL_TIME_CALC_ID);
		lastTravelTime = prefs.getLong(LAST_TRAVEL_TIME_ID, lastTravelTime);
		lastTravelMode = prefs.getInt(LAST_TRAVEL_MODE_ID, lastTravelMode);
		travelMode = Integer.parseInt(prefs.getString(TRAVEL_MODE_ID, travelMode + ""));
		leadTimeInMinutes = Integer.parseInt(prefs.getString(LEAD_TIME_ID, leadTimeInMinutes + ""));
		
		if (travelMode < 0 || travelMode >= MapsWrapper.GOOGLE_MAPS_TRAVEL_MODES.length) {
			Log.d("LucyApps", "Invalid travel mode int of " + travelMode);
			travelMode = 0;
		}
	}

	private boolean isScheduledToRun() {
		return isScheduledToRunPref(getServiceSharedPreferences());
	}

	public static boolean isScheduledToRunPref(SharedPreferences prefs) {
		return prefs.getBoolean(IS_SCHEDULED_TO_RUN_ID,
				IS_SCHEDULED_TO_RUN_DEFAULT_VALUE);
	}
	
	public static void setScheduledToRunPref(SharedPreferences.Editor editor,
			boolean shouldRun) {
		editor.putBoolean(IS_SCHEDULED_TO_RUN_ID, shouldRun);
	}
		
	public static int getTravelMode(SharedPreferences prefs) {
		return prefs.getInt(TRAVEL_MODE_ID, 0);
	}
	
	public static void setTravelMode(SharedPreferences.Editor editor, int travelMode) {
		editor.putInt(TRAVEL_MODE_ID, travelMode);
	}

	public static CalendarWrapper.EventSummary getNextEvent(Context context) {
		return CalendarWrapper.getNextEventSummary(context,
				getNextEventTimeWindow(),
				// TODO might want to limit this to some user provided selection
				CalendarWrapper.getCalendarIds(context) // TODO Could optimize
				// this
				);
	}

	private synchronized void checkEvents() {
		Log.v("LucyApps", "checkEvents()");

		try {
			stopFetchingLocation();

			// get next event with location info within 8 hrs + lead time
			final CalendarWrapper.EventSummary nextEvent = getNextEvent(this);
			if (nextEvent == null
					|| ClassUtilities.equals(lastEventNotified, nextEvent)) {
				if (nextEvent != null) {
					Log.d("LucyApps", "Skipping previously notified event: "
							+ lastEventNotified
							+ " because nextEvent is the same " + nextEvent);
				}
				// we are done, stop the service
				stopSelf();
				return; // nothing to do
			}
			Log.d("LucyApps", "Processing next event: " + nextEvent
					+ ".  Last event notified: " + lastEventNotified);

			Location lastKnownLocation = getLastKnownLocation();
			if (lastKnownLocation != null
					&& lastKnownLocation.getTime() >= System
							.currentTimeMillis() - STALE_GPS_DURATION) {
				processNextEvent(nextEvent, lastKnownLocation);
				stopSelf(); // we are done, stop the service
			} else {
				Log.d("LucyApps",
						"Last known location is too old, fetching location...");
				startFetchingLocation();
			}
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}
	
	private boolean isPreviousLocationTooFar(final Location currentLocation) {
		return locationAtLastTravelTimeCalculation.distanceTo(currentLocation) 
				> getMinUpdateDistance();
	}

	private synchronized void processNextEvent(
			final CalendarWrapper.EventSummary nextEvent,
			final Location currentLocation) throws Exception {
		// see if we need to update the travel time
		if (lastKnownEvent == null
				|| !nextEvent.getLocation()
						.equals(lastKnownEvent.getLocation())
				|| travelMode != lastTravelMode
				|| locationAtLastTravelTimeCalculation == null
				|| isPreviousLocationTooFar(currentLocation)) {
			
			String travelModeStr = MapsWrapper.GOOGLE_MAPS_TRAVEL_MODES[travelMode];
			Log.i("LucyApps", "Calculating travel time with travel mode of " + travelModeStr + "...");
			
			final long travelTime = MapsWrapper.getTravelTime(currentLocation,
															  nextEvent.getLocation(),
															  travelModeStr);
			locationAtLastTravelTimeCalculation = currentLocation;
			lastKnownEvent = nextEvent;
			lastTravelMode = travelMode;
			// get travel time from current location to event location
			lastTravelTime = travelTime;
		}
		Log.i("LucyApps", "Travel time is " + lastTravelTime);

		if (lastTravelTime >= 0
				&& System.currentTimeMillis() + lastTravelTime + getLeadTime() >= 
					nextEvent.getTime()) {
			// send notification intent
			showNotification(nextEvent, lastTravelTime);
		}

		saveState();
	}

	private long getLeadTime() {
		return leadTimeInMinutes * 60 * 1000;
	}
	
	private static long getNextEventTimeWindow() {
		return LOOK_AHEAD_TIME + LEAD_TIME;
	}

	private synchronized void showNotification(EventSummary nextEvent,
			long travelTime) {
		if (ClassUtilities.equals(lastEventNotified, nextEvent)) {
			Log.i("LucyApps",
					"showNotification() already sent notification for event "
							+ nextEvent);
			return; // already notified about this event
		}

		// The PendingIntent to launch our activity if the user selects this
		// notification
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				MapsWrapper.getDirectionsIntent(nextEvent.getLocation()), 0);

		CharSequence tickerStr = nextEvent.getLocation();
		// + ": " + getText(R.string.travel_time) + " = " + (travelTime /
		// (60000)) + " " + getText(R.string.minutes);

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(
				R.drawable.ic_prompt_on_status_bar, tickerStr,
				nextEvent.getTime() - travelTime // set the time when the user
		// should leave by
		);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, nextEvent.getTitle(), tickerStr,
				pendingIntent);

		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		// Set lights
		// notification.defaults |= Notification.DEFAULT_LIGHTS;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;
		notification.ledOnMS = 1000;
		notification.ledOffMS = 1000;
		notification.ledARGB = 0xFFFFFFFF;

		// Post the notification
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (nm != null) {
			nm.cancel(R.string.next_event_id);

			// Send the notification.
			// We use a layout id because it is a unique number. We use it later
			// to cancel.
			nm.notify(R.string.next_event_id, notification);
			lastEventNotified = nextEvent;
			Log.i("LucyApps", "showNotification() sent notification for event "
					+ nextEvent);
		}
	}

	/** @return null or the last known location */
	private Location getLastKnownLocation() {
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// Start with fine location.
		Location gpsLocation = locationManager
				.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		Location networkLocation = locationManager
				.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

		if (gpsLocation == null) {
			return networkLocation;
		}
		if (networkLocation == null) {
			return gpsLocation;
		}
		// return the more recent location
		if (networkLocation.getTime() > gpsLocation.getTime()) {
			return networkLocation;
		}
		return gpsLocation;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null; // don't need this
	}

	@Override
	public void onCreate() {
		try {
			super.onCreate();
			loadState();
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		startService();
	}

	@Override
	public void onDestroy() {
		try {
			stopService();
			super.onDestroy();
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}
	
	private long getMinUpdateDistance() {
		return TRAVEL_MODE_MIN_UPDATE_DISTANCE[travelMode];
	}

	private synchronized void startFetchingLocation() {
		if (myLocation == null) {
			myLocation = new MyLocation(this, new LocationFixProcessor(),
					UPDATE_INTERVAL, getMinUpdateDistance());
			myLocation.start();

			// schedule timer to stop fetching location
			if (timer == null) {
				timer = new Timer();
			}

			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					Log.v("LucyApps", "Location fix has timed out. :-(");
					stopFetchingLocation();
					stopSelf();
				}
			}, LOCATION_FIX_TIMEOUT);
		}
	}

	private synchronized void stopFetchingLocation() {
		Log.v("LucyApps", "PromptService.stopFetchingLocation()");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		if (myLocation != null) {
			myLocation.stop();
			myLocation = null;
		}
	}

	private void startService() {
		Log.v("LucyApps", "PromptService.startService()");
		if (!isScheduledToRun()) {
			Log.d("LucyApps", "PromptService is not scheduled to run.");
			return;
		}

		try {
			new Thread() {

				@Override
				public void run() {
					checkEvents();
				}
			}.start();
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}

	private synchronized void stopService() {
		Log.v("LucyApps", "PromptService.stopService()");
		saveState();
		stopFetchingLocation();
	}

	private class LocationFixProcessor extends MyLocation.LocationResult {

		@Override
		public void gotLocation(final Location currentLocation) {
			Log.v("LucyApps", "Location changed, new location: "
					+ currentLocation);
			stopFetchingLocation();
			// lastUpdatedLocation = currentLocation;
			checkEvents();
		}
	}

	public static void schedulePromptService(Context context) {
		AlarmManager mgr = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime(), PromptService.UPDATE_INTERVAL,
				createStartServicePendingIntent(context));
		LicensingService.scheduleLicensingService(context);
	}

	public static void cancelPromptService(Context context) {
		AlarmManager mgr = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		mgr.cancel(createStartServicePendingIntent(context));
	}

	public static PendingIntent createStartServicePendingIntent(Context context) {
		Intent i = new Intent();
		i.setAction("com.lucyapps.prompt.detail.PromptService");
		return PendingIntent.getService(context, 0, i,
				PendingIntent.FLAG_UPDATE_CURRENT);
	}

}
