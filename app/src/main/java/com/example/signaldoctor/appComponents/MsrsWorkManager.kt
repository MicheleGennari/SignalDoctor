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
import com.example.signaldoctor.utils.whenMsrType
import com.example.signaldoctor.workers.NewNoiseMsrWorker
import com.example.signaldoctor.workers.NewPhoneMsrWorker
import com.example.signaldoctor.workers.NewWifiMsrWorker
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

const val MEASURE_WORK_TAG = "MEASURE_WORK"
const val MEASURE_ONE_TIME_WORK_TAG = "MEASURE_ONETIME_WORK"
const val MEASURE_BACKGROUND_WORK_TAG = "MEASURE_BACKGROUND_WORK"
const val MIC_CALIBRATION_WORK_TAG ="MIC_CALIBRATION_WORK"
const val MEASURE_BACKGROUND_NAME_SUFFIX = "_background"

@Singleton
class MsrsWorkManager @Inject constructor(private val workManager: WorkManager) {



    fun runMeasurement(msrType: Measure): Flow<WorkInfo> {

        val getMsrWorkRequest = when (msrType) {
            Measure.sound -> OneTimeWorkRequestBuilder<NewNoiseMsrWorker>()
            Measure.wifi -> OneTimeWorkRequestBuilder<NewWifiMsrWorker>()
            Measure.phone -> OneTimeWorkRequestBuilder<NewPhoneMsrWorker>()
        }.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(MEASURE_WORK_TAG)
            .addTag(MEASURE_ONE_TIME_WORK_TAG)
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

        return workManager.getWorkInfosForUniqueWorkLiveData(msrType.name).asFlow().map { it.first() }.flowOn(Dispatchers.Default)
    }

    fun runBackgroundMeasurement(msrType: Measure, interval: Duration) : Flow<WorkInfo> {

        val periodicWorkName = msrType.name+ MEASURE_BACKGROUND_NAME_SUFFIX

        val periodicMsrWorkRequest = whenMsrType( msrType,
            phone = PeriodicWorkRequestBuilder<NewPhoneMsrWorker>(interval),
            sound = PeriodicWorkRequestBuilder<NewNoiseMsrWorker>(interval),
            wifi = PeriodicWorkRequestBuilder<NewWifiMsrWorker>(interval)
        ).addTag(MEASURE_WORK_TAG)
            .addTag(MEASURE_BACKGROUND_WORK_TAG)
            /*
            .setInputData(workDataOf(
                MeasurementBase.TILE_INDEX_KEY to MapView.getTileSystem().tileIndexFromLocation(userLocation)
            ))*/
            .build()

        workManager.enqueueUniquePeriodicWork(
            periodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicMsrWorkRequest
        )

        return workManager.getWorkInfosForUniqueWorkLiveData(periodicWorkName).asFlow().map { it.first() }

    }



    fun cancelOneTimeMeasurement(msrType: Measure){
        workManager.cancelUniqueWork(msrType.name)
    }

    fun cancelAllOneTimeMeasurements(){
        workManager.cancelAllWorkByTag(MEASURE_ONE_TIME_WORK_TAG)
    }

    fun cancelBackgroundMeasurement(msrType: Measure){
        workManager.cancelUniqueWork(msrType.name+ MEASURE_BACKGROUND_NAME_SUFFIX)
    }

    fun cancelAllBackgroundMeasurements(){
        workManager.cancelAllWorkByTag(MEASURE_BACKGROUND_WORK_TAG)
    }

    fun cancelAllMeasurements(){
        workManager.cancelAllWorkByTag(MEASURE_WORK_TAG)
    }

}




