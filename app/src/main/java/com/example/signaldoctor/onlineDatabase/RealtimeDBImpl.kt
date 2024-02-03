package com.example.signaldoctor.onlineDatabase

import android.location.Location
import android.util.Log
import androidx.core.util.rangeTo
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.RoomMeasurementEntity
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.toMap
import com.example.signaldoctor.utils.whenMsrType
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import com.google.firebase.database.ktx.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView
import java.lang.IllegalArgumentException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.resume


class RealtimeDBImpl @Inject constructor(private val db : FirebaseDatabase) : IMsrsOnlineDB {

    companion object{
         private const val REALTIME_DB_ROOT_PATH = "measurements"
         private const val PHONE_TABLE_PATH = "$REALTIME_DB_ROOT_PATH/phone"
         private const val SOUND_TABLE_PATH = "$REALTIME_DB_ROOT_PATH/sound"
         private const val WIFI_TABLE_PATH = "$REALTIME_DB_ROOT_PATH/wifi"
    }

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

     override fun avgsMap(msrType: Measure, settings: MeasurementSettings): Flow<MsrsMap> =
         whenMsrType(msrType,
             phone = getPhoneMsrs(settings),
             sound = getSoundMsrs(settings),
             wifi = getWifiMsrs(settings)
             ).map { it.map { it.value.baseInfo }.baseAvgsMap() }.flowOn(Dispatchers.Default)


    private inline fun <reified D : RoomMeasurementEntity> getMsr(msrType : Measure, msrsFilter : List<D>.() -> List<D>) : Flow<Map<String,D>>{


        return db.reference.msrTypePath(msrType).snapshots.flowOn(Dispatchers.IO)
            .map { msrTypeTable ->
                msrTypeTable.children.toMap { newMap ->
                        forEach{ tileIndex ->
                            tileIndex.children.forEach { measurement ->
                                measurement.getValue<D>()?.let {
                                    newMap.putIfAbsent(it.baseInfo.uuid, it)
                                }
                            }
                        }
                    }
                }.flowOn(Dispatchers.Default)
            }





    private inline fun <reified M : RoomMeasurementEntity> List<M>.msrFilter(settings: MeasurementSettings) : List<M> =
        filter { measurement ->
            measurement.baseInfo.run {
                isInTimeRange(settings)
            }
        }.msrsToTake(settings)


    //function that takes only the first msrsToTake measurements if useMsrsToTake is true
    private fun <T> List<T>.msrsToTake(settings: MeasurementSettings) = apply { if (settings.useMsrsToTake) take(settings.msrsToTake) }

    private fun MeasurementBase.isInTimeRange(settings: MeasurementSettings) = kotlin.runCatching {
            Date(settings.oldness).rangeTo(Date(settings.freshness))
                .contains(SimpleDateFormat.getDateTimeInstance().parse(this.date))
    }.onFailure { e ->
        when(e){
            is IllegalArgumentException ->  Log.e(
                "RealtimeDBImpl isInTimeRange()",
                "Filtering measurements by time range reported error because " +
                        "oldness is actually greater than freshness",
                e
            )
            is ParseException -> Log.e(
                "RealtimeDBimpl isInTimeRange()",
                "measurement entity has no valid data format",
                e
            )
            else -> Log.e(
                "RealtimeDBImpl isInTimeRange()",
                "throwed error",
                e
            )
        }
    }.getOrDefault(false)

    private fun phoneMsrsFilter(settings: MeasurementSettings) : List<PhoneMeasurement>.() -> List<PhoneMeasurement> = {
        msrFilter(settings)
    }

    override fun getPhoneMsrs(settings: MeasurementSettings) : Flow<Map<String,PhoneMeasurement>> =
        getMsr(Measure.phone, msrsFilter = phoneMsrsFilter(settings))



     private fun soundMsrsFilter(settings: MeasurementSettings) : List<SoundMeasurement>.() -> List<SoundMeasurement> = {
            msrFilter(settings).filter { soundMeasurement ->
                soundMeasurement.baseInfo.isInTimeRange(settings)
            }.msrsToTake(settings)
        }

