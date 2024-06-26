package com.example.signaldoctor.mapUtils

import android.location.Address
import android.location.Location
import com.example.signaldoctor.utils.Loggers.consoleDebug
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.osmdroid.bonuspack.location.GeocoderNominatim
import java.io.IOException
import javax.inject.Inject

@ActivityRetainedScoped
class FlowOsmGeocoder @Inject constructor(
    private val geocoder : GeocoderNominatim
) : IFlowGeocoder {
    override suspend fun getAddressesFromLocationName(locationName: String) : List<Address> =
        try {
            consoleDebug("searching by location name")
            geocoder.getFromLocationName(
                locationName,
                10
            ).also {
                consoleDebug("first address is ")
            }
        }catch (e : IOException){
            e.printStackTrace()
            emptyList()
        }

    override suspend fun getAddressesFromLocation(location: Location) : List<Address> =
        try {
            consoleDebug("searching by coordinates")
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                10
            )
        }catch (e : IOException){
            e.printStackTrace()
            emptyList()
        }
    }
