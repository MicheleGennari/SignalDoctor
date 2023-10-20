package com.example.signaldoctor.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.example.signaldoctor.repositories.MsrsRepo
import javax.inject.Inject


class PostMsrWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    @Inject lateinit var msrsRepo : MsrsRepo
    override suspend fun doWork(): Result {
        val dataMap = inputData.keyValueMap
        val msr = dataMap.getOrElse(MsrWorkersInputData.MSR_KEY) { return Result.failure() } as Double

        consoledebug("DEBUGG")
        if( //postMsr() returns true if it succeded, false otherwise
            msrsRepo.onlineDB.postMsr(
                msrType = inputData.getString(MsrWorkersInputData.MSR_TYPE_KEY) ?: return Result.failure(),
                msr = msr,
                z = dataMap.getOrElse(MsrWorkersInputData.Z_KEY) { return Result.failure() } as Int,
                x = dataMap.getOrElse(MsrWorkersInputData.X_KEY) { return Result.failure() } as Int,
                y = dataMap.getOrElse(MsrWorkersInputData.Y_KEY) { return Result.failure() } as Int,
                zMax = dataMap.getOrElse(MsrWorkersInputData.Z_MAX_KEY) { return Result.failure() } as Int,
                zMin = dataMap.getOrElse(MsrWorkersInputData.Z_MIN_KEY) { return Result.failure() } as Int
            )
        ) {
            return Result.success(workDataOf(MsrWorkersInputData.MSR_KEY to msr))
        } else return Result.failure()
    }
}
