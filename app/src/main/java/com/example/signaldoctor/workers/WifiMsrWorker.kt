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
import androidx.datastore.core.DataStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.*
import androidx.work.workDataOf
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.isLocationPermissionGranted
import com.example.signaldoctor.appComponents.viewModels.MEASUREMENT_NOTIFICATION_CHANNEL_ID
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.WiFIMeasurement
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.views.MapView
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WifiMsrWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val appSettings: DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    private val locationProvider: FlowLocationProvider,
    private val gson : Gson
    )
    : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {


        return if (!ctx.isLocationPermissionGranted()) Result.failure() else {

            try {
                setForeground(getForegroundInfo())
            } catch (e: IllegalStateException) {
                Log.e(
                    "WIFI MEASUREMENT WORKER ERROR",
                    "Can't run as foreground service due to restrictions"
                )
                e.printStackTrace()
            }

            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> wifiWorkOlderBuilds(
                    applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> wifiWorkNewerBuilds(
                    applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                )

                else -> Result.failure()
            } //end when

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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE + ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
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
            repeat(5){ i ->
                delay(1000)
                msr += wifiManager.connectionInfo.rssi
                setProgress(workDataOf(NoiseMsrWorker.Progress to i/10f))
            }
            msr /= 5

            if(
                    msrsRepo.postWifiMsr(
                    measurement = WiFIMeasurement(
                            baseInfo = MeasurementBase(
                                tileIndex = locationProvider.tileIndexFromLocation(Priority.PRIORITY_HIGH_ACCURACY) ?: return Result.retry(),
                                value= msr
                            )
                    ),
                    appSettings.data.first().networkMode
                )
            ){
              Result.success(workDataOf(MeasurementBase.MSR_KEY to msr))
            } else Result.failure(workDataOf(MeasurementBase.MSR_KEY to msr))

        } else Result.failure()
    }

    private suspend fun wifiWorkNewerBuilds(cm : ConnectivityManager) : Result {
        var msr = 0
        for (i in 1..5){
            try{
                msr += wifiSignalStrength(cm)
                delay(1000)
            }catch (e : IOException){
                Log.e("WifiMsrWorker", "problems reading wifi signal strength", e)
                Result.failure()
            }
        }
        msr /= 5

        return if(
            msrsRepo.postWifiMsr(
                WiFIMeasurement(
                        baseInfo = MeasurementBase(
                            tileIndex = locationProvider.tileIndexFromLocation(Priority.PRIORITY_HIGH_ACCURACY) ?: return Result.retry(),
                            value = msr,
                        )
                ),
            appSettings.data.first().networkMode
            )
        )
            Result.success(workDataOf(MeasurementBase.MSR_KEY to msr))
        else
            Result.failure(workDataOf(MeasurementBase.MSR_KEY to msr))

    }

    suspend fun wifiSignalStrength(cm : ConnectivityManager)  = suspendCancellableCoroutine { continuation ->

        val callback =  @RequiresApi(Build.VERSION_CODES.S)
        object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO){

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


}


suspend fun FlowLocationProvider.tileIndexFromLocation(priority: @Priority Int) = getCurrentLocation(priority)?.let { location ->
    MapView.getTileSystem().tileIndexFromLocation(location)
}
