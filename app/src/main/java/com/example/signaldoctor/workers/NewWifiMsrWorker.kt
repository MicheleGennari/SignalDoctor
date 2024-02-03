package com.example.signaldoctor.workers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.FlowConnectivityManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.PermissionsChecker
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.getServiceType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltWorker
class NewWifiMsrWorker @AssistedInject constructor(
    @Assisted private val ctx : Context,
    @Assisted private val params : WorkerParameters,
    appSettings: DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    flowLocationProvider : FlowLocationProvider,
    private val permissionsChecker: PermissionsChecker,
    private val appNotificationManager: AppNotificationManager
) : BaseMsrWorker<WiFIMeasurement>(ctx, params, appSettings, msrsRepo,appNotificationManager, flowLocationProvider) {

    override val displayName = "Wifi Measurement"

    override val msrType = Measure.wifi

    override val foregroundServiceTypes = getServiceType(msrType)

    private lateinit var connectivityManager : ConnectivityManager

    private lateinit var wifiManager : WifiManager

    override fun arePermissionsGranted() = permissionsChecker.isBaseMeasurementPermitted()

    override suspend fun prepare() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        else
            connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override suspend fun getSingleMeasurement() = suspendCancellableCoroutine { continuation ->


        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S){
            continuation.resume(
                wifiManager.connectionInfo.rssi
            )
        } else {
            val callback =  @RequiresApi(Build.VERSION_CODES.S)
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO){

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {

                    consoleDebug("inside onCapabilitiesChanged, signal strength is : ${(networkCapabilities.transportInfo as WifiInfo).rssi}")

                    if(continuation.isActive)
                        continuation.resume((networkCapabilities.transportInfo as WifiInfo).rssi)

                }

                override fun onLost(network: Network) {
                    if(continuation.isActive)
                        continuation.resumeWithException(MeasurementException("Wifi Connection Not Stable"))
                }

            }

            connectivityManager.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )

            continuation.invokeOnCancellation { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    override fun buildMeasurementEntity(baseInfos: MeasurementBase) = WiFIMeasurement(baseInfo = baseInfos)

    override suspend fun localUpload(m: WiFIMeasurement) = msrsRepo.postWifiMsr(m, NetworkMode.OFFLINE)


    override suspend fun onlineUpload(m: WiFIMeasurement) = msrsRepo.postWifiMsr(m, NetworkMode.ONLINE)

}