/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.util;

import android.os.Looper;

/**
 *
 * @author Thor
 */
public class LooperThread extends Thread {

    private Looper thisThreadsLooper;

    public Looper getLooper() {
        return thisThreadsLooper;
    }

    public void quit() {
        Looper myLooper = thisThreadsLooper;
        if (myLooper != null) {
            myLooper.quit();
        }
        thisThreadsLooper = null;
    }

//    @Override
//    public void start() {
//        if(thisThreadsLooper == null) {
//            super.start();
//        }
//    }

    @Override
    public void run() {
        Looper.prepare();

        thisThreadsLooper = Looper.myLooper();

        Looper.loop();

        thisThreadsLooper = null;
    }
};
