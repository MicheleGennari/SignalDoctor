package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Room
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.WifiMeasurementFirebase
import com.google.firebase.database.Exclude

@Entity(tableName = "wifi_table")
data class WiFIMeasurement(

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TableColumn.id)
    override val id : Int? = null,

    @Embedded
    override val baseInfo: MeasurementBase = MeasurementBase(),


) : RoomMeasurementEntity(Measure.wifi)