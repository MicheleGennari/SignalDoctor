package com.example.signaldoctor.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.onlineDatabase.consoledebug
import java.io.IOException
import java.util.regex.Pattern
import kotlin.IllegalStateException

const val RECORDING_TIME = 5000L
const val REC_FILE_NAME = "temp_msr_recording.3gp"


class NoiseMsrWorker(private val ctx : Context, params : WorkerParameters) : CoroutineWorker(ctx, params) {


    override suspend fun doWork(): Result {

        if(Build.VERSION.SDK_INT> Build.VERSION_CODES.S)
            try{
                setForeground(getForegroundInfo())
            }catch(e: IllegalStateException){
                Log.e("NOISE MEASUREMENT WORKER ERROR", "Can't run as foreground services due to restrictions")
                e.printStackTrace()
            }

        return noiseWork(ctx)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Measure.sound.ordinal,
            NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {
                setContentTitle(ctx.getString(R.string.noise_measurement_notification_content_title))
                setSmallIcon(R.drawable.ear_icon_bitmap)
                setProgress(0,0, true)
                setOngoing(true)
                if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH

            }.build()
        )
    }
}


fun noiseWork(ctx : Context) : ListenableWorker.Result {


        return if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            consoledebug("Record permission not granted")
            ListenableWorker.Result.failure()
        } else try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                val filePath : String = ctx.filesDir.absolutePath+"/"+REC_FILE_NAME

                val micRecorder = if(Build.VERSION.SDK_INT>=31) MediaRecorder(ctx) else MediaRecorder().apply {
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
                Thread.sleep(RECORDING_TIME)
                micRecorder.release()

                val ffmpegSession = FFmpegKit.execute(
                    "-nostats -i ${filePath} -af ebur128=framelog=verbose -f null -"
                )

                if(ReturnCode.isSuccess(ffmpegSession.returnCode)) {

                    val msrLog = ffmpegSession.allLogs[ffmpegSession.allLogs.size-2].message
                    val matcher = Pattern.compile("I:\\s+(-?\\d+(.\\d+)?)").matcher(msrLog)
                    return if(matcher.find()) {
                        matcher.group(1)?.toDoubleOrNull()?.let{ msr->
                            consoledebug("noise msr = $msr")
                            ListenableWorker.Result.success(
                                workDataOf(MsrWorkersInputData.MSR_KEY to msr)
                            )
                        } ?: ListenableWorker.Result.failure()
                    }
                    else ListenableWorker.Result.failure()

                } else ListenableWorker.Result.failure()

            } else ListenableWorker.Result.failure()



        }catch(e : IOException){
            Log.e("MEDIA RECORDER ERROR", "prepare() throwed IO Exception")
            ListenableWorker.Result.failure()
        }


}

fun ShortArray.castToFloatArray() : FloatArray{
    val floatArray = FloatArray(this.size)
    forEachIndexed{index, sh ->
        floatArray[index] = sh.toFloat()
    }
    return floatArray
}

