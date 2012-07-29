package com.lucyapps.prompt.detail;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;

import com.android.vending.licensing.AESObfuscator;
import com.android.vending.licensing.LicenseChecker;
import com.android.vending.licensing.LicenseCheckerCallback;
import com.android.vending.licensing.ServerManagedPolicy;
import com.lucyapps.prompt.PromptActivity;
import com.lucyapps.prompt.R;

public class LicensingService extends Service {

	private static final long LICENSING_INTERVAL_INVALID_LICENSE = 10 * 60 * 1000;  // 10 min
	private static final long LICENSING_INTERVAL_VALID_LICENSE = 3 * 24 * 60 * 60 * 1000;  // 3 days
	private static final int VALID_LICENSE = 1;
	private static final int INVALID_LICENSE = -1;
	private static final int UNKNOWN_LICENSE = 0;
	// The public licensing key generated by the Android Market.
	private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA26rq/LL905oUX8i6kTqOfAKZiKxO38fcebMCPbNHnEQVYZSMbvHEg2FQdvM0pO7D65hF75Eo2BFJOipsdFPH16uH7DBtGQMSYl1woixlbqrdgErCeCGp9/Hy3TgAWBbNE7YKwwOK08Srgu9447COtbLfU0xWNA5z45JCijSsR01tBHZeSPBkygrt0LShgJ83Stb+gMb4DwQjMgdDarsRyMdT/KXTcdAP6NSZqXbp2BS39DyiqD0LOM6GGWo0Q4kxRUfJUibCPJ2/SWv6aLprEKpuyGrFC+X7uQiseann0gRsVt0f71z7IyNGCSUarHx9DqizK8AhYuBik3oEekgPOwIDAQAB";
    private static final byte[] SALT = new byte[] {82, 19, 15, 96, -83, 23, 72, 81, 38, 48, 52, -95, 84, 105, 46, -119, -39, -103, -38, -50};
    private static final String LICENSE_STATE_ID = ".licenseState";
	private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
	private int licenseState = UNKNOWN_LICENSE;

	public static void scheduleLicensingService(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final long updateInterval = (getLicenceState(prefs) == VALID_LICENSE) ?
				LICENSING_INTERVAL_VALID_LICENSE :
				LICENSING_INTERVAL_INVALID_LICENSE;

		AlarmManager mgr = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime(), updateInterval,
				createStartLicensingServicePendingIntent(context));
	}
	
	private void cancelLicensingService() {
		Context context = getApplicationContext();
		AlarmManager mgr = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		mgr.cancel(createStartLicensingServicePendingIntent(context));
	}
	
	private static PendingIntent createStartLicensingServicePendingIntent(Context context) {
		Intent i = new Intent();
		i.setAction("com.lucyapps.prompt.detail.LicensingService");
		return PendingIntent.getService(context, 0, i,
				PendingIntent.FLAG_UPDATE_CURRENT);
	}
	
	private SharedPreferences getServiceSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(getBaseContext());
	}
		
	public static int getLicenceState(SharedPreferences prefs) {
		return prefs.getInt(LICENSE_STATE_ID, UNKNOWN_LICENSE);
	}
	
	private void setLicenseState(int newLicenseState) {
		licenseState = newLicenseState;
		SharedPreferences.Editor editor = getServiceSharedPreferences().edit();
		editor.putInt(LICENSE_STATE_ID, newLicenseState);
		editor.commit();
	}

	@Override
	public void onCreate() {
		try {
			super.onCreate();
			licenseState = getLicenceState(getServiceSharedPreferences());
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		validateLicense();
	}

	@Override
	public void onDestroy() {
		try {
			if(mChecker != null) {
				mChecker.onDestroy();
				mChecker = null;
			}
			super.onDestroy();
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null; // don't need this
	}
	
	// Add check to service too
	private void validateLicense() {
		if (mChecker != null) {
			return; // Already have one check in progress.
		}
		mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        
        // Construct the LicenseChecker with a Policy.
        mChecker = new LicenseChecker(
        								this, 
        								new ServerManagedPolicy(
        										this,
        										new AESObfuscator(
        												SALT, 
        												getPackageName(), 
        												deviceId)),
        												BASE64_PUBLIC_KEY
            											);
        mChecker.checkAccess(mLicenseCheckerCallback);
    }
	
	private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
        public void allow() {
        	Log.v("LucyApps", "LicensingService: Prompt license is valid.");
        	setLicenseState(VALID_LICENSE);
        	cancelLicensingService();
        	stopSelf();
        }

        public void dontAllow() {
        	Log.w("LucyApps", "LicensingService: Prompt license is invalid.");
        	setLicenseState(INVALID_LICENSE);
        	showInvalidLicenseNotification();
        	stopSelf();
        }
        
		public void applicationError(ApplicationErrorCode errorCode) {
			Log.w("LucyApps", "LicensingService: Application error occurred while checking licensing: " + errorCode);
			stopSelf();
		}
    }

	private void showInvalidLicenseNotification() {
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				PromptActivity.getSendUserToMarketIntent(getPackageName()), 0);
		Notification notification = new Notification(
				R.drawable.ic_prompt_on_status_bar, 
				getText(R.string.buy_prompt_license_str), 
				System.currentTimeMillis());
		
		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(
				this, 
				getText(R.string.buy_prompt_license_str), 
				getText(R.string.invalid_license_str),
				pendingIntent);

		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_LIGHTS;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		// Post the notification
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (nm != null) {
			nm.cancel(R.string.license_service_notification_id);

			// Send the notification.
			// We use a layout id because it is a unique number. We use it later
			// to cancel.
			nm.notify(R.string.license_service_notification_id, notification);
			Log.v("LucyApps", "showInvalidLicenseNotification() sent notification for invalid Prompt license");
		}
	}

}
