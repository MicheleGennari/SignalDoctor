package com.example.signaldoctor.viewModels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.MapnikMap
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.uistates.MapScreenUiState
import com.example.signaldoctor.workers.PostMsrsWorker_InputContract
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import org.osmdroid.views.MapView
import javax.inject.Inject

private val POST_MSR_WORK_TAG = "postMsrWOrkTag"
private val POST_MSR_ACTIVE_WORK_NAME = "postMsrActiveWork"
private val POST_MSR_BACKGROUND_WORK_NAME = "postMsrBackgroundWork"

@HiltViewModel
class MyViewModel @Inject constructor(
    private val msrsRepo: MsrsRepo,
    val mapScreenUiState: MapScreenUiState,
    @MapnikMap val map : MapView,
    private val locationProvider : FusedLocationProviderClient,
    private val workManager: WorkManager,
    @ApplicationContext private val app : Context
) : ViewModel() {

    init{
        getUserLocation()
    }

    val phoneAvgs = msrsRepo.getMergedAvgs(Measure.phone).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val soundAvgs = msrsRepo.getMergedAvgs(Measure.sound).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())
    val wifiAvgs = msrsRepo.getMergedAvgs(Measure.wifi).stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = MsrsMap())

    private val _netWorkMode = MutableStateFlow(NetworkMode.ONLINE)
    val networkMode = _netWorkMode.asStateFlow()

    fun changeNetWorkMode(newNetworkMode: NetworkMode){
        _netWorkMode.value = newNetworkMode
    }

    private val _userLocation = MutableStateFlow(Location("location"))
    val userLocation = _userLocation.asStateFlow()

    fun getUserLocation(){
        if(ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || (ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locationProvider.getLastLocation().addOnSuccessListener { location ->
                _userLocation.value = location
            }
        }else consoledebug("Permesso non dato")
    }

    fun setUserLocation(mLatitude : Double, mLongitude : Double){
        _userLocation.value.apply {
            latitude = mLatitude
            longitude = mLongitude
        }
    }
    fun setUserLocationFromQueryString(query : String){
        val longitude : Double?
        val latitude :  Double?
        query.split(",").run {
            latitude = firstOrNull()?.toDoubleOrNull()
            longitude = getOrNull(1)?.toDoubleOrNull()
        }
        if(latitude != null && longitude != null){
            setUserLocation(latitude,longitude)
            mapScreenUiState.centerOnUserLocation()
        }
    }


    /////////////////////////////////////////////////
    //////////////DATABASE OPERATIONS///////////////
    ///////////////////////////////////////////////

    fun sendMeasures(msrType : Measure, timeInterval : Long, measuringMode : MeasuringState){
        val postMsrWorkRequest =OneTimeWorkRequestBuilder<MsrsRepo.PostMsrWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(POST_MSR_WORK_TAG)
                .setInputData(workDataOf(
                    PostMsrsWorker_InputContract.MSR_TYPE_KEY to msrType,
                ))
                .build()

            workManager.enqueueUniqueWork(
                POST_MSR_ACTIVE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                postMsrWorkRequest
            ).state

    }

}

/*
    latitude, longitude --------> maptileindex
    Ypixels, Xpixels-----------> MapTileIndex.getTileIndex() ---------> maptileindex

    zoomLevel -------TileSystem.MapSize()--------> MapSize

 */


