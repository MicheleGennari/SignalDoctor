package com.example.signaldoctor.onlineDatabase

import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.Settings
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers
import com.example.signaldoctor.workers.printAndReturn
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume


class RealtimeDBImpl @Inject constructor(private val db : FirebaseDatabase) : IMsrsOnlineDB {

    companion object{
         private const val REALTIME_DB_ROOT_PATH = "measurements"
         private const val PHONE_TABLE_PATH = "$REALTIME_DB_ROOT_PATH/phone"
         private const val SOUND_TABLE_PATH = "$REALTIME_DB_ROOT_PATH/sound"
         private const val WIFI_TABLE_PATH = "$REALTIME_DB_ROOT_PATH/wifi"
    }

/*
     fun old_getMsrsAvgs(msrType: Measure): Flow<MsrsMap> = callbackFlow {

        val path = db.reference.child("measurements/$msrType")
        lateinit var listener: ValueEventListener

        try{
            listener = path.addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    consoledebug("uploading...")
                    val msrsMap = MsrsMap()
                    msrsMap.putAll(snapshot.children.associateBy(
                        { mapTile -> mapTile.key!!},
                        { mapTile -> mapTile.children.sumOf { measurement ->
                            (measurement.value as Long).toInt()
                        } / mapTile.childrenCount.toInt()
                        }
                    ) )
                    trySendBlocking(msrsMap)
                }

                override fun onCancelled(error: DatabaseError) {
                    cancel(CancellationException("Realtime DB can't send average measurements"))
                }

            })
            awaitCancellation()
        }finally {
            path.removeEventListener(listener)
        }

    }
*/
    private fun List<MeasurementBase>.baseAvgsMap() : MsrsMap {
        val msrsMap = MsrsMap()

        return this.associateByTo(
             destination = msrsMap,
            {msr : MeasurementBase -> msr.tileIndex },
            {msr : MeasurementBase ->
                msrsMap[msr.tileIndex]?.let { (it+msr.value) / 2 } ?: msr.value
            }
        )
    }

     override fun avgsMap(msrType: Measure, settings: MeasurementSettings): Flow<MsrsMap> = when(msrType){
        Measure.phone -> getPhoneMsrs(settings).map { it.map { it.firebaseTable.baseInfo  }.baseAvgsMap() }
        Measure.sound -> getSoundMsrs(settings).map { it.map { it.firebaseTable.baseInfo  }.baseAvgsMap() }
        Measure.wifi -> getWifiMsrs(settings).map { it.map { it.firebaseTable.baseInfo  }.baseAvgsMap() }
    }

    private fun <D,T : Class<D>> getBaseMsrs(msrType : Measure, entity : T, msrsFilter : List<D>.() -> List<D>) : Flow<List<D>>{
        return db.reference.child("$REALTIME_DB_ROOT_PATH/${msrType.name}").snapshots.flowOn(Dispatchers.IO)
            .map { msrTypeTable ->
                msrTypeTable.children.flatMap { tile ->
                   tile.children.mapNotNull { measurement ->
                       measurement.getValue(entity)
                   }.msrsFilter()
            }
        }

    }

    private fun phoneMsrsFilter(settings: MeasurementSettings) : List<PhoneMeasurement>.() -> List<PhoneMeasurement> {
        return  {
            filter { phoneMeasurement ->
                    true
                }
                .take(settings.msrsToTake)
        }
    }

    override fun getPhoneMsrs(settings: MeasurementSettings) : Flow<List<PhoneMeasurement>>{
        return getBaseMsrs(Measure.phone, PhoneMeasurement::class.java, phoneMsrsFilter(settings))
    }


        /* callbackFlow {
        val path = db.reference.child("measurements/phone")
        lateinit var listener: ValueEventListener


        try {

            path.addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                   for(tile in snapshot.children){

                   }
                }

                override fun onCancelled(error: DatabaseError) {
                    cancel("Realtime DB failed", IOException("RealtimeDB failed"))
                }

            })

            awaitCancellation()
        }finally {
            path.removeEventListener(listener)
        }
    }
*/

