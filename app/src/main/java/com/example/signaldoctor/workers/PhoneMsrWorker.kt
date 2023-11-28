package com.example.signaldoctor.workers

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.*
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.PhoneMeasurementFirebase
import com.example.signaldoctor.realtimeFirebase.WifiMeasurementFirebase
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltWorker
class PhoneMsrWorker @AssistedInject constructor(
    @Assisted private val ctx : Context,
    @Assisted private val params : WorkerParameters,
    private val gson : Gson
) :CoroutineWorker(ctx, params) {


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

        val workerNotification = NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle(ctx.getString(R.string.phone_measurement_notification_content_title))
            setSmallIcon(R.drawable.phone_icon_notification_bitmap)
            setProgress(0, 0, true)
            setOngoing(true)
            if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH
        }.build()

        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ForegroundInfo(
            Measure.phone.ordinal,
            workerNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        )
        else ForegroundInfo(
            Measure.phone.ordinal,
            workerNotification
        )
    }

    private suspend fun phoneWorkNewerBuilds(ctx : Context, telephonyManager: TelephonyManager) : Result {

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
            setProgress(workDataOf(NoiseMsrWorker.Progress to i.toFloat()/10f))
            delay(1000)
        }
        msr /= 5
        consoledebug("$msr")
        val outputData = Data.Builder()
            .putInt(MeasurementBase.MSR_KEY, msr)
            .putString(MeasurementBase.MSR_TYPE_KEY, gson.toJson(Measure.phone))
            .putString(MEASUREMENT_KEY,
                gson.toJson(PhoneMeasurement(
                    firebaseTable = PhoneMeasurementFirebase(
                        isLTE = false,
                        baseInfo = MeasurementBase(
                            tileIndex = inputData.getValue(MeasurementBase.TILE_INDEX_KEY),
                            value = msr
                        )
                    )
                ))
                )
            .build()
        Log.i("PHONE MSR WORKER", "output data is ${if(outputData == Data.EMPTY) "empty" else "filled"}")
        return Result.success(outputData)
    }
    
}

