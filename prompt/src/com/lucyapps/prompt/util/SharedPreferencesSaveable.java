/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.util;

import android.content.SharedPreferences;

/**
 *
 * @author Thor
 */
public interface SharedPreferencesSaveable {

    /** Writes state to SharedPreferences.Editor but does not commit(). */
    public void saveState(SharedPreferences.Editor editor, String prefix);

    /**
     * Loads state from SharedPreferences.  If the state cannot be recovered from SharedPreferences,
     * an IllegalArgumentException may be thrown but it preferrable to instead load a default valid state.
     * @throws IllegalArgumentException If there was an error loading the state.
     */
    public void loadState(SharedPreferences prefs, String prefix) throws IllegalArgumentException;
    
}
