package com.example.signaldoctor.contracts

import com.example.signaldoctor.workers.MsrWorkersInputData
import kotlinx.coroutines.flow.Flow

interface IMsrsDB {

    fun getMsrsAvgs(msrType : Measure) : Flow<MsrsMap>


    suspend fun postMsr( msr : Int, mapTileData : MsrWorkersInputData) : Boolean
}