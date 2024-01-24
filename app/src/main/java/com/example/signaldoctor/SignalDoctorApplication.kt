package com.example.signaldoctor

import android.app.Application
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.datastore.core.DataStore
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.workers.MicCalibrationWorker
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.PostMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import com.google.gson.Gson
import dagger.hilt.DefineComponent
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject


@HiltAndroidApp
class SignalDoctorApplication : Application(), Configuration.Provider {

   @Inject lateinit var msrWorkersFactory: MsrWorkersFactory

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(msrWorkersFactory)
            .build()
    }

class MsrWorkersFactory @Inject constructor(
    private val msrsRepo: MsrsRepo,
    private val settingsDataStore : DataStore<AppSettings>,
    private val flowLocationProvider: FlowLocationProvider,
    private val gson : Gson
) : WorkerFactory(){
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        //PostMsrWorker::class.java.name -> PostMsrWorker(appContext, workerParameters, msrsRepo, gson, settingsDataStore.data)
        NoiseMsrWorker::class.java.name -> NoiseMsrWorker(appContext, workerParameters, settingsDataStore, msrsRepo, flowLocationProvider, gson)
        PhoneMsrWorker::class.java.name -> PhoneMsrWorker(appContext, workerParameters, settingsDataStore, msrsRepo, flowLocationProvider, gson)
        WifiMsrWorker::class.java.name -> WifiMsrWorker(appContext, workerParameters, settingsDataStore, msrsRepo, flowLocationProvider, gson)
        else -> null
    }

}
