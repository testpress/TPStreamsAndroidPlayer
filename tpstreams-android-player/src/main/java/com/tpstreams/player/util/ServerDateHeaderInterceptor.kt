package com.tpstreams.player.util

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Records the server-provided "Date" header so we can estimate device↔server time drift later.
 */
internal class ServerDateHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        ClockDriftDiagnostics.recordServerDateHeader(response.header("Date"))
        return response
    }
}

