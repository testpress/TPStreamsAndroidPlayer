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

    internal var orgId: String?
        get() = _orgCode
        set(value) {
            _orgCode = value
        }

    @JvmStatic
    @JvmOverloads
    fun init(orgId: String, provider: Provider = Provider.TPStreams) {
        require(orgId.isNotBlank()) { "orgId cannot be empty." }
        apiService = when (provider) {
            Provider.TPStreams -> TPStreamsApiService()
            Provider.TestPress -> TestPressApiService()
        }
        _orgCode = orgId
    }

    internal fun requireOrgId(): String {
        return _orgCode ?: throw IllegalStateException(
            "TPStreamsSDK.init(orgId) or TPStreamsSDK.init(orgId, Provider) must be called first."
        )
    }
}
