package com.example.signaldoctor.bin

import androidx.room.ColumnInfo
import com.example.signaldoctor.room.TableColumn


data class MsrsMapEntry(
    @ColumnInfo(name = TableColumn.DATE) val date : Long? = null,
    @ColumnInfo(name = TableColumn.TILE_INDEX) val tileIndex : Long? = null,
    @ColumnInfo(name = TableColumn.VALUE) val value : Int? = null
){


}
