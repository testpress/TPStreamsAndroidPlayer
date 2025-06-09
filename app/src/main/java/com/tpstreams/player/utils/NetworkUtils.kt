package com.tpstreams.player.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utility class for network-related operations
 */
object NetworkUtils {
    
    /**
     * Check if the device has an active internet connection
     * 
     * @param context Application or activity context
     * @return true if internet is available, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Check if the device is in offline mode (no internet connection)
     * 
     * @param context Application or activity context
     * @return true if device is offline, false otherwise
     */
    fun isOfflineMode(context: Context): Boolean {
        return !isNetworkAvailable(context)
    }
} 