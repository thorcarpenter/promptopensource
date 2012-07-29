/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.detail;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import com.lucyapps.prompt.util.ClassUtilities;
import com.lucyapps.prompt.util.RestClient;
import com.lucyapps.prompt.util.RestClient.RequestMethod;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Thor
 */
public class MapsWrapper {

    public static final long ERROR_CODE = -1;
    public static final long NO_ROUTE = -2;
    public static final String[] GOOGLE_MAPS_TRAVEL_MODES = { "driving", "walking", "bicycling" };
    // See http://code.google.com/apis/maps/documentation/directions/
    private static final String GOOGLE_MAPS_BASE_URL = "http://maps.google.com/maps/api/directions/json";
    private static final String GOOGLE_MAPS_ORIGIN_PARAM = "origin";
    private static final String GOOGLE_MAPS_DESTINATION_PARAM = "destination";
    private static final String GOOGLE_MAPS_SENSOR_PARAM = "sensor";
    private static final String GOOGLE_MAPS_MODE_PARAM = "mode";

    /**
     * Get the travel time in milliseconds from currentLocation to destination or a negative value if an error occurred.
     *
     * @param currentLocation
     * @param destination
     * @param travelMode a String from GOOGLE_MAPS_TRAVEL_MODES
     * @return Return time in milliseconds from currentLocation to destination or a value less than 0 if error
     * @throws IllegalArgumentException If currentLocation or destination are null or empty
     * @throws JSONException If there was an error parsing the JSON response.
     * @throws Exception If there was an error fetching the JSON response.
     */
    public static long getTravelTime(Location currentLocation, String destination, String travelMode)
            throws IllegalArgumentException, JSONException, Exception {
        if (currentLocation == null || 
            ClassUtilities.nullOrEmpty(destination) || 
            ClassUtilities.nullOrEmpty(travelMode)) {
            throw new IllegalArgumentException("getTravelTime(): currentLocation, destination and travelMode may not be null");
        }
        final String start = currentLocation.getLatitude() + "," + currentLocation.getLongitude();
        JSONObject json = getDirections(start, destination, travelMode);
        String status = json.getString("status");
        if (!status.equals("OK")) {
            Log.w(MapsWrapper.class.getSimpleName(), "Expected status 'OK' but "
                    + "getDirections(" + start + "," + destination + "," + travelMode + ") returned status '" + status + "'");
            return ERROR_CODE;
        }
        JSONArray routesArray = json.getJSONArray("routes");
        if (routesArray.length() == 0) {
            Log.d(MapsWrapper.class.getSimpleName(), "No route found for getDirections(" + start + "," + destination + "," + travelMode + ")");
            return NO_ROUTE;
        }
        JSONObject route0 = routesArray.getJSONObject(0);
        JSONArray legs = route0.getJSONArray("legs");

        long duration = 0;
        for (int l = 0; l < legs.length(); ++l) {
            JSONArray steps = legs.getJSONObject(l).getJSONArray("steps");
            for (int s = 0; s < steps.length(); ++s) {
                duration += steps.getJSONObject(s).getJSONObject("duration").getLong("value");
            }
        }
        return duration * 1000;
    }

    public static Intent getDirectionsIntent(String destination) {
        String directionsIntent = "geo:0,0?q=" + destination;
//        String directionsIntent = "http://maps.google.com/maps?daddr=" + destination;
//        String directionsIntent = "http://maps.google.com/maps?saddr=20.344,34.34&daddr=20.5666,45.345";

        return new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(directionsIntent));
    }

    /**
     * @param start cannot be null or empty
     * @param end cannot be null or empty
     * @param travelMode a String from GOOGLE_MAPS_TRAVEL_MODES
     * @return JSONObject of the directions or null
     * @throws Exception if Google Maps service is unavailable
     * @throws JSONException if there was an error parsing the results
     */
    private static JSONObject getDirections(String start, String end, String travelMode) throws JSONException, Exception {
        if (ClassUtilities.nullOrEmpty(start) || 
        	ClassUtilities.nullOrEmpty(end)	||
        	ClassUtilities.nullOrEmpty(travelMode)) {
            return null;
        }

        RestClient client = new RestClient(GOOGLE_MAPS_BASE_URL);
        client.AddParam(GOOGLE_MAPS_ORIGIN_PARAM, start);
        client.AddParam(GOOGLE_MAPS_DESTINATION_PARAM, end);
        client.AddParam(GOOGLE_MAPS_SENSOR_PARAM, "true");
        client.AddParam(GOOGLE_MAPS_MODE_PARAM, travelMode);

        try {
            client.Execute(RequestMethod.GET);
        } catch (Throwable t) {
            Log.w(MapsWrapper.class.getSimpleName(), t);
            throw new Exception(
                    "getDirections() An exception occurred while accessing Google Maps service.", t);
        }

        String response = client.getResponse();
        JSONObject json = new JSONObject(response);
        return json;
    }
}
