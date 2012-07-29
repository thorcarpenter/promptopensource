/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.util.Log;

/**
 *
 * @author Thor
 */
public class LocationUtil {

    private static final String ACCURACY_ID = ".accuracy";
    private static final String BEARING_ID = ".bearing";
    private static final String SPEED_ID = ".speed";
    private static final String TIME_ID = ".time";
    private static final String ALTITUDE_ID = ".altitude";
    private static final String LATITUDE_ID = ".latitude";
    private static final String LONGITUDE_ID = ".longitude";
    private static final String PROVIDER_ID = ".provider";

    private LocationUtil() {
        //prevent instantiation
    }

    public static void saveState(Location location, Editor editor, String prefix) {
        if (location != null) {
            editor.putString(prefix + PROVIDER_ID, location.getProvider());
            editor.putFloat(prefix + ACCURACY_ID, location.getAccuracy());
            editor.putFloat(prefix + BEARING_ID, location.getBearing());
            editor.putFloat(prefix + SPEED_ID, location.getSpeed());
            editor.putLong(prefix + TIME_ID, location.getTime());
            editor.putString(prefix + ALTITUDE_ID, location.getAltitude() + ""); //double stored as string
            editor.putString(prefix + LATITUDE_ID, location.getLatitude() + ""); //double stored as string
            editor.putString(prefix + LONGITUDE_ID, location.getLongitude() + ""); //double stored as string
        }
    }

    public static Location loadState(SharedPreferences prefs, String prefix) {
        Location location = new Location(prefs.getString(prefix + PROVIDER_ID, "null provider"));
        location.setAccuracy(prefs.getFloat(prefix + ACCURACY_ID, 0.0f));
        location.setBearing(prefs.getFloat(prefix + BEARING_ID, 0.0f));
        location.setSpeed(prefs.getFloat(prefix + SPEED_ID, 0.0f));
        location.setTime(prefs.getLong(prefix + TIME_ID, 0));
        try {
            location.setAltitude(Double.parseDouble(prefs.getString(prefix + ALTITUDE_ID, "0"))); //double stored as string
            location.setLatitude(Double.parseDouble(prefs.getString(prefix + LATITUDE_ID, "0"))); //double stored as string
            location.setLongitude(Double.parseDouble(prefs.getString(prefix + LONGITUDE_ID, "0")));//double stored as string
        } catch (NumberFormatException e) {
            Log.d(LocationUtil.class.getSimpleName(), e.getMessage());
        }
        return location;
    }
}
