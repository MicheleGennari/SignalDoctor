package com.example.signaldoctor.searchBarHint

import com.example.signaldoctor.LocalHint

class ProtoBuffHint(localHint: LocalHint) : ISearchBarHint {

    override val locationName: String = localHint.displayName

    override val latitude: Double = localHint.latitude

    override val longitude: Double = localHint.longitude
}