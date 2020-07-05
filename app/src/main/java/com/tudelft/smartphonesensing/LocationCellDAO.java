package com.tudelft.smartphonesensing;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocationCellDAO {

    /**
     * @deprecated should no longer be necessary
     */
    @Query("SELECT * FROM locationcell")
    List<LocationCell> getAll();

    @Query("SELECT * FROM LocationCell WHERE floorplanId=:floorplan")
    List<LocationCell> getAllInFloorplan(int floorplan);

    @Query("SELECT * FROM locationcell WHERE id=:id")
    LocationCell get(int id);

    @Insert
    long insert(LocationCell cell);


    @Update
    int updateLocationCell(LocationCell cell);
}