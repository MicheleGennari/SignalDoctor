package com.example.signaldoctor.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.utils.Loggers
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

@AndroidEntryPoint
abstract class FlowService : Service() {


    // coroutine scopes where service behaviour runs
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var notificationManager : NotificationManager

    override fun onCreate() {

        super.onCreate()

        notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        Loggers.consoledebug("Background Measurement Service is in onCreate")


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BackgroundMeasurementsService.BACKGROUND_SERVICE_NOTIFICATION,
                completeForegroundNotification(
                    foregroundNotificationBuilder(),

                ),
                foregroundServiceType
            )
        } else
            startForeground(BackgroundMeasurementsService.BACKGROUND_SERVICE_NOTIFICATION, completeForegroundNotification(
                foregroundNotificationBuilder(), foregroundNotificationOptionalActions()
            ))

    }


    //Implement service's onStartCommand() behaviour here. Now you can perform suspendable tasks
    abstract suspend fun suspendableOnStartCommand(intent: Intent?, flags: Int, startId: Int)


    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        coroutineScope.launch{
            suspendableOnStartCommand(intent = intent, flags = flags, startId = startId)
        }

        return super.onStartCommand(intent, flags, startId)
    }



    override fun onDestroy() {
        coroutineScope.cancel("FlowService ended")
        super.onDestroy()
    }

    //SECTION: functions that define service's notification
    //

    private fun completeForegroundNotification(
        notification: NotificationCompat.Builder,
        optionalActions : List<NotificationCompat.Action> = emptyList()
    ) =
        notification.apply {
            for (action in optionalActions){
                addAction(action)
            }
        }.build()


    open fun foregroundNotificationOptionalActions() = emptyList<NotificationCompat.Action>()
    abstract fun foregroundNotificationBuilder() : NotificationCompat.Builder

    //
    //END SECTION: functions that define service's notification


}