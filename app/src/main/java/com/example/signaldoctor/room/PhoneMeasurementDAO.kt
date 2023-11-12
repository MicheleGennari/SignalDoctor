package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.Query
import com.example.signaldoctor.bin.MsrsMapEntry
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PhoneMeasurementDAO : BaseMsrsDAO<PhoneMeasurement>() {

    @Query("SELECT * FROM phone_table WHERE id = :id ORDER BY date DESC")
    abstract fun getMeasurementInfo(id : Int) : Flow<PhoneMeasurement>

    @Query("SELECT date,tile_index,AVG(value) AS value FROM phone_table"+
           // " WHERE date BETWEEN :startDate AND :endDate" +
           " GROUP BY tile_index "//+"LIMIT :number"
    )
    abstract fun getMsrsAvgs() : Flow<List<MsrsMapEntry>>
}