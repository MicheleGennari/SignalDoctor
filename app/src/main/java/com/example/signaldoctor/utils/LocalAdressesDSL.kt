package com.example.signaldoctor.utils

import android.location.Address
import androidx.datastore.core.DataStore
import com.example.signaldoctor.Addresses
import com.google.gson.Gson

suspend fun DataStore<Addresses>.add(vararg address: Address, gson: Gson) = updateData { oldAddresses ->
    oldAddresses.toBuilder().apply {
        addAllAddressJson(
            address.map { address ->
                gson.toJson(address)
            }
        )
    }.build()
}