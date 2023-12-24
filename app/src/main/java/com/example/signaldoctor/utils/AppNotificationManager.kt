package com.example.signaldoctor.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.broadcastReceivers.RUN_MEASUREMENT_ACTION
import com.example.signaldoctor.broadcastReceivers.RunMeasurementReceiver
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.screens.msrTypeWhen
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val gson : Gson,
) {

    init {

        //Create Notification Channel for measurement notifications
        createMeasurementsChannel(
            channelNameResourceId = R.string.measurements_channel_name,
            channelDescriptionResourceId = R.string.measurement_channel_description
        )
    }

    fun createMeasurementsChannel(
        channelNameResourceId : Int,
        channelDescriptionResourceId : Int
    ){
        notificationManager.createNotificationChannel(

            NotificationChannelCompat.Builder(
                MEASUREMENT_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).apply {

                setName(context.getString(channelNameResourceId))
                setDescription(context.getString(channelDescriptionResourceId))

            }.build()
        )
    }

    fun sendRunMeasurementNotification(msrType : Measure){
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("RUN MEASUREMENT NOTIFICATION", "Can't send notification because permissions are not granted")
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            msrType.ordinal,
            Intent()
                .setClass(context, RunMeasurementReceiver::class.java)
                .setAction(RUN_MEASUREMENT_ACTION)
                .putExtra(MeasurementBase.MSR_TYPE_KEY, gson.toJson(msrType)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationManager.notify(
            msrType.ordinal,
            NotificationCompat.Builder(context, MEASUREMENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.signal_doctor_icon)
                .setContentTitle(
                    context.getString(
                        R.string.run_measurement_notification_title,
                        msrType.name
                ))
                .setContentText(context.getString(R.string.run_measurement_notification_description))
                .addAction(
                    R.drawable.signal_doctor_icon,
                    context.getString(R.string.run_measurement_notification_action),
                    pendingIntent
                )
                .build()
        )
    }

    fun cancelRunMeasurementNotification(msrType: Measure){
        notificationManager.cancel(msrType.ordinal)
    }

}