package com.example.signaldoctor.appComponents

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.signaldoctor.utils.Loggers.consoledebug
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FlowConnectivityManager @Inject constructor(private val connectivityManager: ConnectivityManager) {


    fun internetAvailabilityUpdates()  = callbackFlow<Boolean> {

        val callback = getNetworkCallbackBasedOnApi(this)
        try{
            connectivityManager.registerDefaultNetworkCallback(callback)
                    awaitCancellation()
        }finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }


    private fun getNetworkCallbackBasedOnApi(producerScope: ProducerScope<Boolean>) : NetworkCallback =
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.S) {
            object : NetworkCallback() {

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if ( networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        producerScope.trySend(true)
                    else
                        producerScope.trySend(false)
                }

                override fun onLost(network: Network) {
                    consoledebug("connection is lost")
                    producerScope.trySendBlocking(false)
                }

            }
        }
        else {
            object : NetworkCallback(0) {

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        producerScope.trySendBlocking(true)
                    else
                        producerScope.trySendBlocking(false)
                }

                override fun onLost(network: Network) {
                    producerScope.trySend(false)
                }
            }
        }

}