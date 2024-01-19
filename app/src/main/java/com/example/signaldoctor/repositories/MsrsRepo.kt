package com.example.signaldoctor.repositories

import android.location.Location
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.Settings
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.DefaultLocalDB
import com.example.signaldoctor.hiltModules.RealtimeFirebase
import com.example.signaldoctor.localDatabase.IMsrsLocalDB
import com.example.signaldoctor.onlineDatabase.IMsrsOnlineDB
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers.consoledebug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.lang.IllegalStateException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MsrsRepo @Inject constructor(
    @RealtimeFirebase private val onlineDB : IMsrsOnlineDB,
    @DefaultLocalDB private val localDB : IMsrsLocalDB,
) {


    fun  mergeAvgs(localMap : Flow<MsrsMap>,onlineMap : Flow<MsrsMap>) : Flow<MsrsMap> {
        return localMap.combine(onlineMap){ localMap, onlineMap ->
            onlineMap.forEach{ onlineAvgsMapEntry ->
                localMap.merge(
                    onlineAvgsMapEntry.key,
                    onlineAvgsMapEntry.value
                ) { avg1: Int, avg2: Int ->
                    (avg1+avg2) / 2
                }
            }
            localMap
        }
    }

    fun getPhoneMergedAvgs(settings: MeasurementSettings) : Flow<MsrsMap> {
        return mergeAvgs(getPhoneLocalAvgs(settings), getPhoneOnlineAvgs(settings))
    }

    fun getSoundMergedAvgs(settings: MeasurementSettings) : Flow<MsrsMap> {
        return mergeAvgs(getSoundLocalAvgs(settings), getSoundOnlineAvgs(settings))
    }

    fun getWifiMergedAvgs(settings: MeasurementSettings) : Flow<MsrsMap>{
        return mergeAvgs(getWifiLocalAvgs(settings), getWifiOnlineAvgs(settings))
    }



    fun getPhoneLocalAvgs(settings: MeasurementSettings) = localDB.avgsMap(Measure.phone, settings)
    fun getSoundLocalAvgs(settings: MeasurementSettings) = localDB.avgsMap(Measure.sound, settings)
    fun getWifiLocalAvgs(settings: MeasurementSettings) = localDB.avgsMap(Measure.wifi, settings)

    fun getPhoneOnlineAvgs(settings: MeasurementSettings) = onlineDB.avgsMap(Measure.phone, settings)
    fun getSoundOnlineAvgs(settings: MeasurementSettings) = onlineDB.avgsMap(Measure.sound, settings)
    fun getWifiOnlineAvgs(settings: MeasurementSettings) = onlineDB.avgsMap(Measure.wifi, settings)


    fun getOldestDate(msrType: Measure) = combine(
        localDB.getOldestDate(msrType),
        onlineDB.getOldestDate(msrType)
    ) { date, date2 ->
        minOf(date,date2)
    }

    fun countLocalMeasurements(msrType : Measure, userLocation : Location, limitDate: Date) : Flow<Boolean>{
        return localDB.countMeasurements(msrType, userLocation, limitDate).onEach {
            consoledebug("countLocalMeasurements running")
        }.onStart {
            consoledebug("countLocalMeasurements has started")
        }
    }

    fun countMergedMeasurements(msrType: Measure, userLocation : Location, limitDate: Date) : Flow<Boolean>{
        return countLocalMeasurements(msrType, userLocation, limitDate)
            .combine(onlineDB.countMeasurements(msrType, userLocation, limitDate)) { areLocalMsrsDated, areOnlineMsrsDated ->
                consoledebug("countMergedMeasurements running ")

                //consoledebug("areLocalMsrsDated? $areLocalMsrsDated")
                //consoledebug("areOnlineMsrsDated? $areOnlineMsrsDated")
                areLocalMsrsDated && areOnlineMsrsDated
            } .onStart {
                consoledebug("countMergedMeasurements has started")
            }
    }

    fun postPhoneMsr(measurement : PhoneMeasurement, networkMode: NetworkMode) =
        (if(networkMode == NetworkMode.ONLINE) onlineDB.postPhoneMsr(measurement) else true)
                && localDB.postPhoneMsr(measurement)
    fun postSoundMsr(measurement: SoundMeasurement, networkMode: NetworkMode) =
        (if(networkMode == NetworkMode.ONLINE) onlineDB.postSoundMsr(measurement) else true)
                && localDB.postSoundMsr(measurement)
    fun postWifiMsr(measurement: WiFIMeasurement, networkMode: NetworkMode) =
        (if(networkMode == NetworkMode.ONLINE) onlineDB.postWifiMsr(measurement) else true)
                && localDB.postWifiMsr(measurement)


   /* fun <T> postMsr( measurement : T) =  when(measurement){
             is PhoneMeasurement -> postPhoneMsr(measurement)
             is SoundMeasurement -> postSoundMsr(measurement)
             is WiFIMeasurement -> postWifiMsr(measurement)
            else -> throw IllegalStateException("parameter of postMsr() is not of the ones you can upload to the server")
        }*/

    fun closeLocalDB() {
        //FOR DEBUG PURPOSES, CANCEL THIS LINE FOR PRODUCTION!!!
        localDB.clearAllTables()
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