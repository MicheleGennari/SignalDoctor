package com.example.signaldoctor.onlineDatabase

import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.coroutines.resume

const val postMsrRealtimeDbRoot = "measurements"

class RealtimeDBImpl @Inject constructor(private val db : FirebaseDatabase) : IMsrsOnlineDB {


    override fun getMsrsAvgs(msrType: Measure): Flow<MsrsMap> = callbackFlow {

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

    override suspend fun postMsr(
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

}
