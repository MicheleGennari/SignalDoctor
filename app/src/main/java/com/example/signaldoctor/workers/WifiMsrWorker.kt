package com.example.signaldoctor.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WifiMsrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext,
    params
) {
    override suspend fun doWork(): Result {
        TODO("Not yet implemented")
    }
}