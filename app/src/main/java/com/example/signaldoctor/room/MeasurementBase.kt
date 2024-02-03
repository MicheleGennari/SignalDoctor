package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

data class MeasurementBase(
    @ColumnInfo(name= TableColumn.tile_index) val tileIndex : Long = 0,
    @ColumnInfo(name= TableColumn.uuid) val uuid : String =  UUID.randomUUID().toString(),
    @ColumnInfo(name= TableColumn.value) var value : Int = 0,
    @ColumnInfo(name= TableColumn.date)val date : String = SimpleDateFormat.getDateTimeInstance().format(Date()),
){

    companion object {

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
