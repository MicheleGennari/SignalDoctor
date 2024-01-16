package com.example.signaldoctor.utils

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

const val APP_LAUNCH_LOCATION_LAT = 44.498955
const val APP_LAUNCH_LOCATION_LON = 11.327591

class AppSettingsSerializer : Serializer<AppSettings> {

    override val defaultValue: AppSettings
        get() = AppSettings.getDefaultInstance().toBuilder().apply {

            lastLocationLat = APP_LAUNCH_LOCATION_LAT
            lastLocationLon = APP_LAUNCH_LOCATION_LON

            noiseSettings = initializeMeasurementSettings()
            phoneSettings = initializeMeasurementSettings()
            wifiSettings = initializeMeasurementSettings()

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

operator fun NetworkMode.not() : NetworkMode{
    return if(this == NetworkMode.ONLINE)
        NetworkMode.OFFLINE
    else
        NetworkMode.ONLINE
}

fun ZonedDateTime.toEpochMillis() = toInstant().toEpochMilli()

fun Long.toZoneDateTime(): ZonedDateTime = runCatching {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
}.getOrElse { e ->
 e.printStackTrace()
    ZonedDateTime.now()
}
