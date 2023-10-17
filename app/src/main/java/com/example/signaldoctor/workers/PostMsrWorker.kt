package com.example.signaldoctor.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.example.signaldoctor.repositories.MsrsRepo
import javax.inject.Inject


class PostMsrWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    @Inject lateinit var msrsRepo : MsrsRepo
    override suspend fun doWork(): Result {
        val dataMap = inputData.keyValueMap
        consoledebug("DEBUGG")
        if( //postMsr() returns true if it succeded, false otherwise
            msrsRepo.onlineDB.postMsr(
                msrType = inputData.getString(MsrWorkersKeysContract.MSR_TYPE_KEY) ?: return Result.failure(),
                msr = dataMap.getOrElse(MsrWorkersKeysContract.MSR_KEY) { return Result.failure() } as Double,
                z = dataMap.getOrElse(MsrWorkersKeysContract.Z_KEY) { return Result.failure() } as Int,
                x = dataMap.getOrElse(MsrWorkersKeysContract.X_KEY) { return Result.failure() } as Int,
                y = dataMap.getOrElse(MsrWorkersKeysContract.Y_KEY) { return Result.failure() } as Int,
                zMax = dataMap.getOrElse(MsrWorkersKeysContract.Z_MAX_KEY) { return Result.failure() } as Int,
                zMin = dataMap.getOrElse(MsrWorkersKeysContract.Z_MIN_KEY) { return Result.failure() } as Int
            )
        ) {
            return Result.success()
        } else return Result.failure()
    }
}
