package com.example.signaldoctor.workers

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.*
import androidx.work.workDataOf
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.realtimeFirebase.WifiMeasurementFirebase
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WifiMsrWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val gson : Gson
    )
    : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try{
            setForeground(getForegroundInfo())
        } catch (e : IllegalStateException) {
            Log.e("WIFI MEASUREMENT WORKER ERROR", "Can't run as foreground services due to restrictions")
            e.printStackTrace()
        }
        return when{

            Build.VERSION.SDK_INT<Build.VERSION_CODES.S -> wifiWorkOlderBuilds(applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)

            Build.VERSION.SDK_INT>=Build.VERSION_CODES.S -> wifiWorkNewerBuilds(applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)

            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {

        val workerNotification =  NotificationCompat.Builder(applicationContext, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {

            setContentTitle(applicationContext.getString(R.string.wifi_measurement_notification_content_title))
            setSmallIcon(R.drawable.wifi_icon_notification_bitmap)
            setProgress(0,0, true)
            setOngoing(true)
            if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH

        }.build()

        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ForegroundInfo(
            Measure.wifi.ordinal,
           workerNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        )
        else ForegroundInfo(
            Measure.wifi.ordinal,
            workerNotification,
        )

    }

    @SuppressLint("MissingPermission")
    suspend fun wifiWorkOlderBuilds(wifiManager: WifiManager) : Result {
        return if (wifiManager.isWifiEnabled) {
            var msr = 0
            for (i in 1..5){
                delay(1000)
                msr += wifiManager.connectionInfo.rssi
                setProgress(workDataOf(NoiseMsrWorker.Progress to i/10f))
            }
            msr /= 5
            val outputData = workDataOf(
                MeasurementBase.MSR_KEY to printAndReturn("wifi worker result: ",msr),
                MeasurementBase.MSR_TYPE_KEY to gson.toJson(Measure.wifi),
                 MEASUREMENT_KEY to gson.toJson(WiFIMeasurement(
                    firebaseTable = WifiMeasurementFirebase(
                        MeasurementBase(
                            tileIndex = inputData.getValue(MeasurementBase.TILE_INDEX_KEY),
                            value = msr
                        )
                    )
                ))
                )
            return Result.success(outputData)
        } else Result.failure()
    }

    suspend fun wifiWorkNewerBuilds(cm : ConnectivityManager) : Result {
        var msr = 0
        for (i in 1..5){
            try{
                msr += wifiSignalStrength(cm)
                delay(1000)
            }catch (e : IOException){
                Result.failure()
            }
        }
        msr /= 5
        val outputData = workDataOf(
            MeasurementBase.MSR_KEY to printAndReturn("wifi worker result: ",msr),
            MeasurementBase.MSR_TYPE_KEY to gson.toJson(Measure.wifi),
            MEASUREMENT_KEY to gson.toJson(WiFIMeasurement(
                firebaseTable = WifiMeasurementFirebase(
                    MeasurementBase(
                        tileIndex = inputData.getValue(MeasurementBase.TILE_INDEX_KEY),
                        value = msr
                    )
                )
            ))
        )
        return Result.success(outputData)
    }

}

fun <T> printAndReturn(tag : String,t: T) : T{
    Log.i(tag, "$t")
    return t
}




suspend fun wifiSignalStrength(cm : ConnectivityManager)  = suspendCancellableCoroutine { continuation ->
    val callback = @RequiresApi(Build.VERSION_CODES.S) object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO){

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {

            continuation.resume((networkCapabilities.transportInfo as WifiInfo).rssi)

        }

        override fun onLost(network: Network) {
            continuation.resumeWithException(IOException("Wifi Connection Not Stable"))
        }

    }

    cm.requestNetwork(
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build(),
        callback
    )
    continuation.invokeOnCancellation { cm.unregisterNetworkCallback(callback) }
}
