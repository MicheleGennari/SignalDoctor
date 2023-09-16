package com.example.signaldoctor.repositories

import com.example.signaldoctor.contracts.NetworkMode
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.hiltModules.RealtimeFirebase
import com.example.signaldoctor.onlineDatabase.consoledebug
import javax.inject.Inject

class MsrsRepo @Inject constructor(
    @RealtimeFirebase val onlineDB : IMsrsOnlineDB,
    //@RoomDatabase val localDB : IMsrsLocalDB
) {

    fun getMergedAvgs(msrType : Measure) = onlineDB.getMsrsAvgs(msrType)
    //fun getLocalAvgs(msrType: Measure) = localDB.getMsrsAvgs(msrType)

    fun postMsr(msrType: String, msr : Double, z : Int, x : Int, y : Int, zMax : Int, zMin : Int = 0, networkMode: Int) : Boolean{

        return (
                if(networkMode == NetworkMode.ONLINE.ordinal){
                    consoledebug("INSIDE POST MSR OF MsrsRepo")

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