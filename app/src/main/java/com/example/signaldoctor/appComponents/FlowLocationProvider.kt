package com.example.signaldoctor.appComponents

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.*
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.core.app.ActivityCompat
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.disposeOnCancellation
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class FlowLocationProvider @Inject constructor(
    val provider: FusedLocationProviderClient,
    val settingsClient : SettingsClient,
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
        provider.requestLocationUpdates(
            lr,
            callback,
            Looper.myLooper()
        )

        awaitClose{ provider.removeLocationUpdates(callback) }
    }

    /*
    private suspend fun checkLocationRequest(lr: LocationRequest) = suspendCancellableCoroutine<Boolean> {continuation->

        settingsClient.checkLocationSettings(
            LocationSettingsRequest.Builder().addLocationRequest(lr).build()
        ).addOnSuccessListener { response->
            continuation.resume(true)
        }.addOnFailureListener { exception->
            continuation.resumeWithException(exception)
        }
    }
    */



    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(priority: @Priority Int) = suspendCancellableCoroutine<Location> {continuation->

        val cs = CancellationTokenSource()

        provider.getCurrentLocation(
            priority,
            cs.token
        ).addOnSuccessListener {
            result-> continuation.resume(result)
        }.addOnFailureListener{ exception->
            exception.printStackTrace()
            continuation.resumeWithException(exception)
        }

    }



}


