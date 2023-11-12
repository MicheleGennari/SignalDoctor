package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import java.util.UUID

data class MeasurementBase(
    @ColumnInfo(name= TableColumn.TILE_INDEX) val tileIndex : Long = 0 ,
    @ColumnInfo(name= TableColumn.UUID) val uuid : UUID = UUID.randomUUID(),
    @ColumnInfo(name= TableColumn.VALUE) val value : Int = 0,
    @ColumnInfo(name= TableColumn.DATE) val date : Long = 0,
){
    companion object s{

        const val MSR_TYPE_KEY = "msrType"
        const val MSR_KEY = "msr"
        const val TILE_INDEX_KEY = "tileIndex"
        const val Z_MAX_KEY = "zMax"
        const val Z_MIN_KEY = "zMin"
        const val LAT_KEY = "lat"
        const val LONG_KEY = "long"

    }

    fun asWorkDataPairs() = listOf(
        MSR_KEY to value,
        TILE_INDEX_KEY to tileIndex
    )

}
