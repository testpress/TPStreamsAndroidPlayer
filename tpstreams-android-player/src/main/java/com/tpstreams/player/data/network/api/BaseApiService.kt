package com.tpstreams.player.data.network.api

import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject

abstract class BaseApiService {
    // viewerId is only ever meaningful for TPStreamsApiService: it lets the
    // response carry a presence token bound to this device (see
    // PresenceViewerIdStore). TestPressApiService has no presence/live-viewer-
    // count support, so it accepts and ignores the parameter rather than
    // threading it into a URL format that has nothing to do with it.
    abstract fun assetInfoUrl(orgId: String, assetId: String, accessToken: String, viewerId: String? = null): String

    abstract fun drmLicenseUrl(
        orgId: String,
        assetId: String,
        accessToken: String,
        download: Boolean = false,
        licenseDurationSeconds: Long? = null
    ): String

    open fun tokenValidationUrl(orgId: String, assetId: String, accessToken: String): String {
        return assetInfoUrl(orgId, assetId, accessToken)
    }

    abstract fun parseAsset(json: JSONObject): AssetInfo
}
