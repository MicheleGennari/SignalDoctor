package com.example.signaldoctor.workers

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.isLocationPermissionGranted
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.PhoneMeasurementFirebase
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@HiltWorker
class PhoneMsrWorker @AssistedInject constructor(
    @Assisted private val ctx : Context,
    @Assisted private val params : WorkerParameters,
    private val appSettings: DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    private val flowLocationProvider : FlowLocationProvider,
    private val gson: Gson
) :CoroutineWorker(ctx, params) {


    override suspend fun doWork() : Result{

        return if(!ctx.isLocationPermissionGranted()) Result.failure() else  {

            try{
                setForeground(getForegroundInfo())
            }catch(e: IllegalStateException){
                Log.e("PHONE MEASUREMENT WORKER ERROR", "Can't run as foreground services due to restrictions", e)
            }

            phoneWorkNewerBuilds(
                ctx,
                ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            )
        }

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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE + ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
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
        return if(
            msrsRepo.postPhoneMsr(
                PhoneMeasurement(
                    firebaseTable = PhoneMeasurementFirebase(
                        baseInfo = MeasurementBase(
                            tileIndex = flowLocationProvider.tileIndexFromLocation(Priority.PRIORITY_HIGH_ACCURACY) ?: return Result.retry(),
                            value = msr
                        )
                    )
                ),
                appSettings.data.first().networkMode
            )
        ) Result.success(
            gson.workDataOfMsrWorkerResult(msr, Measure.phone)
        )
        else Result.failure(gson.workDataOfMsrWorkerResult(msr, Measure.phone))

    }
    
}

fun Gson.workDataOfMsrWorkerResult(msr : Int, msrType : Measure) : Data{
    return workDataOf(
        MeasurementBase.MSR_KEY to msr,
        MeasurementBase.MSR_TYPE_KEY to toJson(msrType)
    )
}