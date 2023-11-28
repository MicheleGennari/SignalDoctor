package com.example.signaldoctor.bin

import androidx.room.ColumnInfo
import com.example.signaldoctor.room.TableColumn


data class MsrsMapEntry(
    @ColumnInfo(name = TableColumn.date) val date : Long? = null,
    @ColumnInfo(name = TableColumn.tile_index) val tileIndex : Long? = null,
    @ColumnInfo(name = TableColumn.value) val value : Int? = null
){


}
