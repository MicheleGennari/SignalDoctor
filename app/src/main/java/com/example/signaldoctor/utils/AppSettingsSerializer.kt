package com.example.signaldoctor.utils

import android.net.Network
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.NetworkMode
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import java.util.Date

class AppSettingsSerializer() : Serializer<AppSettings> {

    override val defaultValue: AppSettings
        get() = AppSettings.getDefaultInstance().toBuilder().apply {
            lastLocationLat = 44.498955
            lastLocationLon = 11.327591
            wifiSettings = MeasurementSettingsPopulatedDefaultInstance()
            noiseSettings = MeasurementSettingsPopulatedDefaultInstance()
            phoneSettings = MeasurementSettingsPopulatedDefaultInstance()
        }.build()

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            AppSettings.parseFrom(input)
        } catch (e : IOException){
            throw CorruptionException("cannot read protobuf file", e)
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
            t.writeTo(output)
    }
}

fun MeasurementSettingsPopulatedDefaultInstance() : MeasurementSettings {
    return MeasurementSettings.getDefaultInstance().toBuilder().apply {
        freshness = Date().time
        oldness = Date().time - 86400
        msrsToTake = 10
        periodicity = 5
    }.build()
}

operator fun NetworkMode.not() : NetworkMode{
    return if(this == NetworkMode.ONLINE)
        NetworkMode.OFFLINE
    else
        NetworkMode.ONLINE
}