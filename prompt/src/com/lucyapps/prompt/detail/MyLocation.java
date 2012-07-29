/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.detail;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.lucyapps.prompt.util.LooperThread;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapted from: http://stackoverflow.com/questions/3145089/what-is-the-simplest-and-most-robust-way-to-get-the-users-current-location-in-an
 *
 * @author Thor
 */
public class MyLocation {

    private final long minUpdateTime;
    private final long minUpdateDistance;
    private LocationManager locationManager;
    private LocationResult locationResult;
    private LooperThread looperThread = new LooperThread();
    private List<LocationListener> locationListeners = new ArrayList<LocationListener>();

    public MyLocation(
            Context context,
            LocationResult result,
            long minTimeBetweenUpdates,
            long minDistanceBetweenUpdates) {
        if (result == null) {
            throw new IllegalArgumentException("LocationResult callback cannot be null");
        }
        minUpdateTime = minTimeBetweenUpdates;
        minUpdateDistance = minDistanceBetweenUpdates;

        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        //I use LocationResult callback class to pass location value from MyLocation to user code.
        locationResult = result;
    }

    /** @return Returns true if gps or network location was enabled and started.  False otherwise. */
    public synchronized boolean start() {
        stop(); //be sure there isn't another looper running
        looperThread.start();
        return registerListeners();
    }

    /**
     * Start listening for a specific location provider.
     * 
     * @param provider Either LocationManager.GPS_PROVIDER or LocationManager.NETWORK_PROVIDER
     * @return Returns true if specified location provider was enabled and started.  False otherwise.
     */
    public synchronized boolean start(String provider) {
        stop(); //be sure there isn't another looper running
        looperThread.start();
        return registerListener(provider);
    }

    public synchronized void stop() {
        clearLocationListners();
        looperThread.quit();
    }

    private synchronized boolean registerListeners() {
        try {
            clearLocationListners();
            
            final boolean successNetwork = registerNetworkListener();
            final boolean successGPS = registerGPSListener();

            if (successNetwork || successGPS) {
                return true;
            } else {
                Log.v("LucyApps", "GPS and Network location providers are disabled; cannot retrieve location.");
            }
        } catch (Throwable t) {
            Log.w("LucyApps", t);
        }
        return false;
    }

    /** @return Returns true if registration was successful. */
    private boolean registerGPSListener() {
        return registerListener(LocationManager.GPS_PROVIDER);
    }

    /** @return Returns true if registration was successful. */
    private boolean registerNetworkListener() {
        return registerListener(LocationManager.NETWORK_PROVIDER);
    }

    /** @return Returns true if registration was successful. */
    private synchronized boolean registerListener(String provider) {
        if (locationManager.isProviderEnabled(provider)) {
            Log.v("LucyApps", "Listening for location fix from provider " + provider);
            LocationFixListener lfl = new LocationFixListener();
            locationListeners.add(lfl);
            locationManager.requestLocationUpdates(provider, minUpdateTime, minUpdateDistance, lfl, looperThread.getLooper());
            return true;
        }
        Log.v("LucyApps", "Cannot listen for location fix from disabled provider " + provider);
        return false;
    }

    private synchronized void clearLocationListners() {
        for (LocationListener ll : locationListeners) {
            locationManager.removeUpdates(ll);
        }
        locationListeners.clear();
    }

    private class LocationFixListener implements LocationListener {

        public void onLocationChanged(Location location) {
            locationResult.gotLocation(location);
        }

        public void onProviderDisabled(String provider) {
        	registerListeners();
        }

        public void onProviderEnabled(String provider) {
        	registerListeners();
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        	registerListeners();
        }
    }

//    class GetLastLocation extends TimerTask {
//
//        @Override
//        public void run() {
//            locationManager.removeUpdates(locationListener);
//
//            Location net_loc = null, gps_loc = null;
//            if (gps_enabled) {
//                gps_loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//            }
//            if (network_enabled) {
//                net_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
//            }
//
//            //if there are both values use the latest one
//            if (gps_loc != null && net_loc != null) {
//                if (gps_loc.getTime() > net_loc.getTime() - minUpdateTime) {
//                    locationResult.gotLocation(gps_loc);
//                } else {
//                    locationResult.gotLocation(net_loc);
//                }
//                return;
//            }
//
//            if (gps_loc != null) {
//                locationResult.gotLocation(gps_loc);
//                return;
//            }
//            if (net_loc != null) {
//                locationResult.gotLocation(net_loc);
//                return;
//            }
//            locationResult.gotLocation(null);
//        }
//    }
    public static abstract class LocationResult {

        public abstract void gotLocation(Location location);
    }
}
