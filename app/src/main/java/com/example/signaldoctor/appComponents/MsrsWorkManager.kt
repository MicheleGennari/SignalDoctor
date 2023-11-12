package com.example.signaldoctor.appComponents

import androidx.lifecycle.LiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.PostMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import javax.inject.Inject

const val MEASURE_WORK_TAG = "MEASURE_WORK"
const val MIC_CALIBRATION_WORK_TAG ="MIC_CALIBRATION_WORK"

class MsrsWorkManager @Inject constructor(private val workManager: WorkManager) {



    fun runMeasurement(msrType: Measure, inputData: MsrWorkersInputData): LiveData<List<WorkInfo>> {

        val getMsrWorkRequest = when (msrType) {
            Measure.sound -> OneTimeWorkRequestBuilder<NoiseMsrWorker>()
            Measure.wifi -> OneTimeWorkRequestBuilder<WifiMsrWorker>()
            Measure.phone -> OneTimeWorkRequestBuilder<PhoneMsrWorker>()
        }.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(MEASURE_WORK_TAG)
            .build()

        val postMsrWorkRequest = OneTimeWorkRequestBuilder<PostMsrWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData.asWorkData())
            .build()

        workManager.beginUniqueWork(
            msrType.name,
            ExistingWorkPolicy.KEEP,
            getMsrWorkRequest
        ).then(postMsrWorkRequest)
            .enqueue()

        return workManager.getWorkInfosForUniqueWorkLiveData(inputData.msrType)
    }

    fun cancelMeasurement(msrType: Measure){
        workManager.cancelUniqueWork(msrType.name)
    }

    fun cancelAllMeasurements(){
        workManager.cancelAllWorkByTag(MEASURE_WORK_TAG)
    }
}




