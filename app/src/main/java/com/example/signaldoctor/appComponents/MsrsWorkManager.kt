package com.example.signaldoctor.appComponents

import android.media.AudioManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.workers.MicCalibrationWorker
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.PostMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

const val MEASURE_WORK_TAG = "MEASURE_WORK"
const val MIC_CALIBRATION_WORK_TAG ="MIC_CALIBRATION_WORK"

class MsrsWorkManager @Inject constructor(private val workManager: WorkManager) {



    fun runMeasurement(inputData: MsrWorkersInputData): LiveData<List<WorkInfo>> {

        val getMsrWorkRequest = when (inputData.msrType) {
            Measure.sound -> OneTimeWorkRequestBuilder<NoiseMsrWorker>()
            Measure.wifi -> OneTimeWorkRequestBuilder<PhoneMsrWorker>()
            Measure.phone -> OneTimeWorkRequestBuilder<WifiMsrWorker>()
        }.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(MEASURE_WORK_TAG)
            .build()

        val postMsrWorkRequest = OneTimeWorkRequestBuilder<PostMsrWorker>()
            .setInputData(inputData.workData)
            .build()


        workManager.beginUniqueWork(
            inputData.msrType.name,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            getMsrWorkRequest
        )//.then(postMsrWorkRequest)
            .enqueue()

        return workManager.getWorkInfosForUniqueWorkLiveData(inputData.msrType.name)
    }

    fun cancelMeasurement(msrType: Measure){
        workManager.cancelUniqueWork(msrType.name)
    }

    fun cancelAllMeasurements(){
        workManager.cancelAllWorkByTag(MEASURE_WORK_TAG)
    }
}




