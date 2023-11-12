package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.Query
import com.example.signaldoctor.bin.MsrsMapEntry
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WiFiMeasurementDAO : BaseMsrsDAO<WiFIMeasurement>() {

    @Query("SELECT * FROM wifi_table WHERE id = :id ORDER BY date DESC")
    abstract  fun getMeasurementInfo(id : Int) : Flow<WiFIMeasurement>

    @Query("SELECT date, tile_index, AVG(value) AS value FROM wifi_table GROUP BY tile_index")
    abstract  fun getMsrsAvgs() : Flow<List<MsrsMapEntry>>

}