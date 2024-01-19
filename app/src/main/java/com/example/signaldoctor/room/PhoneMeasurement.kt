package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.realtimeFirebase.FirebaseMeasurementEntity
import com.example.signaldoctor.realtimeFirebase.PhoneMeasurementFirebase

@Entity(tableName = "phone_table")
data class PhoneMeasurement(

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TableColumn.id)
    override val id : Int? = null,

    @Embedded
    override val firebaseTable : FirebaseMeasurementEntity = PhoneMeasurementFirebase()

) : RoomMeasurementEntity()

