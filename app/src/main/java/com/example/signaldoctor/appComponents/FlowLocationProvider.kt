package com.example.signaldoctor.appComponents

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.hiltModules.AppCoroutineScope
import com.example.signaldoctor.utils.Loggers
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume


const val CHANGE_LOCATION_SETTINGS = 42

const val LOCATION_GRANULARITY = 2000
const val LOCATION_INTERVAL = 5000L
const val LOCATION_PRIORITY = Priority.PRIORITY_HIGH_ACCURACY

@Singleton
class FlowLocationProvider @Inject constructor(
    private val provider: FusedLocationProviderClient,
    private val settingsClient : SettingsClient,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
){

    companion object{
        val defaultLocationUpdateSettings = LocationRequest.Builder(LOCATION_INTERVAL).setPriority(
            LOCATION_PRIORITY
        ).build()
    }

    @SuppressLint("MissingPermission")
    val userLocation : StateFlow<Location?> =  callbackFlow<Location?> {

        val callback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                trySend(p0.lastLocation)
            }

            override fun onLocationAvailability(p0: LocationAvailability) {
                super.onLocationAvailability(p0)
                if (!p0.isLocationAvailable) trySend(null)
            }

        }

        provider.requestLocationUpdates(
            defaultLocationUpdateSettings,
            callback,
            Looper.getMainLooper()
        )
        awaitClose{ provider.removeLocationUpdates(callback) }

    }.onStart {
        consoleDebug("gps location collection started")
    }.onCompletion {
        consoleDebug("gps location collection ended")
    }.stateIn(appCoroutineScope, SharingStarted.WhileSubscribed(5000), null)

    fun userLoc(permission : Flow<Boolean>) = permission.flatMapLatest { isLocationGranted ->
        if(isLocationGranted) userLocation
        else MutableStateFlow<Location?>(null).asStateFlow()
    }.stateIn(appCoroutineScope, SharingStarted.WhileSubscribed(), null)


    @SuppressLint("MissingPermission")
    fun requestLocationUpdates(lr : LocationRequest = defaultLocationUpdateSettings) = callbackFlow<Location?> {

            val callback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)
                    trySend(p0.lastLocation)
                }

                override fun onLocationAvailability(p0: LocationAvailability) {
                    super.onLocationAvailability(p0)
                    if (!p0.isLocationAvailable) trySend(null)
                }

            }

            provider.requestLocationUpdates(
                lr,
                callback,
                Looper.getMainLooper()
            )
            awaitClose{ provider.removeLocationUpdates(callback) }

    }.flowOn(Dispatchers.IO)

     suspend fun checkLocationSettings(lr: LocationRequest, mainActivity: Activity) =
         suspendCancellableCoroutine { continuation->

            settingsClient.checkLocationSettings(
                LocationSettingsRequest.Builder().addLocationRequest(lr).build()
            ).addOnSuccessListener { _ ->
                continuation.resume(true)
            }.addOnFailureListener { exception->
                if(exception is ResolvableApiException){
                    exception.startResolutionForResult(mainActivity, CHANGE_LOCATION_SETTINGS)
                }
                continuation.resume(false)
            }
        }



    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(priority: @Priority Int) = suspendCancellableCoroutine<Location?> {continuation->

        val cs = CancellationTokenSource()
        provider.getCurrentLocation(
            priority,
            cs.token
        ).addOnSuccessListener {
            result-> continuation.resume(result)
        }.addOnFailureListener{ exception->
            exception.printStackTrace()
            continuation.resume(null)
        }

    }


}


