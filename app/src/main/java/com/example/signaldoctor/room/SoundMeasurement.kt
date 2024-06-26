package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.PhoneMeasurementFirebase
import com.example.signaldoctor.realtimeFirebase.SoundMeasurementFirebase
import com.google.firebase.database.Exclude
import java.lang.annotation.Inherited

@Entity(tableName = "sound_table")
data class SoundMeasurement(

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TableColumn.id)
    override val id : Int? = null,

    @Embedded
     override val baseInfo : MeasurementBase = MeasurementBase(),



) : RoomMeasurementEntity(Measure.sound)