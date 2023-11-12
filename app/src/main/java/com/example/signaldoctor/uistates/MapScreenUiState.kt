package com.example.signaldoctor.uistates

import android.location.Location
import androidx.annotation.FloatRange
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import com.example.signaldoctor.contracts.Measure
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.MutableLiveData
import com.example.signaldoctor.contracts.MeasuringState
import com.google.android.gms.location.LocationServices
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

    private val _screenLocation = MutableStateFlow<Location?>(null)
    val screenLocation = _screenLocation.asStateFlow()
    fun changeScreenLocation(@FloatRange(from = -90.0, to= 90.0 ) latitude : Double, @FloatRange(from = -180.0, to= 180.0 ) longitude : Double ) {
        _screenLocation.value = Location("provider").also {newScreenLocation ->
            newScreenLocation.latitude = latitude
            newScreenLocation.longitude = longitude
        }
    }


    private val _showMicCalibrationDialog = MutableStateFlow(true)
    val showMicCalibrationDialog = _showMicCalibrationDialog.asStateFlow()

    fun toggleMicCalibrationDialog() {
        _showMicCalibrationDialog.value = !showMicCalibrationDialog.value
    }

    private val _currentMsrType = MutableStateFlow(Measure.phone)
    val currentMsrType = _currentMsrType.asStateFlow()
    fun setCurrentMsrMode(newMsrMode : Measure){
        _currentMsrType.value = newMsrMode
    }


    private val _centerOnScreenLocation = MutableStateFlow(true)
    val centerOnScreenLocation = _centerOnScreenLocation.asStateFlow()
    fun centerOnScreenLocation(){
        _centerOnScreenLocation.value = true
        screenLocation.value?.run{
            updateSearchBarText("$latitude, $longitude")
        }
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