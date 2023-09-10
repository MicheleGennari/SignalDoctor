package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface PhoneMeasurementDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE )
    fun insert(measure : PhoneMeasurement)
}