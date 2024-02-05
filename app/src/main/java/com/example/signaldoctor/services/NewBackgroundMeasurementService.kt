package com.example.signaldoctor.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.LifecycleService
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.viewModels.MapScreen.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.contracts.Measure
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class NewBackgroundMeasurementService : LifecycleService() {

    @Inject lateinit var settingsDataStore : DataStore<AppSettings>
    @Inject lateinit var msrsWorkManager: MsrsWorkManager

    companion object {
        const val START_BACKGROUND_ACTION = "RUN_BACKGROUND_ACTION"
        const val STOP_BACKGROUND_ACTION = "STOP_BACKGROUND_ACTION"

        const val BACKGROUND_SERVICE_NOTIFICATION = 5001

        const val EXTRA_MSR_TYPE = "msr_type"
        const val EXTRA_PERIODICITY = "periodicity"
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BACKGROUND_SERVICE_NOTIFICATION,
                getForegroundInfo(),
                foregroundServiceType
            )
        } else {
            startForeground(
                BACKGROUND_SERVICE_NOTIFICATION,
                getForegroundInfo()
            )
        }

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if(intent == null) return super.onStartCommand(null, flags, startId)



        if(intent.action == STOP_BACKGROUND_ACTION) stopSelf(startId)
        else {

            val msrType = intent.extras?.getInt(EXTRA_MSR_TYPE, Int.MAX_VALUE)
                ?: return super.onStartCommand(intent, flags, startId)

            if(msrType == Int.MAX_VALUE) return super.onStartCommand(intent, flags, startId)

            val interval = Duration.ofMinutes(
                intent.getIntExtra(EXTRA_PERIODICITY, 15).toLong()
            )

            when (intent.action) {

                START_BACKGROUND_ACTION -> msrsWorkManager.runBackgroundMeasurement(Measure.values()[msrType], interval)

                STOP_BACKGROUND_ACTION -> msrsWorkManager.cancelBackgroundMeasurement(Measure.values()[msrType])
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }


    private fun getLaunchSettingsScreenPendingIntent() =
        TaskStackBuilder.create(applicationContext).run {
            addNextIntentWithParentStack(
                Intent(
                    Intent.ACTION_VIEW,
                    DestinationsInfo.SettingsScreen.LAUNCH_FROM_SERVICE_URI.toUri(),
                    applicationContext,
                    MainActivity::class.java
                )
            )
            getPendingIntent(BackgroundMeasurementsService.BACKGROUND_SERVICE_NOTIFICATION, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    private fun getForegroundInfo() = NotificationCompat.Builder(applicationContext, MEASUREMENT_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.termometer_icon)
        .setContentTitle("Background Measurements On")
        .setContentText("touch here to manage background measurements")
        .setContentIntent(getLaunchSettingsScreenPendingIntent())
        .build()



}