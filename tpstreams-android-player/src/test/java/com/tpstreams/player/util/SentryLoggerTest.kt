package com.tpstreams.player.util

import android.content.Context
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Scopes
import io.sentry.SentryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SentryLoggerTest {
    @Test
    fun `player id is initialized once with eleven characters`() {
        val sentryLogger = createLogger()
        try {
            assertEquals(11, sentryLogger.playerId.length)
            assertEquals(sentryLogger.playerId, sentryLogger.playerId)
        } finally {
            sentryLogger.close()
        }
    }

    @Test
    fun `common player scope is initialized at creation`() {
        TPStreamsSDK.init("my_org_code")
        val sentryLogger = createLogger(assetId = "asset-123")
        try {
            val scope = requireNotNull(currentScopes(sentryLogger)).isolationScope

            assertEquals(sentryLogger.playerId, scope.tags["playerId"])
            assertEquals("asset-123", scope.tags["assetId"])
            assertEquals("my_org_code", scope.tags["orgCode"])
            assertEquals(BuildConfig.SDK_VERSION, scope.tags["sdkVersion"])
        } finally {
            sentryLogger.close()
        }
    }

    @Test
    fun `each logger owns independent scopes`() {
        val firstSentryLogger = createLogger()
        val secondSentryLogger = createLogger()
        try {
            val firstScopes = currentScopes(firstSentryLogger)
            val secondScopes = currentScopes(secondSentryLogger)

            assertNotNull(firstScopes)
            assertNotNull(secondScopes)
            assertNotSame(firstScopes, secondScopes)

            firstSentryLogger.addBreadcrumb(Breadcrumb("first player"))
            secondSentryLogger.addBreadcrumb(Breadcrumb("second player"))

            assertEquals(listOf("first player"), firstScopes?.isolationScope?.breadcrumbs?.map { it.message })
            assertEquals(listOf("second player"), secondScopes?.isolationScope?.breadcrumbs?.map { it.message })
        } finally {
            firstSentryLogger.close()
            secondSentryLogger.close()
        }
    }

    @Test
    fun `closing one logger does not close another logger scopes`() {
        val firstSentryLogger = createLogger()
        val secondSentryLogger = createLogger()
        try {
            firstSentryLogger.close()

            assertNull(currentScopes(firstSentryLogger))
            assertNotNull(currentScopes(secondSentryLogger))
        } finally {
            secondSentryLogger.close()
        }
    }

    @Test
    fun `helpers forward tags breadcrumbs and capture scope enrichment`() {
        val sentryLogger = createLogger()
        try {
            val scopes = requireNotNull(currentScopes(sentryLogger))
            val beforeSendCalled = CountDownLatch(2)
            val capturedTags = Collections.synchronizedSet(mutableSetOf<String>())
            val capturedNames = Collections.synchronizedSet(mutableSetOf<String>())
            val capturedFingerprints = Collections.synchronizedSet(mutableSetOf<List<String>>())
            scopes.options.setBeforeSend { event, _ ->
                event.getTag("event_tag")?.let(capturedTags::add)
                event.transaction?.let(capturedNames::add)
                event.fingerprints?.let(capturedFingerprints::add)
                beforeSendCalled.countDown()
                null
            }

            sentryLogger.addBreadcrumb(Breadcrumb("breadcrumb"))
            sentryLogger.logMessage(
                message = "message details",
                category = "Playback",
                errorDomain = "Network",
                name = "Connection failed",
                groupingKey = listOf("playback", "network", "connection"),
                level = SentryLevel.WARNING,
                tags = mapOf("event_tag" to "message_value"),
            )
            sentryLogger.logException(
                throwable = IllegalStateException("exception"),
                category = "Initializing",
                errorDomain = "HTTP",
                name = "Asset not found",
                groupingKey = listOf("asset-fetch", "http-404"),
                tags = mapOf("event_tag" to "exception_value"),
            )

            assertEquals("breadcrumb", scopes.isolationScope.breadcrumbs.last().message)
            assertTrue(beforeSendCalled.await(5, TimeUnit.SECONDS))
            assertEquals(setOf("message_value", "exception_value"), capturedTags)
            assertEquals(
                setOf(
                    "Playback: Network: Connection failed",
                    "Initializing: HTTP: Asset not found",
                ),
                capturedNames,
            )
            assertEquals(
                setOf(
                    listOf("playback", "network", "connection"),
                    listOf("asset-fetch", "http-404"),
                ),
                capturedFingerprints,
            )
        } finally {
            sentryLogger.close()
        }
    }

    @Test
    fun `log message includes orgCode tag initialized on common scope`() {
        TPStreamsSDK.init("my_org_code")
        val sentryLogger = createLogger()
        try {
            val scopes = requireNotNull(currentScopes(sentryLogger))
            val beforeSendCalled = CountDownLatch(1)
            var capturedOrgCode: String? = null
            scopes.options.setBeforeSend { event, _ ->
                capturedOrgCode = event.getTag("orgCode")
                beforeSendCalled.countDown()
                null
            }

            sentryLogger.logMessage(
                message = "test message",
                category = "Playback",
                errorDomain = "ExoPlayer",
                name = "Source error",
                groupingKey = listOf("playback", "exoplayer", "2000"),
                level = SentryLevel.INFO,
            )

            assertTrue(beforeSendCalled.await(5, TimeUnit.SECONDS))
            assertEquals("my_org_code", capturedOrgCode)
        } finally {
            sentryLogger.close()
        }
    }

    private fun currentScopes(sentryLogger: SentryLogger): Scopes? {
        val field = SentryLogger::class.java.getDeclaredField("scopes")
        field.isAccessible = true
        return field.get(sentryLogger) as Scopes?
    }

    private fun createLogger(assetId: String? = null): SentryLogger {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        return SentryLogger.create(context = context, assetId = assetId)
    }
}
