package com.example.signaldoctor.utils

import android.location.Address
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.signaldoctor.Addresses
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class AddressesSerializer : Serializer<Addresses> {

    override val defaultValue: Addresses
        get() = Addresses.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Addresses {
        try {

            return Addresses.parseFrom(input)

        }catch (e : IOException){
            throw CorruptionException("can't read local search bar hints", e)
        }
    }

    override suspend fun writeTo(t: Addresses, output: OutputStream) {
        t.writeTo(output)
    }


}

fun Addresses.toPojoList(gson : Gson) =
        addressJsonList.map { addressJson ->
            try{
                gson.fromJson(addressJson, Address::class.java)
            }catch (e : JsonSyntaxException){
                e.printStackTrace()

            }
        }

