package com.example.signaldoctor.appComponents.viewModels

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SettingsScreenVM @Inject constructor(
    val userSettings: DataStore<AppSettings>
) : ViewModel() {
    init {
        
        userSettings.data.onEach {  updatedAppSettings ->
            _phoneSettings.value = updatedAppSettings.phoneSettings
            _noiseSettings.value = updatedAppSettings.noiseSettings
            _wifiSettings.value = updatedAppSettings.wifiSettings
        }.launchIn(viewModelScope)

    }

    private val _phoneSettings = MutableStateFlow(MeasurementSettings.getDefaultInstance())
    val phoneSettings = _phoneSettings.asStateFlow()

    private val _noiseSettings = MutableStateFlow(MeasurementSettings.getDefaultInstance())
    val noiseSettings = _noiseSettings.asStateFlow()

    private val _wifiSettings = MutableStateFlow(MeasurementSettings.getDefaultInstance())
    val wifiSettings = _wifiSettings.asStateFlow()
}