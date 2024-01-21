package com.example.signaldoctor.appComponents.viewModels

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.screens.msrTypeWHen
import com.example.signaldoctor.services.BackgroundMeasurementsManager
import com.example.signaldoctor.services.BackgroundMeasurementsService
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.catchIOException
import com.example.signaldoctor.utils.noiseSettings
import com.example.signaldoctor.utils.phoneSettings
import com.example.signaldoctor.utils.updateAppSettings
import com.example.signaldoctor.utils.wifiSettings
import com.google.android.gms.location.LocationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject


@HiltViewModel
class SettingsScreenVM @Inject constructor(
    private val userSettings: DataStore<AppSettings>,
    private val backgroundMeasurementsManager : BackgroundMeasurementsManager,
    private val locationProvider: FlowLocationProvider
) : ViewModel() {

    private val locationUpdateSettings = LocationRequest.Builder(LOCATION_INTERVAL).setPriority(LOCATION_PRIORITY)
        .build()

    private fun <T> DataStore<AppSettings>.settingStateFlow(sharingMode : SharingStarted = SharingStarted.Eagerly, initialValue : T, getter : AppSettings.() -> T) =
        data.map { it.getter() }.flowOn(Dispatchers.IO).stateIn(viewModelScope, sharingMode, initialValue = initialValue)


    val noiseSettings = userSettings.settingStateFlow(initialValue = MeasurementSettings.getDefaultInstance()){
        noiseSettings
    }

    val phoneSettings = userSettings.settingStateFlow(initialValue = MeasurementSettings.getDefaultInstance()){
        phoneSettings
    }

    val wifiSettings = userSettings.settingStateFlow(initialValue = MeasurementSettings.getDefaultInstance()){
        wifiSettings
    }

    private val _currentSettingsList = MutableStateFlow(Measure.sound)
    val currentSettingsList = _currentSettingsList.asStateFlow()

    fun changeCurrentSettingsList(msrType: Measure){
        _currentSettingsList.value = msrType
    }

    fun updateSettings(updater : AppSettings.Builder.() -> Unit){
        viewModelScope.launch {
            userSettings.updateData { oldSettingsSnap ->
                oldSettingsSnap.updateAppSettings(updater)
            }
        }.invokeOnCompletion { e : Throwable? ->
            e?.printStackTrace()
        }
    }

    fun updateMeasureSettings(msrType : Measure, updater : MeasurementSettings.Builder.() -> Unit) {

        updateSettings {
            msrTypeWHen( msrType,
                phone = { phoneSettings(updater) },
                sound = { noiseSettings(updater) },
                wifi = { wifiSettings(updater) }
            )
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

    //value in this stateFlow tells whether service is active or not
    private fun startBackgroundMeasurementsManager() =
        combine(
            userSettings.data.catchIOException().map { it.phoneSettings.isBackgroundMsrOn }.distinctUntilChanged(),
            userSettings.data.catchIOException().map { it.noiseSettings.isBackgroundMsrOn }.distinctUntilChanged(),
            userSettings.data.catchIOException().map { it.wifiSettings.isBackgroundMsrOn }.distinctUntilChanged()
        ) { isPhoneBackgroundOn, isNoiseBackgroundOn, isWIfiBackgroundOn ->

            if(isNoiseBackgroundOn) backgroundMeasurementsManager.start(BackgroundMeasurementsService.START_NOISE_ACTION)

            if(isPhoneBackgroundOn) backgroundMeasurementsManager.start(BackgroundMeasurementsService.START_PHONE_ACTION)

            if(isWIfiBackgroundOn) backgroundMeasurementsManager.start(BackgroundMeasurementsService.START_WIFI_ACTION)

        }.shareIn(viewModelScope, SharingStarted.Eagerly)


    fun checkLocationSettings(mainActivity: MainActivity){
        viewModelScope.launch{
            locationProvider.checkLocationSettings(locationUpdateSettings, mainActivity)
        }
    }

    override fun onCleared() {
        consoledebug("the Settings Screen ViewModel is now cleared")
        super.onCleared()
    }

    init {

        consoledebug("A Settings Screen ViewModel is now created")

        startBackgroundMeasurementsManager()

    }
}