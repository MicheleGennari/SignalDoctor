package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
abstract class WiFiMeasurementDAO : RoomMeasurementDAO<WiFIMeasurement>() {

    @Query("SELECT * FROM wifi_table WHERE id = :id ORDER BY date DESC")
    abstract override fun getMeasurementInfo(id : Int) : Flow<WiFIMeasurement>


    @Query("SELECT uuid,* FROM wifi_table ORDER BY DATE")
    abstract override fun getMsrs() : Flow<Map<@MapColumn(columnName = "uuid") String, WiFIMeasurement>>

    @Query("SELECT COUNT(tile_index) FROM phone_table WHERE :currentTile <= tile_index AND date >= :oldness")
    abstract override fun countMeasures(currentTile : Long, oldness: Date) : Flow<Int>

    @Query("SELECT MIN(date) FROM wifi_table")
    abstract override fun getOldestDate() : Flow<String>

    @Query("SELECT tile_index, AVG(value) AS value FROM wifi_table " +
            "WHERE date >= :oldness OR :oldness IS NULL AND date <= :freshness OR :freshness IS NULL " +
            "GROUP BY tile_index " +
            "ORDER BY date DESC LIMIT :msrsToTake")
    abstract override fun getMsrsAvgs(freshness : Date?, oldness : Date?, msrsToTake : Int?)
    : Flow<Map<@MapColumn(TableColumn.tile_index)Long,@MapColumn(TableColumn.value)Int>>

}