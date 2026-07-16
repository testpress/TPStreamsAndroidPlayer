package com.tpstreams.player.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

internal class NetworkMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isRegistered = false
    
    private var currentNetworkType: String = "Unknown"

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val newType = getNetworkType(capabilities)
            if (newType != currentNetworkType) {
                FlightRecorder.log("CONNECTIVITY", "Network Changed", mapOf("old" to currentNetworkType, "new" to newType))
                currentNetworkType = newType
            }
        }

        override fun onLost(network: Network) {
            FlightRecorder.log("CONNECTIVITY", "Network Lost", mapOf("old" to currentNetworkType, "new" to "No Network"))
            currentNetworkType = "No Network"
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val newType = getNetworkType(networkCapabilities)
            if (newType != currentNetworkType) {
                FlightRecorder.log("CONNECTIVITY", "Network Changed", mapOf("old" to currentNetworkType, "new" to newType))
                currentNetworkType = newType
            }
        }
    }

    private fun getNetworkType(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "No Network"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Determine 4G/5G is complex without READ_PHONE_STATE, we'll just log Cellular
                "Cellular"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
    }

    fun start() {
        if (isRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            
            // Log initial state
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            currentNetworkType = getNetworkType(capabilities)
            FlightRecorder.log("CONNECTIVITY", "Initial Network", mapOf("type" to currentNetworkType))
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to register network callback", e)
        }
    }

    fun stop() {
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to unregister network callback", e)
        }
    }
}
