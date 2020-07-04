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

    // Get all scans from a certain location
    @Query("SELECT * FROM Scan WHERE loc = :loc")
    List<Scan> getAllScansAtLocation(int loc);

    // Get all mac addresses at a location
    @Query("SELECT distinct MAC from Scan WHERE loc = :loc")
    List<Long> getAllMacsAtLocation(int loc);

    // Get all scans with given mac address and location
    @Query("SELECT * FROM scan WHERE loc = :loc AND MAC = :mac")
    List<Scan> getAllScansWithMacAndLocation(int loc, long mac);
}
