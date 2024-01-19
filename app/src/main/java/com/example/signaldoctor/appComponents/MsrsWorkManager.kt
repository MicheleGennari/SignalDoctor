package com.example.signaldoctor.appComponents

import androidx.lifecycle.asFlow
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.screens.msrTypeWhen
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

const val MEASURE_WORK_TAG = "MEASURE_WORK"
const val MEASURE_ONETIME_WORK_TAG = "MEASURE_ONETIME_WORK"
const val MEASURE_BACKGROUND_WORK_TAG = "MEASURE_BACKGROUND_WORK"
const val MIC_CALIBRATION_WORK_TAG ="MIC_CALIBRATION_WORK"

@Singleton
class MsrsWorkManager @Inject constructor(private val workManager: WorkManager) {



    fun runMeasurement(msrType: Measure): Flow<WorkInfo> {

        val getMsrWorkRequest = when (msrType) {
            Measure.sound -> OneTimeWorkRequestBuilder<NoiseMsrWorker>()
            Measure.wifi -> OneTimeWorkRequestBuilder<WifiMsrWorker>()
            Measure.phone -> OneTimeWorkRequestBuilder<PhoneMsrWorker>()
        }.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(MEASURE_WORK_TAG)
            .addTag(MEASURE_ONETIME_WORK_TAG)
            /*
            .setInputData(workDataOf(
                MeasurementBase.TILE_INDEX_KEY to MapView.getTileSystem().tileIndexFromLocation(userLocation)
            ))*/
            .build()


        workManager.beginUniqueWork(
            msrType.name,
            ExistingWorkPolicy.KEEP,
            getMsrWorkRequest
        ).enqueue()

        return workManager.getWorkInfosForUniqueWorkLiveData(msrType.name).asFlow().map { it.first() }
    }

    fun runBackgroundMeasurement(msrType: Measure, interval: Duration) : Flow<WorkInfo> {

        val workName = msrType.name+"_background"

        val periodicMsrWorkRequest = msrTypeWhen( msrType,
            phone = PeriodicWorkRequestBuilder<PhoneMsrWorker>(interval),
            sound = PeriodicWorkRequestBuilder<NoiseMsrWorker>(interval),
            wifi = PeriodicWorkRequestBuilder<WifiMsrWorker>(interval)
        ).addTag(MEASURE_WORK_TAG)
            .addTag(MEASURE_BACKGROUND_WORK_TAG)
            /*
            .setInputData(workDataOf(
                MeasurementBase.TILE_INDEX_KEY to MapView.getTileSystem().tileIndexFromLocation(userLocation)
            ))*/
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicMsrWorkRequest
        )

        return workManager.getWorkInfosForUniqueWorkLiveData(workName).asFlow().map { it.first() }

    }



    fun cancelMeasurement(msrType: Measure){
        workManager.cancelUniqueWork(msrType.name)
    }

    fun cancelBackgroundMeasurement(msrType: Measure){
        workManager.cancelUniqueWork(msrType.name+"_background")
    }

    fun cancelAllMeasurements(){
        workManager.cancelAllWorkByTag(MEASURE_WORK_TAG)
    }
}




