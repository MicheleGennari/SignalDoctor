package com.example.signaldoctor.uistates

import com.example.signaldoctor.contracts.Measure
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.example.signaldoctor.contracts.MeasuringState
import com.google.android.material.search.SearchBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

class MapScreenUiState @Inject constructor(
) {
    private val _currentMsrMode = MutableStateFlow(Measure.phone)
    val currentMsrMode = _currentMsrMode.asStateFlow()
    fun setCurrentMsrMode(newMsrMode : Measure){
        _currentMsrMode.value = newMsrMode
    }

    private val _centerOnUserLocation = MutableStateFlow(false)
    val centerUserLocation = _centerOnUserLocation.asStateFlow()
    fun centerOnUserLocation(){
        _centerOnUserLocation.value = true
    }
    fun disableCenterOnUserLocation(){
        _centerOnUserLocation.value = false
    }
    private val _searchBarText = MutableStateFlow("")
    val searchBarText = _searchBarText.asStateFlow()
    fun updateSearchBarText(updatedText: String){
        _searchBarText.value = updatedText
    }

    private val _measuringState = MutableStateFlow(MeasuringState.STOP)
    val measuringState = _measuringState.asStateFlow()
    fun changeMeasuringState(newMeasuringState: MeasuringState){
        _measuringState.value = newMeasuringState
    }




}
/*
object measuringStates {
    val STOPPED = 0
    val RUNNING = 1
    val BACKGROUND = 2
}

object ActiveOverlayStates {
    val PHONE = 0
    val NOISE = 1
    val WIFI = 2
}*/