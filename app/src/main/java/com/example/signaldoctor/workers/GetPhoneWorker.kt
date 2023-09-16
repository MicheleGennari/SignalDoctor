package com.example.signaldoctor.workers

import android.content.Context
import android.telephony.TelephonyManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import com.example.signaldoctor.contracts.Measure
import javax.inject.Inject

class GetPhoneWorker(context : Context, params : WorkerParameters) :CoroutineWorker(context, params) {

    @Inject lateinit var telephonyManager : TelephonyManager
    override suspend fun doWork() : Result{


        return TODO()
        }
    }