     private fun soundMsrsFilter(settings: MeasurementSettings) : List<SoundMeasurement>.() -> List<SoundMeasurement>{
        return {
            filter { soundMeasurement ->
                true
            }.take(settings.msrsToTake)
        }
    }
    override fun getSoundMsrs(settings: MeasurementSettings): Flow<List<SoundMeasurement>> {
        return getBaseMsrs(Measure.sound, SoundMeasurement::class.java,soundMsrsFilter(settings))
    }


    private fun wifiMsrsFilter(settings: MeasurementSettings) : List<WiFIMeasurement>.() -> List<WiFIMeasurement> {
        return {
            filter {  wifiMeasurement->
                true
            }.take(settings.msrsToTake)
        }
    }

    override fun getWifiMsrs(settings: MeasurementSettings): Flow<List<WiFIMeasurement>> {
        return getBaseMsrs(Measure.wifi, WiFIMeasurement::class.java, wifiMsrsFilter(settings))
    }

    override fun postPhoneMsr(phoneMeasurement: PhoneMeasurement): Boolean {
        val dbTileIndex = phoneMeasurement.firebaseTable.baseInfo.tileIndex
        db.reference.child("measurements/phone").child("$dbTileIndex").push()
            .setValue(phoneMeasurement)
        return true
    }

    override fun postSoundMsr(soundMeasurement: SoundMeasurement): Boolean {
        val dbTileIndex = soundMeasurement.firebaseTable.baseInfo.tileIndex
        db.reference.child("measurements/sound").child("$dbTileIndex").push()
            .setValue(soundMeasurement)
        return true
    }

    override fun postWifiMsr(wifiMeasurement: WiFIMeasurement): Boolean {
        val dbTileIndex = wifiMeasurement.firebaseTable.baseInfo.tileIndex
        db.reference.child("measurements/wifi").child("$dbTileIndex").push()
            .setValue(wifiMeasurement)
        return true
    }


    /*
    fun computeAvg(z : Int, avgsMap : MsrsMap, measurements: DataSnapshot){

        if (z<0) return
        else {
            // path to single measurement is "measurement/z/mapTile{id}/measurement{id]/value"

            measurements.child("$z").children.forEach{ mapTile ->

            if(avgsMap[mapTile.key!!] == null) {
                avgsMap[mapTile.key!!] = mapTile.children.sumOf { measurement ->
                    (measurement.child("value").value as Long).toInt()
                } / mapTile.childrenCount.toInt()
            }else {
                avgsMap[mapTile.key!!] = (avgsMap[mapTile.key!!]!! +( mapTile.children.sumOf { measurement ->
                    (measurement.child("value").value as Long).toInt()
                } / mapTile.childrenCount.toInt()) ) / 2
            }
            }
        }
    }
*/
    /*
     suspend fun postMsr(
        msr : Int,
        mapTileData: MsrWorkersInputData
    ): Boolean = suspendCancellableCoroutine { continuation ->

        val mapTileIndex = MapTileIndex.getTileIndex(MapTileIndex.mMaxZoomLevel, MapView.getTileSystem().getTileXFromLongitude(mapTileData.long, MapTileIndex.mMaxZoomLevel), MapView.getTileSystem().getTileYFromLatitude(mapTileData.lat, MapTileIndex.mMaxZoomLevel) )

        db.reference.child("measurements/${mapTileData.msrType}/$mapTileIndex").push()
            .setValue(msr) {error, _ ->

                // if error doesn't exist, return true, otherwise something bad happened, so false is returned
                if(error == null)
                    continuation.resume(true)
                else
                    continuation.resume(false)
            }
    }
*/

    suspend fun DatabaseReference.insert( value : Any?) = suspendCancellableCoroutine { continuation->
        setValue(value) {error, _ ->

        // if error doesn't exist, return true, otherwise something bad happened, so false is returned
        if(error == null)
            continuation.resume(true)
        else
            continuation.resume(false)
    }
    }

}
