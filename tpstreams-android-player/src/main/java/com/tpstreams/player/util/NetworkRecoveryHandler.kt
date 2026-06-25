package com.tpstreams.player.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackException

class NetworkRecoveryHandler(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false
    private var onNetworkAvailable: (() -> Unit)? = null
    private var timeoutRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val RECOVERY_TIMEOUT_MS = 30_000L
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Defensive fallback: onCapabilitiesChanged may not fire reliably on some OEM builds.
            // Check immediately whether the network is already validated (API 23+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (isMonitoring && hasInternet && isValidated) {
                        val action = onNetworkAvailable
                        stopMonitoring()
                        action?.invoke()
                    }
                }
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (isMonitoring && hasInternet && isValidated) {
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

            // Safety timeout: if onCapabilitiesChanged is never called (OEM bug, edge case),
            // fire the recovery callback anyway so the user isn't stuck on the error overlay.
            timeoutRunnable = Runnable {
                if (isMonitoring) {
                    val action = onNetworkAvailable
                    stopMonitoring()
                    action?.invoke()
                }
            }
            mainHandler.postDelayed(timeoutRunnable!!, RECOVERY_TIMEOUT_MS)
        } catch (e: Exception) {
            isMonitoring = false
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            timeoutRunnable = null
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            // Callback was not registered, which is fine since we wanted to unregister it anyway
        } catch (e: Exception) {
            Log.w("NetworkRecoveryHandler", "Error unregistering network callback", e)
        } finally {
            isMonitoring = false
            onNetworkAvailable = null
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            timeoutRunnable = null
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
