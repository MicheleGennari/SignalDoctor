package com.example.signaldoctor.room

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.utils.whenMsrType

@Database(entities = [PhoneMeasurement::class, SoundMeasurement::class, WiFIMeasurement::class], version = 9, exportSchema = false )
@TypeConverters(RoomTypeConverters::class)
abstract class MsrsDB : RoomDatabase() {


    abstract fun phoneMeasurementDAO() : PhoneMeasurementDAO

    abstract fun soundMeasurementDAO() : SoundMeasurementDAO

    abstract fun wifiMeasurementDAO() : WiFiMeasurementDAO

}