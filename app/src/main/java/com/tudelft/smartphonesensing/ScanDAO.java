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
}
