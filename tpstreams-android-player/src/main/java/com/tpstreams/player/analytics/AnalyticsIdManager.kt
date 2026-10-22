package com.tpstreams.player.analytics

import android.content.Context
import java.security.SecureRandom

internal object AnalyticsIdManager {
    private const val PREFS_NAME = "tpstreams_analytics"
    private const val KEY_VISITOR_ID = "tpstreams_visitor_id"
    const val VISITOR_ID_LENGTH = 32
    const val SESSION_ID_LENGTH = 40

    private val CHAR_POOL: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun getOrCreateVisitorId(context: Context): String {
        val prefs = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
        val existing = prefs?.getString(KEY_VISITOR_ID, null)
        if (existing != null && isValidAlphanumeric(existing, VISITOR_ID_LENGTH)) {
            return existing
        }
        val newId = generateRandomAlphanumeric(VISITOR_ID_LENGTH)
        try {
            prefs?.edit()?.putString(KEY_VISITOR_ID, newId)?.apply()
        } catch (_: Throwable) {
            // Ignored in restricted or mocked environments
        }
        return newId
    }

    fun generateSessionId(): String {
        return generateRandomAlphanumeric(SESSION_ID_LENGTH)
    }

    fun isValidAlphanumeric(value: String, expectedLength: Int): Boolean {
        if (value.length != expectedLength) return false
        return value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    }

    private fun generateRandomAlphanumeric(length: Int): String {
        val random = SecureRandom()
        return (1..length)
            .map { CHAR_POOL[random.nextInt(CHAR_POOL.size)] }
            .joinToString("")
    }
}
