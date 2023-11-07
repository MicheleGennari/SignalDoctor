package com.example.signaldoctor.repositories

import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.workers.MsrWorkersInputData
import kotlinx.coroutines.flow.Flow

interface IMsrsOnlineDB : IMsrsLocalDB {

    fun getMsrsAvgs(msrType : Measure) : Flow<MsrsMap>


    suspend fun postMsr( msr : Int, mapTileData: MsrWorkersInputData) : Boolean
}