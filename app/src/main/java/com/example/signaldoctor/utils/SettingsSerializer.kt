package com.example.signaldoctor.utils

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.example.signaldoctor.Settings
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream


class SettingsSerializer : Serializer<Settings>{
    override val defaultValue: Settings
        get() = Settings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Settings {
        try{
            return Settings.parseFrom(input)
        }catch (e : InvalidProtocolBufferException){
            throw CorruptionException("Cannot read settings", e)
        }
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        t.writeTo(output)
    }

}

