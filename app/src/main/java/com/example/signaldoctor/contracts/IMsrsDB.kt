package com.example.signaldoctor.contracts

import android.location.Location
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.Settings
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.RoomMeasurementEntity
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.example.signaldoctor.workers.WifiMsrWorker
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface IMsrsDB {

    fun avgsMap(msrType : Measure, settings : MeasurementSettings) : Flow<MsrsMap>

    fun getPhoneMsrs(settings: MeasurementSettings) : Flow<Map<String, PhoneMeasurement>>

    fun getSoundMsrs(settings: MeasurementSettings) : Flow<Map<String,SoundMeasurement>>

    fun getWifiMsrs(settings: MeasurementSettings) : Flow<Map<String,WiFIMeasurement>>

    fun getOldestDate(msrType: Measure) : Flow<Date>

    fun areMsrsDated(msrType: Measure, userLocation : Location, limitDate : Date) : Flow<Boolean>

    suspend fun postPhoneMsr(phoneMeasurement: PhoneMeasurement) : Boolean
    suspend fun postSoundMsr(soundMeasurement: SoundMeasurement) : Boolean

    suspend fun postWifiMsr(wifiMeasurement: WiFIMeasurement) : Boolean

}