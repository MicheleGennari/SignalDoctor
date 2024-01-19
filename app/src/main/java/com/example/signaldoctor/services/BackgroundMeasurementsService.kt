package com.example.signaldoctor.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.screens.msrTypeWhen
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.noiseSettings
import com.example.signaldoctor.utils.phoneSettings
import com.example.signaldoctor.utils.update
import com.example.signaldoctor.utils.wifiSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

const val START_BACKGROUND_ACTION = "RUN_BACKGROUND_ACTION"
const val STOP_BACKGROUND_ACTION = "STOP_BACKGROUND_ACTION"
const val STOP_PHONE_ACTION = "STOP_PHONE_ACTION"
const val STOP_NOISE_ACTION = "STOP_NOISE_ACTION"
const val STOP_WIFI_ACTION = "STOP_WIFI_ACTION"
const val BACKGROUND_SERVICE_NOTIFICATION = 5001


const val DURATION_KEY = "duration"

@AndroidEntryPoint
class BackgroundMeasurementsService: Service() {

    private lateinit var notificationManager : NotificationManager
    @Inject lateinit var msrsWorkManager: MsrsWorkManager
    @Inject lateinit var settingsDataStore : DataStore<AppSettings>

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private fun openSettingsScreenAction() : NotificationCompat.Action {
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

    private fun openSettingsScreenPendingIntent() =
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
                else msrTypeWhen(
                        msrType,
                        phone = STOP_PHONE_ACTION,
                        sound = STOP_NOISE_ACTION,
                        wifi = STOP_WIFI_ACTION
                )
            }

        return NotificationCompat.Action.Builder(
            if(msrType != null)
                msrTypeWhen(
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
        .setSmallIcon(R.drawable.signal_doctor_icon)
        .setContentTitle("Background Measurements On")
        .setContentText("touch here to manage background measurements")
            .setShowWhen(false)
            .setContentIntent(openSettingsScreenPendingIntent())
        .apply {
            for (action in optionalActions){
                addAction(action)
            }
        }.build()



    private fun backgroundMeasurementsManager() = settingsDataStore.data.catch {e ->
        consoledebug("Inside coroutine catch")
        e.printStackTrace()
        emit(AppSettings.getDefaultInstance())
    }.onEach { appSettings ->
        consoledebug("Inside coroutine onEach")

        msrsWorkManager.runBackgroundMeasurement(Measure.phone, Duration.ofMinutes(appSettings.phoneSettings.periodicity.toLong()))

        /*
        manageBackgroundMeasurement(Measure.phone,appSettings.phoneSettings)
        manageBackgroundMeasurement(Measure.sound,appSettings.noiseSettings)
        manageBackgroundMeasurement(Measure.wifi, appSettings.wifiSettings)
        */

        shutdownServiceIfIdle(appSettings)

    }.onCompletion { _ ->
        consoledebug("coroutine onCompletion")
        msrsWorkManager.cancelAllMeasurements()
    }


    private fun manageBackgroundMeasurement(msrType : Measure, settings: MeasurementSettings) {



        if(settings.isBackgroundMsrOn) {
            consoledebug("manageBackgroundMeasurement(): msrType is ${msrType.name}")
            msrsWorkManager.runBackgroundMeasurement(
                msrType,
                Duration.ofMinutes(settings.periodicity.toLong())
            )
        }
        else {
            consoledebug("${msrType.name} background measurements will be stopped")
            msrsWorkManager.cancelBackgroundMeasurement(msrType)
        }
    }

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

    private fun shutdownServiceIfIdle(appSettings : AppSettings) = appSettings.run{

        if(
            appSettings.run {
                !(phoneSettings.isBackgroundMsrOn || noiseSettings.isBackgroundMsrOn || wifiSettings.isBackgroundMsrOn)
            }
        ) {
            consoledebug("Background Measurements are all turned of hence service will shutdown")
            stopSelf()
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {

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

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        consoledebug("Background Measurement Service is in onStartCommand")

        when (intent?.action) {

            START_BACKGROUND_ACTION -> runBackground()
            STOP_NOISE_ACTION -> settingsDataStore.update(coroutineScope){
                noiseSettings {
                    isBackgroundMsrOn = false
                }
            }
            STOP_PHONE_ACTION -> settingsDataStore.update(coroutineScope){
                phoneSettings {
                    isBackgroundMsrOn = false
                }
            }
            STOP_WIFI_ACTION -> settingsDataStore.update(coroutineScope){
                wifiSettings {
                    isBackgroundMsrOn = false
                }
            }
            STOP_BACKGROUND_ACTION -> stopSelf(startId)

        }
        /*

        // this is from the first implementation

        when(intent?.action) {
            RUN_BACKGROUND_ACTION -> runBackgroundMeasurement(intent)
            STOP_BACKGROUND_ACTION -> cancelBackgroundMeasurement(intent)
        }

        */
        return super.onStartCommand(intent, flags, startId)
    }

    private fun runBackground() {

     /*   if(coroutineScope.isActive) {
            consoledebug("Service coroutines are already active")
            return
        } else { */
            consoledebug("launching coroutine (this debug is  outside launch())")
            coroutineScope.launch {
                consoledebug("Launching coroutine...")
                backgroundMeasurementsManager().stateIn(coroutineScope).collect()
            }

    }

    override fun onDestroy() {
        consoledebug("Inside Service's onDestroy()")
        coroutineScope.cancel()
        super.onDestroy()
    }

    inner class A(){

        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        @Inject lateinit var settingsDataStore: DataStore<AppSettings>

        @Inject lateinit var msrsWorkManager: MsrsWorkManager


        

    }

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
