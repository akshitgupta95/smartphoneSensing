package com.tudelft.smartphonesensing;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Scan {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "SSID")
    private String SSID;

    @ColumnInfo(name = "MAC")
    private long MAC;

    @ColumnInfo(name = "RSSi")
    private int RSSi;

    @ColumnInfo(name = "level")
    private double level;

    @ColumnInfo(name = "freq")
    private int freq;

    @ColumnInfo(name = "loc")
    private int loc;

    @ColumnInfo(name = "time")
    private long time;


    public Scan(long MAC, String SSID, int RSSi, double level, int freq, int loc, long time) {
        this.MAC = MAC;
        this.SSID = SSID;
        this.RSSi = RSSi;
        this.level = level;
        this.freq = freq;
        this.loc = loc;
        this.time = time;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("[%s] %s dBm, %s level (%s)", this.MAC, this.RSSi, this.level, this.SSID);
    }

    /* Getters and Setters */

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSSID() {
        return SSID;
    }

    public void setSSID(String SSID) {
        this.SSID = SSID;
    }

    public long getMAC() {
        return MAC;
    }

    public void setMAC(long MAC) {
        this.MAC = MAC;
    }

    public void setMAC(String MAC) {
        this.MAC = Util.macStringToLong(MAC);
    }

    public int getRSSi() {
        return RSSi;
    }

    public void setRSSi(int RSSi) {
        this.RSSi = RSSi;
    }

    public double getLevel() {
        return level;
    }

    public void setLevel(double level) {
        this.level = level;
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public int getLoc() {
        return loc;
    }

    public void setLoc(int loc) {
        this.loc = loc;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
