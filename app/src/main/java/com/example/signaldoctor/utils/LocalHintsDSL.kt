package com.example.signaldoctor.utils

import androidx.datastore.core.DataStore
import com.example.signaldoctor.LocalHint
import com.example.signaldoctor.LocalHints
import com.example.signaldoctor.searchBarHint.ISearchBarHint

suspend fun DataStore<LocalHints>.addHint(hint: ISearchBarHint) {

    /*
localHints.toBuilder().addLocalHints(
    LocalHint.getDefaultInstance().toBuilder().apply {
        latitude = hint.latitude
        longitude = hint.longitude
        displayName = hint.locationName
    }.build()
).build()
}*/

    updateData { localHints ->

        localHints.toBuilder().apply {

            //add only if location is not already saved as a search bar hint
            if (!localHints.localHintsList.any { it.displayName == hint.locationName }) {
                addLocalHints(
                    LocalHint.getDefaultInstance().toBuilder().apply {
                        latitude = hint.latitude
                        longitude = hint.longitude
                        displayName = hint.locationName
                    }.build()
                )
            }
        }.build()


    }
}