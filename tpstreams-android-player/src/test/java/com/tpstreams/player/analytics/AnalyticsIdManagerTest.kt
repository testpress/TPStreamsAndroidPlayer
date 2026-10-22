package com.tpstreams.player.analytics

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AnalyticsIdManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val memoryPrefs = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        sharedPrefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)
        memoryPrefs.clear()

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        `when`(sharedPrefs.edit()).thenReturn(editor)
        `when`(sharedPrefs.getString(anyString(), org.mockito.ArgumentMatchers.nullable(String::class.java)))
            .thenAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val default = invocation.getArgument<String?>(1)
                memoryPrefs[key] ?: default
            }
        `when`(editor.putString(anyString(), anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<String>(1)
            memoryPrefs[key] = value
            editor
        }
    }

    @Test
    fun `generateSessionId produces exactly 40 alphanumeric characters`() {
        val sessionId = AnalyticsIdManager.generateSessionId()
        assertNotNull(sessionId)
        assertEquals(40, sessionId.length)
        assertTrue(AnalyticsIdManager.isValidAlphanumeric(sessionId, 40))
    }

    @Test
    fun `getOrCreateVisitorId generates and persists 32 alphanumeric characters`() {
        val visitorId1 = AnalyticsIdManager.getOrCreateVisitorId(context)
        assertEquals(32, visitorId1.length)
        assertTrue(AnalyticsIdManager.isValidAlphanumeric(visitorId1, 32))

        // Subsequent call returns the existing visitor ID
        val visitorId2 = AnalyticsIdManager.getOrCreateVisitorId(context)
        assertEquals(visitorId1, visitorId2)
    }

    @Test
    fun `isValidAlphanumeric correctly validates strings`() {
        assertTrue(AnalyticsIdManager.isValidAlphanumeric("aB1234567890abcdef123456789012ab", 32))
        assertFalse(AnalyticsIdManager.isValidAlphanumeric("short", 32))
        assertFalse(AnalyticsIdManager.isValidAlphanumeric("aB1234567890abcdef1234567890ab!", 32))
        assertFalse(AnalyticsIdManager.isValidAlphanumeric("aB1234567890-bcdef1234567890ab", 32))
    }
}
