package com.example.signaldoctor.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.navigation.Navigator
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.screens.msrTypeWhen
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject

const val RUN_BACKGROUND_ACTION = "RUN_BACKGROUND_ACTION"
const val STOP_BACKGROUND_ACTION = "STOP_BACKGROUND_ACTION"
const val BACKGROUND_SERVICE_NOTIFICATION = 0


const val DURATION_KEY = "duration"

@AndroidEntryPoint
class BackgroundMeasurementsService @Inject constructor(
    private val msrsWorkManager: MsrsWorkManager
) : Service() {



    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {


        val foregroundNotification = NotificationCompat.Builder(this, MEASUREMENT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.signal_doctor_icon)
            .setContentText("Signal Doctor Background Measurements")
            .setContentText("Signal doctor is currently performing background measurements...")

        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                BACKGROUND_SERVICE_NOTIFICATION,
                foregroundNotification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE + ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        if(intent != null) {
            when(intent.action) {
                RUN_BACKGROUND_ACTION -> runBackgroundMeasurement(intent)
                STOP_BACKGROUND_ACTION -> cancelBackgroundMeasurement(intent)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun runBackgroundMeasurement(intent: Intent) {

        msrsWorkManager.runBackgroundMeasurmeent(
            msrType = readMsrTypeFromIntentExtras(intent.extras) ?: return,
            interval = readDurationMinutesFromIntentExtras(intent.extras) ?: return
        )
    }

    override fun onDestroy() {
        msrsWorkManager.cancelAllMeasurements()
        super.onDestroy()
    }

    private fun cancelBackgroundMeasurement(intent : Intent){
        msrsWorkManager.cancelBackgroundMeasurement(readMsrTypeFromIntentExtras(intent.extras) ?: return)
    }

    private fun readMsrTypeFromIntentExtras(extras : Bundle?) : Measure? {

        if(extras == null) return null

        return when (extras.getInt(MeasurementBase.MSR_TYPE_KEY, Int.MIN_VALUE)) {
            Measure.sound.ordinal -> Measure.sound
            Measure.wifi.ordinal -> Measure.wifi
            Measure.phone.ordinal -> Measure.phone
            else -> null
        }
    }

    private fun readDurationMinutesFromIntentExtras(extras : Bundle?) : Duration? {

        if(extras == null) return null

        return Duration.ofMinutes(extras.getLong( DURATION_KEY, 0L).takeUnless { it < 15L } ?: return null)
    }

}