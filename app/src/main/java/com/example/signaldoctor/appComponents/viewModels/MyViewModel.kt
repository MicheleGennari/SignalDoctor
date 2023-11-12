package com.example.signaldoctor.appComponents.viewModels

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.annotation.FloatRange
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.hasKeyWithValueOfType
import com.example.signaldoctor.R
import com.example.signaldoctor.Settings
import com.example.signaldoctor.appComponents.FlowConnectivityManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.contracts.NetworkMode
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
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
    private val settingsDataStore : DataStore<Settings>,
    private val msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    //@DefaultTileMap val map : MapView,
    private val locationProvider : FlowLocationProvider,
    private val msrsWorkManager: MsrsWorkManager,
    private val notificationManager : NotificationManagerCompat,
    private val connectivityManager: FlowConnectivityManager,
    @ApplicationContext private val app : Context
) : ViewModel() {

    private val userSettings = settingsDataStore.data.onEach { settings ->
        _msrsToTakeSetting.value = settings.msrsToTake
        _measurementFrequency.value = settings.measurementsFrequency
        _freshnessBound.value = settings.freshnessBound
        _oldnessBound.value = settings.oldnessBound
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = Settings.getDefaultInstance() )

    private val _msrsToTakeSetting : MutableStateFlow<Int?> = MutableStateFlow(null)
    val msrsToTakeSetting  = _msrsToTakeSetting.asStateFlow()

    private val _measurementFrequency : MutableStateFlow<Int?> = MutableStateFlow(null)
    val measurementFrequency  = _measurementFrequency.asStateFlow()

    private val _freshnessBound : MutableStateFlow<Long?> = MutableStateFlow(null)
    val freshnessBound  = _freshnessBound.asStateFlow()

    private val _oldnessBound : MutableStateFlow<Long?> = MutableStateFlow(null)
    val oldnessBound  = _oldnessBound.asStateFlow()

    private suspend fun setLastLocationUserSettings(newLocation : Location) {
        settingsDataStore.updateData { settingsSnap ->
            settingsSnap.toBuilder()
                .setLastLocationLat(newLocation.latitude)
                .setLastLocationLon(newLocation.longitude)
                .build()
        }
    }

    val phoneAvgs = msrsRepo.getMergedAvgs(Measure.phone).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val soundAvgs = msrsRepo.getMergedAvgs(Measure.sound)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val wifiAvgs = msrsRepo.getMergedAvgs(Measure.wifi)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())


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
            }.collect{ newLocation->
                _userLocation.value = newLocation

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
         if(!it)
             _netWorkMode.value = NetworkMode.OFFLINE

     }.stateIn(
        scope= viewModelScope,
        started= SharingStarted.WhileSubscribed(),
        initialValue = false
    )

    private val _netWorkMode = MutableStateFlow(NetworkMode.ONLINE)
    val networkMode = _netWorkMode.asStateFlow()

    fun changeNetWorkMode(newNetworkMode: NetworkMode) {
        _netWorkMode.value = newNetworkMode
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


        val msrWorkObserver = Observer<List<WorkInfo>> { workInfos ->

                consoledebug("${measurementProgress.value}")
                if(!workInfos.first().state.isFinished){
                    _measurementProgress.value =
                        workInfos.first().progress.getFloat(NoiseMsrWorker.Progress, 0f)
                }else if(workInfos.last().progress.hasKeyWithValueOfType<Float>(NoiseMsrWorker.Progress)){
                    _measurementProgress.value =
                        workInfos.last().progress.getFloat(NoiseMsrWorker.Progress, 8/10f)
                }


            workInfos.first().let { msrWorkInfo ->
                if (msrWorkInfo.state == WorkInfo.State.SUCCEEDED) {

                    when (msrType) {
                        Measure.wifi ->
                            _lastWifiMsr.value = msrWorkInfo.outputData.getInt(
                                MsrWorkersInputData.MSR_KEY,
                                Int.MIN_VALUE
                            )

                        Measure.phone ->
                            _lastPhoneMsr.value = msrWorkInfo.outputData.getInt(
                                MsrWorkersInputData.MSR_KEY,
                                Int.MIN_VALUE
                            )

                        Measure.sound ->
                            _lastNoiseMsr.value = msrWorkInfo.outputData.getInt(
                                MsrWorkersInputData.MSR_KEY,
                                Int.MIN_VALUE
                            )
                    }
                }
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
        val workInfos = msrsWorkManager.runMeasurement(
            msrType = msrType,
            userLocation.value?.let {userLocation ->
                MsrWorkersInputData(
                    msrType = msrType.name,
                    lat = userLocation.latitude,
                    long = userLocation.longitude
                )
            } ?: return
        )
        changeMeasuringState(MeasuringState.RUNNING)

        //workInfos.observeForever(progressWorker)
        workInfos.observeForever(msrWorkObserver)
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

        viewModelScope.launch{
            userLocation.collect{ newLocation ->
                newLocation?.let{
                    setLastLocationUserSettings(newLocation)
                }
            }
        }

        mapScreenUiState.changeScreenLocation(
            latitude = userSettings.value.lastLocationLat.takeUnless { it == 0.0 } ?: 44.494887,
            longitude = userSettings.value.lastLocationLat.takeUnless { it == 0.0 } ?: 11.3426163
        )

        //If location permissions are granted, start location update on app boot
        if(app.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ||
            app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            locationUpdatesOn()
            setUserLocationAsScreenLocation()
        }

        //Create Notification Channel for measurement notifications
        notificationManager.createNotificationChannel(

            NotificationChannelCompat.Builder(
                MEASUREMENT_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).apply {

                setName(app.getString(R.string.measurements_channel_name))
                setDescription(app.getString(R.string.measurement_channel_description))

            }.build()
        )

    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {

        msrsRepo.closeLocalDB()
        super.onCleared()
    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


