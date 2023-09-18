package com.example.signaldoctor.viewModels

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.preference.PreferenceManager
import android.telephony.TelephonyManager
import androidx.annotation.FloatRange
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.signaldoctor.contracts.NetworkMode
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.MapnikMap
import com.example.signaldoctor.mapUtils.FlowLocationProvider
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.workers.work
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject

private val POST_MSR_WORK_TAG = "postMsrWOrkTag"
private val POST_MSR_ACTIVE_WORK_NAME = "postMsrActiveWork"
private val POST_MSR_BACKGROUND_WORK_NAME = "postMsrBackgroundWork"

@HiltViewModel
open class MyViewModel @Inject constructor(
    private val msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    @MapnikMap val map : MapView,
    private val locationProvider : FlowLocationProvider,
    private val workManager: WorkManager,
    private val telephonyMngr : TelephonyManager,
    @ApplicationContext private val app : Context
) : ViewModel() {


    val phoneAvgs = msrsRepo.getMergedAvgs(Measure.phone)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val soundAvgs = msrsRepo.getMergedAvgs(Measure.sound)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val wifiAvgs = msrsRepo.getMergedAvgs(Measure.wifi)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())

    //private val _isGpsEnabled = MutableStateFlow(false)
    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled = _isGpsEnabled.asStateFlow()
    suspend fun checkifGpsIsAvailable(): Boolean {
        _isGpsEnabled.value = locationProvider.isProviderAvailable()
        return _isGpsEnabled.value
    }

    private val _userLocation = MutableStateFlow(Location("provider").apply {
        latitude = 44.29
        longitude = 11.20
    }
    )
    val userlocation = _userLocation.asStateFlow()
    suspend fun updateUserLocation(): Boolean {  //true if user location is retrieved, false otherwise

        checkifGpsIsAvailable()
        locationProvider.getCurrentLocation()?.let { location ->
            _userLocation.value = location
            return true

        }
        consoledebug("GPS disattivato")
        return false
    }


    private val _netWorkMode = MutableStateFlow(NetworkMode.ONLINE)
    val networkMode = _netWorkMode.asStateFlow()

    fun changeNetWorkMode(newNetworkMode: NetworkMode) {
        _netWorkMode.value = newNetworkMode
    }
    /*
private val _userLocation =
)
*/
    /*
   private val _userLocation = MutableStateFlow(Location("provider").apply {
       latitude = 44.29
       longitude = 11.20
   })*/


    /*    fun isGpsEnabled() = callbackFlow<Boolean>{
        //val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
               locationProvider.locationAvailability.addOnSuccessListener {
                   _isGpsEnabled.value = it.isLocationAvailable
               }
        }
        awaitClose()
    }
    fun getUserLocation(){
        if(ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || (ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locationProvider.lastLocation.addOnSuccessListener { location ->
                if(location != null) _userLocation.value = location

            }
        }else consoledebug("User GeoLocation's permission not granted")
    }
    */

    /*fun getUserLocation() : Flow<Location> = callbackFlow{

        if(ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || (ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            val locationListener = locationProvider.lastLocation.addOnSuccessListener { location ->
                location?.run{
                    trySendBlocking(location)
                }
            }
        }
        else consoledebug("User GeoLocation's permission not granted")
        awaitClose()
    }*/

    fun changeScreenLocation(
        @FloatRange(from = -90.0, to = 90.0) latitude: Double,
        @FloatRange(from = -180.0, to = 180.0) longitude: Double
    ) {
        mapScreenUiState.changeScreenLocation(latitude, longitude)
    }

    fun setUserLocationAsScreenLocation() {
        viewModelScope.launch {
            if (updateUserLocation()) {
                consoledebug("PROCA")
                userlocation.value.run {
                    mapScreenUiState.changeScreenLocation(latitude, longitude)
                    centerOnScreenLocation()
                }
            }
        }
    }


    fun centerOnScreenLocation() {
        mapScreenUiState.centerOnScreenLocation()
        mapScreenUiState.updateSearchBarText("${userlocation.value.latitude}, ${userlocation.value.longitude}")
    }

    fun setUserLocationFromQueryString(query: String) {
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
    //////////////DATABASE OPERATIONS///////////////
    ///////////////////////////////////////////////

    @SuppressLint("SuspiciousIndentation")
    // according to Measuring state, work will be executed persistently in background (background mode) or
    // until
    fun sendMeasures(
        msrType: Measure,
        timeInterval: Long = 15,
        measuringMode: MeasuringState = MeasuringState.RUNNING
    ) {

        viewModelScope.launch(Dispatchers.Default) {
            /*msrsRepo.onlineDB.postMsr(
                msrType = msrType.name,
                msr = 125.7,
                7, 10, 12,
                19, 3
            )*/
            consoledebug(work(app).toString())
            changeMeasuringState(MeasuringState.STOP)
        }
        /*workManager.enqueue(OneTimeWorkRequestBuilder<PostMsrWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(
                WorkersKeysContract.MSR_TYPE_KEY to msrType.name,
                WorkersKeysContract.MSR_KEY to 125.7,
                WorkersKeysContract.Z_KEY to 7,
                WorkersKeysContract.X_KEY to 10,
                WorkersKeysContract.Y_KEY to 12,
                WorkersKeysContract.Z_MAX_KEY to 19,
                WorkersKeysContract.Z_MIN_KEY to 3
            ))
            .build())*/
    }

    fun stopMeasuring() {
        val currentMeasuringState = mapScreenUiState.measuringState.value
        if (
            (currentMeasuringState == MeasuringState.RUNNING)
            || (currentMeasuringState == MeasuringState.BACKGROUND)
        ) {
            workManager.cancelUniqueWork(POST_MSR_ACTIVE_WORK_NAME)
            mapScreenUiState.changeMeasuringState(MeasuringState.STOP)
        }
    }

    fun changeMeasuringState(newMsrState: MeasuringState) = run {
        mapScreenUiState.changeMeasuringState(newMsrState)
    }

    init {

        viewModelScope.launch {
            Configuration.getInstance().load(app, PreferenceManager.getDefaultSharedPreferences(app))
            setUserLocationAsScreenLocation()

            //getUserLocation()
        }

        this.onCleared()
    }
}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


