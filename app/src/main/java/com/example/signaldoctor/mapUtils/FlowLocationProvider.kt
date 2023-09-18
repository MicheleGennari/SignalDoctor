package com.example.signaldoctor.mapUtils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.*
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnSuccessListener
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class FlowLocationProvider @Inject constructor(
    val provider: FusedLocationProviderClient,
    @ApplicationContext val app : Context,
    @ActivityContext val activity : Activity
){

    val cs = CoroutineScope(Dispatchers.Default)

    suspend fun getCurrentLocation() = suspendCancellableCoroutine {continuation->

        val cts = CancellationTokenSource()
        val fineLocationPermission = ActivityCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarseLocationPermission = ActivityCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (
            fineLocationPermission == PackageManager.PERMISSION_GRANTED
            &&
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED
        ) {

            provider.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).run {
                addOnSuccessListener { location -> continuation.resume(location) }
            }.addOnFailureListener{e->
                continuation.resumeWithException(e)
            }

        }
    }

    fun getLocationConstantUpdates(block: com.google.android.gms.location.LocationRequest.Builder.() -> Unit) = callbackFlow {
            if (ActivityCompat.checkSelfPermission(
                    app,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    app,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val callback = object : LocationCallback() {
                    override fun onLocationAvailability(p0: LocationAvailability) {
                        super.onLocationAvailability(p0)
                        if (p0.isLocationAvailable == false) cancel()
                    }

                    override fun onLocationResult(p0: LocationResult) {
                        super.onLocationResult(p0)
                        trySend(p0.lastLocation)
                    }
                }

                provider.requestLocationUpdates(
                    checkLocationSettings(block),
                    callback,
                    Looper.myLooper()
                )

                awaitClose { provider.removeLocationUpdates(callback) }
            }



    }

    suspend fun checkLocationSettings(block: com.google.android.gms.location.LocationRequest.Builder.()-> Unit) = suspendCancellableCoroutine { continuation ->
        val settingsClient = LocationServices.getSettingsClient(app)
        val locationSettingsRequest = LocationSettingsRequest.Builder()
        val lr = com.google.android.gms.location.LocationRequest.Builder(10000L)
        lr.block()
        settingsClient.checkLocationSettings(
            locationSettingsRequest.addLocationRequest(lr.build()).build()
        ).addOnSuccessListener { response ->
            continuation.resume(lr.build())
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }

    suspend fun isProviderAvailable() = suspendCancellableCoroutine {continuation->
        val fineLocationPermission = ActivityCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarseLocationPermission = ActivityCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if(
            fineLocationPermission == PackageManager.PERMISSION_GRANTED
            &&
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED
        ){
          provider.locationAvailability.addOnSuccessListener { availability ->
              continuation.resume(availability.isLocationAvailable)
          }.addOnFailureListener{e->
              continuation.resumeWithException(e)
          }
        }
    }
}


