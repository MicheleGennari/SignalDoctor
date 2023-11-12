package com.example.signaldoctor.realtimeFirebase

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.TableColumn

data class PhoneMeasurementFirebase(

    @Embedded
    val baseInfo : MeasurementBase = MeasurementBase(),
    @ColumnInfo(name= TableColumn.IS_LTE) val isLTE : Boolean? = null

    )

data class SoundMeasurementFirebase(

    @Embedded
    val baseInfo : MeasurementBase = MeasurementBase(),
)

data class WifiMeasurementFirebase(

    @Embedded
    val baseInfo : MeasurementBase = MeasurementBase(),
)