package com.tpstreams.player

object TestpressSDK {
    /**
     * Initialize the SDK for Testpress.
     * @param subdomain The subdomain (orgCode) for Testpress.
     * @param authToken Optional JWT token for authentication.
     */
    @JvmStatic
    @JvmOverloads
    fun init(subdomain: String, authToken: String? = null) {
        TPStreamsSDK.init(subdomain, TPStreamsSDK.Provider.TestPress, authToken)
    }
}
