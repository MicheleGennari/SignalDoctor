package com.example.signaldoctor.mapUtils

import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.example.signaldoctor.searchBarHint.GeocoderHint
import com.example.signaldoctor.utils.Loggers.consoleDebug
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.osmdroid.bonuspack.location.GeocoderNominatim
import java.io.IOException
import javax.inject.Inject

const val GEOCODER_BOUNDING_BOX_SIDE= 50.0

@ActivityRetainedScoped
class FlowGeocoder @Inject constructor(
    private val geocoder: Geocoder
) : IFlowGeocoder {





    override suspend fun getAddressesFromLocationName(locationName: String) : List<Address> {

        if(locationName.isBlank()) return emptyList()

        return try {
            geocoder.getFromLocationName(locationName, 10)?: emptyList()
        }catch (e : IOException){
            e.printStackTrace()
            emptyList()
        }.onEach {
            consoleDebug(it.getAddressLine(0))
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