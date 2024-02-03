package com.example.signaldoctor.appComponents

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.signaldoctor.broadcastReceivers.RUN_MEASUREMENT_ACTION
import com.example.signaldoctor.broadcastReceivers.RunMeasurementReceiver
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