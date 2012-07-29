/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.detail;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.lucyapps.prompt.util.ClassUtilities;
import com.lucyapps.prompt.util.SharedPreferencesSaveable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author Thor
 */
public class CalendarWrapper {

    public static class EventSummary implements SharedPreferencesSaveable {

        private String title;
        private String location;
        private long time;

        public EventSummary() {
        }

        public EventSummary(String etitle, String eLocation, long eTime) {
            title = etitle;
            location = eLocation;
            time = eTime;
        }

        public String getLocation() {
            return location;
        }

        public long getTime() {
            return time;
        }

        public String getTitle() {
            return title;
        }
        private static final String TITLE_ID = ".title";
        private static final String LOCATION_ID = ".location";
        private static final String TIME_ID = ".time";

        public void saveState(SharedPreferences.Editor editor, String prefix) {
            editor.putString(prefix + TITLE_ID, title);
            editor.putString(prefix + LOCATION_ID, location);
            editor.putLong(prefix + TIME_ID, time);
        }

        public void loadState(SharedPreferences prefs, String prefix) {
            title = prefs.getString(prefix + TITLE_ID, null);
            location = prefs.getString(prefix + LOCATION_ID, null);
            time = prefs.getLong(prefix + TIME_ID, 0);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EventSummary)) {
                return false;
            }
            EventSummary e = (EventSummary) o;
            return ClassUtilities.equals(title, e.title)
                    && ClassUtilities.equals(location, e.location)
                    && time == e.time;
        }

        @Override
        public int hashCode() {
            int hash = 13;
            hash = hash * 83 + title == null ? 0 : title.hashCode();
            hash = hash * 83 + location == null ? 0 : location.hashCode();
            hash = hash * 83 + (int) time;
            return hash;
        }

        @Override
        public String toString() {
            return "CalendarWrapper{title:" + title + ", location:" + location + ", time:" + time + "}";
        }
    }
    private static String calendarContentProviderStr;
//    private static Uri calendarsContentUri;
//    private static Uri eventsContentUri;
//    private static Uri remindersContentUri;

    static {
        if (Build.VERSION.SDK_INT >= 8 /* FROYO */) {
//        if (Build.VERSION.RELEASE.contains("2.2")) {
            calendarContentProviderStr = "com.android.calendar";
        } else {
            calendarContentProviderStr = "calendar";
        }

//        calendarsContentUri = Uri.parse(String.format("content://%s/calendars", calendarContentProviderStr));
//        eventsContentUri = Uri.parse(String.format("content://%s/events", calendarContentProviderStr));
//        remindersContentUri = Uri.parse(String.format("content://%s/reminders", calendarContentProviderStr));
    }

    public static class CalendarSummary {

        public String name;
        public String id;

        public CalendarSummary(String n, String i) {
            name = n;
            id = i;
        }
    }

    public static List<CalendarSummary> getCalendarIds(Context context) {
        List<CalendarSummary> calendars = new ArrayList<CalendarSummary>();
        Cursor managedCursor = null;
        try {
            String[] projection = new String[]{"_id", "name"};
            Uri calendarsUri = Uri.parse("content://" + calendarContentProviderStr + "/calendars");
            managedCursor = context.getContentResolver().query(
                    calendarsUri,
                    projection,
                    "selected=1",
                    null,
                    null);

            if (managedCursor != null && managedCursor.moveToFirst()) {
                int nameColumn = managedCursor.getColumnIndex("name");
                int idColumn = managedCursor.getColumnIndex("_id");
                do {
                    calendars.add(new CalendarSummary(managedCursor.getString(nameColumn), managedCursor.getString(idColumn)));
                } while (managedCursor.moveToNext());
            }
        } catch (Throwable t) {
            Log.w(CalendarWrapper.class.getSimpleName(), t);
        } finally {
        	if (managedCursor != null) {
        		managedCursor.close();
        	}
        }
        return calendars;
    }

    public static EventSummary getNextEventSummary(
            Context context,
            long nextEventTimeWindow,
            List<CalendarSummary> calendars) {

        EventSummary nextEvent = null;

        //chose the next event as the soonest event from all calendars
        for (CalendarSummary cal : calendars) {
            EventSummary calNextEvent = getNextEventSummary(context, nextEventTimeWindow, cal.id);
            if (calNextEvent != null) {
                if (nextEvent == null || calNextEvent.time < nextEvent.time) {
                    nextEvent = calNextEvent;
                }
            }
        }
        return nextEvent;
    }

    public static EventSummary getNextEventSummary(Context context, long nextEventTimeWindow, String calId) {
        Uri.Builder builder = Uri.parse("content://" + calendarContentProviderStr + "/instances/when").buildUpon();
        final long now = System.currentTimeMillis();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, now + nextEventTimeWindow);

        ContentResolver contentResolver = context.getContentResolver();
        Cursor eventCursor = contentResolver.query(builder.build(),
                new String[]{
                    //TODO Remove unused fields
                    "title", //0
                    "begin", //1
                    "end", //2
                    "allDay", //3
                    "eventLocation",//4
                    "visibility" //5
                },
                "Calendars._id=" + calId,
                null, "startDay ASC, startMinute ASC");
        // For a full list of available columns see http://tinyurl.com/yfbg76w

		try {
			while (eventCursor != null && eventCursor.moveToNext()) {
				final String title = eventCursor.getString(0);
				final Date begin = new Date(eventCursor.getLong(1));
				// final Date end = new Date(eventCursor.getLong(2));
				// final Boolean allDay = !eventCursor.getString(3).equals("0");
				String location = eventCursor.getString(4);
				// final String visibility = eventCursor.getString(5);

				final long beginTime = begin.getTime();
				if (beginTime >= now) {
					if (location != null
							&& (location = location.trim()).length() > 0) {
						return new EventSummary(title, location, beginTime);
					}
				} // else we are in the middle of an event
			}
		} finally {
			if (eventCursor != null) {
				eventCursor.close();
			}
		}
		return null;
    }
}
