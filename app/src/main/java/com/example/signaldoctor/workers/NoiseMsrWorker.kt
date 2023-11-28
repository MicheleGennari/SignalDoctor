package com.example.signaldoctor.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.SoundMeasurementFirebase
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.regex.Pattern
import kotlin.IllegalStateException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

const val RECORDING_TIME = 5000L
const val REC_FILE_NAME = "temp_msr_recording.3gp"


@HiltWorker
class NoiseMsrWorker @AssistedInject constructor(
    @Assisted ctx : Context,
    @Assisted params : WorkerParameters,
    private val gson : Gson
) : CoroutineWorker(ctx, params) {


    companion object {
        const val Progress = "Progress"
    }

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S)
            try {
                setForeground(getForegroundInfo())
            } catch (e: IllegalStateException) {
                Log.e(
                    "NOISE MEASUREMENT WORKER ERROR",
                    "Can't run as foreground services due to restrictions"
                )
                e.printStackTrace()
            }

        return noiseWork()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {

        val notificationWorker = NotificationCompat.Builder(applicationContext, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle(applicationContext.getString(R.string.noise_measurement_notification_content_title))
            setSmallIcon(R.drawable.ear_icon_notification_bitmap)
            setProgress(0, 0, true)
            setOngoing(true)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) priority =
                NotificationCompat.PRIORITY_HIGH

        }.build()

        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ForegroundInfo(
            Measure.sound.ordinal,
            notificationWorker,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        ) else ForegroundInfo(
            Measure.sound.ordinal,
            notificationWorker,
        )
    }


    suspend fun noiseWork(): Result {

        return if (
            ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            consoledebug("Record permission not granted")
            ListenableWorker.Result.failure()
        } else try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                val filePath: String = applicationContext.filesDir.absolutePath + "/" + REC_FILE_NAME

                val micRecorder =
                    if (Build.VERSION.SDK_INT >= 31) MediaRecorder(applicationContext) else MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        setAudioEncodingBitRate(256)
                        setAudioChannels(1)
                        setOutputFile(filePath)
                    }
                micRecorder.prepare()

                /*val micSensitivity = micRecorder.activeMicrophones.sumOf { micInfo->
                    micInfo.sensitivity.toDouble()
                }/2*/


                micRecorder.start()
                delay(RECORDING_TIME)
                //Thread.sleep(RECORDING_TIME)
                micRecorder.release()

                setProgress(workDataOf(Progress to 2/10f))

                val ffmpegSession = FFmpegKit.execute(
                    "-nostats -i ${filePath} -af ebur128=framelog=verbose -f null -"
                )
                setProgress(workDataOf(Progress to 5/10f))

                if (ReturnCode.isSuccess(ffmpegSession.returnCode)) {

                    val msrLog = ffmpegSession.allLogs[ffmpegSession.allLogs.size - 2].message
                    val matcher = Pattern.compile("I:\\s+(-?\\d+(.\\d+)?)").matcher(msrLog)
                    return if (matcher.find()) {
                        matcher.group(1)?.toDoubleOrNull()?.toInt()?.let { msr ->
                            consoledebug("noise msr = $msr")
                            setProgress(workDataOf(Progress to 8/10f))
                            Result.success(
                                workDataOf(MeasurementBase.MSR_KEY to msr,
                                    MeasurementBase.MSR_TYPE_KEY to gson.toJson(Measure.sound),
                                    MEASUREMENT_KEY to gson.toJson(SoundMeasurement(
                                        firebaseTable = SoundMeasurementFirebase(
                                            MeasurementBase(
                                                tileIndex = inputData.getValue(MeasurementBase.TILE_INDEX_KEY),
                                                value = msr
                                            )
                                        )
                                    ))
                                )
                            )
                        } ?: Result.failure()
                    } else Result.failure()

                } else Result.failure()

            } else Result.failure()


        } catch (e: IOException) {
            Log.e("MEDIA RECORDER ERROR", "prepare() throwed IO Exception")
            ListenableWorker.Result.failure()
        }


    }
}

fun ShortArray.castToFloatArray() : FloatArray{
    val floatArray = FloatArray(this.size)
    forEachIndexed{index, sh ->
        floatArray[index] = sh.toFloat()
    }
    return floatArray
}

inline fun <reified T> Data.getValue(key : String) : T =
      keyValueMap.getValue(key) as T //this will throw NoSuchElementException in case there's no mapping with such key


