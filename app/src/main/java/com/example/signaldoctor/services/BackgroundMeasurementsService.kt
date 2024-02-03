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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.hasKeyWithValueOfType
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.utils.whenMsrType
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.getMeasureSettings
import com.example.signaldoctor.utils.noiseSettings
import com.example.signaldoctor.utils.phoneSettings
import com.example.signaldoctor.utils.updateDSL
import com.example.signaldoctor.utils.wifiSettings
import com.example.signaldoctor.workers.BaseMsrWorker
import com.example.signaldoctor.workers.getValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject


const val DURATION_KEY = "duration"

@AndroidEntryPoint
class BackgroundMeasurementsService: LifecycleService() {

    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var msrsWorkManager: MsrsWorkManager
    @Inject lateinit var settingsDataStore : DataStore<AppSettings>
    @Inject lateinit var locationProvider: FlowLocationProvider

    companion object {
        const val START_BACKGROUND_ACTION = "RUN_BACKGROUND_ACTION"
        const val STOP_BACKGROUND_ACTION = "STOP_BACKGROUND_ACTION"
        const val START_PHONE_ACTION = "START_PHONE_ACTION"
        const val START_NOISE_ACTION = "START_NOISE_ACTION"
        const val START_WIFI_ACTION = "START_WIFI_ACTION"
        const val BACKGROUND_SERVICE_NOTIFICATION = 5001

    }

    private lateinit var appSettings : SharedFlow<AppSettings>

    private fun initializeAppSettings() = settingsDataStore.data.shareIn(lifecycleScope, SharingStarted.WhileSubscribed())


    private val phoneJob = Job().apply { complete() }
    private val noiseJob = Job().apply { complete() }
    private val wifiJob = Job().apply{ complete() }


    override fun onCreate(){
        super.onCreate()

        consoleDebug("Background Measurement Service is in onCreate")

        promoteAsForegroundService()


        appSettings = initializeAppSettings()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                appSettings.collect{
                    if(!(it.phoneSettings.isBackgroundMsrOn || it.noiseSettings.isBackgroundMsrOn || it.wifiSettings.isBackgroundMsrOn))
                        stopSelf()
                }
            }
        }


        lifecycleScope.launch{
            lifecycle.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                locationProvider.requestLocationUpdates(FlowLocationProvider.defaultLocationUpdateSettings).collect{ userLocation ->
                    if(userLocation == null) {
                        settingsDataStore.updateDSL {
                            phoneSettings { isBackgroundMsrOn = false }
                            noiseSettings { isBackgroundMsrOn = false }
                            wifiSettings { isBackgroundMsrOn = false }
                        }
                        stopSelf()
                    }
                }
            }
        }

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        when (intent?.action) {

            START_PHONE_ACTION -> if(!phoneJob.isActive) backgroundMsrTypeManager(Measure.phone)


            START_NOISE_ACTION -> if(!noiseJob.isActive)  backgroundMsrTypeManager(Measure.sound)

            START_WIFI_ACTION -> if(!wifiJob.isActive)  backgroundMsrTypeManager(Measure.wifi)

            STOP_BACKGROUND_ACTION -> stopSelf(startId)
        }

        return super.onStartCommand(intent, flags, startId)
    }


    private fun promoteAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BACKGROUND_SERVICE_NOTIFICATION,
                foregroundNotification(),
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0 + ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else startForeground(BACKGROUND_SERVICE_NOTIFICATION, foregroundNotification())
    }

    private fun foregroundNotification(optionalActions : List<NotificationCompat.Action> = emptyList() ) =
        NotificationCompat.Builder(this, MEASUREMENT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.termometer_icon)
            .setContentTitle("Background Measurements On")
            .setContentText("touch here to manage background measurements")
            .setShowWhen(false)
            .setOngoing(true)
            .setContentIntent(launchSettingsScreenPendingIntent())
            .apply {
                for (action in optionalActions){
                    addAction(action)
                }
            }.build()

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


    private fun updateForegroundNotification(appSettings: AppSettings){

        appNotificationManager.notify(
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


    private fun backgroundMsrTypeManager(msrType : Measure) = combine(
        appSettings.map { appSettings ->
            appSettings.getMeasureSettings(msrType).isBackgroundMsrOn
        }.distinctUntilChanged(),
        settingsDataStore.data.map { appSettings ->
            appSettings.getMeasureSettings(msrType).periodicity
        }.distinctUntilChanged()
    ){ isBackgroundMsrOn, periodicity ->

        Log.d("service measurement manager", "$msrType background measurement flow is running")

        if(!isBackgroundMsrOn) {
            msrsWorkManager.cancelBackgroundMeasurement(msrType)
            currentCoroutineContext().cancel()
        }
        else
            msrsWorkManager.runBackgroundMeasurement(
                msrType = msrType,
                interval = Duration.ofMinutes(periodicity.toLong())
            ).launchIn(lifecycleScope)
    }.onCompletion {
        consoleDebug("service $msrType flow completed")
    }.launchIn(lifecycleScope)
    //END OF backgroundMeasurementsManager()

}



suspend fun manageBackgorundMeasurements(msrSettings: Flow<MeasurementSettings>){

    val isBackgroundOn = combine(msrSettings.map { it.isBackgroundMsrOn }, ){

    }

}





