package com.example.signaldoctor.workers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.*
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.utils.Loggers.consoledebug
import kotlinx.coroutines.delay
import javax.inject.Inject

class PhoneMsrWorker(private val ctx : Context, val params : WorkerParameters) :CoroutineWorker(ctx, params) {


    override suspend fun doWork() : Result{
        try{
            setForeground(getForegroundInfo())
        }catch(e: IllegalStateException){
            Log.e("PHONE MEASUREMENT WORKER ERROR", "Can't run as foreground services due to restrictions")
            e.printStackTrace()
        }

        return phoneWorkNewerBuilds(ctx, ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)

        }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Measure.phone.ordinal,
            NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
                setContentTitle(ctx.getString(R.string.phone_measurement_notification_content_title))
                setSmallIcon(R.drawable.phone_icon_notification_bitmap)
                setProgress(0, 0, true)
                setOngoing(true)
                if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH
            }.build()
        )
    }
}

suspend fun phoneWorkNewerBuilds(ctx : Context, telephonyManager: TelephonyManager) : Result {

    if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        return Result.failure()

    var msr = 0
    for (i in 1..5){
        //return the highest signal strength dbm value among all cellSignalStrengths
        msr += telephonyManager.signalStrength?.run {
                cellSignalStrengths.maxOf {
                    cellSignalStrength ->
                    cellSignalStrength.dbm
                }
        } ?: return Result.failure()
        delay(1000)
    }
    msr /= 5
    consoledebug("$msr")
    return Result.success(workDataOf(MsrWorkersInputData.MSR_KEY to msr))
}