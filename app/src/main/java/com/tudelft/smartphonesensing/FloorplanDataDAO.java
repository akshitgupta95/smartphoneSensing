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
public interface FloorplanDataDAO {

    @Query("SELECT id,name FROM FloorplanData")
    List<FloorplanMeta> getAllNames();

    @Query("SELECT * FROM FloorplanData WHERE id=:id")
    FloorplanData getById(int id);

    @Insert
    void InsertAll(FloorplanData... floorplans);

    class FloorplanMeta {
        @ColumnInfo()
        public int id;

        @ColumnInfo()
        public String name;
    }

    @Entity
    class FloorplanData {
        @PrimaryKey(autoGenerate = true)
        private int id;

        @ColumnInfo()
        private String name;

        @ColumnInfo()
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

        void setFloorplan(Floorplan floor, String name) {
            try {
                layoutJson = floor.serialize().toString();
                this.name = name;
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

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
