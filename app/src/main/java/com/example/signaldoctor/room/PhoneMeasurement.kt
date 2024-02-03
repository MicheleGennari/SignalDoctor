package com.example.signaldoctor.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.FirebaseMeasurementEntity
import com.example.signaldoctor.realtimeFirebase.PhoneMeasurementFirebase
import com.google.firebase.database.Exclude

@Entity(tableName = "phone_table")
data class PhoneMeasurement(

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TableColumn.id)
    override val id : Int? = null,

    @ColumnInfo(name= TableColumn.is_lte) val isLTE : Boolean? = null,

    @Embedded
    override val baseInfo: MeasurementBase = MeasurementBase(),


) : RoomMeasurementEntity(Measure.phone)

