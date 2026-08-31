package com.tpstreams.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@UnstableApi
internal class ImageWatermarkController(parent: TPStreamsPlayerView) : BaseWatermarkController<ImageWatermarkConfig>(parent) {

    private var imageView: ImageView? = null
    private var loadedBitmap: Bitmap? = null
    private var currentUrl: String? = null
    private var isControlsVisible = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fetchJob: Job? = null

    override fun getTargetX(): Int = config?.x ?: 92
    override fun getTargetY(): Int = config?.y ?: 88
    override fun getTargetOpacity(): Float = config?.opacity ?: 1.0f

    override fun apply(config: ImageWatermarkConfig?) {
        super.apply(config)
        if (config == null) return

        container?.visibility = View.INVISIBLE

        container?.post {
            if (!parent.isAttachedToWindow) return@post
            reposition()
            if (loadedBitmap != null) {
                container?.visibility = if (isControlsVisible) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    override fun createViews(config: ImageWatermarkConfig) {
        val density = parent.context.resources.displayMetrics.density
        val widthPx = (config.width * density).toInt().coerceAtLeast(1)
        val heightPx = (config.height * density).toInt().coerceAtLeast(1)

        val c = FrameLayout(parent.context).apply {
            isClickable = false
            isFocusable = false
            alpha = config.opacity
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        }
        container = c

        val iv = ImageView(parent.context).apply {
            isClickable = false
            isFocusable = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        imageView = iv
        c.addView(iv)

        if (loadedBitmap != null && currentUrl == config.imageUrl) {
            iv.setImageBitmap(loadedBitmap)
        } else {
            loadImage(config.imageUrl)
        }
    }

    private fun loadImage(imageUrl: String) {
        fetchJob?.cancel()
        currentUrl = imageUrl

        fetchJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                downloadBitmap(imageUrl)
            }

            if (bitmap != null && currentUrl == imageUrl) {
                loadedBitmap = bitmap
                imageView?.setImageBitmap(bitmap)
                container?.let {
                    reposition()
                    it.visibility = if (isControlsVisible) View.INVISIBLE else View.VISIBLE
                }
            }
        }
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doInput = true
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                Log.w(TAG, "Failed to download watermark image. HTTP response code: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading watermark image from $urlString", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    override fun onControlsVisibilityChanged(visible: Boolean) {
        isControlsVisible = visible
        val c = container ?: return
        val targetOpacity = config?.opacity ?: 1.0f

        if (visible) {
            c.animate()
                .alpha(0f)
                .setDuration(FADE_DURATION_MS)
                .withEndAction {
                    if (isControlsVisible) c.visibility = View.INVISIBLE
                }
                .start()
        } else {
            c.visibility = View.VISIBLE
            c.animate()
                .alpha(targetOpacity)
                .setDuration(FADE_DURATION_MS)
                .start()
        }
    }

    override fun remove() {
        fetchJob?.cancel()
        fetchJob = null
        imageView?.setImageDrawable(null)
        imageView = null
        loadedBitmap = null
        currentUrl = null
        super.remove()
    }

    override fun destroy() {
        scope.cancel()
        super.destroy()
    }

    companion object {
        private const val TAG = "ImageWatermarkController"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
        private const val FADE_DURATION_MS = 150L
    }
}
