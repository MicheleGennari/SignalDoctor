package com.example.signaldoctor.appComponents

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.signaldoctor.broadcastReceivers.RUN_MEASUREMENT_ACTION
import com.example.signaldoctor.broadcastReceivers.RunMeasurementReceiver
import com.example.signaldoctor.utils.Loggers.consoledebug
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class ReceiversManager @Inject constructor(
    @ApplicationContext private val applicationContext: Context
)  {

    private lateinit var runMeasurementReceiver : RunMeasurementReceiver


    fun registerRunMeasurementReceiver(exported : Boolean) {

        ContextCompat.registerReceiver(
            applicationContext,
            runMeasurementReceiver,
            IntentFilter(RUN_MEASUREMENT_ACTION),
            if(exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED)

    }

    fun unregisterRunMeasurementReceiver(){
        applicationContext.unregisterReceiver(runMeasurementReceiver)
    }

}