package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sound_table")
data class SoundMeasurement(
    @PrimaryKey @ColumnInfo(name = "id") val id : Int,
    @ColumnInfo(name="tile_index")  val tileIndex : Long,
    @ColumnInfo(name="value") val value : Int,
    @ColumnInfo(name="date") val date : Long,
    @ColumnInfo(name="user_id") val userId : Int,
)