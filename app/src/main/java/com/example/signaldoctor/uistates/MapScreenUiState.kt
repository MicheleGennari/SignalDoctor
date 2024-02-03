package com.example.signaldoctor.uistates

import android.location.Location
import androidx.annotation.FloatRange
import androidx.datastore.core.DataStore
import com.example.signaldoctor.LocalHints
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.hiltModules.AndroidGeocoder
import com.example.signaldoctor.mapUtils.IFlowGeocoder
import com.example.signaldoctor.mapUtils.geocoderHints
import com.example.signaldoctor.searchBarHint.ISearchBarHint
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.addHint
import com.example.signaldoctor.utils.protoBuffHints
import com.example.signaldoctor.utils.printAndReturn
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.time.debounce
import java.time.Duration
import javax.inject.Inject

@ViewModelScoped
class MapScreenUiState @Inject constructor(
    private val localHintsDataStore: DataStore<LocalHints>,
   @AndroidGeocoder geocoder: IFlowGeocoder

) {


    private val _screenLocation = MutableStateFlow<Location?>(Location("").apply { latitude = 54.6
    longitude = 23.2})
    val screenLocation = _screenLocation.asStateFlow()
    fun setScreenLocation(@FloatRange(from = -90.0, to= 90.0 ) latitude : Double, @FloatRange(from = -180.0, to= 180.0 ) longitude : Double ) {

        consoleDebug("inside set Screen Location")

        _screenLocation.value = Location("provider").also { newScreenLocation ->
            newScreenLocation.latitude = latitude
            newScreenLocation.longitude = longitude
        }

        setCenterOnScreenLocation(true)
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


    private val _centerOnScreenLocation = MutableStateFlow(false)
    val centerOnScreenLocation = _centerOnScreenLocation.asStateFlow()

    fun setCenterOnScreenLocation(newValue : Boolean){
        consoleDebug("Setting center on screen to $newValue")
        _centerOnScreenLocation.value = newValue
    }

    private val _searchBarQuery = MutableStateFlow("")
    val searchBarQuery = _searchBarQuery.asStateFlow()
    fun updateSearchBarQuery(updatedQuery: String){
        _searchBarQuery.value = updatedQuery
    }


    val localSearchBarHints = combine(
        searchBarQuery.debounce(Duration.ofSeconds(1)),
        localHintsDataStore.data.flowOn(Dispatchers.IO).map { it.protoBuffHints() }
        ){ query, localHints ->
        localHints.filter { localHint ->
            localHint.locationName.contains(query, true)
        }
    }

    suspend fun addLocalHint(hint : ISearchBarHint){
        localHintsDataStore.addHint(hint)
    }

    val searchBarHints =
        searchBarQuery.onEach {
            setIsSearchBarLoading(true)
        }.debounce(Duration.ofSeconds(1)).flowOn(Dispatchers.Default).map{ query ->
            geocoder.getAddressesFromLocationName(query).also {
                consoleDebug("a geocoder call has been completed")
            }
        }.flowOn(Dispatchers.IO).map{it.geocoderHints()}
            .onEach {
                setIsSearchBarLoading(false)
             }.filterNotNull()


    private val _searchbarShowHints = MutableStateFlow(false)
    val searchbarShowHints = _searchbarShowHints.asStateFlow()

    private val _isSearchBarLoading = MutableStateFlow(false)
    val isSearchBarLoading = _isSearchBarLoading.asStateFlow()

    fun setIsSearchBarLoading(newValue : Boolean){
        _isSearchBarLoading.value = newValue
    }

    fun toggleHints(){ _searchbarShowHints.value = printAndReturn("inside mapScreenUiState showHints has changed to: ",!_searchbarShowHints.value) }

    fun setShowHints(newValue : Boolean){
        _searchbarShowHints.value = newValue
    }

    private val _measuringState = MutableStateFlow(MeasuringState.STOP)
    val measuringState = _measuringState.asStateFlow()
    fun changeMeasuringState(newMeasuringState: MeasuringState){
        _measuringState.value = newMeasuringState

    }

}