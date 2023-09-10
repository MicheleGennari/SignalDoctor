package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundMeasurementDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE )
    fun insert(measure : SoundMeasurement)

    @Query("SELECT * FROM sound_table WHERE id = :id ORDER BY date DESC")
    fun findMeasurement(id : Int) : Flow<SoundMeasurement>

}