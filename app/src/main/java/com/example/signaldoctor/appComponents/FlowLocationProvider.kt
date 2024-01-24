package com.example.signaldoctor.appComponents

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.utils.Loggers
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
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
const val LOCATION_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY

@Singleton
class FlowLocationProvider @Inject constructor(
    val provider: FusedLocationProviderClient,
    private val settingsClient : SettingsClient,
){


    companion object{
        val defaultLocationUpdateSettings = LocationRequest.Builder(LOCATION_INTERVAL).setPriority(
            LOCATION_PRIORITY
        ).build()
    }



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

            try {
                provider.requestLocationUpdates(
                    lr,
                    callback,
                    Looper.getMainLooper()
                )
                awaitCancellation()
            } finally {
                provider.removeLocationUpdates(callback)
            }

    }.conflate().distinctUntilChanged()

     suspend fun checkLocationSettings(lr: LocationRequest, mainActivity: MainActivity) =
         suspendCancellableCoroutine { continuation->

            settingsClient.checkLocationSettings(
                LocationSettingsRequest.Builder().addLocationRequest(lr).build()
            ).addOnSuccessListener { _ ->
                continuation.resume(true)
            }.addOnFailureListener { exception->
                if(exception is ResolvableApiException){
                    exception.startResolutionForResult(mainActivity, CHANGE_LOCATION_SETTINGS)
                }
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


