package com.tpstreams.player

object TPStreamsSDK {
    internal var orgId: String? = null

    @JvmStatic
    fun init(orgId: String) {
        this.orgId = orgId
    }
} 