package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
abstract class SoundMeasurementDAO : RoomMeasurementDAO<SoundMeasurement>() {

    @Query("SELECT * FROM sound_table WHERE id = :id ORDER BY date DESC")
    abstract override fun getMeasurementInfo(id : Int) : Flow<SoundMeasurement>


    @Query("SELECT uuid,* FROM sound_table ORDER BY DATE")
    abstract override fun getMsrs() : Flow<Map<@MapColumn(columnName = "uuid") String, SoundMeasurement>>

    @Query("SELECT COUNT(tile_index) FROM phone_table WHERE :currentTile <= tile_index AND date >= :oldness")
    abstract override fun countMeasures(currentTile : Long, oldness: Date) : Flow<Int>

    @Query("SELECT MIN(date) FROM sound_table")
    abstract override fun getOldestDate() : Flow<String>

    @Query("SELECT tile_index, AVG(value) AS value FROM sound_table " +
            "WHERE date >= :oldness OR :oldness IS NULL AND date <= :freshness OR :freshness IS NULL " +
            "GROUP BY tile_index " +
            "ORDER BY date DESC LIMIT :msrsToTake")
    abstract override fun getMsrsAvgs(freshness : Date?, oldness : Date?, msrsToTake : Int?)
    : Flow<Map<@MapColumn(TableColumn.tile_index)Long,@MapColumn(TableColumn.value)Int>>

}