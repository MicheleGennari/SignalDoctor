package com.example.signaldoctor.workers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.*
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import javax.inject.Inject

class PhoneMsrWorker(private val ctx : Context, val params : WorkerParameters) :CoroutineWorker(ctx, params) {

    @Inject lateinit var telephonyManager : TelephonyManager

    override suspend fun doWork() : Result{
        try{
            setForeground(getForegroundInfo())
        }catch(e: IllegalStateException){
            Log.e("PHONE MEASUREMENT WORKER ERROR", "Can't run as foreground services due to restrictions")
            e.printStackTrace()
        }

        return phoneWork(ctx, telephonyManager)
        }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Measure.phone.ordinal,
            NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
                setContentTitle(ctx.getString(R.string.phone_measurement_notification_content_title))
                setSmallIcon(R.drawable.ear_icon_bitmap)
                setProgress(0, 0, true)
                setOngoing(true)
                if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH
            }.build()
        )
    }
}

fun phoneWork(ctx : Context, telephonyManager: TelephonyManager)  : Result {

    if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        return Result.failure()

    //return the highest signal strength dbm value among all cellSignalStrengths
     return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
         telephonyManager.signalStrength?.run {
             Result.success(
                 workDataOf(MsrWorkersInputData.MSR_KEY to cellSignalStrengths.maxOf { cellSignalStrength ->
                         cellSignalStrength.dbm
                     }
                 )
             )
         } ?: Result.failure()
     } else Result.failure()
}
