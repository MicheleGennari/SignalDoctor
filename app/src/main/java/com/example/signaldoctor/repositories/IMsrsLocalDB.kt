package com.example.signaldoctor.repositories

import androidx.room.Dao
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import kotlinx.coroutines.flow.Flow

interface IMsrsLocalDB {


    fun getMsrsAvgs(msrType : Measure) : Flow<MsrsMap>


    fun postMsr(msrType: String, msr : Int, z : Int, x : Int, y : Int, zMax : Int, zMin : Int ) : Boolean
}