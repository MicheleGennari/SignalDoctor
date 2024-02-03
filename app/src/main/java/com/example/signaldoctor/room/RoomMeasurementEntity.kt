package com.example.signaldoctor.room


import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.FirebaseMeasurementEntity
import com.google.firebase.database.Exclude

abstract class RoomMeasurementEntity(@Exclude @get:Exclude @Ignore val msrType : Measure){

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TableColumn.id)
    open val id : Int? = null

    @Embedded open val baseInfo : MeasurementBase = MeasurementBase()


}