package com.example.signaldoctor.room


import com.example.signaldoctor.realtimeFirebase.FirebaseMeasurementEntity

abstract class RoomMeasurementEntity{

    open val id : Int? = null

    abstract val firebaseTable : FirebaseMeasurementEntity

}