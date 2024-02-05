package com.example.signaldoctor.viewModels.MapScreen

import android.app.Activity
import android.location.Location
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.AppSettingsManager
import com.example.signaldoctor.appComponents.FlowConnectivityManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.appComponents.PermissionsChecker
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.AndroidGeocoder
import com.example.signaldoctor.mapUtils.IFlowGeocoder
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.searchBarHint.ISearchBarHint
import com.example.signaldoctor.searchBarHint.ProtoBuffHint
import com.example.signaldoctor.services.BackgroundMeasurementsManager
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.not
import com.example.signaldoctor.utils.settingAsStateFlow
import com.example.signaldoctor.utils.settingsAsSharedFlow
import com.example.signaldoctor.workers.BaseMsrWorker
import com.example.signaldoctor.workers.NoiseMsrWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.inject.Inject


const val MEASUREMENT_NOTIFICATION_CHANNEL_ID = "MEASUREMENT_CHANNEL"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
open class MyViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val appSettingsManager: AppSettingsManager,
    msrsBusiness: MsrsBusiness, //data of 'MsrsRepo' is collected from here
    val mapScreenUiState: MapScreenUiState,
    private val locationProvider : FlowLocationProvider,
    private val msrsWorkManager: MsrsWorkManager,
    private val notificationManager : AppNotificationManager,
    private val backgroundMeasurementsManager: BackgroundMeasurementsManager,
    connectivityManager: FlowConnectivityManager,
    permissionsChecker: PermissionsChecker,
    @AndroidGeocoder private val geocoder: IFlowGeocoder
) : ViewModel() {

    val appSettings = appSettingsManager.appSettings


    val isNetworkAvailable = connectivityManager.isInternetAvailable

    val networkMode = appSettings.settingsAsSharedFlow(viewModelScope){networkMode}.combine(isNetworkAvailable){ networkModeSetting, isNetworkAvailable ->
        if(networkModeSetting == NetworkMode.ONLINE && isNetworkAvailable) NetworkMode.ONLINE
        else NetworkMode.OFFLINE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMode.OFFLINE)

    private val _locationPermissionsState = MutableStateFlow(permissionsChecker.isLocationGranted())
    val locationPermissionsState = _locationPermissionsState.asStateFlow()

    fun setLocationPermissionsState(newValue: Boolean){
        _locationPermissionsState.value = newValue
    }


    val userLocation = locationProvider.userLoc(locationPermissionsState)

    val centerWhenNavigatingOnMapScreen = savedStateHandle.getStateFlow(DestinationsInfo.MapScreen.CENTER_LOCATION_ARGUMENT_NAME, false)
        .onEach {
            consoleDebug("Saved state handle to $it")
            if(it) {
                coroutineScope{ async { appSettings.first() }.await() }.apply {
                        consoleDebug("coordinates retrieved")
                        mapScreenUiState.setScreenLocation(
                            lastLocationLat,
                            lastLocationLon
                        )
                    }
                consoleDebug("end of setScreenLocation")
                savedStateHandle[DestinationsInfo.MapScreen.CENTER_LOCATION_ARGUMENT_NAME] = false
            }
    }.launchIn(viewModelScope)

    val saveUserLocationInSettingsJob = userLocation.onEach { userLocation ->
        try{
            userLocation?.run{
                appSettingsManager.updateSettings {
                    lastLocationLat = latitude
                    lastLocationLon = longitude
                }
            }
        }catch (e : IOException){
            Log.e("AppSettings DataStore", "IO Exception while writing last location coordinates", e)
        }
    }.launchIn(viewModelScope)

    private fun setLastLocationUserSettings(newLocation : Location) {

        viewModelScope.launch {
            try{
                newLocation.run{
                    appSettingsManager.updateSettings {
                        lastLocationLat = latitude
                        lastLocationLon = longitude
                    }
                }
            }catch (e : IOException){
                Log.e("AppSettings DataStore", "IO Exception while writing last location coordinates", e)
            }
        }

    }

    val phoneAvgs = msrsBusiness.getMsrAvgs(viewModelScope = viewModelScope,Measure.phone)

    val soundAvgs = msrsBusiness.getMsrAvgs(viewModelScope = viewModelScope,Measure.sound)

    val wifiAvgs = msrsBusiness.getMsrAvgs(viewModelScope = viewModelScope,Measure.wifi)



    private val _lastNoiseMsr : MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastNoiseMsr = _lastNoiseMsr.asStateFlow()

    private val _lastPhoneMsr : MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastPhoneMsr = _lastPhoneMsr.asStateFlow()

    private val _lastWifiMsr : MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastWifiMsr = _lastWifiMsr.asStateFlow()

    private val _measurementProgress : MutableStateFlow<Float> = MutableStateFlow(0f)
    val measurementProgress = _measurementProgress.asStateFlow()



    val localSearchBarHints = mapScreenUiState.localSearchBarHints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val searchBarHints = mapScreenUiState.searchBarHints.combine(localSearchBarHints){ onlineHints : List<ISearchBarHint>, localHints : List<ISearchBarHint> ->
        onlineHints.filter { onlineHint ->
            //only onlineHints that are not in localHints yet will pass the filter
            !localHints.any { localHint ->
                localHint.locationName == onlineHint.locationName
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addLocalHint(hint : ISearchBarHint){
        viewModelScope.launch {
            consoleDebug("inside add local hint")
            mapScreenUiState.addLocalHint(hint)
        }
    }


    val arePhoneMsrsDated = msrsBusiness.areMsrsDated(viewModelScope,Measure.phone)
    val areNoiseMsrsDated = msrsBusiness.areMsrsDated(viewModelScope,Measure.sound)
    val areWifiMsrsDated = msrsBusiness.areMsrsDated(viewModelScope,Measure.sound)

    val notifyIfPhoneMsrsAreDatedJob = arePhoneMsrsDated.onEach { areMsrsDated ->
        notificationManager.run {
            if(areMsrsDated)
                sendRunMeasurementNotification(Measure.phone)
            else
                cancelRunMeasurementNotification(Measure.phone)
        }
    }.launchIn(viewModelScope)
    val notifyIfNoiseMsrsAreDatedJob = arePhoneMsrsDated.onEach { areMsrsDated ->
        notificationManager.run {
            if(areMsrsDated)
                sendRunMeasurementNotification(Measure.sound)
            else
                cancelRunMeasurementNotification(Measure.sound)
        }
    }.launchIn(viewModelScope)

    val notifyIfWifiMsrsAreDatedJob = arePhoneMsrsDated.onEach { areMsrsDated ->
        notificationManager.run {
            if(areMsrsDated)
                sendRunMeasurementNotification(Measure.wifi)
            else
                cancelRunMeasurementNotification(Measure.wifi)
        }
    }.launchIn(viewModelScope)


     fun checkLocationSettings(mainActivity: Activity) = viewModelScope.launch {
            locationProvider.checkLocationSettings(FlowLocationProvider.defaultLocationUpdateSettings, mainActivity)
    }

    fun getLocationNameFromUserLocation() {
        userLocation.value?.let { location ->
            viewModelScope.launch {
                withContext(Dispatchers.IO){
                    geocoder.getAddressesFromLocation(location).firstOrNull()
                }?.run {
                    mapScreenUiState.updateSearchBarQuery(getAddressLine(0))

                    addLocalHint(ProtoBuffHint(
                        displayName = getAddressLine(0),
                        latitude = latitude,
                        longitude = longitude
                    ))

                } ?: mapScreenUiState.updateSearchBarQuery("${location.latitude},${location.longitude}")
            }
            mapScreenUiState.setShowHints(false)
        }
    }

    fun switchNetworkMode() {
        viewModelScope.launch {
            appSettingsManager.updateSettings {
                networkMode = !networkMode
            }
        }
    }




    /////////////////////////////////////////////////
    //////////////MEASUREMENTS OPERATIONS///////////
    ///////////////////////////////////////////////


    fun runMeasurement(msrType: Measure) {

        //the msrType is passed as a Measure object for logic in MsrsWorkManager and as a String so that it can be passed to workers

        msrsWorkManager.runMeasurement(msrType = msrType)
            .onStart {
                    changeMeasuringState(MeasuringState.RUNNING)
            }.onEach { workInfo ->

                when(workInfo.state){
                    WorkInfo.State.RUNNING -> _measurementProgress.update { workInfo.progress.getFloat(NoiseMsrWorker.Progress, 0f) }
                    WorkInfo.State.SUCCEEDED -> when(msrType){
                        Measure.sound -> _lastNoiseMsr
                        Measure.wifi -> _lastWifiMsr
                        Measure.phone -> _lastPhoneMsr
                    }.update { workInfo.outputData.getInt(MeasurementBase.MSR_KEY, 0) }
                    WorkInfo.State.FAILED -> {
                        workInfo.outputData.getString(BaseMsrWorker.ERROR_KEY)?.let {
                            notificationManager.sendMeasurementErrorNotification(msrType, it)
                        }
                    }
                    else -> {}
                }



        }.onEach{ workInfo ->
                if(workInfo.state.isFinished){
                    mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
                    currentCoroutineContext().cancel() // stop collecting this workInfo's updates
                }
        }.onCompletion {
            _measurementProgress.update { 0f }
            }.flowOn(Dispatchers.Default).launchIn(viewModelScope)

    }

    fun cancelMeasurement(msrType : Measure){
        msrsWorkManager.cancelOneTimeMeasurement(msrType)
    }

    fun cancelBackgroundMeasurement(msrType: Measure){
        backgroundMeasurementsManager.stop(msrType)
        //msrsWorkManager.cancelBackgroundMeasurement(msrType)
    }

    fun cancelAllOneTimeMeasurements(){
        msrsWorkManager.cancelAllOneTimeMeasurements()
    }

    fun changeMeasuringState(newMsrState: MeasuringState) {
        mapScreenUiState.changeMeasuringState(newMsrState)
    }

    fun sendDatedMeasurementNotification(msrType: Measure) {
            notificationManager.sendRunMeasurementNotification(msrType)
    }

    fun cancelDatedMeasurementNotification(msrType: Measure){
        notificationManager.cancelRunMeasurementNotification(msrType)
    }



    init{
        consoleDebug("My ViewModel is Initialized")
    }



    override fun onCleared() {
        consoleDebug("Clearing MyViewModel....")
        //receiversManager.unregisterRunMeasurementReceiver()
        super.onCleared()
    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


