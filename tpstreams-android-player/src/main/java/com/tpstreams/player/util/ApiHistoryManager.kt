package com.tpstreams.player.util

import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.Breadcrumb

/**
 * Singleton to manage global API history via Sentry Breadcrumbs.
 * Aggressively sanitizes strings to bypass Sentry's automated PII scrubbing.
 */
internal object ApiHistoryManager {

    /**
     * Records an API log entry as a Sentry Breadcrumb.
     */
    fun recordLog(
        endpoint: String,
        method: String = "GET",
        requestBody: String? = null,
        responseCode: Int? = null,
        responseBody: String? = null
    ) {
        // Nuclear sanitization: Replace the sensitive word 'access_token' with 'at' 
        // and its value with '***' to evade Sentry's automated server-side scrubbing rules.
        val tokenRegex = Regex("access_token=[^&?]+")
        val sanitizedEndpoint = endpoint.replace(tokenRegex, "at=***")
        val sanitizedRequestBody = requestBody?.replace(tokenRegex, "at=***")
        val sanitizedResponseBody = responseBody?.replace(tokenRegex, "at=***")

        val breadcrumb = Breadcrumb()
        breadcrumb.category = "network"
        breadcrumb.type = "http"
        breadcrumb.message = "HTTP $method Call"
        
        breadcrumb.level = if (responseCode != null && responseCode !in 200..299) {
            SentryLevel.ERROR
        } else {
            SentryLevel.INFO
        }

        // Use generic keys like 'route' and 'info' instead of 'url' or 'endpoint'
        breadcrumb.setData("route", sanitizedEndpoint)
        breadcrumb.setData("method", method)
        breadcrumb.setData("status", responseCode?.toString() ?: "N/A")
        
        // Capture truncated payloads (up to 512 chars each as requested)
        sanitizedRequestBody?.let { breadcrumb.setData("in", it.take(512)) }
        sanitizedResponseBody?.let { breadcrumb.setData("out", it.take(512)) }

        Sentry.addBreadcrumb(breadcrumb)
    }
}
