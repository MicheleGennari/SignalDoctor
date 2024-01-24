package com.example.signaldoctor.appComponents.viewModels

import android.location.Location
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
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
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.AndroidGeocoder
import com.example.signaldoctor.mapUtils.IFlowGeocoder
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.screens.whenMsrType
import com.example.signaldoctor.searchBarHint.ISearchBarHint
import com.example.signaldoctor.searchBarHint.ProtoBuffHint
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.utils.AppNotificationManager
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.not
import com.example.signaldoctor.utils.updateAppSettings
import com.example.signaldoctor.utils.updateDSL
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.inject.Inject

const val POST_MSR_WORK_TAG = "postMsrWOrkTag"
const val POST_MSR_ACTIVE_WORK_NAME = "postMsrActiveWork"
const val POST_MSR_BACKGROUND_WORK_NAME = "postMsrBackgroundWork"


const val MEASUREMENT_NOTIFICATION_CHANNEL_ID = "MEASUREMENT_CHANNEL"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
open class MyViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
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


    private val centerWhenNavigatingOnMapScreen = savedStateHandle.getStateFlow(DestinationsInfo.MapScreen.CENTER_LOCATION_ARGUMENT_NAME, true).onStart {
      consoledebug("Saved state handle flow has started")
    }.onEach {

        consoledebug("Saved state handle is $it")
        if(it) {
            mapScreenUiState.setScreenLocation(
                settingsDataStore.data.first().lastLocationLat,
                settingsDataStore.data.first().lastLocationLon
            )
            savedStateHandle[DestinationsInfo.MapScreen.CENTER_LOCATION_ARGUMENT_NAME] = false
        }
    }.onCompletion {
        consoledebug("saved state handle flow completed")
    }.catch {e ->
        e.printStackTrace()
        consoledebug("Saved state handle flow error")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    //val phoneSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.phoneSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettings.getDefaultInstance())

    //val noiseSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.noiseSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettings.getDefaultInstance())

    //val wifiSettings = settingsDataStore.data.flowOn(Dispatchers.IO).map { it.wifiSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettings.getDefaultInstance())

    val isNoiseBackgroundOn = backgroundMeasurementsManager(Measure.sound)

    val isPhoneBackgroundOn = backgroundMeasurementsManager(Measure.phone)

    val isWifiBackgroundOn = backgroundMeasurementsManager(Measure.wifi)

    private fun backgroundMeasurementsManager(msrType: Measure) = combine(
        //flow #1 of combine
        settingsDataStore.data.flowOn(Dispatchers.IO).map {
            whenMsrType(msrType,
                phone = it.phoneSettings,
                sound = it.noiseSettings.takeIf { permissionsChecker.isRecordingGranted() },
                wifi = it.wifiSettings
                )?.isBackgroundMsrOn ?: false
        }.flowOn(Dispatchers.IO).distinctUntilChanged(),
        //flow #2 of combine
        settingsDataStore.data.flowOn(Dispatchers.IO).map {
            whenMsrType(msrType,
                phone = it.phoneSettings,
                sound = it.noiseSettings,
                wifi = it.wifiSettings
            ).periodicity
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    ){ isBackgroundOn, periodicity ->

        if (isBackgroundOn) {
            consoledebug("background ${msrType.name} activated")
            //runBackgroundMeasurement(msrType, Duration.ofMinutes(periodicity.toLong()))
        } else {
            consoledebug("no ${msrType.name} background measurement")
            //cancelBackgroundMeasurement(msrType)
        }
        isBackgroundOn
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    private fun setLastLocationUserSettings(newLocation : Location) {

        viewModelScope.launch(Dispatchers.IO) {
            try{
                settingsDataStore.updateDSL {
                    lastLocationLat = newLocation.latitude
                    lastLocationLon = newLocation.longitude
                }
            }catch (e : IOException){
                Log.e("AppSettings DataStore", "IO Exception while writing last location coordinates", e)
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



    private val _userLocation : MutableStateFlow<Location?> = MutableStateFlow(null)
    val userLocation = _userLocation.asStateFlow()

    val localSearchBarHints = mapScreenUiState.localSearchBarHints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val searchBarHints = mapScreenUiState.searchBarHints.combine(localSearchBarHints){ onlineHints : List<ISearchBarHint>, localHints : List<ISearchBarHint> ->
        onlineHints.filter { onlineHint ->
            //only onlineHints that are not in localHints yet will pass the filter
            !localHints.any { localHint ->
                localHint.locationName == onlineHint.locationName
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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

    private var locationUpdatesJob : Job = Job()

    val arePhoneMsrsDated = areMsrsDated(Measure.phone)
    val areNoiseMsrsDated = areMsrsDated(Measure.sound)
    val areWifiMsrsDated = areMsrsDated(Measure.wifi)

    private fun areMsrsDated(msrType: Measure) = combineTransform(
        settingsDataStore.data.map { it.networkMode }.flowOn(Dispatchers.IO).distinctUntilChanged(),
        userLocation.filterNotNull()
    ){ networkMode, userLocation ->
        emitAll(if (networkMode == NetworkMode.ONLINE) {
            msrsRepo.countMergedMeasurements(
                msrType,
                userLocation,
                Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli())
            )
        } else {
            msrsRepo.countLocalMeasurements(
                msrType,
                userLocation,
                Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli())
            )
        })
    }.distinctUntilChanged().onEach { areMsrsDated ->
        if (areMsrsDated)
            sendDatedMeasurementNotification(msrType)
        else
            cancelDatedMeasurementNotification(msrType)
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = Duration.ofMinutes(5).toMillis()), initialValue = false)


    /*
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
    }.distinctUntilChanged().onEach { areMsrsDated ->
        if (areMsrsDated)
            sendDatedMeasurementNotification(msrType)
        else
            cancelDatedMeasurementNotification(msrType)
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = Duration.ofMinutes(5).toMillis()), initialValue = false)

*/

     fun checkLocationSettings(mainActivity: MainActivity) = viewModelScope.launch {
           // userLocation.value?.let { MapView.getTileSystem().tileIndexFromLocation(it )}
            locationProvider.checkLocationSettings(FlowLocationProvider.defaultLocationUpdateSettings, mainActivity)
    }


    fun locationUpdatesOn() {

        locationUpdatesJob = locationProvider.requestLocationUpdates(FlowLocationProvider.defaultLocationUpdateSettings).onCompletion {
                consoledebug("locations updates are cancelled")
                _userLocation.update { null }
                it?.printStackTrace()
            }.onEach{ newLocation->

                consoledebug("MyViewModel location updates are on")

                _userLocation.update { newLocation }

                if(newLocation!=null)
                    setLastLocationUserSettings(newLocation)
                else {
                    //this ensures that measurements stop when user location is not available
                    mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
                    msrsWorkManager.cancelAllMeasurements()
                }
            }.launchIn(viewModelScope)

    }
    fun locationUpdatesOff(){
        if(locationUpdatesJob.job.isActive)
            locationUpdatesJob.cancel()
    }

    val isNetworkAvailable = connectivityManager.internetAvailabilityUpdates().onEach {
         //if internet is not available, app's network mode is immediately set to OFFLINE
         if(!it){
             settingsDataStore.updateDSL {
                 networkMode = NetworkMode.OFFLINE
             }
         }
    }.stateIn(scope= viewModelScope, started= SharingStarted.WhileSubscribed(), initialValue = false)

    fun getLocationNameFromUserLocation() {
        userLocation.value?.let { location ->
            viewModelScope.launch(Dispatchers.IO) {
                geocoder.getAddressesFromLocation(location).firstOrNull()?.run {

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
        consoledebug("${isNetworkAvailable.value}")

        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.updateDSL {
                networkMode = !networkMode
            }
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
                    WorkInfo.State.RUNNING -> _measurementProgress.update { workInfo.progress.getFloat(NoiseMsrWorker.Progress, 0f) }
                    WorkInfo.State.SUCCEEDED -> when(msrType){
                        Measure.sound -> _lastNoiseMsr
                        Measure.wifi -> _lastWifiMsr
                        Measure.phone -> _lastPhoneMsr
                    }.update { workInfo.outputData.getInt(MeasurementBase.MSR_KEY, 0) }
                    WorkInfo.State.FAILED -> {}
                    else -> {}
                }

            if(workInfo.state.isFinished){
                mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
            }

        }.launchIn(viewModelScope)

    }

    fun runBackgroundMeasurement(msrType: Measure, interval: Duration) {
        msrsWorkManager.runBackgroundMeasurement(msrType, interval)
            .onStart {
                mapScreenUiState.changeMeasuringState(MeasuringState.BACKGROUND)
            }.onEach { workInfo ->
                if (workInfo.state.isFinished){
                    mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
                }
            }

    }

    fun cancelMeasurement(msrType : Measure){
        msrsWorkManager.cancelOneTimeMeasurement(msrType)
    }

    fun cancelBackgroundMeasurement(msrType: Measure){
        msrsWorkManager.cancelBackgroundMeasurement(msrType)
    }

    fun cancelAllMeasurements(){
        msrsWorkManager.cancelAllOneTimeMeasurements()
    }

    fun changeMeasuringState(newMsrState: MeasuringState) = run {
        mapScreenUiState.changeMeasuringState(newMsrState)
    }

    fun sendDatedMeasurementNotification(msrType: Measure) : Boolean {
        return if(permissionsChecker.isPostingNotificationGranted()) {
            notificationManager.sendRunMeasurementNotification(msrType)
            true
        }
        else false
    }

    fun cancelDatedMeasurementNotification(msrType: Measure){
        notificationManager.cancelRunMeasurementNotification(msrType)
    }


    init{
        /*
        if(savedStateHandle.get<Boolean>(DestinationsInfo.MapScreen.CENTER_LOCATION_ARGUMENT_NAME) == true) {


            viewModelScope.launch {
                settingsDataStore.data.first().let { settingsSnap ->
                    mapScreenUiState.setScreenLocation(
                        latitude = settingsSnap.lastLocationLat,
                        longitude = settingsSnap.lastLocationLon
                    )
                }
            }
        }*/

        //centerWhenNavigatingOnMapScreen.launchIn(viewModelScope)

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


