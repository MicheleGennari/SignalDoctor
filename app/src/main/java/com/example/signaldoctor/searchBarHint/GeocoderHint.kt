package com.example.signaldoctor.searchBarHint

import android.location.Address

class GeocoderHint(address: Address) : ISearchBarHint {

    override val locationName: String = address.getAddressLine(0)

    override val latitude: Double = address.latitude

    override val longitude: Double = address.longitude
}

fun Address.geocoderHint() = GeocoderHint(this)