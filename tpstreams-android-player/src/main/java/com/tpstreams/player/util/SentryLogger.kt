package com.tpstreams.player.util

import android.content.Context
import com.tpstreams.player.BuildConfig
import com.tpstreams.player.TPStreamsSDK
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Scopes
import io.sentry.SentryClient
import io.sentry.SentryLevel
import io.sentry.SentryOptions

/**
 * Generic Sentry event reporter with a scope isolated from the host application.
 *
 * Business code owns event classification and prepares any domain-specific tags and
 * contexts. This class only adds common SDK/device diagnostics and sends the event.
 */
internal class SentryLogger private constructor(
    context: Context,
    assetId: String? = null,
    val playerId: String = generatePlayerId(),
) {
    private val applicationContext = context.applicationContext
    private var scopes: Scopes? = createScopes()

    init {
        initializeCommonScope(assetId)
    }

    @Synchronized
    fun logException(
        throwable: Throwable,
        category: String,
        errorDomain: String,
        name: String,
        groupingKey: List<String>,
        tags: Map<String, String> = emptyMap(),
        contexts: Map<String, Map<String, Any>> = emptyMap(),
    ): String? {
        val eventName = buildEventName(category, errorDomain, name)
        return scopes?.captureException(throwable, ScopeCallback { scope ->
            configureEventScope(scope, eventName, category, errorDomain, groupingKey, tags, contexts)
        })?.toString()
    }

    @Synchronized
    fun logMessage(
        message: String,
        category: String,
        errorDomain: String,
        name: String,
        groupingKey: List<String>,
        level: SentryLevel = SentryLevel.WARNING,
        tags: Map<String, String> = emptyMap(),
        contexts: Map<String, Map<String, Any>> = emptyMap(),
    ): String? {
        val eventName = buildEventName(category, errorDomain, name)
        return scopes?.captureMessage(eventName, level, ScopeCallback { scope ->
            scope.setExtra("details", message)
            configureEventScope(scope, eventName, category, errorDomain, groupingKey, tags, contexts)
        })?.toString()
    }

    @Synchronized
    fun addBreadcrumb(breadcrumb: Breadcrumb) {
        scopes?.addBreadcrumb(breadcrumb)
    }

    @Synchronized
    fun close() {
        scopes?.close()
        scopes = null
    }

    private fun configureEventScope(
        scope: IScope,
        eventName: String,
        category: String,
        errorDomain: String,
        groupingKey: List<String>,
        tags: Map<String, String>,
        contexts: Map<String, Map<String, Any>>,
    ) {
        scope.setTransaction(eventName)
        scope.setTag("event_name", eventName)
        scope.setTag("error_category", category)
        scope.setTag("error_domain", errorDomain)
        scope.fingerprint = groupingKey
        tags.forEach(scope::setTag)
        contexts.forEach(scope::setContexts)
        addAutomaticContexts(scope)
    }

    private fun addAutomaticContexts(scope: IScope) {
        val nowEpochMs = System.currentTimeMillis()
        try {
            ClockDriftDiagnostics.buildSentryClockTags(nowEpochMs).forEach(scope::setTag)
            scope.setContexts("Clock Drift", ClockDriftDiagnostics.buildSentryClockContext(nowEpochMs))
        } catch (_: Exception) { /* best-effort */ }

        val context = applicationContext
        try {
            val info = StorageMemoryProvider.getStorageMemoryInfo(context)
            info.lowMemory?.let { scope.setTag("low_memory", it.toString()) }
            scope.setContexts("Storage & Memory", buildMap {
                info.availableRamMb?.let { put("available_ram_mb", it) }
                info.totalRamMb?.let { put("total_ram_mb", it) }
                info.availableStorageMb?.let { put("available_storage_mb", it) }
                info.totalStorageMb?.let { put("total_storage_mb", it) }
                info.lowMemory?.let { put("low_memory", it) }
            })
        } catch (_: Exception) { /* best-effort */ }

        try {
            val info = NetworkInfoProvider.getNetworkInfo(context)
            info.networkType?.let { scope.setTag("network_type", it) }
            info.vpnActive?.let { scope.setTag("vpn_active", it.toString()) }
            info.networkValidated?.let { scope.setTag("network_validated", it.toString()) }
            info.operatorName?.let { scope.setTag("operator_name", it) }
            scope.setContexts("Network Info", buildMap {
                info.networkType?.let { put("network_type", it) }
                info.vpnActive?.let { put("vpn_active", it) }
                info.isRoaming?.let { put("is_roaming", it) }
                info.networkValidated?.let { put("network_validated", it) }
                info.activeNetworkMetered?.let { put("active_network_metered", it) }
                info.operatorName?.let { put("operator_name", it) }
            })
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun initializeCommonScope(assetId: String?) {
        val scope = scopes?.isolationScope ?: return
        scope.setTag("sdkVersion", BuildConfig.SDK_VERSION)
        scope.setTag("playerId", playerId)
        assetId?.let { scope.setTag("assetId", it) }
        TPStreamsSDK.orgId?.let { scope.setTag("orgCode", it) }
        scope.setContexts("TPStreamsPlayer", buildMap {
            put("Player ID", playerId)
            assetId?.let { put("Asset ID", it) }
        })

        val context = applicationContext
        try {
            DeviceInfoProvider.getTags(context).forEach(scope::setTag)
            scope.setContexts("Device Info", DeviceInfoProvider.getContext(context))
        } catch (_: Exception) { /* best-effort */ }

        try {
            AppInfoProvider.getHostAppVersion(context)?.let { scope.setTag("client_app", it) }
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun createScopes(): Scopes {
        val options = SentryOptions().apply {
            dsn = DSN
            release = "TPStreamsAndroidPlayer@${BuildConfig.SDK_VERSION}"
        }
        val globalScope = Scope(options).apply { bindClient(SentryClient(options)) }
        return Scopes(Scope(options), Scope(options), globalScope, CREATOR)
    }

    companion object {
        private const val DSN = "https://1a888cef4d504918b5b506f9b1decef7@sentry.testpress.in/23"
        private const val CREATOR = "TPStreamsPlayer.init"

        fun create(
            context: Context,
            assetId: String? = null,
            playerId: String = generatePlayerId(),
        ): SentryLogger = SentryLogger(context, assetId, playerId)

        private fun generatePlayerId(): String = (1..11)
            .map { (('a'..'z') + ('0'..'9')).random() }
            .joinToString("")

        private fun buildEventName(category: String, errorDomain: String, name: String): String =
            "${category.trim()}: ${errorDomain.trim()}: ${name.trim()}"
    }
}
