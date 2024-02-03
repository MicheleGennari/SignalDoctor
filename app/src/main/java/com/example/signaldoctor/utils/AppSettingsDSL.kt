package com.example.signaldoctor.utils

import android.util.Log
import androidx.datastore.core.DataStore
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn


suspend fun DataStore<AppSettings>.updateDSL( updater : suspend AppSettings.Builder.() -> Unit){
        try{
            updateData { appSettings ->
                appSettings.updateAppSettings(updater)
            }
        }catch (e : Exception){
            Log.e("AppSettings Datastore", "Something went wrong while writing on disk", e)
        }
}

 suspend fun AppSettings.updateAppSettings(
    update : suspend AppSettings.Builder.() -> Unit
) : AppSettings {
    return toBuilder().apply{
        update()
    }.build()
}

 suspend fun AppSettings.Builder.phoneSettings(
     update : suspend MeasurementSettings.Builder.() -> Unit
){
    phoneSettings = phoneSettings.toBuilder().apply{
        update()
    }.build()
}

suspend fun AppSettings.Builder.noiseSettings(
    update : suspend MeasurementSettings.Builder.() -> Unit
){
    noiseSettings = noiseSettings.toBuilder().apply{
        update()
    }.build()
}

suspend fun AppSettings.Builder.wifiSettings(
    update : suspend MeasurementSettings.Builder.() -> Unit
){
    wifiSettings = wifiSettings.toBuilder().apply{
        update()
    }.build()
}

fun DataStore<AppSettings>.asSharedFlow(scope : CoroutineScope, sharingMode : SharingStarted = SharingStarted.WhileSubscribed()) =
    data.shareIn(scope, sharingMode)

fun <T> Flow<AppSettings>.settingAsStateFlow(
    scope : CoroutineScope,
    sharingMode : SharingStarted = SharingStarted.WhileSubscribed(),
    initialValue : T,
    operators : Flow<T>.() -> Flow<T> = {this},
    getter : AppSettings.() -> T
) =
    map { it.getter() }.operators().stateIn(scope, sharingMode, initialValue = initialValue)

fun <T> Flow<AppSettings>.settingsAsFlow(getter : AppSettings.() -> T) = map { it.getter() }.conflate().distinctUntilChanged()

fun <T> Flow<AppSettings>.settingsAsSharedFlow(scope: CoroutineScope, sharingMode: SharingStarted = SharingStarted.WhileSubscribed(), getter : AppSettings.() -> T) =
    map { it.getter() }.shareIn(scope, sharingMode)