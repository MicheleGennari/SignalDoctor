package com.example.signaldoctor.workers

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WifiMsrWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx,
    params
) {
    @Inject lateinit var wifiManager: WifiManager
    @Inject lateinit var connectivityManager: ConnectivityManager

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        return when{

            Build.VERSION.SDK_INT<Build.VERSION_CODES.S -> wifiWorkOlderBuilds(wifiManager)

            Build.VERSION.SDK_INT>=Build.VERSION_CODES.S -> wifiWorkNewerBuilds(connectivityManager)

            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Measure.wifi.ordinal,
            NotificationCompat.Builder(ctx, MEASUREMENT_NOTIFICATION_CHANNEL_ID).apply {

                setContentTitle(ctx.getString(R.string.wifi_measurement_notification_content_title))
                setSmallIcon(R.drawable.wifi_icon_bitamp)
                setProgress(0,0, true)
                setOngoing(true)
                if(Build.VERSION.SDK_INT< Build.VERSION_CODES.O) priority = NotificationCompat.PRIORITY_HIGH

            }.build()
        )
    }
}

@SuppressLint("MissingPermission")
suspend fun wifiWorkOlderBuilds(wifiManager: WifiManager) : Result {
    return if (wifiManager.isWifiEnabled) {
        var msr = 0
        for (i in 1..5){
            delay(1000)
            msr += wifiManager.connectionInfo.rssi
        }
        msr /= 5
        Result.success(workDataOf(MsrWorkersInputData.MSR_KEY to msr))
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
    return Result.success(workDataOf(MsrWorkersInputData.MSR_KEY to msr))
}

suspend fun wifiSignalStrength(cm : ConnectivityManager)  = suspendCancellableCoroutine {continuation ->
    val callback = @RequiresApi(Build.VERSION_CODES.S) object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO){

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {

            if(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ){
                continuation.resume((networkCapabilities.transportInfo as WifiInfo).rssi)
            } else continuation.resumeWithException(IOException("Wifi Connection Not Stable"))
        }

        override fun onLost(network: Network) {
            continuation.resumeWithException(IOException("Wifi Connection Not Stable"))
        }

    }

    cm.requestNetwork(
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build(),
        callback
    )
    continuation.invokeOnCancellation { cm.unregisterNetworkCallback(callback) }
}
