package com.tpstreams.player.data.network.api

import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject
import java.util.Locale

class TPStreamsApiService : BaseApiService() {
    override fun assetInfoUrl(orgId: String, assetId: String, accessToken: String): String {
        val baseUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/"
        return if (accessToken.isNotBlank()) "$baseUrl?access_token=$accessToken" else baseUrl
    }

    override fun drmLicenseUrl(
        orgId: String,
        assetId: String,
        accessToken: String,
        download: Boolean,
        licenseDurationSeconds: Long?
    ): String {
        val baseUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/drm_license/"
        val withToken = if (accessToken.isNotBlank()) "$baseUrl?access_token=$accessToken" else baseUrl
        if (!download) return withToken
        val duration = licenseDurationSeconds ?: 0L
        val separator = if (withToken.contains("?")) "&" else "?"
        return "$withToken${separator}download=true&license_duration_seconds=$duration"
    }

    override fun parseAsset(json: JSONObject): AssetInfo {
        val title = json.optString("title", "Undefined")
        val assetType = json.optString("type", "video")
        val isLiveStream = assetType == "livestream"
        return if (isLiveStream && json.has("live_stream") && !json.isNull("live_stream")) {
            parseLiveStreamAssetInfo(json, title)
        } else {
            parseVideoAssetInfo(json, title)
        }
    }

    private fun parseLiveStreamAssetInfo(json: JSONObject, title: String): AssetInfo {
        val liveStreamObj = json.getJSONObject("live_stream")
        val liveStreamStatus = liveStreamObj.optString("status", "")
        val transcodeRecordedVideo = liveStreamObj.optBoolean("transcodeRecordedVideo", true)

        return when (liveStreamStatus.uppercase(Locale.ROOT)) {
            "NOT STARTED" -> throw LiveStreamNotStartedException("Live stream will begin soon")
            "COMPLETED" -> {
                val recordedInfo = createRecordedAssetInfoIfReady(json, title)
                if (!transcodeRecordedVideo && recordedInfo == null) {
                    createLiveStreamAssetInfo(liveStreamObj, title)
                } else if (recordedInfo != null) {
                    recordedInfo
                } else {
                    createLiveStreamAssetInfo(liveStreamObj, title)
                }
            }

            else -> {
                createLiveStreamAssetInfo(liveStreamObj, title)
            }
        }
    }

    private fun createRecordedAssetInfoIfReady(json: JSONObject, title: String): AssetInfo? {
        if (!json.has("video") || json.isNull("video")) return null

        val videoObj = json.getJSONObject("video")
        if (!videoObj.optString("status").equals("Completed", ignoreCase = true)) return null

        val enableDrm = videoObj.optBoolean("enable_drm", false)
        val mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm)
        if (mediaUrl.isEmpty()) return null

        val isAes = videoObj.optString("content_protection_type").equals("aes", ignoreCase = true)
        return AssetInfo(
            mediaUrl = mediaUrl,
            enableDrm = enableDrm,
            thumbnailUrl = getThumbnail(videoObj),
            videoObj = videoObj,
            isLiveStream = false,
            durationSeconds = videoObj.optDouble("duration", 0.0),
            title = title,
            isAes = isAes
        )
    }

    private fun createLiveStreamAssetInfo(liveStreamObj: JSONObject, title: String): AssetInfo {
        val enableDrm = liveStreamObj.optBoolean("enable_drm", false)
        val mediaUrl = if (enableDrm) {
            liveStreamObj.optString("dash_url")
        } else {
            liveStreamObj.optString("hls_url")
        }
        if (mediaUrl.isEmpty()) {
            throw LiveStreamEndedException("Live stream has ended")
        }
        return AssetInfo(
            mediaUrl = mediaUrl,
            enableDrm = enableDrm,
            thumbnailUrl = "",
            videoObj = null,
            isLiveStream = true,
            durationSeconds = liveStreamObj.optDouble("duration", 0.0),
            title = title
        )
    }

    private fun parseVideoAssetInfo(json: JSONObject, title: String): AssetInfo {
        val videoObj = json.getJSONObject("video")
        val enableDrm = videoObj.optBoolean("enable_drm", false)
        val isAes = videoObj.optString("content_protection_type").equals("aes", ignoreCase = true)
        return AssetInfo(
            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
            enableDrm = enableDrm,
            thumbnailUrl = getThumbnail(videoObj),
            videoObj = videoObj,
            isLiveStream = false,
            durationSeconds = videoObj.optDouble("duration", 0.0),
            title = title,
            isAes = isAes
        )
    }

    private fun getVideoPlaybackUrl(videoObj: JSONObject, enableDrm: Boolean): String {
        val h265OutputUrl = videoObj
            .optJSONObject("output_urls")
            ?.optJSONObject("h265")

        if (h265OutputUrl != null) {
            val h265Url = if (enableDrm) {
                h265OutputUrl.optString("dash_url")
            } else {
                h265OutputUrl.optString("hls_url")
            }
            if (h265Url.isNotEmpty()) return h265Url
        }

        return if (enableDrm) {
            videoObj.optString("dash_url")
        } else {
            videoObj.optString("playback_url")
        }
    }

    private fun getThumbnail(videoObj: JSONObject): String {
        return videoObj.optString("preview_thumbnail_url")
            .ifEmpty { videoObj.optJSONArray("thumbnails")?.optString(0) ?: "" }
    }
}
