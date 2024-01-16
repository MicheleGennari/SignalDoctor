package com.example.signaldoctor.utils

import android.location.Address
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.signaldoctor.LocalHints
import com.example.signaldoctor.searchBarHint.ProtoBuffHint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class LocalHintsSerializer : Serializer<LocalHints> {

    override val defaultValue: LocalHints
        get() = LocalHints.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LocalHints {
        try {

            return LocalHints.parseFrom(input)

        }catch (e : IOException){
            throw CorruptionException("can't read local search bar hints list", e)
        }
    }

    override suspend fun writeTo(t: LocalHints, output: OutputStream) {
        t.writeTo(output)
    }
}

fun LocalHints.protoBufHints() = localHintsList.map { ProtoBuffHint(it) }