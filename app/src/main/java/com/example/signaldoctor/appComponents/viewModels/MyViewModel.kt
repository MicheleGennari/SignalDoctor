package com.example.signaldoctor.appComponents.viewModels

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.annotation.FloatRange
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.FlowConnectivityManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.contracts.NetworkMode
import com.example.signaldoctor.hiltModules.DefaultTileMap
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.example.signaldoctor.workers.noiseWork
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystem
import org.osmdroid.util.TileSystemWebMercator
import org.osmdroid.views.MapView
import java.util.concurrent.Executors
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
    msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    @DefaultTileMap val map : MapView,
    val locationProvider : FlowLocationProvider,
    private val msrsWorkManager: MsrsWorkManager,
    private val notificationManager : NotificationManagerCompat,
    private val connectivityManager: FlowConnectivityManager,
    @ApplicationContext private val app : Context
) : ViewModel() {

    val phoneAvgs = msrsRepo.getMergedAvgs(Measure.phone).onEach { hashMap ->
        hashMap.forEach{entry ->
        Log.i("OUTPUT AVGS", "KEY: ${entry.key}, VALUE: ${entry.value}")
    }}.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
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
/*
    val userLocation = locationProvider.requestLocationUpdates(
        LocationRequest.Builder(LOCATION_INTERVAL).setPriority(LOCATION_PRIORITY)
            .build()
    ).stateIn(scope = viewModelScope, started= SharingStarted.WhileSubscribed(), initialValue = null)
*/
    private val _userLocation : MutableStateFlow<Location?> = MutableStateFlow(null)
    val userLocation = _userLocation.asStateFlow()
    private var locationUpdatesJob : Job? = null

    fun locationUpdatesOn() {

        locationUpdatesJob = viewModelScope.launch{

            locationProvider.requestLocationUpdates(
                LocationRequest.Builder(LOCATION_INTERVAL).setPriority(LOCATION_PRIORITY)
                    .build()
            ).collect{ newLocation->
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


        val msrObserver = Observer<List<WorkInfo>> { workInfos ->
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
        }

        val progressWorker = Observer<List<WorkInfo>> { workInfos ->
            workInfos.last().let { lastWorkInfo ->
                if (lastWorkInfo.state.isFinished)
                    changeMeasuringState(MeasuringState.STOP)
            }
        }

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

        workInfos.observeForever(progressWorker)
        workInfos.observeForever(msrObserver)
    }

    fun runNoiseMeasurementDebug(){
        Executors.newSingleThreadExecutor().submit{
            noiseWork(app)
        }
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

    override fun onCleared() {
        super.onCleared()
    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


