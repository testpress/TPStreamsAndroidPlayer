package com.tpstreams.player.ui

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.R
import com.tpstreams.player.constants.NetworkDiagnostics
import com.tpstreams.player.constants.PlaybackError

/**
 * Manages the error overlay, diagnostic list rendering, retry indicators, and state transitions for a player view.
 */
@UnstableApi
internal class PlayerErrorViewController(
    private val containerView: FrameLayout,
) {

    private val context: Context get() = containerView.context

    private var errorOverlay: View? = null
    private var errorTextView: TextView? = null
    private var errorDescription: TextView? = null
    private var diagnosticsContainer: LinearLayout? = null
    private var errorSubtitle: TextView? = null
    private var errorDivider: View? = null
    private var retryLoader: View? = null
    private var retryIndicator: TextView? = null

    fun ensureErrorOverlaySetup() {
        if (errorOverlay != null) return

        try {
            val overlay = LayoutInflater.from(context)
                .inflate(R.layout.error_overlay, containerView, false)

            overlay.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            containerView.addView(overlay, containerView.childCount)
            overlay.bringToFront()

            errorOverlay = overlay
            errorTextView = overlay.findViewById(R.id.error_message_text)
            errorDescription = overlay.findViewById(R.id.error_description)
            errorSubtitle = overlay.findViewById(R.id.error_subtitle)
            diagnosticsContainer = overlay.findViewById(R.id.diagnostics_container)
            retryLoader = overlay.findViewById(R.id.retry_loader)
            retryIndicator = overlay.findViewById(R.id.retry_indicator)
            errorDivider = overlay.findViewById(R.id.error_divider)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup error overlay", e)
        }
    }

    fun showErrorMessage(message: String) {
        ensureErrorOverlaySetup()

        val overlay = errorOverlay ?: return
        val textView = errorTextView ?: return

        overlay.visibility = View.VISIBLE
        overlay.bringToFront()

        // Isolate from network diagnostics UI
        textView.visibility = View.VISIBLE
        errorSubtitle?.visibility = View.GONE
        errorDescription?.visibility = View.GONE
        diagnosticsContainer?.visibility = View.GONE
        errorDivider?.visibility = View.GONE
        retryLoader?.visibility = View.GONE

        measureOverlay()

        if (isDecoderError(message)) {
            setHtmlText(textView, message)
        } else {
            textView.text = message
        }
    }

    fun showDiagnosingState() {
        ensureErrorOverlaySetup()
        val overlay = errorOverlay ?: return

        overlay.visibility = View.VISIBLE
        overlay.bringToFront()

        errorTextView?.visibility = View.GONE
        errorDescription?.let {
            it.visibility = View.VISIBLE
            it.text = context.getString(R.string.network_diag_diagnosing)
        }
        errorSubtitle?.visibility = View.GONE
        diagnosticsContainer?.visibility = View.GONE
        errorDivider?.visibility = View.GONE

        retryLoader?.visibility = View.VISIBLE
        retryIndicator?.text = context.getString(R.string.network_diag_checking_connection)

        measureOverlay()
    }

    fun showNetworkDiagnostics(error: PlaybackError, diagnostics: NetworkDiagnostics) {
        ensureErrorOverlaySetup()
        val overlay = errorOverlay ?: return

        overlay.visibility = View.VISIBLE
        overlay.bringToFront()

        errorTextView?.visibility = View.GONE
        retryLoader?.visibility = View.GONE

        resolveDiagnosticText(error, diagnostics)
        buildDiagnosticsList(diagnostics)
        errorDivider?.visibility = View.VISIBLE

        errorSubtitle?.let {
            val id = diagnostics.playerId
            it.visibility = if (id != null) View.VISIBLE else View.GONE
            it.text = if (id != null) context.getString(R.string.network_diag_player_id, id) else ""
        }
        if (diagnostics.retryAttempt > 0) {
            retryLoader?.visibility = View.VISIBLE
            retryIndicator?.text = (1..diagnostics.maxRetries).joinToString(" ") { i ->
                if (i <= diagnostics.retryAttempt) "\u25CF" else "\u25CB"
            }
        }

        measureOverlay()
    }

    fun hideErrorMessage() {
        errorOverlay?.visibility = View.GONE
    }

    fun onParentLayout(left: Int, top: Int, right: Int, bottom: Int) {
        errorOverlay?.let { overlay ->
            if (overlay.visibility == View.VISIBLE && (overlay.width == 0 || overlay.height == 0)) {
                val overlayWidth = right - left
                val overlayHeight = bottom - top
                if (overlayWidth > 0 && overlayHeight > 0) {
                    overlay.measure(
                        View.MeasureSpec.makeMeasureSpec(overlayWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(overlayHeight, View.MeasureSpec.EXACTLY)
                    )
                    overlay.layout(0, 0, overlayWidth, overlayHeight)
                }
            }
        }
    }

    private fun resolveDiagnosticText(error: PlaybackError, diagnostics: NetworkDiagnostics) {
        val text = when {
            error == PlaybackError.VIDEO_SERVICE_BLOCKED -> {
                val isDnsFailure = !diagnostics.dnsResolves && diagnostics.internetReachable
                val isCdnProbeFailed = diagnostics.cdnReachable == false
                when {
                    isDnsFailure -> context.getString(R.string.network_diag_dns_failure)
                    isCdnProbeFailed -> context.getString(R.string.network_diag_cdn_blocked)
                    else -> context.getString(R.string.network_diag_generic_blocked)
                }
            }
            error == PlaybackError.UNSPECIFIED -> context.getString(R.string.network_diag_unknown_error)
            diagnostics.proxyConfigured -> context.getString(R.string.network_diag_proxy_unreachable)
            else -> context.getString(R.string.network_diag_no_internet)
        }
        errorDescription?.let {
            it.visibility = View.VISIBLE
            it.text = text
        }
    }

    private fun buildDiagnosticsList(diagnostics: NetworkDiagnostics) {
        val container = diagnosticsContainer ?: return
        container.visibility = View.VISIBLE
        container.removeAllViews()

        data class DiagItem(val label: String, val ok: Boolean?, val detail: String?)

        val items = mutableListOf(
            DiagItem(
                context.getString(R.string.network_diag_label_internet), diagnostics.internetReachable,
                diagnostics.internetLatencyMs?.let { "${it}ms" }
            ),
            DiagItem(
                context.getString(R.string.network_diag_label_video_server), diagnostics.serverReachable,
                diagnostics.serverDetail ?: diagnostics.serverLatencyMs?.let { "${it}ms" }
            ),
            DiagItem(context.getString(R.string.network_diag_label_dns), diagnostics.dnsResolves, null),
            DiagItem(
                context.getString(R.string.network_diag_label_cdn), if (diagnostics.cdnHostname == null) null else diagnostics.cdnReachable,
                if (diagnostics.cdnHostname == null) "\u2014" else diagnostics.cdnDetail ?: diagnostics.cdnLatencyMs?.let { "${it}ms" }
            )
        )
        if (diagnostics.proxyConfigured) {
            items.add(DiagItem(context.getString(R.string.network_diag_label_proxy), null, null))
        }

        items.forEach { item ->
            val color = when (item.ok) {
                true -> ContextCompat.getColor(context, R.color.network_diag_ok)
                false -> ContextCompat.getColor(context, R.color.network_diag_fail)
                null -> ContextCompat.getColor(context, R.color.network_diag_unknown)
            }
            val symbol = when (item.ok) {
                true -> "\u2713"
                false -> "\u2717"
                null -> "\u2014"
            }
            val detailText = if (item.detail != null) " \u00B7 ${item.detail}" else ""

            val fullText = "$symbol  ${item.label}$detailText"
            val detailStart = fullText.length - detailText.length

            val spannable = SpannableString(fullText)
            spannable.setSpan(
                ForegroundColorSpan(color),
                0, fullText.length - detailText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (detailText.isNotEmpty()) {
                spannable.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.network_diag_detail)
                    ),
                    detailStart, fullText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    AbsoluteSizeSpan(12, true),
                    detailStart, fullText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            TextView(context).apply {
                text = spannable
                textSize = 12f
                setPadding(0, 3, 0, 3)
                container.addView(this)
            }
        }
    }

    private fun measureOverlay() {
        val overlay = errorOverlay ?: return
        containerView.post {
            val parentWidth = containerView.width
            val parentHeight = containerView.height
            if (parentWidth > 0 && parentHeight > 0) {
                overlay.measure(
                    View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.EXACTLY)
                )
                overlay.layout(0, 0, parentWidth, parentHeight)
            } else {
                overlay.requestLayout()
            }
            overlay.invalidate()
        }
    }

    private fun isDecoderError(message: String): Boolean {
        return listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED.toString(),
            PlaybackException.ERROR_CODE_DECODING_FAILED.toString()
        ).any { message.contains(it) }
    }

    private fun setHtmlText(textView: TextView, message: String) {
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(message)
        }
    }

    private companion object {
        private const val TAG = "PlayerErrorViewController"
    }
}
