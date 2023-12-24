package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.MapInfo
import androidx.room.Query
import com.example.signaldoctor.Settings
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.contracts.MsrsMap
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
abstract class PhoneMeasurementDAO : BaseMsrsDAO<PhoneMeasurement>() {

    @Query("SELECT * FROM phone_table WHERE id = :id ORDER BY date DESC")
    abstract fun getMeasurementInfo(id : Int) : Flow<PhoneMeasurement>

    @Query("SELECT * FROM phone_table ORDER BY DATE")
    abstract fun getMsrs() : Flow<List<PhoneMeasurement>>

    @Query("SELECT COUNT(tile_index) FROM phone_table WHERE :currentTile <= tile_index AND date > :limitDate")
    abstract fun countMeasures(currentTile : Long, limitDate: Date) : Flow<Int>

    @MapInfo(keyColumn = TableColumn.tile_index, valueColumn = TableColumn.value)
    @Query("SELECT tile_index, AVG(value) AS value FROM phone_table " +
            "WHERE date >= :oldness OR :oldness IS NULL AND date <= :freshness OR :freshness IS NULL " +
            "GROUP BY tile_index " +
            "ORDER BY date DESC LIMIT :msrsToTake")
   abstract fun getMsrsAvgs(freshness : Date? = null, oldness : Date? = null, msrsToTake : Int? = Int.MAX_VALUE) : Flow<Map<Long,Int>>
}