package com.tudelft.smartphonesensing;

public class Util {

    static long macStringToLong(String mac) {
        return Long.parseLong(mac.replace(":", ""), 16);
    }

    static String macLongToString(long mac) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x", (mac >> 40) & 0xff, (mac >> 32) & 0xff, (mac >> 24) & 0xff, (mac >> 16) & 0xff, (mac >> 8) & 0xff, (mac >> 0) & 0xff);
    }
}
