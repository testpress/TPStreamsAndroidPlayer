package com.tpstreams.player.data.network.api

import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.data.network.model.AssetInfo
import com.tpstreams.player.presence.PresenceConfig
import org.json.JSONObject
import java.util.Locale

class TPStreamsApiService : BaseApiService() {
    override fun assetInfoUrl(orgId: String, assetId: String, accessToken: String, viewerId: String?): String {
        val baseUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/"
        val withToken = if (accessToken.isNotBlank()) "$baseUrl?access_token=$accessToken" else baseUrl
        if (viewerId.isNullOrEmpty()) return withToken
        // Without this the server mints a fresh anonymous id per request, and
        // the presence token it hands back could never pass device binding at
        // all — see PresenceViewerIdStore.
        val separator = if (withToken.contains("?")) "&" else "?"
        return "$withToken${separator}viewer_id=$viewerId"
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

        return when (liveStreamStatus.uppercase(Locale.ROOT)) {
            "NOT STARTED" -> throw LiveStreamNotStartedException("Live stream will begin soon")
            "COMPLETED" -> {
                if (json.has("video") && !json.isNull("video")) {
                    val videoObj = json.getJSONObject("video")
                    val videoStatus = videoObj.optString("status", "")

                    if (videoStatus.equals("Completed", ignoreCase = true)) {
                        val enableDrm = videoObj.optBoolean("enable_drm", false)
                        val isAes = videoObj.optString("content_protection_type").equals("aes", ignoreCase = true)
                        AssetInfo(
                            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
                            enableDrm = enableDrm,
                            thumbnailUrl = getThumbnail(videoObj),
                            videoObj = videoObj,
                            isLiveStream = false,
                            durationSeconds = videoObj.optDouble("duration", 0.0),
                            title = title,
                            isAes = isAes
                        )
                    } else {
                        throw LiveStreamEndedException("Live stream has ended")
                    }
                } else {
                    throw LiveStreamEndedException("Live stream has ended")
                }
            }

            else -> {
                val enableDrm = liveStreamObj.optBoolean("enable_drm", false)
                val mediaUrl = if (enableDrm) {
                    liveStreamObj.optString("dash_url")
                } else {
                    liveStreamObj.optString("hls_url")
                }
                AssetInfo(
                    mediaUrl = mediaUrl,
                    enableDrm = enableDrm,
                    thumbnailUrl = "",
                    videoObj = null,
                    isLiveStream = true,
                    durationSeconds = liveStreamObj.optDouble("duration", 0.0),
                    title = title,
                    presence = parsePresence(liveStreamObj.optJSONObject("presence"))
                )
            }
        }
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

    // A malformed or absent presence payload is treated as no presence at
    // all, rather than propagating a half-populated config further —
    // presence is a bolt-on feature that must never be able to break asset
    // parsing or playback.
    private fun parsePresence(presenceObj: JSONObject?): PresenceConfig? {
        if (presenceObj == null) return null
        val token = presenceObj.optString("token")
        val baseUrl = presenceObj.optString("base_url")
        if (token.isBlank() || baseUrl.isBlank()) return null
        return PresenceConfig(token = token, baseUrl = baseUrl)
    }
}
