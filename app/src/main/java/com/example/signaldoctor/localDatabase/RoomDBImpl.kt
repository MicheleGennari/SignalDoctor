package com.example.signaldoctor.localDatabase

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.repositories.IMsrsLocalDB
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.PhoneMeasurementDAO
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.SoundMeasurementDAO
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.room.WiFiMeasurementDAO
import com.example.signaldoctor.workers.MsrWorkersInputData
import kotlinx.coroutines.flow.Flow

@Database(entities = [PhoneMeasurement::class, SoundMeasurement::class, WiFIMeasurement::class], version = 1, exportSchema = false )
abstract class RoomDBImpl : RoomDatabase(), IMsrsLocalDB {

    abstract fun phoneMeasurementDAO() : PhoneMeasurementDAO

    abstract fun soundMeasurementDAO() : SoundMeasurementDAO

    abstract  fun wifiMeasurementDAO() : WiFiMeasurementDAO


}