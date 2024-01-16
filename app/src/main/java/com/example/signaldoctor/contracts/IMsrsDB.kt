package com.example.signaldoctor.contracts

import android.location.Location
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.Settings
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.workers.MsrWorkersInputData
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface IMsrsDB {

    fun avgsMap(msrType : Measure, settings : MeasurementSettings) : Flow<MsrsMap>

    fun getPhoneMsrs(settings: MeasurementSettings) : Flow<List<PhoneMeasurement?>>

    fun getSoundMsrs(settings: MeasurementSettings) : Flow<List<SoundMeasurement?>>

    fun getWifiMsrs(settings: MeasurementSettings) : Flow<List<WiFIMeasurement?>>

    fun getOldestDate(msrType: Measure) : Flow<Date>

    fun countMeasurements(msrType: Measure, userLocation : Location, limitDate : Date) : Flow<Boolean>

    fun postPhoneMsr(phoneMeasurement: PhoneMeasurement) : Boolean
     fun postSoundMsr(soundMeasurement: SoundMeasurement) : Boolean

    fun postWifiMsr(wifiMeasurement: WiFIMeasurement) : Boolean

}