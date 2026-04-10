package com.tpstreams.player.data.network.api

import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject
import java.util.Locale

class TPStreamsApiService : BaseApiService() {
    override fun assetInfoUrl(orgId: String, assetId: String, accessToken: String): String {
        return "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/?access_token=$accessToken"
    }

    override fun drmLicenseUrl(
        orgId: String,
        assetId: String,
        accessToken: String,
        download: Boolean,
        licenseDurationSeconds: Long?
    ): String {
        val baseUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/drm_license/?access_token=$accessToken"
        if (!download) return baseUrl
        val duration = licenseDurationSeconds ?: 0L
        return "$baseUrl&download=true&license_duration_seconds=$duration"
    }

    override fun parseAsset(json: JSONObject): AssetInfo {
        val assetType = json.optString("type", "video")
        val isLiveStream = assetType == "livestream"
        return if (isLiveStream && json.has("live_stream") && !json.isNull("live_stream")) {
            parseLiveStreamAssetInfo(json)
        } else {
            parseVideoAssetInfo(json)
        }
    }

    private fun parseLiveStreamAssetInfo(json: JSONObject): AssetInfo {
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
                        AssetInfo(
                            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
                            enableDrm = enableDrm,
                            thumbnailUrl = getThumbnail(videoObj),
                            videoObj = videoObj,
                            isLiveStream = false,
                            durationSeconds = videoObj.optDouble("duration", 0.0)
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
                    durationSeconds = liveStreamObj.optDouble("duration", 0.0)
                )
            }
        }
    }

    private fun parseVideoAssetInfo(json: JSONObject): AssetInfo {
        val videoObj = json.getJSONObject("video")
        val enableDrm = videoObj.optBoolean("enable_drm", false)
        return AssetInfo(
            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
            enableDrm = enableDrm,
            thumbnailUrl = getThumbnail(videoObj),
            videoObj = videoObj,
            isLiveStream = false,
            durationSeconds = videoObj.optDouble("duration", 0.0)
        )
    }

    private fun getVideoPlaybackUrl(videoObj: JSONObject, enableDrm: Boolean): String {
        val h265OutputUrl = videoObj
            .optJSONObject("output_urls")
            ?.optJSONObject("h265")

        if (h265OutputUrl != null) {
            return if (enableDrm) {
                h265OutputUrl.optString("dash_url")
            } else {
                h265OutputUrl.optString("hls_url")
            }
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
