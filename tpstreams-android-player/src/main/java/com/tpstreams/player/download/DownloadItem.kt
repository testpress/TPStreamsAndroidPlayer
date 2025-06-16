package com.tpstreams.player.download

import androidx.annotation.Keep

/**
 * Data class representing a downloadable item with metadata
 */
@Keep
data class DownloadItem(
    val assetId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val progressPercentage: Float = 0f,
    val state: Int = 0
) 