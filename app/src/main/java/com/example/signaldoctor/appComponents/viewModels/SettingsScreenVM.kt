package com.example.signaldoctor.appComponents.viewModels

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.MeasurementSettingsPopulatedDefaultInstance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsScreenVM @Inject constructor(
    userSettings: DataStore<AppSettings>
) : ViewModel() {

    init {
        consoledebug("A Settings Screen ViewModel is now created")
    }

    val phoneSettings = userSettings.data.map { it.phoneSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val noiseSettings =  userSettings.data.map { it.noiseSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val wifiSettings =  userSettings.data.map { it.wifiSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    override fun onCleared() {
        consoledebug("the Settings Screen ViewModel is now cleared")
        super.onCleared()
    }
}