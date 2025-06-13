package com.tpstreams.player

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.databinding.ActivityPlayerBinding
import com.tpstreams.player.offline.DownloadUtils
import com.tpstreams.player.utils.NetworkUtils

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: TPStreamsPlayer? = null
    private val TAG = "PlayerActivity"

    companion object {
        const val EXTRA_CONTENT_TYPE = "content_type"
        const val EXTRA_CONTENT_ID = "content_id"
        const val EXTRA_ACCESS_TOKEN = "access_token"
        
        const val CONTENT_TYPE_DRM = "drm"
        const val CONTENT_TYPE_NON_DRM = "non_drm"
        const val CONTENT_TYPE_DOWNLOAD = "download"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SDK
        TPStreamsPlayer.init("9q94nm", applicationContext)

        // Get content type and details from intent
        val contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: ""
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID) ?: ""
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""

        // Set up back button with appropriate text
        if (contentType == CONTENT_TYPE_DOWNLOAD) {
            binding.backButton.text = "Back to Downloads"
        } else {
            binding.backButton.text = "Back to Home"
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }

        // Check network status for streaming content
        if ((contentType == CONTENT_TYPE_DRM || contentType == CONTENT_TYPE_NON_DRM) && 
            NetworkUtils.isOfflineMode(this)) {
            Toast.makeText(this, "Cannot play streaming content in offline mode", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize player based on content type
        when (contentType) {
            CONTENT_TYPE_DRM -> {
                binding.contentTitle.text = "DRM Protected Content"
                initializeStreamingPlayer(contentId, accessToken)
            }
            CONTENT_TYPE_NON_DRM -> {
                binding.contentTitle.text = "Non-DRM Content"
                initializeStreamingPlayer(contentId, accessToken)
            }
            CONTENT_TYPE_DOWNLOAD -> {
                binding.contentTitle.text = "Downloaded Content"
                initializeOfflinePlayer(contentId)
            }
            else -> {
                Toast.makeText(this, "Invalid content type", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initializeStreamingPlayer(contentId: String, accessToken: String) {
        try {
            // Check for internet connectivity
            if (!NetworkUtils.isNetworkAvailable(this)) {
                Toast.makeText(this, "No internet connection available. Cannot play streaming content.", Toast.LENGTH_LONG).show()
                binding.contentTitle.text = "${binding.contentTitle.text} (No Internet)"
                return
            }
            
            player = TPStreamsPlayer.create(
                context = applicationContext,
                assetId = contentId,
                accessToken = accessToken,
                shouldAutoPlay = true
            )

            setupPlayerView()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing streaming player: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeOfflinePlayer(contentId: String) {
        try {
            // First verify the download is complete and valid
            if (!DownloadUtils.verifyDownload(this, contentId)) {
                Toast.makeText(this, "Content not fully downloaded or download is invalid", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            player = TPStreamsPlayer.create(
                this,
                contentId,
                "", // No access token needed for offline playback
                false // Don't auto-play, we'll control it manually
            )

            val success = DownloadUtils.playOfflineContent(player!!, contentId)
            if (success) {
                player?.play()
                setupPlayerView()
            } else {
                Toast.makeText(this, "Failed to play downloaded content", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing offline content: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupPlayerView() {
        binding.playerView.player = player
        binding.playerView.showController()

        // Add error listener
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}")
                Toast.makeText(
                    this@PlayerActivity,
                    "Playback error: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
} 