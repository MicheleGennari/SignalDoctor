package com.example.signaldoctor.mapUtils

import android.location.Address
import android.location.Location

interface IFlowGeocoder {
    suspend fun getAddressesFromLocationName(locationName: String): List<Address>
    suspend fun getAddressesFromLocation(location: Location): List<Address>
}