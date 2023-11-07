package com.example.signaldoctor.onlineDatabase

import androidx.compose.animation.core.snap
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.repositories.IMsrsOnlineDB
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.snapshots
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystemWebMercator
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
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
                            consoledebug("${ measurement.value}")
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
    /*
    override fun postMsr(msrType: String, msr : Double, z : Int, x : Int, y : Int, zMax : Int, zMin : Int): Boolean {

        ///this reference is the place where to store the measurement: e.g. measurements/phone/maptileid/
        val dbRef = db.getReference("measurements/${msrType}")



        return descendingPost(
            msrType= msrType,
            msr = msr,
            z = z,
            x= x,
            y = y,
            zMax=zMax,
            dbRef = dbRef
        ) &&
        climbingPost(
            msrType= msrType,
            msr = msr,
            z = z,
            x = x,
            y = y,
            zMin = zMin,
            dbRef = dbRef
        )
    }

}
*/
    /*
fun descendingPost(msrType: String, msr : Double, z : Int, x : Int, y : Int, zMax: Int, dbRef : DatabaseReference) : Boolean {


    if(z>zMax) return true
    else{
        try{
            dbRef.child("${z}_${x}_${y}").push().setValue(msr)
        }catch (e: Exception) {
            consoledebug("Errore nella discesa")
            return false
        }
        //Add the measurement to tiles with deeper zoom level that are in the same area of the previous tile
        return descendingPost(
            msrType = msrType,
            msr = msr,
            zMax = zMax,
            dbRef = dbRef,
            z = z+1,
            x= 2*x-1,
            y =2*y-1,
            )   &&
        descendingPost(
            msrType = msrType,
            msr = msr,
            zMax = zMax,
            dbRef = dbRef,
            z = z+1,
            x= (2*x-1)+1,
            y =2*y-1,
        ) &&
         descendingPost(
            msrType = msrType,
            msr = msr,
            zMax = zMax,
            dbRef = dbRef,
            z = z+1,
            x= 2*x-1,
            y =(2*y-1)+1,
        ) &&
         descendingPost(
            msrType = msrType,
            msr = msr,
            zMax = zMax,
            dbRef = dbRef,
            z = z+1,
            x= (2*x-1)+1,
            y = (2*y-1)+1,
        )
    }
}

fun climbingPost(msrType: String, msr : Double, z : Int, x : Int, y : Int, zMin: Int = 0, dbRef : DatabaseReference) : Boolean{
    if(z <zMin) return true
    else {
        try{
            dbRef.child("${z}_${x}_${y}").push().setValue(msr)
        }catch (e : Exception){
            consoledebug("Errore nella salita")
            return false
        }
        return climbingPost(
            msrType = msrType,
            msr = msr,
            dbRef = dbRef,
            zMin = zMin,
            z = z-1,
            x= (x+1)/2,
            y = (x+1)/2,
        )
    }
*/

}
