package com.example.signaldoctor.mapUtils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.example.signaldoctor.searchBarHint.GeocoderHint
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.workers.printAndReturn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.commons.math3.analysis.function.Add
import org.osmdroid.bonuspack.location.GeocoderNominatim
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val GEOCODER_BOUNDING_BOX_SIDE= 50.0

@ActivityRetainedScoped
class FlowGeocoder @Inject constructor(
    private val geocoder: Geocoder
) : IFlowGeocoder {


    private val g = GeocoderNominatim("Signal_Doctor")



    override suspend fun getAddressesFromLocationName(locationName: String) : List<Address> {

        if(locationName.isBlank()) return emptyList()

        return try {
            geocoder.getFromLocationName(locationName, 10)?: emptyList()
        }catch (e : IOException){
            e.printStackTrace()
            emptyList()
        }.onEach {
            consoledebug(it.getAddressLine(0))
        }


    }

    override suspend fun getAddressesFromLocation(location : Location) : List<Address> {

        return try{
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                10
            ) ?: emptyList()
        }catch (e : IOException){
            Log.e("FlowGeocoder", " something went wrong while asking for addresses", e)
            e.printStackTrace()
            emptyList()
        }


    }



}

fun List<Address>.geocoderHints() = this.map { GeocoderHint(it) }