package com.example.signaldoctor.searchBarHint

import androidx.compose.runtime.Stable

@Stable
interface ISearchBarHint {

    val locationName : String
    val latitude : Double
    val longitude : Double


}