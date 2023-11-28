package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.MapInfo
import androidx.room.Query
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.contracts.MsrsMap
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
abstract class WiFiMeasurementDAO : BaseMsrsDAO<WiFIMeasurement>() {

    @Query("SELECT * FROM wifi_table WHERE id = :id ORDER BY date DESC")
    abstract  fun getMeasurementInfo(id : Int) : Flow<WiFIMeasurement>


    @Query("SELECT * FROM wifi_table ORDER BY DATE")
    abstract fun getMsrs() : Flow<List<WiFIMeasurement>>
    @MapInfo(keyColumn = TableColumn.tile_index, valueColumn = TableColumn.value)
    @Query("SELECT * FROM wifi_table"+
            " WHERE date BETWEEN :freshness AND :oldness" +
            " LIMIT :x"
    )
    abstract  fun getMsrsAvgs(x : Long = Long.MAX_VALUE, freshness : Date = Date(), oldness : Long = Long.MAX_VALUE) : Flow<Map<Long,Int>>

}