package com.tpstreams.player.download

object DownloadConstants {
    const val KEY_CUSTOM_METADATA = "customMetadata"
    const val KEY_TITLE = "title"
    const val KEY_THUMBNAIL_URL = "thumbnailUrl"
    const val KEY_CALCULATED_SIZE_BYTES = "calculatedSizeBytes"

    const val FIFTEEN_DAYS_IN_SECONDS = 15L * 24 * 60 * 60

    const val BITRATE_AUDIO_LOW = 72_000
    const val BITRATE_AUDIO_HIGH = 128_000

    const val BITRATE_240P = 192_000 + BITRATE_AUDIO_LOW
    const val BITRATE_360P = 400_000 + BITRATE_AUDIO_LOW
    const val BITRATE_480P = 500_000 + BITRATE_AUDIO_HIGH
    const val BITRATE_720P = 1_000_000 + BITRATE_AUDIO_HIGH
    const val BITRATE_1080P = 2_500_000 + BITRATE_AUDIO_HIGH
} 