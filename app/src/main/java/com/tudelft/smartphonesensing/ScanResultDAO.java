package com.tudelft.smartphonesensing;

public class ScanResultDAO {


    private int RSSI;
    private int level;

    public ScanResultDAO(int RSSI, int level) {
        this.RSSI = RSSI;
        this.level = level;
    }
    public int getRSSI() {
        return RSSI;
    }

    public void setRSSI(int RSSI) {
        this.RSSI = RSSI;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }


}
