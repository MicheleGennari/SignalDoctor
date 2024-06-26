package com.example.signaldoctor.appComponents

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.example.signaldoctor.hiltModules.AppCoroutineScope
import com.example.signaldoctor.utils.Loggers.consoleDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlowConnectivityManager @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) {

    val isInternetAvailable = internetAvailabilityUpdates().stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    fun internetAvailabilityUpdates()  = callbackFlow {

        val callback = isInternetAvailableCallbackBasedOnApi()
        try{
            connectivityManager.registerDefaultNetworkCallback(callback)
                    awaitCancellation()
        }finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.conflate()


    private fun ProducerScope<Boolean>.isInternetAvailableCallbackBasedOnApi() : NetworkCallback =
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.S) {
            object : NetworkCallback() {

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if ( networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        trySend(true)
                    else
                        trySend(false)
                }

                override fun onLost(network: Network) {
                    consoleDebug("connection is lost")
                    trySend(false)
                }

            }
        }
        else {
            object : NetworkCallback(0) {

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        trySend(true)
                    else
                        trySend(false)
                }

                override fun onLost(network: Network) {
                    trySend(false)
                }
            }
        }

}