package com.tudelft.smartphonesensing;

import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

@Dao
public interface FloorplanMetaDAO {

    @Entity
    public static class FloorplanMeta {
        @PrimaryKey(autoGenerate = true)
        private int id;

        @ColumnInfo(name = "layoutJson")
        private String layoutJson;

        Floorplan getFloorplan() {
            Floorplan floor = new Floorplan();
            try {
                JSONObject obj = new JSONObject(layoutJson);
                floor.deserialize(obj);
            } catch (JSONException err) {
                //TODO add toast message error and remove from database?
                return null;
            }
            return floor;
        }

        void setFloorplan(Floorplan floor) {
            try {
                layoutJson = floor.serialize().toString();
            } catch (JSONException err) {
                //TODO add toast message+clean up data
            }
        }

        public String getLayoutJson() {
            return layoutJson;
        }

        public void setLayoutJson(String layoutJson) {
            this.layoutJson = layoutJson;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    //TODO add ui and stuff so we don't need "getlast" but get using name/id instead
    @Query("SELECT * FROM FloorplanMeta ORDER BY id DESC LIMIT 1")
    FloorplanMeta getLast();

    @Insert
    void InsertAll(FloorplanMeta... floorplans);
}
