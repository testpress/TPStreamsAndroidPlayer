package com.tpstreams.player.presence

// Nothing downstream needs the hashed vid the server also mints alongside
// this — only the raw device id (PresenceViewerIdStore) that produced it,
// which the app already has, matters for resending on heartbeat/leave.
data class PresenceConfig(
    val token: String,
    val baseUrl: String
)
