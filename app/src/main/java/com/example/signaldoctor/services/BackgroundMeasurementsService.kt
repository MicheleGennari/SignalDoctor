package com.example.signaldoctor.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.screens.whenMsrType
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.getMeasureSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import java.time.Duration
import javax.inject.Inject


const val DURATION_KEY = "duration"

@AndroidEntryPoint
class BackgroundMeasurementsService: LifecycleService() {

    private lateinit var notificationManager : NotificationManager
    @Inject lateinit var msrsWorkManager: MsrsWorkManager
    @Inject lateinit var settingsDataStore : DataStore<AppSettings>

    companion object {
        const val START_BACKGROUND_ACTION = "RUN_BACKGROUND_ACTION"
        const val STOP_BACKGROUND_ACTION = "STOP_BACKGROUND_ACTION"
        const val START_PHONE_ACTION = "START_PHONE_ACTION"
        const val START_NOISE_ACTION = "START_NOISE_ACTION"
        const val START_WIFI_ACTION = "START_WIFI_ACTION"
        const val BACKGROUND_SERVICE_NOTIFICATION = 5001

    }

    private lateinit var appSettings : SharedFlow<AppSettings>

    private fun initializeAppSettings() = settingsDataStore.data.onEach {
        if(!(it.phoneSettings.isBackgroundMsrOn || it.noiseSettings.isBackgroundMsrOn || it.wifiSettings.isBackgroundMsrOn))
            stopSelf()
    }.shareIn(lifecycleScope, SharingStarted.WhileSubscribed())


    private val phoneJob = Job()
    private val noiseJob = Job()
    private val wifiJob = Job()

    private fun launchSettingsScreenAction() : NotificationCompat.Action {
        val intent = Intent(
            Intent.ACTION_VIEW,
            DestinationsInfo.SettingsScreen.LAUNCH_FROM_SERVICE_URI.toUri(),
            applicationContext,
            MainActivity::class.java
        )

        return NotificationCompat.Action.Builder(
            R.drawable.signal_doctor_icon,
            "touch this notification to manage background measurements",
            TaskStackBuilder.create(applicationContext)
                .addNextIntent(intent)
                .getPendingIntent(BACKGROUND_SERVICE_NOTIFICATION, PendingIntent.FLAG_UPDATE_CURRENT)
        ).build()

    }

    private fun launchSettingsScreenPendingIntent() =
        TaskStackBuilder.create(applicationContext).run {
            addNextIntentWithParentStack(
                Intent(
                    Intent.ACTION_VIEW,
                    DestinationsInfo.SettingsScreen.LAUNCH_FROM_SERVICE_URI.toUri(),
                    applicationContext,
                    MainActivity::class.java
                )
            )
            getPendingIntent(BACKGROUND_SERVICE_NOTIFICATION, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    private fun serviceNotificationAction(msrType: Measure? = null) : NotificationCompat.Action {

        val intent = Intent(applicationContext, BackgroundMeasurementsService::class.java).apply {

            //action is STOP_BACKGROUND_ACTION if msrType is null, otherwise is bound to specific msrType
            action = if(msrType == null)
                        STOP_BACKGROUND_ACTION
                else whenMsrType(
                        msrType,
                        phone = START_PHONE_ACTION,
                        sound = START_NOISE_ACTION,
                        wifi = START_WIFI_ACTION
                )
            }

        return NotificationCompat.Action.Builder(
            if(msrType != null)
                whenMsrType(
                    msrType,
                    phone = R.drawable.phone_icon_notification,
                    sound = R.drawable.ear_icon_notification,
                    wifi = R.drawable.wifi_icon_notification
                ) else
                    R.drawable.signal_doctor_icon,
            "Stop ${msrType?.name ?: ""}",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    msrType?.ordinal ?: BACKGROUND_SERVICE_NOTIFICATION,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    msrType?.ordinal ?: BACKGROUND_SERVICE_NOTIFICATION,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        ).build()
    }

    private fun foregroundNotification(optionalActions : List<NotificationCompat.Action> = emptyList() ) =
        NotificationCompat.Builder(this, MEASUREMENT_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.termometer_icon)
        .setContentTitle("Background Measurements On")
        .setContentText("touch here to manage background measurements")
            .setShowWhen(false)
            .setContentIntent(launchSettingsScreenPendingIntent())
        .apply {
            for (action in optionalActions){
                addAction(action)
            }
        }.build()





    private fun updateForegroundNotification(appSettings: AppSettings){

        notificationManager.notify(
            BACKGROUND_SERVICE_NOTIFICATION,
            foregroundNotification(
            optionalActions = listOfNotNull(
                if(appSettings.wifiSettings.isBackgroundMsrOn) serviceNotificationAction(Measure.wifi) else null,
                if(appSettings.phoneSettings.isBackgroundMsrOn) serviceNotificationAction(Measure.phone) else null,
                if(appSettings.noiseSettings.isBackgroundMsrOn) serviceNotificationAction(Measure.sound) else null,

                if(!(appSettings.noiseSettings.isBackgroundMsrOn || appSettings.phoneSettings.isBackgroundMsrOn || appSettings.wifiSettings.isBackgroundMsrOn))
                    serviceNotificationAction()
                else null
            ))
        )

    }

    override fun onCreate(){
        super.onCreate()


        notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        consoledebug("Background Measurement Service is in onCreate")


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BACKGROUND_SERVICE_NOTIFICATION,
                foregroundNotification(),
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0 or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else startForeground(BACKGROUND_SERVICE_NOTIFICATION, foregroundNotification())

        appSettings = initializeAppSettings()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        phoneJob.isActive

        when (intent?.action) {

            START_PHONE_ACTION -> backgroundMsrTypeManager(Measure.phone)

            START_NOISE_ACTION -> backgroundMsrTypeManager(Measure.sound)

            START_WIFI_ACTION -> backgroundMsrTypeManager(Measure.wifi)

            STOP_BACKGROUND_ACTION -> stopSelf(startId)
        }

        return super.onStartCommand(intent, flags, startId)
    }


    private fun startBackgroundManager(msrType : Measure, startId: Int) : Job {
        val thisMsrJob =whenMsrType(msrType,
            phone = phoneJob,
            sound = noiseJob,
            wifi = wifiJob
            )

        return if(thisMsrJob?.isActive != true)
            backgroundMsrTypeManager(msrType)
        else
            thisMsrJob

    }

    private fun backgroundMsrTypeManager(msrType : Measure) = combine(
        appSettings.map { appSettings ->
            appSettings.getMeasureSettings(msrType).isBackgroundMsrOn
        }.distinctUntilChanged(),
        settingsDataStore.data.map { appSettings ->
            appSettings.getMeasureSettings(msrType).periodicity
        }.distinctUntilChanged()
    ){ isBackgroundMsrOn, periodicity ->

        Log.d("service measurement manager", "$msrType background measurement flow is running")

        if(!isBackgroundMsrOn)
            msrsWorkManager.cancelBackgroundMeasurement(msrType)
        else
            msrsWorkManager.runBackgroundMeasurement(
                msrType = msrType,
                interval = Duration.ofMinutes(periodicity.toLong())
            )

    }.onCompletion {
        consoledebug("service $msrType flow completed")
    }.launchIn(lifecycleScope)
    //END OF backgroundMeasurementsManager()

}



/*
    private fun runBackgroundMeasurement(intent: Intent) {

        msrsWorkManager.runBackgroundMeasurmeent(
            msrType = readMsrTypeFromIntentExtras(intent.extras) ?: return,
            interval = readDurationMinutesFromIntentExtras(intent.extras) ?: return
        )
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
*/


