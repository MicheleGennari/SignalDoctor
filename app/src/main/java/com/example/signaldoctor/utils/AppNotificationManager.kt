package com.example.signaldoctor.utils

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat
) {

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

}