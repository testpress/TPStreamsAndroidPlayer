package com.tpstreams.player

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.databinding.ActivityPlayerBinding

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerUIViewModel by viewModels()
    private var exitTrigger = "activity_destroy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val assetId = intent.getStringExtra(MainActivity.EXTRA_ASSET_ID) ?: return
        val accessToken = intent.getStringExtra(MainActivity.EXTRA_ACCESS_TOKEN) ?: return
        val isTestpress = intent.getBooleanExtra(MainActivity.EXTRA_IS_TESTPRESS, false)
        
        val enableDiagnostics = intent.getBooleanExtra("extra_enable_diagnostics", false)
        val playbackType = intent.getStringExtra("extra_playback_type") ?: ""

        if (enableDiagnostics) {
            com.tpstreams.player.util.FlightRecorder.clear()
            com.tpstreams.player.util.FlightRecorder.startSession(java.util.UUID.randomUUID().toString().take(6))
        }

        viewModel.initPlayer(assetId, accessToken, isTestpress)
        
        if (enableDiagnostics) {
            binding.playerView.showDiagnosticIndicator()
        }

        viewModel.player?.setMaxResolution(1080)
        binding.playerView.setVideoResolution(720)
        binding.playerView.player = viewModel.player
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitTrigger = "back_press"
                finish()
            }
        })
    }

    // Lifecycle events are logged by PlayerLifecycleManager (SDK) — no duplicate logging needed here

    override fun onDestroy() {
        val enableDiagnostics = intent.getBooleanExtra("extra_enable_diagnostics", false)
        val playbackType = intent.getStringExtra("extra_playback_type") ?: ""

        super.onDestroy()

        // Send AFTER super.onDestroy() so FlightRecorder captures ALL exit events
        // (PlayerLifecycleManager.onDestroy() + TPStreamsPlayer.release() are now logged)
        // Note: sendAndCheck() inside sendDiagnostics prevents duplicates — no isActive() gate needed
        if (enableDiagnostics) {
            (viewModel.player as? TPStreamsPlayer)?.sendDiagnostics(
                triggerReason = exitTrigger,
                playbackType = playbackType
            )
        }
    }
}
