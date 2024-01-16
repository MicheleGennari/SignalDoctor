package com.example.signaldoctor.utils

import android.location.Address
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.signaldoctor.LocalHint
import com.example.signaldoctor.LocalHints
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class LocalHintSerializer : Serializer<LocalHint> {

    override val defaultValue: LocalHint
        get() = LocalHint.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LocalHint {
        try{
           return LocalHint.parseFrom(input)
        }catch (e : IOException){
            throw CorruptionException("error riding a local search bar hint",e)
        }
    }

    override suspend fun writeTo(t: LocalHint, output: OutputStream) {
        t.writeTo(output)
    }
}
