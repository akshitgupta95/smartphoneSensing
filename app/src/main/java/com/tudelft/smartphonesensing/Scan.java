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
    private String MAC;

    @ColumnInfo(name = "RSSi")
    private int RSSi;

    @ColumnInfo(name = "level")
    private int level;

    @ColumnInfo(name = "freq")
    private int freq;

    @ColumnInfo(name = "loc")
    private String loc;

    @ColumnInfo(name = "time")
    private long time;


    public Scan(String MAC, String SSID, int RSSi, int level, int freq, String loc, long time) {
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

    public String getMAC() {
        return MAC;
    }

    public void setMAC(String MAC) {
        this.MAC = MAC;
    }

    public int getRSSi() {
        return RSSi;
    }

    public void setRSSi(int RSSi) {
        this.RSSi = RSSi;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public String getLoc() {
        return loc;
    }

    public void setLoc(String loc) {
        this.loc = loc;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
