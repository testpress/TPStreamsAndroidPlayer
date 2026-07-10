package com.tpstreams.player.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.tpstreams.player.data.NetworkInfo

/**
 * Stateless provider of network connectivity information.
 *
 * All fields are permission-free except operatorName (requires READ_PHONE_STATE).
 * Values are collected fresh on every call.
 *
 * Internal info methods should be preferred when both tags and context are needed —
 * use [getNetworkInfo] once and build maps from the result.
 */
internal object NetworkInfoProvider {

    /**
     * Returns the raw network info for the given [context].
     * Use this to avoid redundant system queries when building both tags and context.
     */
    internal fun getNetworkInfo(context: Context): NetworkInfo {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) return NetworkInfo()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // API 23+: modern NetworkCapabilities-based API
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

                val networkType = capabilities?.let { classifyNetworkType(it) }
                val vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
                val isRoamingRaw = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                val isRoaming = when (isRoamingRaw) {
                    null -> null   // unknown
                    true -> false  // NOT_ROAMING present → not roaming
                    false -> true  // NOT_ROAMING absent → is roaming
                }
                val networkValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isMetered = connectivityManager.isActiveNetworkMetered
                val operatorName = getOperatorName(context)

                NetworkInfo(
                    networkType = networkType,
                    vpnActive = vpnActive,
                    isRoaming = isRoaming,
                    networkValidated = networkValidated,
                    activeNetworkMetered = isMetered,
                    operatorName = operatorName
                )
            } else {
                // API 21-22: fallback using deprecated activeNetworkInfo
                @Suppress("DEPRECATION")
                val activeInfo = connectivityManager.activeNetworkInfo
                val networkType = when (activeInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "WIFI"
                    ConnectivityManager.TYPE_MOBILE -> "CELLULAR"
                    ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                    else -> "UNKNOWN"
                }
                NetworkInfo(
                    networkType = networkType,
                    isRoaming = activeInfo?.isRoaming,
                    operatorName = getOperatorName(context)
                )
            }
        } catch (_: Exception) {
            NetworkInfo()
        }
    }

    private fun classifyNetworkType(capabilities: NetworkCapabilities): String {
        // VPN first — network_type answers "what kind of connection is this?"
        // vpn_active separately confirms VPN overlay.
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            else -> "UNKNOWN"
        }
    }

    private fun getOperatorName(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
            }
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkOperatorName?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
