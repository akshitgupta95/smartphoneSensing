package com.tudelft.smartphonesensing;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Scan.class, FloorplanDataDAO.FloorplanData.class, LocationCell.class}, version = 6)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance = null;

    public abstract ScanDAO scanDAO();

    public abstract FloorplanDataDAO floorplanDataDAO();

    public abstract LocationCellDAO locationCellDAO();

    public static AppDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "production")
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
