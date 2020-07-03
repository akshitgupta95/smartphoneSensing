package com.tudelft.smartphonesensing;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class LocationCell {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int floorplanId;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFloorplanId() {
        return floorplanId;
    }

    public void setFloorplanId(int floorplanId) {
        this.floorplanId = floorplanId;
    }
}
