package com.example.signaldoctor.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.screens.msrTypeWhen
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundMeasurementsManager @Inject constructor(
    @ApplicationContext private val app : Context
) {
    fun start() =
        sendStartCommand(
            Intent(app, BackgroundMeasurementsService::class.java)
                .setAction(START_BACKGROUND_ACTION)
        )

    fun stop(){
        sendStartCommand(
            Intent(app, BackgroundMeasurementsService::class.java)
                .setAction(START_BACKGROUND_ACTION)
        )
    }

    fun forceStop(){
        app.stopService(Intent(app, BackgroundMeasurementsService::class.java))
    }

    /*
    private var isSoundOn = false
    private var isPhoneOn = false
    private var isWifiOn = false


    fun startMeasuring(msrType: Measure, minutes : Long = 15L) {

        if(isBackGroundMeasurementOn(msrType = msrType)) {
            Log.w("Background Measurement Service", "${msrType.name} background measurements are already on")
            return
        }

        val intent = Intent(app, BackgroundMeasurementsService::class.java).apply {
            action = START_BACKGROUND_ACTION
            putExtra(msrType.name, msrType.ordinal)
            putExtra(DURATION_KEY, minutes)
        }

        sendStartCommand(intent)?.also { setBackgroundMeasurementSwitch(msrType, true) }

    }

    fun stopMeasuring(msrType: Measure){

        if(!isBackGroundMeasurementOn(msrType)) {

            Log.w(
                "Background Measurements Manager",
                "${msrType.name} background measurements can't be stopped since they're already turned off"
            )
            return

        } else {

            val intent = Intent(app, BackgroundMeasurementsService::class.java).apply {
                action = STOP_BACKGROUND_ACTION
                putExtra(msrType.name, msrType.ordinal)
                }

                sendStartCommand(intent)?.also { setBackgroundMeasurementSwitch(msrType, false) }

        }

    }


    private fun setBackgroundMeasurementSwitch(msrType: Measure, isOn : Boolean){
        when(msrType){
            Measure.phone -> isPhoneOn = isOn
            Measure.wifi -> isWifiOn = isOn
            Measure.sound -> isSoundOn = isOn
        }

        shutdownIdleService()

    }
 */
    private fun sendStartCommand(intent : Intent) : ComponentName? = try{
        (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    app.startForegroundService(intent)
                else
                    app.startService(intent)
                ) ?: throw RuntimeException("BackgroundMeasurementService does not exist")

    }catch (e : RuntimeException){
        e.printStackTrace()
        null
    }


    /*
    private fun shutdownIdleService(){
        try{
            if (!(isSoundOn || isPhoneOn || isWifiOn))
                app.stopService(Intent(app, BackgroundMeasurementsService::class.java))
        }catch (e : RuntimeException){
            e.printStackTrace()
        }
    }

    private fun isBackGroundMeasurementOn(msrType: Measure) = msrTypeWhen(msrType,
        phone = isPhoneOn,
        sound = isSoundOn,
        wifi = isWifiOn
    )

    */

}