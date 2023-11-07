package com.example.signaldoctor.workers

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.Logger
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.utils.Loggers
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.utils.Loggers.hashCode
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

const val POST_MSR_NOTIFICATION_ID = 2

@HiltWorker
class PostMsrWorker  @AssistedInject constructor(
     @Assisted private val ctx: Context,
     @Assisted params: WorkerParameters,
     private val msrsRepo: MsrsRepo
     ) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {

        try{
            setForeground(getForegroundInfo())
        }catch(e: IllegalStateException){
            Log.e("POST MEASUREMENT WORKER ERROR", "Can't run as foreground service due to restrictions")
            e.printStackTrace()
        }

        val dataMap = inputData.keyValueMap
        val msr = dataMap.getOrElse(MsrWorkersInputData.MSR_KEY) { return Result.failure() } as Int

        consoledebug("DEBUGG")
        if( //postMsr() returns true if it succeded, false otherwise
            msrsRepo.postMsr(msr = msr,
                mapTileData = MsrWorkersInputData(
                msrType = dataMap.getOrElse(MsrWorkersInputData.MSR_TYPE_KEY) { return Result.failure() } as String,
                lat = dataMap.getOrElse(MsrWorkersInputData.LAT_KEY) { return Result.failure() } as Double,
                long = dataMap.getOrElse(MsrWorkersInputData.LONG_KEY) { return Result.failure() } as Double,
            )
        )) {
            return Result.success(workDataOf(MsrWorkersInputData.MSR_KEY to msr))
        } else return Result.failure()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            POST_MSR_NOTIFICATION_ID,
            NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
                setSmallIcon(R.drawable.upload_icon_notification_bitmap)
                setContentTitle(ctx.getString(R.string.post_measurement_content_title))
                setProgress(0,0, true)
                setOngoing(true)
                }.build()
        )
    }
}
