package com.example.signaldoctor.repositories

import android.location.Location
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.contracts.IMsrsDB
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.hiltModules.DefaultLocalDB
import com.example.signaldoctor.hiltModules.RealtimeFirebase
import com.example.signaldoctor.localDatabase.IMsrsLocalDB
import com.example.signaldoctor.onlineDatabase.IMsrsOnlineDB
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.RoomMeasurementEntity
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.whenMsrType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MsrsRepo @Inject constructor(
    @RealtimeFirebase private val onlineDB : IMsrsOnlineDB,
    @DefaultLocalDB private val localDB : IMsrsLocalDB,
) {

    fun getPhoneMergedAvgs(settings: MeasurementSettings) : Flow<MsrsMap> =
         getMergedAvgs(getMergedMsrs(Measure.phone, settings))

    fun getSoundMergedAvgs(settings: MeasurementSettings) =
        getMergedAvgs(getMergedMsrs(Measure.sound, settings))

    fun getWifiMergedAvgs(settings: MeasurementSettings) : Flow<MsrsMap> =
        getMergedAvgs(getMergedMsrs(Measure.wifi, settings))


    private fun getMergedAvgs(mergedMsrs : Flow<Map<String, RoomMeasurementEntity>>) : Flow<MsrsMap> {

        return mergedMsrs.map {

            val map = MsrsMap()

            it.toList().forEach{ entry ->
                map.merge(
                    entry.second.baseInfo.tileIndex,
                    entry.second.baseInfo.value
                ) { avg1: Int, avg2: Int ->
                    (avg1+avg2) / 2
                }
            }
            map
        }
    }


    fun getPhoneLocalAvgs(settings: MeasurementSettings) = localDB.avgsMap(Measure.phone, settings)
    fun getSoundLocalAvgs(settings: MeasurementSettings) = localDB.avgsMap(Measure.sound, settings)
    fun getWifiLocalAvgs(settings: MeasurementSettings) = localDB.avgsMap(Measure.wifi, settings)

    fun getPhoneOnlineAvgs(settings: MeasurementSettings) = onlineDB.avgsMap(Measure.phone, settings)
    fun getSoundOnlineAvgs(settings: MeasurementSettings) = onlineDB.avgsMap(Measure.sound, settings)
    fun getWifiOnlineAvgs(settings: MeasurementSettings) = onlineDB.avgsMap(Measure.wifi, settings)


    private fun getMergedMsrs(msrType: Measure, settings: MeasurementSettings) =
        combine(localDB.getMsrs(msrType, settings), onlineDB.getMsrs(msrType, settings)){ localMsrs, onlineMsrs ->
            onlineMsrs.toMutableMap().apply {
                putAll(localMsrs.toMutableMap())
            }
        }.onEach { consoleDebug("combined $msrType measurements are: ${it.size}") }

    private fun IMsrsDB.getMsrs(msrType: Measure, settings: MeasurementSettings) = whenMsrType(msrType,
        phone = getPhoneMsrs(settings),
        sound = getSoundMsrs(settings),
        wifi = getWifiMsrs(settings)
        )


    fun getOldestDate(msrType: Measure) = combine(
        localDB.getOldestDate(msrType),
        onlineDB.getOldestDate(msrType)
    ) { date, date2 ->
        minOf(date,date2)
    }

    fun countLocalMeasurements(msrType : Measure, userLocation : Location, limitDate: Date) : Flow<Boolean>{
        return localDB.areMsrsDated(msrType, userLocation, limitDate).onEach {
            consoleDebug("countLocalMeasurements running")
        }.onStart {
            consoleDebug("countLocalMeasurements has started")
        }
    }

    fun countMergedMeasurements(msrType: Measure, userLocation : Location, limitDate: Date) : Flow<Boolean>{
        return countLocalMeasurements(msrType, userLocation, limitDate)
            .combine(onlineDB.areMsrsDated(msrType, userLocation, limitDate)) { areLocalMsrsDated, areOnlineMsrsDated ->
                consoleDebug("countMergedMeasurements running ")

                //consoledebug("areLocalMsrsDated? $areLocalMsrsDated")
                //consoledebug("areOnlineMsrsDated? $areOnlineMsrsDated")
                areLocalMsrsDated && areOnlineMsrsDated
            } .onStart {
                consoleDebug("countMergedMeasurements has started")
            }
    }




    suspend fun postPhoneMsr(measurement : PhoneMeasurement, networkMode: NetworkMode) =
        (if(networkMode == NetworkMode.ONLINE) onlineDB.postPhoneMsr(measurement) else true)
                && localDB.postPhoneMsr(measurement)
    suspend fun postSoundMsr(measurement: SoundMeasurement, networkMode: NetworkMode) =
        (if(networkMode == NetworkMode.ONLINE) onlineDB.postSoundMsr(measurement) else true)
                && localDB.postSoundMsr(measurement)
    suspend fun postWifiMsr(measurement: WiFIMeasurement, networkMode: NetworkMode) = run{
        consoleDebug("inside postWifiMsr ${measurement.baseInfo.value}")
        (if (networkMode == NetworkMode.ONLINE) onlineDB.postWifiMsr(measurement) else true)
                && localDB.postWifiMsr(measurement)
    }


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

