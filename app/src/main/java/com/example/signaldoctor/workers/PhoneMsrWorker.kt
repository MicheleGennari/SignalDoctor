package com.example.signaldoctor.workers

import android.content.Context
import android.telephony.TelephonyManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import javax.inject.Inject

class PhoneMsrWorker(context : Context, params : WorkerParameters) :CoroutineWorker(context, params) {

    @Inject lateinit var telephonyManager : TelephonyManager
    override suspend fun doWork() : Result{


        return TODO()
        }
    }
