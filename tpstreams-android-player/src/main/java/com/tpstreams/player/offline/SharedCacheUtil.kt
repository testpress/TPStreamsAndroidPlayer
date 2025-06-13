package com.tpstreams.player.offline

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Utility class to provide a singleton cache instance that can be shared
 * between the player and download components
 */
@UnstableApi
object SharedCacheUtil {
    private const val TAG = "SharedCacheUtil"
    const val DOWNLOAD_CONTENT_DIRECTORY = "tpstreams_downloads"
    
    private var cache: Cache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    
    @Synchronized
    fun getCache(context: Context): Cache {
        if (cache == null) {
            Log.d(TAG, "Creating new shared cache instance")
            
            // Create database provider if not already created
            if (databaseProvider == null) {
                databaseProvider = StandaloneDatabaseProvider(context)
            }
            
            // Create the cache directory
            val downloadDirectory = File(context.filesDir, DOWNLOAD_CONTENT_DIRECTORY)
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs()
            }
            
            // Create the cache
            try {
                Log.d(TAG, "Creating download cache at: ${downloadDirectory.absolutePath}")
                cache = SimpleCache(
                    downloadDirectory,
                    NoOpCacheEvictor(),
                    databaseProvider!!
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating cache", e)
                throw e
            }
        }
        
        return cache ?: throw IllegalStateException("Cache could not be initialized")
    }
    
    @Synchronized
    fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        
        return databaseProvider ?: throw IllegalStateException("DatabaseProvider could not be initialized")
    }
} 