package com.example.signaldoctor.utils

import androidx.datastore.core.DataStore
import com.example.signaldoctor.LocalHint
import com.example.signaldoctor.LocalHints
import com.example.signaldoctor.searchBarHint.ISearchBarHint

suspend fun DataStore<LocalHints>.addHint(hint: ISearchBarHint){
    updateData { localHintsMap ->
        localHintsMap.toBuilder().addLocalHints(
            LocalHint.getDefaultInstance().toBuilder().apply {
                latitude = hint.latitude
                longitude = hint.longitude
                displayName = hint.locationName
            }.build()
        ).build()
    }
}