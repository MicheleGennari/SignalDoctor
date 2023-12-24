package com.example.signaldoctor.appComponents.viewModels

import android.location.LocationRequest
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.mapUtils.FlowGeocoder
import com.example.signaldoctor.screens.msrTypeWHen
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.MeasurementSettingsPopulatedDefaultInstance
import com.example.signaldoctor.utils.noiseSettings
import com.example.signaldoctor.utils.phoneSettings
import com.example.signaldoctor.utils.updateAppSettings
import com.example.signaldoctor.utils.wifiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsScreenVM @Inject constructor(
    private val userSettings: DataStore<AppSettings>,
    private val locationProvider: FlowLocationProvider
) : ViewModel() {

    val locationUpdateSettings = com.google.android.gms.location.LocationRequest.Builder(LOCATION_INTERVAL).setPriority(
        LOCATION_PRIORITY
    ).build()

    private fun <T> DataStore<AppSettings>.settingStateFlow(sharingMode : SharingStarted = SharingStarted.WhileSubscribed(), initialValue : T, getter : AppSettings.() -> T) =
        data.map { it.getter() }.flowOn(Dispatchers.IO).stateIn(viewModelScope, sharingMode, initialValue = initialValue)


    val noiseSettings = userSettings.settingStateFlow(initialValue = MeasurementSettingsPopulatedDefaultInstance()){
        noiseSettings
    }

    val phoneSettings = userSettings.settingStateFlow(initialValue = MeasurementSettingsPopulatedDefaultInstance()){
        phoneSettings
    }

    val wifiSettings = userSettings.settingStateFlow(initialValue = MeasurementSettingsPopulatedDefaultInstance()){
        wifiSettings
    }

    fun updateSettings(updater : AppSettings.Builder.() -> Unit){
        viewModelScope.launch {
            userSettings.updateData { oldSettingsSnap ->
                oldSettingsSnap.updateAppSettings(updater)
            }
        }
    }

    fun updateMeasureSettings(msrType : Measure, updater : MeasurementSettings.Builder.() -> Unit) {
        viewModelScope.launch {
            userSettings.updateData { oldSettingsSnap ->
                oldSettingsSnap.updateAppSettings {
                    msrTypeWHen(
                        msrType,
                        phone = {
                            phoneSettings(updater)
                        },
                        wifi = {
                            wifiSettings(updater)
                        },
                        sound = {
                            noiseSettings(updater)
                        }
                    )
                }
            }
        }
    }

    fun updateNoiseSettings(updater: MeasurementSettings.Builder.() -> Unit){
        updateMeasureSettings(msrType = Measure.sound, updater = updater)
    }

    fun updatePhoneSettings(updater: MeasurementSettings.Builder.() -> Unit){
        updateMeasureSettings(msrType = Measure.phone, updater = updater)
    }

    fun updateWifiSettings(updater: MeasurementSettings.Builder.() -> Unit){
        updateMeasureSettings(msrType = Measure.wifi, updater = updater)
    }

    override fun onCleared() {
        consoledebug("the Settings Screen ViewModel is now cleared")
        super.onCleared()
    }

    init {
        consoledebug("A Settings Screen ViewModel is now created")
    }
}