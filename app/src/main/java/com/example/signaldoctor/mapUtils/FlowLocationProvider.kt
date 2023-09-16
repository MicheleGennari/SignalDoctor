package com.example.signaldoctor.mapUtils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnSuccessListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject


class FlowLocationProvider @Inject constructor(
    val provider: FusedLocationProviderClient,
    @ApplicationContext val app : Context
) {

    val cs = CoroutineScope(Dispatchers.Default)

    fun getCurrentLocation() = callbackFlow<Location> {

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
            try {
                if(this@FlowLocationProvider.isProviderAvailable().last()){
                    provider.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).run {
                        addOnFailureListener { failure -> trySend(Location("provider")) }
                        addOnSuccessListener { location -> trySend(location) }
                    }
                }
                awaitCancellation()
            } finally { }
        }
        awaitClose()
    }


    fun isProviderAvailable() = callbackFlow<Boolean> {
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
          try{
              provider.locationAvailability.addOnSuccessListener { availability ->
                  trySend(availability.isLocationAvailable)
              }
              awaitCancellation()
          }finally { }
        }
        awaitClose()
    }
}


