package com.example.signaldoctor.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PhoneMeasurement::class, SoundMeasurement::class, WiFIMeasurement::class], version = 4, exportSchema = false )
@TypeConverters(RoomTypeConverters::class)
abstract class MsrsDB : RoomDatabase() {

    abstract fun phoneMeasurementDAO() : PhoneMeasurementDAO

    abstract fun soundMeasurementDAO() : SoundMeasurementDAO

    abstract fun wifiMeasurementDAO() : WiFiMeasurementDAO


}