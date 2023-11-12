package com.example.signaldoctor.localDatabase

import androidx.room.Dao
import com.example.signaldoctor.contracts.IMsrsDB
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.workers.MsrWorkersInputData
import com.example.signaldoctor.workers.PostWorkerInputData
import kotlinx.coroutines.flow.Flow

interface IMsrsLocalDB : IMsrsDB {

    fun close()

    fun clearAllTables()

}