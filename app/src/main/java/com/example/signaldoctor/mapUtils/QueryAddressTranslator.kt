package com.example.signaldoctor.mapUtils

import android.location.Address
import android.location.Location
import com.example.signaldoctor.hiltModules.AndroidGeocoder
import com.example.signaldoctor.utils.Loggers
import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.util.regex.Pattern
import javax.inject.Inject

@ActivityRetainedScoped
class QueryAddressTranslator @Inject constructor(
    @AndroidGeocoder private val geocoder: IFlowGeocoder
) {

    //if query
    suspend fun getAddressesFromQuery(query : String) : List<Address>? {
        return if (query.isLocationName()) geocoder.getAddressesFromLocationName(query)
            else if (query.areCoordinates()) {
                val location = getCoordFromQuery(query) ?: return listOf()
                geocoder.getAddressesFromLocation(location)
            } else listOf()
    }

    companion object{
        fun getCoordFromQuery(query: String): Location? = query.split(",").run {
            Location("").apply {
                latitude = first().toDoubleOrNull() ?: return null
                longitude = get(1)?.toDoubleOrNull() ?: return null
            }
        }
    }

}

fun String.isLocationName() =
    Pattern.matches("[a-zA-Z]+",this)

fun String.areCoordinates() =
    Pattern.matches("[0-9]+,[0-9]+", this)