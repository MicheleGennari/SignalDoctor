package com.example.signaldoctor.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PhoneMeasurement::class, SoundMeasurement::class, WiFIMeasurement::class], version = 1, exportSchema = false )
abstract class MsrsDB : RoomDatabase() {

    abstract fun phoneMeasurementDAO() : PhoneMeasurementDAO

    abstract fun soundMeasurementDAO() : SoundMeasurementDAO

    abstract  fun wifiMeasurementDAO() : WiFiMeasurementDAO


}