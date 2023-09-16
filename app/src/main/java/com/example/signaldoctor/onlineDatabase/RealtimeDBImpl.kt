package com.example.signaldoctor.onlineDatabase

import android.util.Log
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.repositories.IMsrsOnlineDB
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.childEvents
import com.google.firebase.database.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import javax.inject.Inject

class RealtimeDBImpl @Inject constructor(val db : FirebaseDatabase) : IMsrsOnlineDB {




    override fun getMsrsAvgs(msrType: Measure): Flow<MsrsMap> {

        /*return flow{
                val hashMap = MsrsMap()
                db.reference.child("averages/${msrType}").snapshots.take(1).collect { dataSnapshot ->

                    dataSnapshot.children.forEach {entry ->
                        consoledebug("${msrType}: KEY:"+entry.key +", VALUE:"+ entry.value)
                        entry.key?.let {
                            hashMap[it] = entry.value as Long }
                    }
                }
                emit(hashMap)
            }*/
        return db.reference.child("averages/${msrType}").snapshots.map{ dataSnapshot ->
            var hashMap = MsrsMap()
            dataSnapshot.children.forEach {entry ->
                consoledebug("${msrType}: KEY:"+entry.key +", VALUE:"+ entry.value)
                entry.key?.let {tileindex->
                    hashMap[tileindex] =  (entry.value as Long).toDouble()
                }
            }
            hashMap
        }

    }

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

 fun consoledebug(msg : String){
   Log.i("DEBUG:", msg)
}

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
}