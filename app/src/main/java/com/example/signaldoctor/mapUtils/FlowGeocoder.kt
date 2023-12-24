package com.example.signaldoctor.mapUtils

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@ActivityRetainedScoped
class FlowGeocoder @Inject constructor(
    @ApplicationContext applicationContext: Context
){

    private val geocoder = Geocoder(applicationContext)

    fun geocode2(locationName : String) = Location("").apply {
        latitude = geocoder.getFromLocationName(locationName, 1)?.first()?.latitude ?: 2.0
        longitude = geocoder.getFromLocationName(locationName, 1)?.first()?.latitude ?: 2.0
    }
    suspend fun geocode(locationName : String)= suspendCancellableCoroutine<Location> { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(
                locationName,
                1
            ) { locationNames ->
                continuation.resume(Location("").apply {
                    latitude = locationNames.first().latitude
                    longitude = locationNames.first().longitude
                })
            }
        } else try {
           continuation.resume(
               Location("").apply {
                   latitude = geocoder.getFromLocationName(locationName, 1)?.first()?.latitude ?: 2.0
                   longitude = geocoder.getFromLocationName(locationName, 1)?.first()?.latitude ?: 2.0
               }
           )
        } catch (e: IOException) {
            Log.e("Geocoder Exception", "can't retrieve location from location name")
            continuation.resumeWithException(e)
        }
    }

}