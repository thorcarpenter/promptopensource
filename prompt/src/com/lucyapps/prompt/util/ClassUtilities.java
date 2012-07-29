/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.util;

/**
 *
 * @author Thor
 */
public class ClassUtilities {

    private ClassUtilities() {
        //prevent instantiation
    }

    public static boolean equals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1 == null || o2 == null) {
            return false;
        }
        return o1.equals(o2);
    }

    public static boolean nullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }
}
