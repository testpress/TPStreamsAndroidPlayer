package com.tpstreams.player.data.network.api

import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject

abstract class BaseApiService {
    abstract fun assetInfoUrl(orgId: String, assetId: String, accessToken: String): String

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

    open fun shieldedManifestKeyUrl(
        orgId: String,
        assetId: String,
        accessToken: String,
        clientPublicKey: String,
    ): String {
        throw UnsupportedOperationException("Shielded manifests are not supported by this provider")
    }

    abstract fun parseAsset(json: JSONObject): AssetInfo
}

