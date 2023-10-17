package com.example.signaldoctor.workers

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.onlineDatabase.consoledebug
import java.io.IOException
import javax.inject.Inject

class MicCalibrationWorker(private val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun doWork(): Result {

        //val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        return try{
            Result.success(
                workDataOf(
                   // "sensitivity" to am.microphones[0].sensitivity
                )
            )

        }catch (e : IOException){
            e.printStackTrace()
            Result.failure()
        }
    }
}

fun calibrateMic(am : AudioManager) {

}