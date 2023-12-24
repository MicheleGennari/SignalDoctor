package com.example.signaldoctor.utils

import androidx.datastore.core.DataStore
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.utils.Loggers.consoledebug
import java.util.Date


fun AppSettings.updateAppSettings(
    update : AppSettings.Builder.() -> Unit
) : AppSettings {
    return toBuilder().apply{
        update()
    }.build()
}

fun AppSettings.Builder.phoneSettings(
    update : MeasurementSettings.Builder.() -> Unit
){
    phoneSettings = phoneSettings.toBuilder().apply{
        update()
    }.build()
}

fun AppSettings.Builder.noiseSettings(
    update : MeasurementSettings.Builder.() -> Unit
){
    noiseSettings = noiseSettings.toBuilder().apply{
        update()
    }.build()
}

fun AppSettings.Builder.wifiSettings(
    update : MeasurementSettings.Builder.() -> Unit
){
    wifiSettings = wifiSettings.toBuilder().apply{
        update()
    }.build()
}

/*
suspend fun DataStore<AppSettings>.updateOnly(
    newNetworkMode: NetworkMode?,
    newLastLocationLat : Double?,
    newLastLocationLon : Double?,
    updatePhoneSettings : MeasurementSettings.() -> MeasurementSettings,
    updateNoiseSettings : MeasurementSettings.() -> MeasurementSettings,
    updateWifiSettings: MeasurementSettings.() -> MeasurementSettings
) {

    updateData { oldSettingsSnap ->
        oldSettingsSnap.toBuilder().apply {
            networkMode = newNetworkMode ?: oldSettingsSnap.networkMode
            lastLocationLat = newLastLocationLat ?: oldSettingsSnap.lastLocationLat
            lastLocationLon = newLastLocationLon ?: oldSettingsSnap.lastLocationLon

            phoneSettings =  oldSettingsSnap.phoneSettings.updatePhoneSettings()
            noiseSettings = oldSettingsSnap.noiseSettings.updateNoiseSettings()
            wifiSettings = oldSettingsSnap.wifiSettings.updateWifiSettings()

        }.build()
    }
}

fun MeasurementSettings.updateOnly : MeasurementSettings(
     Periodicity : Int?,
     msrsToTake : Int?,
     freshness : Long?,
     oldness : Long?,
){

}

*/