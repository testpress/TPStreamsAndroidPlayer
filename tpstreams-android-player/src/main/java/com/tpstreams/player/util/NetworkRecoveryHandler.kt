package com.tpstreams.player.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.util.Log
import androidx.media3.common.PlaybackException

class NetworkRecoveryHandler(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false
    private var onNetworkAvailable: (() -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isMonitoring) {
                val action = onNetworkAvailable
                stopMonitoring()
                action?.invoke()
            }
        }
    }

    fun startMonitoring(action: () -> Unit) {
        if (isMonitoring) return 
        
        isMonitoring = true
        onNetworkAvailable = action
        try {
            val builder = NetworkRequest.Builder()
            connectivityManager.registerNetworkCallback(builder.build(), networkCallback)
        } catch (e: Exception) {
            isMonitoring = false
        }
    }

    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } finally {
            isMonitoring = false
            onNetworkAvailable = null
        }
    }
}

fun isNetworkError(error: PlaybackException): Boolean {
    return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
           error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
           (error.cause is java.io.IOException && error.errorCode != PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
}

fun isApiNetworkError(e: Exception): Boolean {
    return e is java.io.IOException
}
