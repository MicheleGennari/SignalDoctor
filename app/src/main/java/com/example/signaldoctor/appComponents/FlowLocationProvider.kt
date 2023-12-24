package com.example.signaldoctor.appComponents

import android.annotation.SuppressLint
import android.content.IntentSender
import android.location.Location
import android.os.Looper
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


const val CHANGE_LOCATION_SETTINGS = 42


class FlowLocationProvider @Inject constructor(
    val provider: FusedLocationProviderClient,
    private val settingsClient : SettingsClient,
){

    @SuppressLint("MissingPermission")
    fun requestLocationUpdates(lr : LocationRequest) = callbackFlow<Location?> {

        val callback = object : LocationCallback(){
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                trySend(p0.lastLocation)
            }

            override fun onLocationAvailability(p0: LocationAvailability) {
                super.onLocationAvailability(p0)
                if(!p0.isLocationAvailable) trySend(null)
            }

        }

        try{
            provider.requestLocationUpdates(
                lr,
                callback,
                Looper.myLooper()
            )
            awaitCancellation()
        }finally {
            provider.removeLocationUpdates(callback)
        }

    }

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


