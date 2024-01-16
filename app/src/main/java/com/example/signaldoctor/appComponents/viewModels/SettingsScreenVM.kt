package com.example.signaldoctor.appComponents.viewModels

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.screens.msrTypeWHen
import com.example.signaldoctor.utils.Loggers.consoledebug
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
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsScreenVM @Inject constructor(
    private val userSettings: DataStore<AppSettings>,
    pri
) : ViewModel() {


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
        /*
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
         */
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