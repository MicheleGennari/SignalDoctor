package com.example.signaldoctor.viewModels

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.preference.PreferenceManager
import android.telephony.TelephonyManager
import androidx.annotation.FloatRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.signaldoctor.contracts.NetworkMode
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.DefaultTileMap
import com.example.signaldoctor.hiltModules.MapnikMap
import com.example.signaldoctor.mapUtils.FlowLocationProvider
import com.example.signaldoctor.mapUtils.FlowLocationProvider_Factory
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.workers.work
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import javax.inject.Inject

const val POST_MSR_WORK_TAG = "postMsrWOrkTag"
const val POST_MSR_ACTIVE_WORK_NAME = "postMsrActiveWork"
const val POST_MSR_BACKGROUND_WORK_NAME = "postMsrBackgroundWork"

const val LOCATION_GRANULARITY = 2000
const val LOCATION_INTERVAL = 5000L
const val LOCATION_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY
@HiltViewModel
open class MyViewModel @Inject constructor(
    msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    @DefaultTileMap val map : MapView,
    locationProvider : FlowLocationProvider,
    private val workManager: WorkManager,
    private val telephonyMngr : TelephonyManager,
) : ViewModel() {


    val phoneAvgs = msrsRepo.getMergedAvgs(Measure.phone)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val soundAvgs = msrsRepo.getMergedAvgs(Measure.sound)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val wifiAvgs = msrsRepo.getMergedAvgs(Measure.wifi)
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())


    val userLocation = locationProvider.requestLocationUpdates(
        LocationRequest.Builder(LOCATION_INTERVAL).setPriority(LOCATION_PRIORITY).build()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null )



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

    init{
            setUserLocationAsScreenLocation()
    }

    override fun onCleared() {
        map.onDetach()
        super.onCleared()
    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


