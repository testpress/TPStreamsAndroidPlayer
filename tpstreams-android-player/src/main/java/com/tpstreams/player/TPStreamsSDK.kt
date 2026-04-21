package com.tpstreams.player

import com.tpstreams.player.data.network.api.BaseApiService
import com.tpstreams.player.data.network.api.TPStreamsApiService
import com.tpstreams.player.data.network.api.TestPressApiService

object TPStreamsSDK {

    enum class Provider {
        TPStreams,
        TestPress
    }

    internal lateinit var apiService: BaseApiService
    private var _orgCode: String? = null
    internal var authToken: String? = null
        private set

    internal var orgId: String?
        get() = _orgCode
        private set(value) {
            _orgCode = value
        }

    @JvmStatic
    @JvmOverloads
    fun init(orgId: String, provider: Provider = Provider.TPStreams, authToken: String? = null) {
        require(orgId.isNotBlank()) { "orgId cannot be empty." }
        if (provider == Provider.TPStreams && authToken != null) {
            throw IllegalArgumentException("authToken must be null for TPStreams provider")
        }

        apiService = when (provider) {
            Provider.TPStreams -> TPStreamsApiService()
            Provider.TestPress -> TestPressApiService()
        }
        _orgCode = orgId
        this.authToken = authToken
    }

    internal fun getAuthHeaders(): Map<String, String> {
        return authToken?.let { mapOf("Authorization" to "JWT $it") } ?: emptyMap()
    }

    internal fun requireOrgId(): String {
        return _orgCode ?: throw IllegalStateException(
            "TPStreamsSDK.init(orgId) or TPStreamsSDK.init(orgId, Provider) must be called first."
        )
    }
}
