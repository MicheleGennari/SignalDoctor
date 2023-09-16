package com.example.signaldoctor.uistates

import android.location.Location
import androidx.annotation.FloatRange
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
import java.nio.DoubleBuffer
import javax.inject.Inject

class MapScreenUiState @Inject constructor(
) {

    private val _ScreenLocation = MutableStateFlow(Location("provider").apply { latitude = 44.22; longitude = 11.20 })
    val ScreenLocation = _ScreenLocation.asStateFlow()

    fun changeScreenLocation(@FloatRange(from = -90.0, to= 90.0 ) latitude : Double, @FloatRange(from = -180.0, to= 180.0 ) longitude : Double ) {
        _ScreenLocation.value.apply {
            this.latitude = latitude
            this.longitude = longitude
        }
    }

    private val _currentMsrMode = MutableStateFlow(Measure.phone)
    val currentMsrMode = _currentMsrMode.asStateFlow()
    fun setCurrentMsrMode(newMsrMode : Measure){
        _currentMsrMode.value = newMsrMode
    }

    private val _centerOnScreenLocation = MutableStateFlow(true)
    val centerOnScreenLocation = _centerOnScreenLocation.asStateFlow()
    fun centerOnScreenLocation(){
        _centerOnScreenLocation.value = true
    }
    fun disableCenterOnScreenLocation(){
        _centerOnScreenLocation.value = false
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