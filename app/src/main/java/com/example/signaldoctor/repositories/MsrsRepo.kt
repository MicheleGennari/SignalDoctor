package com.example.signaldoctor.repositories

import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.hiltModules.DefaultLocalDB
import com.example.signaldoctor.hiltModules.RealtimeFirebase
import com.example.signaldoctor.localDatabase.IMsrsLocalDB
import com.example.signaldoctor.onlineDatabase.IMsrsOnlineDB
import com.example.signaldoctor.workers.MsrWorkersInputData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MsrsRepo @Inject constructor(
    @RealtimeFirebase private val onlineDB : IMsrsOnlineDB,
    @DefaultLocalDB private val localDB : IMsrsLocalDB,
) {

    fun getMergedAvgs(msrType : Measure) = onlineDB.getMsrsAvgs(msrType)

    fun getLocalAvgs(msrType: Measure) = localDB.getMsrsAvgs(msrType)

    //fun getLocalAvgs(msrType: Measure) = localDB.getMsrsAvgs(msrType)

    suspend fun postMsr(msr : Int, mapTileData : MsrWorkersInputData) : Boolean{

        return onlineDB.postMsr(msr = msr, mapTileData = mapTileData) && localDB.postMsr(msr, mapTileData)

    }

    fun closeLocalDB() {
        //FOR DEBUG PURPOSES, CANCEL THIS LINE FOR PRODUCTION!!!
        localDB.clearAllTables()

        localDB.close()
    }


}
/*
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
 */