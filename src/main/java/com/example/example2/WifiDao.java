package com.example.example2;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WifiDao {
    @Query("SELECT * FROM wifi")
    List<Wifi> getAllWifis();

    @Insert
    void InsertAll(Wifi... wifis);
}




