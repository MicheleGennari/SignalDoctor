package com.example.signaldoctor.workers

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.*
import androidx.work.workDataOf
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure

class WifiMsrWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx,
    params
) {
    override suspend fun doWork(): Result {
        TODO("Not yet implemented")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Measure.wifi.ordinal,
            NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {

                setContentTitle(ctx.getString(R.string.wifi_measurement_notification_content_title))
                setSmallIcon(R.drawable.wifi_icon_bitamp)
                setProgress(0,0, true)
                setOngoing(true)
                if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH

            }.build()
        )
    }
}

@SuppressLint("MissingPermission")
fun wifiWork(ctx : Context, wifiManager: WifiManager) =
    wifiManager.scanResults.forEach {
        Result.success(
            workDataOf(MsrWorkersInputData.MSR_KEY to it.level)
        )
    }