    override fun getSoundMsrs(settings: MeasurementSettings): Flow<Map<String,SoundMeasurement>> =
        getMsr(Measure.sound, msrsFilter = soundMsrsFilter(settings))

    private fun wifiMsrsFilter(settings: MeasurementSettings) : List<WiFIMeasurement>.() -> List<WiFIMeasurement> = {
            msrFilter(settings).filter {  wifiMeasurement->
                wifiMeasurement.baseInfo.isInTimeRange(settings)
            }.msrsToTake(settings)
    }

    override fun getWifiMsrs(settings: MeasurementSettings): Flow<Map<String,WiFIMeasurement>> =
         getMsr(Measure.wifi, wifiMsrsFilter(settings))


    override fun getOldestDate(msrType: Measure) = db.reference.child(
        whenMsrType(msrType,
            phone = PHONE_TABLE_PATH,
            sound = SOUND_TABLE_PATH,
            wifi = WIFI_TABLE_PATH
            )
    ).snapshots.flowOn(Dispatchers.IO).map { tileIndexes ->
        tileIndexes.children.flatMap { tileIndexMsrs ->
            tileIndexMsrs.children.mapNotNull { measurement ->

                measurement.getValue<@JvmSuppressWildcards MeasurementBase>()?.date?.let {
                    SimpleDateFormat.getDateTimeInstance().parse(it)
                }
            }
        }.min()
    }.flowOn(Dispatchers.Default)


    override fun areMsrsDated(msrType : Measure, userLocation : Location, maxOldness : Date) : Flow<Boolean> {

        val currentTileIndex = MapView.getTileSystem().tileIndexFromLocation(userLocation)

        return db.reference.child(
            when (msrType) {

                Measure.sound -> SOUND_TABLE_PATH
                Measure.phone -> PHONE_TABLE_PATH
                Measure.wifi -> WIFI_TABLE_PATH

            }).orderByKey().startAt("$currentTileIndex").snapshots.flowOn(Dispatchers.IO).map { tileIndexesSnap ->
                tileIndexesSnap.children.flatMap { tileIndexSnap ->
                    tileIndexSnap.children
                }
        }.map { measurementsSnap ->
            measurementsSnap.mapNotNull { measurementSnap ->
                whenMsrType(msrType,
                    phone =  measurementSnap.getValue<PhoneMeasurement>()?.baseInfo,
                    sound =  measurementSnap.getValue<SoundMeasurement>()?.baseInfo,
                    wifi =  measurementSnap.getValue<WiFIMeasurement>()?.baseInfo
                    )
            }.count { measurement ->
                try{
                    SimpleDateFormat.getDateTimeInstance().parse(measurement.date)
                        ?.after(expirationDate)
                }catch (e : ParseException){ false} ?: false
            } <1 //if there are no measurements fresher than the expiration date, returns true
        }.flowOn(Dispatchers.Default)
    }


    private suspend inline fun <reified M : RoomMeasurementEntity> postMsr(msrEntity: M) : Boolean{
        val dbTileIndex = msrEntity.baseInfo.tileIndex
        return withContext(Dispatchers.IO){
            db.reference.msrTypePath(msrEntity.msrType).child("$dbTileIndex").insertMsr(msrEntity)
        }
    }

    private fun DatabaseReference.msrTypePath(msrType: Measure) = child(
        whenMsrType(msrType,
        phone = PHONE_TABLE_PATH,
        sound = SOUND_TABLE_PATH,
        wifi = WIFI_TABLE_PATH
        )
    )


    override suspend fun postPhoneMsr(phoneMeasurement: PhoneMeasurement) = postMsr(phoneMeasurement)

    override suspend fun postSoundMsr(soundMeasurement: SoundMeasurement) = postMsr(soundMeasurement)

    override suspend fun postWifiMsr(wifiMeasurement: WiFIMeasurement) = postMsr(wifiMeasurement)


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

    private suspend fun DatabaseReference.insertMsr(value : RoomMeasurementEntity) = suspendCancellableCoroutine { continuation->
        push().setValue(value) {error, _ ->

        // if error doesn't exist, return true, otherwise something bad happened, so false is returned
        if(error == null)
            continuation.resume(true)
        else
            continuation.resume(false)
    }
    }

}
