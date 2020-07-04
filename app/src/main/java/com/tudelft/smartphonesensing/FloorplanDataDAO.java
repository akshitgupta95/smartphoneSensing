package com.tudelft.smartphonesensing;

import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Update;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

@Dao
public interface FloorplanDataDAO {

    @Query("SELECT id,name FROM FloorplanData")
    List<FloorplanMeta> getAllNames();

    @Query("SELECT * FROM FloorplanData WHERE id=:id")
    FloorplanData getById(int id);

    @Query("SELECT * FROM FloorplanData ORDER BY lastChanged DESC LIMIT 1")
    FloorplanData getLastSaved();

    @Insert
    long insert(FloorplanData floorplan);

    @Update
    void update(FloorplanData floorplan);

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

        @ColumnInfo()
        private long lastChanged;

        static FloorplanData fromFloorplan(Floorplan floor) throws JSONException {
            FloorplanData data = new FloorplanData();
            data.setLayoutJson(floor.serialize().toString());
            data.setName(floor.getName());
            data.setId(floor.getId());
            data.setLastChanged(System.currentTimeMillis());
            return data;
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

        public long getLastChanged() {
            return lastChanged;
        }

        public void setLastChanged(long time) {
            lastChanged = time;
        }
    }
}
