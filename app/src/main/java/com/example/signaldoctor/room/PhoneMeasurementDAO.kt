package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.utils.msrTypeWhen
import com.example.signaldoctor.utils.whenMsrType
import com.example.signaldoctor.workers.BaseMsrWorker
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
abstract class PhoneMeasurementDAO : RoomMeasurementDAO<PhoneMeasurement>() {

    @Query("SELECT * FROM phone_table WHERE id = :id ORDER BY date DESC")
    abstract override fun getMeasurementInfo(id : Int) : Flow<PhoneMeasurement>

    @Query("SELECT uuid,* FROM phone_table ORDER BY DATE")
    abstract override fun getMsrs() : Flow<Map<@MapColumn(TableColumn.uuid) String, PhoneMeasurement>>


    @Query("SELECT COUNT(tile_index) FROM phone_table WHERE :currentTile <= tile_index AND date > :maxOldness")
    abstract override fun countMeasures(currentTile : Long, maxOldness: Date) : Flow<Int>

    @Query("SELECT MIN(date) FROM phone_table")
    abstract override fun getOldestDate() : Flow<String>

    @Query("SELECT tile_index, AVG(value) AS value FROM phone_table " +
            "WHERE date >= :oldness OR :oldness IS NULL AND date <= :freshness OR :freshness IS NULL " +
            "GROUP BY tile_index " +
            "ORDER BY date DESC LIMIT :msrsToTake")
   abstract override fun getMsrsAvgs(freshness : Date?, oldness : Date?, msrsToTake : Int?)
   : Flow<Map<@MapColumn(TableColumn.tile_index)Long,@MapColumn(TableColumn.value)Int>>
}