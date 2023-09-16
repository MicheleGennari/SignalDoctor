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
                msrType = inputData.getString(WorkersKeysContract.MSR_TYPE_KEY) ?: return Result.failure(),
                msr = dataMap.getOrElse(WorkersKeysContract.MSR_KEY) { return Result.failure() } as Double,
                z = dataMap.getOrElse(WorkersKeysContract.Z_KEY) { return Result.failure() } as Int,
                x = dataMap.getOrElse(WorkersKeysContract.X_KEY) { return Result.failure() } as Int,
                y = dataMap.getOrElse(WorkersKeysContract.Y_KEY) { return Result.failure() } as Int,
                zMax = dataMap.getOrElse(WorkersKeysContract.Z_MAX_KEY) { return Result.failure() } as Int,
                zMin = dataMap.getOrElse(WorkersKeysContract.Z_MIN_KEY) { return Result.failure() } as Int
            )
        ) {
            return Result.success()
        } else return Result.failure()
    }
}
