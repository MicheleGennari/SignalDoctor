package com.example.signaldoctor.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.Logger
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import androidx.work.workDataOf
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.TableColumn
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.screens.msrTypeWHen
import com.example.signaldoctor.utils.Loggers
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.Loggers.hashCode
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

const val POST_MSR_NOTIFICATION_ID = 2
const val MEASUREMENT_KEY = "Measurement"
@HiltWorker
class PostMsrWorker  @AssistedInject constructor(
     @Assisted  ctx: Context,
     @Assisted params: WorkerParameters,
     private val msrsRepo: MsrsRepo,
     private val gson : Gson,
     private val appSettings : Flow<AppSettings>
     ) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {

        try{
            setForeground(getForegroundInfo())
        }catch(e: IllegalStateException){
            Log.e("POST MEASUREMENT WORKER ERROR", "Can't run as foreground service due to restrictions")
            e.printStackTrace()
        }
        setProgress(workDataOf(NoiseMsrWorker.Progress to 9/10f))
        consoledebug("DEBUGG")
        //val msr =  inputData.getInt(MeasurementBase.MSR_KEY, 0)
        val msrType = objFromInputData(MeasurementBase.MSR_TYPE_KEY, Measure::class.java) ?: return Result.failure()
        return if(
            postMsrWork(msrType, dataJson = inputData.getString(MEASUREMENT_KEY) ?: return Result.failure() )
        )
               Result.success()
            else
                Result.failure()
    }

    private fun <T, D : Class<T>> objFromInputData(key : String, d : D) : T? {
        return inputData.getString(key)?.let { json ->
            gson.fromJson(json, d)
        }

    }
    private fun failureMsrWorkData(msr : Int) = Result.failure(workDataOf(MeasurementBase.MSR_KEY to msr))

    private suspend fun postMsrWork(msrType : Measure, dataJson : String) : Boolean {

        return when(msrType){
            Measure.phone -> msrsRepo.postPhoneMsr(gson.fromJson(dataJson, PhoneMeasurement::class.java), appSettings.first().networkMode)
            Measure.sound-> msrsRepo.postSoundMsr(gson.fromJson(dataJson, SoundMeasurement::class.java), appSettings.first().networkMode)
            Measure.wifi -> msrsRepo.postWifiMsr(gson.fromJson(dataJson, WiFIMeasurement::class.java), appSettings.first().networkMode)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {

        val workerNotification = NotificationCompat.Builder(applicationContext, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.upload_icon_notification_bitmap)
            setContentTitle(applicationContext.getString(R.string.post_measurement_content_title))
            setProgress(0,0, true)
            setOngoing(true)
        }.build()

        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ForegroundInfo(
            POST_MSR_NOTIFICATION_ID,
            workerNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
        ) else ForegroundInfo(
            POST_MSR_NOTIFICATION_ID,
            workerNotification,
        )
    }

}
