package com.tpstreams.player.presence

import android.content.Context
import java.util.UUID

internal interface PersistentKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

internal class SharedPreferencesKeyValueStore(context: Context) : PersistentKeyValueStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "tpstreams_presence"
    }
}

// Generated once and persisted, so the same value can be forwarded as the
// viewer_id query param when requesting playback and resent on every
// heartbeat/leave call after — same purpose as the web SDK's
// getOrCreatePersistentViewerId(), backed by SharedPreferences so it survives
// app restarts instead of localStorage.
internal class PresenceViewerIdStore(private val store: PersistentKeyValueStore) {
    constructor(context: Context) : this(SharedPreferencesKeyValueStore(context))

    fun getOrCreate(): String {
        val existing = store.getString(KEY_VIEWER_ID)
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        store.putString(KEY_VIEWER_ID, generated)
        return generated
    }

    companion object {
        private const val KEY_VIEWER_ID = "viewer_id"
    }
}
