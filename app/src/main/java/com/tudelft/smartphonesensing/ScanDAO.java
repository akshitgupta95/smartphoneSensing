package com.tudelft.smartphonesensing;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScanDAO {
    @Query("SELECT * FROM Scan")
    List<Scan> getAllScanResults();

    @Insert
    void InsertAll(Scan... scanresults);

    //Get all locations in the database
    @Query("SELECT DISTINCT loc FROM Scan")
    List<String> getAllLocations();

    // Get all scans from a certain location
    @Query("SELECT * FROM Scan WHERE loc = :loc")
    List<Scan> getAllScansLoc(String loc);
}
