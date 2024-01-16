package com.example.signaldoctor.appComponents.viewModels

import android.location.Location
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
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
import com.example.signaldoctor.hiltModules.AndroidGeocoder
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.mapUtils.IFlowGeocoder
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.screens.msrTypeWhen
import com.example.signaldoctor.searchBarHint.ISearchBarHint
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.utils.AppNotificationManager
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.not
import com.example.signaldoctor.utils.updateAppSettings
import com.example.signaldoctor.workers.NoiseMsrWorker
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
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Date
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
    @AndroidGeocoder private val geocoder: IFlowGeocoder
) : ViewModel() {


    val networkMode = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.networkMode }.distinctUntilChanged().withIndex().flowOn(Dispatchers.Default).onEach {
        if(it.index>0)
            notificationManager.launchToast(message = "switched to ${it.value.name} mode")
    }.flowOn(Dispatchers.Main).map{it.value}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMode.OFFLINE)

    //val phoneSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.phoneSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettings.getDefaultInstance())

    //val noiseSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.noiseSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettings.getDefaultInstance())

    //val wifiSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.wifiSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettings.getDefaultInstance())

    val isNoiseBackgroundOn = backgroundMeasurementsManager(Measure.sound)

    val isPhoneBackgroundOn = backgroundMeasurementsManager(Measure.phone)

    val isWifiBackgroundOn = backgroundMeasurementsManager(Measure.wifi)

    private fun backgroundMeasurementsManager(msrType: Measure) = combine(
        //flow #1 of combine
        settingsDataStore.data.flowOn(Dispatchers.IO).map {
            msrTypeWhen(msrType,
                phone = it.phoneSettings,
                sound = it.noiseSettings.takeIf { permissionsChecker.isRecordingGranted() },
                wifi = it.wifiSettings
                )?.isBackgroundMsrOn ?: false
        }.flowOn(Dispatchers.IO).distinctUntilChanged(),
        //flow #2 of combine
        settingsDataStore.data.flowOn(Dispatchers.IO).map {
            msrTypeWhen(msrType,
                phone = it.phoneSettings,
                sound = it.noiseSettings,
                wifi = it.wifiSettings
            ).periodicity
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    ){ isBackgroundOn, periodicity ->

        if (isBackgroundOn) {
            consoledebug("background ${msrType.name} activated")
            runBackgroundMeasurement(msrType, Duration.ofMinutes(periodicity.toLong()))
        } else {
            consoledebug("no ${msrType.name} background measurement")
            cancelBackgroundMeasurement(msrType)
        }
        isBackgroundOn
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    private fun setLastLocationUserSettings(newLocation : Location) {

        viewModelScope.launch(Dispatchers.IO) {
            try{
                settingsDataStore.updateData { settingsSnap ->
                    settingsSnap.updateAppSettings {
                        lastLocationLat = newLocation.latitude
                        lastLocationLon = newLocation.longitude
                    }
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

    val localSearchBarHints = mapScreenUiState.localSearchBarHints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val searchBarHints = mapScreenUiState.searchBarHints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())


    /*
    val searchBarHints = mapScreenUiState.searchBarQuery.onEach {
            mapScreenUiState.setIsSearchBarLoading(true)
    }.flowOn(Dispatchers.Default).debounce(Duration.ofSeconds(1)).map{ query ->
        geocoder.getAddressesFromLocationName(query).also {
            consoledebug("a geocoder call has been completed")
        }
    }.flowOn(Dispatchers.IO).onEach {
        mapScreenUiState.setIsSearchBarLoading(false)
    }.filterNotNull()
        .stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = listOf())
    */

    private var locationUpdatesJob : Job? = null
    fun areLocationUpdatesOn() = locationUpdatesJob?.job?.isActive ?: false

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

    fun getLocationNameFromUserLocation() {
        userLocation.value?.let { location ->
            viewModelScope.launch(Dispatchers.IO) {
                geocoder.getAddressesFromLocation(location).firstOrNull()?.run {

                    mapScreenUiState.updateSearchBarQuery(getAddressLine(0))

                } ?: mapScreenUiState.updateSearchBarQuery("${location.latitude},${location.longitude}")
            }
        }
    }

    fun switchNetworkMode() {

        consoledebug("${isNetworkAvailable.value}")
        viewModelScope.launch(Dispatchers.IO){

            settingsDataStore.updateData { settingsSnap ->
                settingsSnap.updateAppSettings {
                    networkMode = !networkMode
                }
            }
        }.invokeOnCompletion { e ->
            e?.printStackTrace()
        }

    }


    fun addLocalHint(hint : ISearchBarHint){
        viewModelScope.launch {
            consoledebug("inside add local hint")
            mapScreenUiState.addLocalHint(hint)
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

        viewModelScope.launch {
            settingsDataStore.data.first().let {settingsSnap ->
                mapScreenUiState.setScreenLocation(
                    latitude = settingsSnap.lastLocationLat,
                    longitude = settingsSnap.lastLocationLon
                )
            }

        }

        consoledebug("My ViewModel is Initialized")

        //wait for reading of the last location saved, so that app can start in that position
        /*
        runBlocking{

            settingsDataStore.data.first().let {settingsSnap ->
                mapScreenUiState.setScreenLocation(
                    latitude = settingsSnap.lastLocationLat,
                    longitude = settingsSnap.lastLocationLon
                )
            }


        }
         */

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


