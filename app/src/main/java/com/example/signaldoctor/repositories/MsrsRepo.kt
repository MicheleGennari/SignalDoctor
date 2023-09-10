package com.example.signaldoctor.repositories

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result.Success
import androidx.work.ListenableWorker.Result.failure
import androidx.work.ListenableWorker.Result.retry
import androidx.work.ListenableWorker.Result.success
import androidx.work.Operation.State.FAILURE
import androidx.work.WorkerParameters
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.hiltModules.RealtimeFirebase
import com.example.signaldoctor.hiltModules.RoomDatabase
import javax.inject.Inject

class MsrsRepo @Inject constructor(
    @RealtimeFirebase val onlineDB : IMsrsOnlineDB,
    @RoomDatabase val localDB : IMsrsLocalDB
) {

    fun getMergedAvgs(msrType : Measure) = onlineDB.getMsrsAvgs(msrType)
    fun getLocalAvgs(msrType: Measure) = localDB.getMsrsAvgs(msrType)

    fun postMsr(msrType: String, msr : Int, z : Int, x : Int, y : Int, zMax : Int, zMin : Int = 0, networkMode: Int) : Boolean{
        return (
                if(networkMode == NetworkMode.ONLINE.ordinal){
                    onlineDB.postMsr(
                        msrType = msrType,
                        msr = msr,
                        z = z,
                        x = x,
                        y = y,
                        zMax = zMax,
                        zMin = zMin,
                    )
                } else false
            ) && true //localDB.postMsr()

    }

   inner class PostMsrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            if(!inputDataIsValid()) return failure()
            return if( this@MsrsRepo.postMsr(
                    msrType = inputData.getString("msrType") ?: return failure(),
                    msr = inputData.getInt("msr",-1),
                    z = inputData.getInt("z",-1),
                    x = inputData.getInt("x",-1),
                    y = inputData.getInt("y",-1),
                    zMax = inputData.getInt("zMax",-1),
                    zMin = inputData.getInt("zMin",-1),
                    networkMode = inputData.getInt("networkMode",-1)
                )
            ) retry() else failure()
        }
    }
}

fun CoroutineWorker.inputDataIsValid() :  Boolean {
   if (inputData.getInt("msr",-1) == -1) return false
    if (inputData.getInt("z",-1) == -1) return false
    if (inputData.getInt("x",-1) == -1) return false
    if (inputData.getInt("y",-1) == -1) return false
    if (inputData.getInt("zMax",-1) == -1) return false
    if (inputData.getInt("zMin",-1) == -1) return false
    if (inputData.getInt("networkMode",-1) == -1) return false

    return true
}