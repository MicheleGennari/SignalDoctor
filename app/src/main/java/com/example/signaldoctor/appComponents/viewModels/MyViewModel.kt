package com.example.signaldoctor.appComponents.viewModels

import android.location.Geocoder
import android.location.Location
import android.util.Log
import android.util.Patterns
import androidx.annotation.FloatRange
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.appComponents.FlowConnectivityManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.appComponents.PermissionsChecker
import com.example.signaldoctor.appComponents.ReceiversManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.mapUtils.FlowGeocoder
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.screens.msrTypeWhen
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.utils.AppNotificationManager
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.MeasurementSettingsPopulatedDefaultInstance
import com.example.signaldoctor.utils.not
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.printAndReturn
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.osmdroid.views.MapView
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject

const val POST_MSR_WORK_TAG = "postMsrWOrkTag"
const val POST_MSR_ACTIVE_WORK_NAME = "postMsrActiveWork"
const val POST_MSR_BACKGROUND_WORK_NAME = "postMsrBackgroundWork"

const val LOCATION_GRANULARITY = 2000
const val LOCATION_INTERVAL = 5000L
const val LOCATION_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY

const val MEASUREMENT_NOTIFICATION_CHANNEL_ID = "MEASUREMENT_CHANNEL"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
open class MyViewModel @Inject constructor(
    private val settingsDataStore : DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    //@DefaultTileMap val map : MapView,
    private val locationProvider : FlowLocationProvider,
    private val msrsWorkManager: MsrsWorkManager,
    private val notificationManager : AppNotificationManager,
    connectivityManager: FlowConnectivityManager,
    private val receiversManager: ReceiversManager,
    private val permissionsChecker: PermissionsChecker,
    private val geocoder: FlowGeocoder
) : ViewModel() {


    val networkMode = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.networkMode }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMode.OFFLINE)

    val phoneSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.phoneSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val noiseSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.noiseSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val wifiSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.wifiSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val isNoiseBackgroundOn = backgroundMeasurementsManager(Measure.sound)

    val isPhoneBackgroundOn = backgroundMeasurementsManager(Measure.phone)

    val isWifiBackgroundOn = backgroundMeasurementsManager(Measure.wifi)

    private fun backgroundMeasurementsManager(msrType: Measure) = combine(
        settingsDataStore.data.flowOn(Dispatchers.IO).map {
            msrTypeWhen(msrType,
                phone = it.phoneSettings,
                sound = it.noiseSettings.takeIf { printAndReturn("recording is : ",permissionsChecker.isRecordingGranted()) },
                wifi = it.wifiSettings
                )?.isBackgroundMsrOn ?: false
        }.flowOn(Dispatchers.IO).distinctUntilChanged(),
        settingsDataStore.data.flowOn(Dispatchers.IO).map {
            msrTypeWhen(msrType,
                phone = it.phoneSettings,
                sound = it.noiseSettings,
                wifi = it.wifiSettings
            ).periodicity
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    ){ isBackgroundOn, periodicity ->

        if (isBackgroundOn) {
            consoledebug("background activated")
            runBackgroundMeasurement(msrType, Duration.ofMinutes(periodicity.toLong()))
        } else
            cancelBackgroundMeasurement(msrType)

        isBackgroundOn
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private fun setLastLocationUserSettings(newLocation : Location) {

        viewModelScope.launch(Dispatchers.IO) {
            try{
                settingsDataStore.updateData { settingsSnap ->
                    settingsSnap.toBuilder()
                        .setLastLocationLat(newLocation.latitude)
                        .setLastLocationLon(newLocation.longitude)
                        .build()
                }
            }catch (e : IOException){
                Log.e("AppSettings DataStore", "IO Exception while writing last location coordinates")
                e.printStackTrace()
            }
        }

    }

    val phoneAvgs = settingsDataStore.data.flowOn(Dispatchers.IO).flatMapLatest{ appSettings ->
        if (appSettings.networkMode == NetworkMode.ONLINE)
            msrsRepo.getPhoneMergedAvgs(appSettings.phoneSettings)
        else
            msrsRepo.getPhoneLocalAvgs(appSettings.phoneSettings)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())

    val soundAvgs = settingsDataStore.data.flowOn(Dispatchers.IO).flatMapLatest{ appSettings ->
            if (appSettings.networkMode == NetworkMode.ONLINE)
                msrsRepo.getSoundMergedAvgs(appSettings.noiseSettings)
            else
                msrsRepo.getSoundLocalAvgs(appSettings.noiseSettings)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())

    val wifiAvgs = settingsDataStore.data.flowOn(Dispatchers.IO).flatMapLatest{ appSettings ->
        if (appSettings.networkMode == NetworkMode.ONLINE)
            msrsRepo.getWifiMergedAvgs(appSettings.wifiSettings)
        else
            msrsRepo.getWifiLocalAvgs(appSettings.wifiSettings)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())


    private val _lastNoiseMsr : MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastNoiseMsr = _lastNoiseMsr.asStateFlow()

    private val _lastPhoneMsr : MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastPhoneMsr = _lastPhoneMsr.asStateFlow()

    private val _lastWifiMsr : MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastWifiMsr = _lastWifiMsr.asStateFlow()

    private val _measurementProgress : MutableStateFlow<Float> = MutableStateFlow(0f)
    val measurementProgress = _measurementProgress.asStateFlow()

    private val locationUpdateSettings = LocationRequest.Builder(LOCATION_INTERVAL).setPriority(LOCATION_PRIORITY)
        .build()


    private val _userLocation : MutableStateFlow<Location?> = MutableStateFlow(null)
    val userLocation = _userLocation.asStateFlow()
    private var locationUpdatesJob : Job? = null

    val arePhoneMsrsDated = areMsrsDated(Measure.phone)
    val areNoiseMsrsDated = areMsrsDated(Measure.sound)
    val areWifiMsrsDated = areMsrsDated(Measure.wifi)

    private fun areMsrsDated(msrType: Measure) = combine(
        settingsDataStore.data.map { it.networkMode }.flowOn(Dispatchers.IO),
        userLocation.filterNotNull(),
    ) { networkMode, userLocation ->
        object : Any() {
            val networkMode = networkMode
            val userLocation = userLocation
        }
    }.flatMapLatest { array ->
        if (array.networkMode == NetworkMode.ONLINE) {
            msrsRepo.countMergedMeasurements(
                msrType,
                array.userLocation,
                Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli())
            )
        }
        else {
            msrsRepo.countLocalMeasurements(
                msrType,
                array.userLocation,
                Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli())
            )
        }
    }.distinctUntilChanged().stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = false)

     fun checkLocationSettings(mainActivity: MainActivity) = viewModelScope.launch {
            userLocation.value?.let { MapView.getTileSystem().tileIndexFromLocation(it )}
            locationProvider.checkLocationSettings(locationUpdateSettings, mainActivity)
    }



    fun locationUpdatesOn() {

        locationUpdatesJob?.let { job ->
            if(job.isActive) return
        }

        locationUpdatesJob = viewModelScope.launch{

            locationProvider.requestLocationUpdates(locationUpdateSettings).onCompletion {
                _userLocation.value = null
                it?.printStackTrace()
            }.distinctUntilChanged().collect{ newLocation->

                _userLocation.value = newLocation

                if(newLocation!=null)
                    setLastLocationUserSettings(newLocation)
            }

        }
    }
    fun locationUpdatesOff(){
        locationUpdatesJob?.run {
            cancel("location updates off")
        }
    }

    val isNetworkAvailable = connectivityManager.internetAvailabilityUpdates().onEach {
         //if internet is not available, app's network mode is immediately set to OFFLINE
         if(!it){
             settingsDataStore.updateData { settingsSnap ->
                 settingsSnap.toBuilder().setNetworkMode(NetworkMode.OFFLINE).build()
             }
         }
    }.stateIn(
        scope= viewModelScope,
        started= SharingStarted.WhileSubscribed(),
        initialValue = false
    )

    fun switchNetworkMode() {

        consoledebug("${isNetworkAvailable.value}")
        viewModelScope.launch(Dispatchers.IO){
            settingsDataStore.updateData { settingsSnap ->
                settingsSnap.toBuilder()
                    .setNetworkMode(!settingsSnap.networkMode)
                    .build()
            }
        }.invokeOnCompletion { e ->
            e?.printStackTrace()
        }

    }


    fun changeScreenLocation(
        @FloatRange(from = -90.0, to = 90.0) latitude: Double,
        @FloatRange(from = -180.0, to = 180.0) longitude: Double
    ) {
        mapScreenUiState.changeScreenLocation(latitude, longitude)
    }

    fun setUserLocationAsScreenLocation() {
        consoledebug("init:"+userLocation.value?.latitude.toString())
        userLocation.value?.run {
            mapScreenUiState.changeScreenLocation(latitude, longitude)
            centerOnScreenLocation()
        }
    }


     fun centerOnScreenLocation() {
        mapScreenUiState.centerOnScreenLocation()
        mapScreenUiState.updateSearchBarText("${userLocation.value?.latitude}, ${userLocation.value?.longitude}")
    }

    fun setScreenLocationFromQueryString(query: String) {
        val longitude: Double?
        val latitude: Double?
        TODO()
        if(query.contains(Regex.fromLiteral("/[a-z]/"))){
                consoledebug("Bologna")
                latitude = geocoder.geocode2(query).latitude
                longitude = geocoder.geocode2(query).longitude
        } else {
            consoledebug("coordinate")
            query.split(",").run {
                latitude = firstOrNull()?.toDoubleOrNull()
                longitude = getOrNull(1)?.toDoubleOrNull()
            }
        }
        if (latitude != null && longitude != null) {
            changeScreenLocation(latitude, longitude)
            mapScreenUiState.centerOnScreenLocation()
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
                    WorkInfo.State.RUNNING -> _measurementProgress.value = workInfo.progress.getFloat(NoiseMsrWorker.Progress, 0f)
                    WorkInfo.State.SUCCEEDED -> when(msrType){
                        Measure.sound -> _lastNoiseMsr
                        Measure.wifi -> _lastWifiMsr
                        Measure.phone -> _lastPhoneMsr
                    }.value = workInfo.outputData.getInt(MeasurementBase.MSR_KEY, 0)
                    WorkInfo.State.FAILED -> {}
                    else -> {}
                }

            if(workInfo.state.isFinished){
                mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
            }

        }.launchIn(viewModelScope)

    }

    fun runBackgroundMeasurement(msrType: Measure, interval: Duration) {
        msrsWorkManager.runBackgroundMeasurmeent(msrType, interval)
            .onStart {
                mapScreenUiState.changeMeasuringState(MeasuringState.BACKGROUND)
            }.onEach { workInfo ->
                if (workInfo.state.isFinished){
                    mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
                }
            }

    }

    fun cancelMeasurement(msrType : Measure){
        msrsWorkManager.cancelMeasurement(msrType)
    }

    fun cancelBackgroundMeasurement(msrType: Measure){
        msrsWorkManager.cancelBackgroundMeasurement(msrType)
    }

    fun cancelAllMeasurements(){
        msrsWorkManager.cancelAllMeasurements()
    }

    fun changeMeasuringState(newMsrState: MeasuringState) = run {
        mapScreenUiState.changeMeasuringState(newMsrState)
    }


    init{

        consoledebug("My ViewModel is Initialized")

        //wait for reading of the last location saved, so that app can start in that position
        runBlocking{

            settingsDataStore.data.first().let {settingsSnap ->
                mapScreenUiState.changeScreenLocation(
                    latitude = settingsSnap.lastLocationLat,
                    longitude = settingsSnap.lastLocationLon
                )
            }


        }

        //registerRunMeasurementReceiver()

        //If location permissions are granted, start location update on app boot
       /* if(app.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ||
            app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            locationUpdatesOn()
            setUserLocationAsScreenLocation()
        }*/



    }

    fun sendRunMeasurementNotification(msrType: Measure){
        consoledebug("inside sendRunMeasurementNotification()")
        notificationManager.sendRunMeasurementNotification(msrType)
    }

    fun cancelRunMeasurementNotification(msrType: Measure){
        notificationManager.cancelRunMeasurementNotification(msrType)
    }

    fun registerRunMeasurementReceiver(){
        receiversManager.registerRunMeasurementReceiver(exported = false)
    }


    override fun onCleared() {
        consoledebug("Clearing MyViewModel....")
        //receiversManager.unregisterRunMeasurementReceiver()
        super.onCleared()
    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


