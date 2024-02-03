package com.example.signaldoctor.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.repositories.MsrsRepo
import androidx.work.workDataOf
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.MEASURE_BACKGROUND_WORK_TAG
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.RoomMeasurementEntity
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.getServiceContentTitle
import com.example.signaldoctor.utils.getMsrTypeSmallIcon
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.osmdroid.views.MapView
import kotlin.jvm.Throws

abstract class BaseMsrWorker<M : RoomMeasurementEntity> (
    private val ctx : Context,
    params : WorkerParameters,
    private val appSettings: DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    private val appNotificationManager: AppNotificationManager,
    private val flowLocationProvider : FlowLocationProvider,
) : CoroutineWorker(ctx, params) {

    private var progress = 0/10f

    abstract val displayName : String

    abstract val msrType : Measure

    abstract val foregroundServiceTypes : Int


    companion object {
        const val PROGRESS_KEY = "Progress"
        const val MSR_KEY = "msr"
        const val ERROR_KEY = "error"
        const val MSR_ERROR_KEY = "msrError"
        const val LOCAL_UPLOAD_ERROR = "localUploadError"
        const val ONLINE_UPLOAD_ERROR = "onlineUploadError"
        const val LOCATION_NOT_AVAILABLE_KEY = "locationNotAvailable"
        const val INTERVAL_BETWEEN_SINGLE_MEASUREMENTS = 1000L
    }

    protected suspend fun updateProgress() {
        progress += progress + 1/10f
        setProgress(workDataOf(PROGRESS_KEY to progress))
    }

    private suspend fun setProgressToFinishedState(){
        setProgress(workDataOf(PROGRESS_KEY to 1f))
    }

    override suspend fun doWork(): Result {

         return if(!arePermissionsGranted())
             preMsrError("app is not granted the required permissions to run the measurement")
         else {

             try {
                setForeground(getForegroundInfo())
                try {

                    prepare()
                    updateProgress()

                    val result = measure()
                    setProgress(workDataOf(PROGRESS_KEY to 8/10f))
                    finish()
                    sendMsr(msr = result)
                } catch (e: MeasurementException) {
                    finish()
                    Log.e(displayName, "exception thrown during measurement execution", e)
                    preMsrError(e.message ?: "error during measurement")
                }
             } catch (e: IllegalStateException) {
                 finish()
                 if(e is CancellationException) throw e

                 Log.e(
                    "$displayName WORKER ERROR",
                    null,
                    e
                 )
                 preMsrError("error during measurement")
             }

        }
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {

        val workerNotification = NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle(ctx.resources.getString(getServiceContentTitle(msrType)))
            setSmallIcon(getMsrTypeSmallIcon(msrType))
            setProgress(0, 0, true)
            //setOngoing(true)
            if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH
        }.build()

        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ForegroundInfo(
            msrType.ordinal,
            workerNotification,
            foregroundServiceTypes
        )
        else ForegroundInfo(
            msrType.ordinal,
            workerNotification
        )
    }

    open fun foregroundNotification() = NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
        setContentTitle(ctx.resources.getString(R.string.phone_measurement_notification_content_title))
        setSmallIcon(R.drawable.phone_icon_notification_bitmap)
        setProgress(0, 0, true)
        setOngoing(true)
        if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH
    }.build()

    abstract fun arePermissionsGranted() : Boolean





    open suspend fun measure() : Int{

            var avg: Int = getSingleMeasurement()


            repeat(4) {
                avg = (avg + getSingleMeasurement()) / 2
                updateProgress()
                delay(INTERVAL_BETWEEN_SINGLE_MEASUREMENTS)
            }

            consoleDebug("$displayName result is $avg")

            return avg.sendAsResultNotification()


    }

    abstract suspend fun prepare()
    @Throws(MeasurementException::class)
    abstract suspend fun getSingleMeasurement() : Int
    open suspend fun finish(){}
    private suspend fun sendMsr(msr : Int) : Result {

        val tileIndex = MapView.getTileSystem().tileIndexFromLocation(
            flowLocationProvider.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY) ?:
        return locationError(result = msr)
        )

        updateProgress()

        val msrEntity = buildMeasurementEntity(MeasurementBase(tileIndex = tileIndex, value = msr))

        val isLocalSaveDone = localUpload(msrEntity)
        val isOnlineSaveDone = if(appSettings.data.first().networkMode == NetworkMode.ONLINE) onlineUpload(msrEntity) else true

        return if(isLocalSaveDone && isOnlineSaveDone)
            success(result = msr)
        else
            postMsrError(result = msr, isLocalUploadDone = isLocalSaveDone, isOnlineUploadDone = isOnlineSaveDone)

    }

    @Throws(MeasurementException::class)
    abstract fun buildMeasurementEntity(baseInfos: MeasurementBase) : M

    abstract suspend fun localUpload(m : M) : Boolean

    abstract suspend fun onlineUpload(m : M) : Boolean

    private fun success(result : Int) = Result.success(workDataOf(MSR_KEY to result))

    private fun preMsrError(errorMessage: String) = run {
        errorMessage.sendAsErrorNotification()
        Result.failure(
            workDataOf(
                ERROR_KEY to errorMessage
            )
        )
    }

    private fun postMsrError(result : Int, isLocalUploadDone : Boolean, isOnlineUploadDone : Boolean) = run {

            val failureData = Data.Builder().apply {
                putInt(MSR_KEY, result)
                if(!isLocalUploadDone) putString(ERROR_KEY, "Error saving measurement in local storage".sendAsErrorNotification())
                if(!isOnlineUploadDone) putString(ERROR_KEY, "Error saving measurement on the server".sendAsErrorNotification())
                if(!isOnlineUploadDone && !isLocalUploadDone) putString(ERROR_KEY, "Error saving measurements".sendAsErrorNotification())

            }.build()



            Result.failure(failureData)
    }

    private fun locationError(result: Int) = Result.failure(
        workDataOf(MSR_KEY to result, ERROR_KEY to "location not available".sendAsErrorNotification())
    )

    open class MeasurementException(message : String) : Exception(message)

    protected fun Int.sendAsResultNotification() = also {
        if(tags.contains(MEASURE_BACKGROUND_WORK_TAG))
            appNotificationManager.sendMeasurementResultNotification(msrType, it)
    }

    protected fun String.sendAsErrorNotification() = also {
        if(tags.contains(MEASURE_BACKGROUND_WORK_TAG))
            appNotificationManager.sendMeasurementErrorNotification(msrType, it)
    }
}