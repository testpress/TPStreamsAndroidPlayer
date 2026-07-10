package com.tpstreams.player.util

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.os.Build
import com.tpstreams.player.data.StorageMemoryInfo

/**
 * Stateless provider of runtime storage and memory information.
 *
 * Values are collected fresh on every call. Callers should be aware this does
 * filesystem I/O (StatFs) and should not be called on the critical UI thread
 * on slow devices.
 *
 * Tags: available_ram_mb, total_ram_mb, available_storage_mb, low_memory
 * Context: all StorageMemoryInfo fields
 */
internal object StorageMemoryProvider {

    private const val BYTES_TO_MB = 1_048_576L

    /** Returns tags for Sentry events (low cardinality only). */
    fun getTags(context: Context? = null): Map<String, String> {
        if (context == null) return emptyMap()
        val info = getStorageMemoryInfo(context)
        return buildMap {
            info.lowMemory?.let { put("low_memory", it.toString()) }
        }
    }

    /** Returns the full storage/memory context. */
    fun getContext(context: Context? = null): Map<String, Any> {
        if (context == null) return emptyMap()
        val info = getStorageMemoryInfo(context)
        return buildMap {
            info.availableRamMb?.let { put("available_ram_mb", it) }
            info.totalRamMb?.let { put("total_ram_mb", it) }
            info.availableStorageMb?.let { put("available_storage_mb", it) }
            info.totalStorageMb?.let { put("total_storage_mb", it) }
            info.lowMemory?.let { put("low_memory", it) }
        }
    }

    private fun getStorageMemoryInfo(context: Context): StorageMemoryInfo {
        val memInfo = getMemoryInfo(context) ?: return StorageMemoryInfo(
            availableStorageMb = getAvailableStorage(context),
            totalStorageMb = getTotalStorage(context)
        )
        return StorageMemoryInfo(
            availableRamMb = memInfo.availMem / BYTES_TO_MB,
            totalRamMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                memInfo.totalMem / BYTES_TO_MB
            } else null,
            lowMemory = memInfo.lowMemory,
            availableStorageMb = getAvailableStorage(context),
            totalStorageMb = getTotalStorage(context)
        )
    }

    private fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo? = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        memInfo
    } catch (_: Exception) {
        null
    }

    private fun getAvailableStorage(context: Context): Long? = try {
        val dir = context.filesDir ?: return null
        val stat = StatFs(dir.absolutePath)
        stat.availableBytes / BYTES_TO_MB
    } catch (_: Exception) {
        null
    }

    private fun getTotalStorage(context: Context): Long? = try {
        val dir = context.filesDir ?: return null
        val stat = StatFs(dir.absolutePath)
        stat.totalBytes / BYTES_TO_MB
    } catch (_: Exception) {
        null
    }
}
