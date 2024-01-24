package com.example.signaldoctor.utils

import android.R.bool
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.signaldoctor.MeasurementSettings
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

const val IS_BACKGROUND_ON_DEFAULT = false

const val PERIODICITY_MIN = 15
const val PERIODICITY_DEFAULT = 15
const val PERIODICITY_MAX = 60
const val USE_MSRS_TO_TAKE = false

const val MSRS_TO_TAKE_MIN = 10
const val MSRS_TO_TAKE_DEFAULT = 10
const val MSRS_TO_TAKE_MAX = 100

class MeasurementSettingsSerializer() : Serializer<MeasurementSettings> {

    override val defaultValue: MeasurementSettings
        get() = initializeMeasurementSettings()
    override suspend fun readFrom(input: InputStream): MeasurementSettings {
        try{
            return MeasurementSettings.parseFrom(input)
        }catch (e : IOException){
            throw CorruptionException("Cannot read settings of this measurement.", e)
        }
    }

    override suspend fun writeTo(t: MeasurementSettings, output: OutputStream) {
        t.writeTo(output)
    }
}

fun initializeMeasurementSettings(): MeasurementSettings = MeasurementSettings
    .getDefaultInstance().toBuilder().apply {

        isBackgroundMsrOn = IS_BACKGROUND_ON_DEFAULT
        periodicity = PERIODICITY_DEFAULT

        useMsrsToTake  = USE_MSRS_TO_TAKE
        msrsToTake = MSRS_TO_TAKE_DEFAULT

        freshness = ZonedDateTime.now().toEpochMillis()
        oldness = ZonedDateTime.now().minusWeeks(1).toEpochMillis()

}.build()