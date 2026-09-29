package com.tpstreams.player.util

import io.sentry.Breadcrumb
import io.sentry.Scopes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TPStreamsSentryTest {
    @After
    fun tearDown() {
        while (referenceCount() > 0) {
            TPStreamsSentry.close()
        }
    }

    @Test
    fun `scopes remain active until the final owner closes them`() {
        TPStreamsSentry.init()
        val firstScopes = currentScopes()

        TPStreamsSentry.init()
        assertSame(firstScopes, currentScopes())
        assertEquals(2, referenceCount())

        TPStreamsSentry.close()
        assertSame(firstScopes, currentScopes())
        assertEquals(1, referenceCount())

        TPStreamsSentry.close()
        assertNull(currentScopes())
        assertEquals(0, referenceCount())
    }

    @Test
    fun `scopes can be initialized again after final close`() {
        TPStreamsSentry.init()
        val firstScopes = currentScopes()
        TPStreamsSentry.close()

        TPStreamsSentry.init()

        assertNotNull(currentScopes())
        assertNotSame(firstScopes, currentScopes())
    }

    @Test
    fun `helpers forward tags breadcrumbs and capture scope enrichment`() {
        TPStreamsSentry.init()
        val scopes = requireNotNull(currentScopes())
        val beforeSendCalled = CountDownLatch(2)
        val capturedTags = Collections.synchronizedSet(mutableSetOf<String>())
        scopes.options.setBeforeSend { event, _ ->
            event.getTag("event_tag")?.let(capturedTags::add)
            beforeSendCalled.countDown()
            null
        }

        TPStreamsSentry.setTag("shared_tag", "shared_value")
        TPStreamsSentry.addBreadcrumb(Breadcrumb("breadcrumb"))
        TPStreamsSentry.captureMessage("message", io.sentry.SentryLevel.WARNING) { scope ->
            scope.setTag("event_tag", "message_value")
        }
        TPStreamsSentry.captureException(IllegalStateException("exception")) { scope ->
            scope.setTag("event_tag", "exception_value")
        }

        assertEquals("shared_value", scopes.isolationScope.tags["shared_tag"])
        assertEquals("breadcrumb", scopes.isolationScope.breadcrumbs.last().message)
        assertTrue(beforeSendCalled.await(5, TimeUnit.SECONDS))
        assertEquals(setOf("message_value", "exception_value"), capturedTags)
    }

    private fun currentScopes(): Scopes? {
        val field = TPStreamsSentry::class.java.getDeclaredField("scopes")
        field.isAccessible = true
        return field.get(TPStreamsSentry) as Scopes?
    }

    private fun referenceCount(): Int {
        val field = TPStreamsSentry::class.java.getDeclaredField("referenceCount")
        field.isAccessible = true
        return field.getInt(TPStreamsSentry)
    }
}
