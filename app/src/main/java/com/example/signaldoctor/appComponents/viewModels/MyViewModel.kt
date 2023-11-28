package com.example.signaldoctor.appComponents.viewModels

import android.location.Location
import android.util.Log
import androidx.annotation.FloatRange
import androidx.datastore.core.DataStore
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.hasKeyWithValueOfType
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.FlowConnectivityManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
import javax.inject.Inject

const val POST_MSR_WORK_TAG = "postMsrWOrkTag"
const val POST_MSR_ACTIVE_WORK_NAME = "postMsrActiveWork"
const val POST_MSR_BACKGROUND_WORK_NAME = "postMsrBackgroundWork"

const val LOCATION_GRANULARITY = 2000
const val LOCATION_INTERVAL = 5000L
const val LOCATION_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY

const val MEASUREMENT_NOTIFICATION_CHANNEL_ID = "MEASUREMENT_CHANNEL"

@HiltViewModel
open class MyViewModel @Inject constructor(
    private val settingsDataStore : DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    //@DefaultTileMap val map : MapView,
    private val locationProvider : FlowLocationProvider,
    private val msrsWorkManager: MsrsWorkManager,
    notificationManager : AppNotificationManager,
    connectivityManager: FlowConnectivityManager,
    //@ApplicationContext private val app : Context
) : ViewModel() {


    val phoneSettings = settingsDataStore.data.map { it.phoneSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val noiseSettings = settingsDataStore.data.map { it.noiseSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

    val wifiSettings = settingsDataStore.data.map { it.wifiSettings }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSettingsPopulatedDefaultInstance())

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

    fun switchNetworkMode() {

             if((
                    networkMode.value == NetworkMode.OFFLINE
                    && !printAndReturn("network availability:",isNetworkAvailable.value)
                ))
                    return
            else {
                consoledebug("burdegaaa")
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

    }

    val phoneAvgs = phoneSettings.flatMapLatest {
            msrsRepo.getPhoneMergedAvgs(it)

    }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())

    val soundAvgs = noiseSettings.flatMapLatest {
            msrsRepo.getSoundMergedAvgs(it)
    }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())

    val wifiAvgs = wifiSettings.flatMapLatest {
            msrsRepo.getWifiMergedAvgs(it)
    }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())


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


     fun checkLocationSettings(mainActivity: MainActivity) = viewModelScope.launch {
            userLocation.value?.let { MapView.getTileSystem().tileIndexFromLocation(it )}
            locationProvider.checkLocationSettings(locationUpdateSettings, mainActivity)
    }

    fun locationUpdatesOn() {

        locationUpdatesJob = viewModelScope.launch{

            locationProvider.requestLocationUpdates(locationUpdateSettings).onCompletion {
                _userLocation.value = null
                it?.printStackTrace()
            }.collect{ newLocation->

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

     private val isNetworkAvailable = connectivityManager.internetAvailabilityUpdates().onEach {

         //if internet is not available, app's network mode is immediately set to OFFLINE
         if(!it){
             consoledebug("check")
             settingsDataStore.updateData { settingsSnap ->
                 settingsSnap.toBuilder().setNetworkMode(NetworkMode.OFFLINE).build()
             }
         }

     }.stateIn(
        scope= viewModelScope,
        started= SharingStarted.WhileSubscribed(),
        initialValue = false
    )

    val networkMode = settingsDataStore.data.map { it.networkMode }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMode.OFFLINE)


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
        query.split(",").run {
            latitude = firstOrNull()?.toDoubleOrNull()
            longitude = getOrNull(1)?.toDoubleOrNull()
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


        val msrWorkerObserver = Observer<List<WorkInfo>> { workInfos ->
            workInfos.first().run{
                    if (state == WorkInfo.State.SUCCEEDED) {
                        when (msrType) {
                            Measure.wifi -> {
                                consoledebug(
                                    " msr worker returned, ${
                                        outputData == Data.EMPTY
                                    }"
                                )
                                _lastWifiMsr.value = outputData.getInt(
                                    MeasurementBase.MSR_KEY,
                                    Int.MIN_VALUE
                                )
                            }

                            Measure.phone ->
                                _lastPhoneMsr.value = outputData.getInt(
                                    MeasurementBase.MSR_KEY,
                                    Int.MIN_VALUE
                                )

                            Measure.sound ->
                                _lastNoiseMsr.value = outputData.getInt(
                                    MeasurementBase.MSR_KEY,
                                    Int.MIN_VALUE
                                )
                        }
                    }
            }
        }

        val postWorkerObserver = Observer<List<WorkInfo>> { workInfos ->


            Log.i("outputdata is: ", if(workInfos.first().outputData == Data.EMPTY) "empty" else "filled")

                consoledebug("${measurementProgress.value}")
                if(!workInfos.first().state.isFinished){
                    _measurementProgress.value =
                        workInfos.first().progress.getFloat(NoiseMsrWorker.Progress, 0f)
                }else if(workInfos.last().progress.hasKeyWithValueOfType<Float>(NoiseMsrWorker.Progress)){
                    _measurementProgress.value =
                        workInfos.last().progress.getFloat(NoiseMsrWorker.Progress, 8/10f)
                }



            workInfos.last().let { postMsrWorkInfo ->
                if (postMsrWorkInfo.state.isFinished) {
                    _measurementProgress.value = 0f
                    changeMeasuringState(MeasuringState.STOP)
                }
            }
        }
/*
        val progressWorker = Observer<List<WorkInfo>> { workInfos ->
            workInfos.last().let { lastWorkInfo ->
                if (lastWorkInfo.state.isFinished)
                    changeMeasuringState(MeasuringState.STOP)
            }
        }
*/
        //the msrType is passed as a Measure object for logic in MsrsWorkManager and as a String so that it can be passed to workers

        msrsWorkManager.runMeasurement(
                msrType = msrType,
                tileIndex = userLocation.value?.let { userLocation ->
                    MapView.getTileSystem().tileIndexFromLocation(userLocation)
                } ?: return
            ).onStart {
                    changeMeasuringState(MeasuringState.RUNNING)
                }.onEach { (workInfo1, workInfo2) ->

                    (workInfo1.takeIf { it.state == WorkInfo.State.RUNNING }
                        ?: workInfo2.takeIf { it.state == WorkInfo.State.RUNNING })?.apply {
                            _measurementProgress.value = progress.getFloat(NoiseMsrWorker.Progress, 0f)
                        }

                    if (workInfo1.state.isFinished && workInfo2.state.isFinished) {

                        changeMeasuringState(MeasuringState.STOP)

                        (workInfo1.takeIf {
                            it.outputData.hasKeyWithValueOfType<Int>(MeasurementBase.MSR_KEY)
                        } ?: workInfo2.takeIf {
                            it.outputData.hasKeyWithValueOfType<Int>(MeasurementBase.MSR_KEY)
                        })?.apply {
                            consoledebug("measure type is $msrType")

                            when (msrType) {
                                Measure.wifi -> {

                                    _lastWifiMsr.value = outputData.getInt(
                                        MeasurementBase.MSR_KEY,
                                        Int.MIN_VALUE
                                    )
                                }

                                Measure.phone ->
                                    _lastPhoneMsr.value = outputData.getInt(
                                        MeasurementBase.MSR_KEY,
                                        Int.MIN_VALUE
                                    )

                                Measure.sound ->
                                    _lastNoiseMsr.value = outputData.getInt(
                                        MeasurementBase.MSR_KEY,
                                        Int.MIN_VALUE
                                    )
                            }
                        }
                        _measurementProgress.value = 0f
                    }
            }.launchIn(viewModelScope)

    }


    fun cancelMeasurement(msrType : Measure){
        msrsWorkManager.cancelMeasurement(msrType)
    }

    fun cancelAllMeasurements(){
        msrsWorkManager.cancelAllMeasurements()
    }

    fun changeMeasuringState(newMsrState: MeasuringState) = run {
        mapScreenUiState.changeMeasuringState(newMsrState)
    }

    init{

        runBlocking{

            settingsDataStore.data.first().let {settingsSnap ->
                mapScreenUiState.changeScreenLocation(
                    latitude = settingsSnap.lastLocationLat,
                    longitude = settingsSnap.lastLocationLon
                )
            }

        }

        //If location permissions are granted, start location update on app boot
       /* if(app.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ||
            app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            locationUpdatesOn()
            setUserLocationAsScreenLocation()
        }*/

        //Create Notification Channel for measurement notifications
        notificationManager.createMeasurementsChannel(
            channelNameResourceId = R.string.measurements_channel_name,
            channelDescriptionResourceId = R.string.measurement_channel_description
            )

    }



    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        consoledebug("Clearing viewmodel....")
        //msrsRepo.closeLocalDB()
        super.onCleared()
    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


