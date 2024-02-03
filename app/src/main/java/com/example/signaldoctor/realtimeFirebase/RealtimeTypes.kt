package com.example.signaldoctor.realtimeFirebase

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.TableColumn

data class PhoneMeasurementFirebase(

    //@ColumnInfo(name= TableColumn.is_lte) val isLTE : Boolean? = null,

    @Embedded
    override val baseInfo: MeasurementBase = MeasurementBase()

) : FirebaseMeasurementEntity()

data class SoundMeasurementFirebase(

    @Embedded
    override val baseInfo : MeasurementBase = MeasurementBase()

) : FirebaseMeasurementEntity()

data class WifiMeasurementFirebase(

    @Embedded
    override val baseInfo : MeasurementBase = MeasurementBase()

) : FirebaseMeasurementEntity()

open class FirebaseMeasurementEntity(
    @Embedded
    open val baseInfo: MeasurementBase = MeasurementBase()
)