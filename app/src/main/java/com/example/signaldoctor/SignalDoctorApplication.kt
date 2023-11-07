package com.example.signaldoctor

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.workers.MicCalibrationWorker
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.PostMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import dagger.hilt.DefineComponent
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SignalDoctorApplication() : Application(), Configuration.Provider {

   @Inject lateinit var postMsrWorkerFactory: PostMsrWorkerFactory
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(postMsrWorkerFactory)
            .build()
    }

class PostMsrWorkerFactory @Inject constructor(private val msrsRepo: MsrsRepo) : WorkerFactory(){
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when {
        workerClassName == PostMsrWorker::class.java.name -> PostMsrWorker(appContext, workerParameters, msrsRepo)
        else -> null
    }

}
