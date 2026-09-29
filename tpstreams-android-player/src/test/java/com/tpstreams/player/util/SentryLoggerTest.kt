package com.tpstreams.player.util

import io.sentry.Breadcrumb
import io.sentry.Scopes
import io.sentry.SentryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SentryLoggerTest {
    @Test
    fun `each logger owns independent scopes`() {
        val firstSentryLogger = SentryLogger.create()
        val secondSentryLogger = SentryLogger.create()
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
        val firstSentryLogger = SentryLogger.create()
        val secondSentryLogger = SentryLogger.create()
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
        val sentryLogger = SentryLogger.create()
        try {
            val scopes = requireNotNull(currentScopes(sentryLogger))
            val beforeSendCalled = CountDownLatch(2)
            val capturedTags = Collections.synchronizedSet(mutableSetOf<String>())
            scopes.options.setBeforeSend { event, _ ->
                event.getTag("event_tag")?.let(capturedTags::add)
                beforeSendCalled.countDown()
                null
            }

            sentryLogger.setTag("shared_tag", "shared_value")
            sentryLogger.addBreadcrumb(Breadcrumb("breadcrumb"))
            sentryLogger.captureMessage("message", SentryLevel.WARNING) { scope ->
                scope.setTag("event_tag", "message_value")
            }
            sentryLogger.captureException(IllegalStateException("exception")) { scope ->
                scope.setTag("event_tag", "exception_value")
            }

            assertEquals("shared_value", scopes.isolationScope.tags["shared_tag"])
            assertEquals("breadcrumb", scopes.isolationScope.breadcrumbs.last().message)
            assertTrue(beforeSendCalled.await(5, TimeUnit.SECONDS))
            assertEquals(setOf("message_value", "exception_value"), capturedTags)
        } finally {
            sentryLogger.close()
        }
    }

    private fun currentScopes(sentryLogger: SentryLogger): Scopes? {
        val field = SentryLogger::class.java.getDeclaredField("scopes")
        field.isAccessible = true
        return field.get(sentryLogger) as Scopes?
    }
}
