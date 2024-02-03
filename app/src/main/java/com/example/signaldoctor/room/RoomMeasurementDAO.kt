package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow
import java.util.Date


@Dao
abstract class RoomMeasurementDAO<T : RoomMeasurementEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE )
    abstract fun postMsr(measurement : T)

    abstract fun getMeasurementInfo(id : Int) : Flow<T>


    abstract fun getMsrs() : Flow<Map<@MapColumn(columnName = "uuid") String, T>>


    abstract fun countMeasures(currentTile : Long, maxOldness: Date) : Flow<Int>


    abstract fun getOldestDate() : Flow<String>

    abstract fun getMsrsAvgs(freshness : Date? = null, oldness : Date? = null, msrsToTake : Int? = Int.MAX_VALUE) : Flow<Map<Long,Int>>

}