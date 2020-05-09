package com.example.example2;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity
public class Wifi {
    public Wifi(String ssid, String mac, int rssi, int freq, String loc, long time) {
        this.ssid = ssid;
        this.mac = mac;
        this.rssi = rssi;
        this.freq = freq;
        this.loc = loc;
        this.time = time;
    }

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "ssid")
    private String ssid;

    @ColumnInfo(name = "mac")
    private String mac;

    @ColumnInfo(name = "rssi")
    private int rssi;

    @ColumnInfo(name = "freq")
    private int freq;

    @ColumnInfo(name = "loc")
    private String loc;

    @ColumnInfo(name = "time")
    private long time;

    public int getId() {
        return id;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
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

//    Dont know why we need this as it is automatically generated
    public void setId(int id) {
        this.id = id;
    }
}
