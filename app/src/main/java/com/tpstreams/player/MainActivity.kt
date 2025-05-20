package com.tpstreams.player

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PlayerUIViewModel by viewModels()

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SDK once
        TPStreamsPlayer.init("6332n7")

        binding.playerView.player = viewModel.player
        binding.playerView.useController = true
        binding.playerView.setFullscreenButtonState(viewModel.isFullscreen.value)
        binding.playerView.setFullscreenButtonClickListener {
            viewModel.toggleFullscreen()
        }
        observeFullscreenState()
    }

    private fun observeFullscreenState() {
        lifecycleScope.launch {
            viewModel.isFullscreen.collectLatest { isFullscreen ->

                val layoutParams = binding.playerView.layoutParams as ConstraintLayout.LayoutParams
                if (isFullscreen) {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    layoutParams.dimensionRatio = null
                    layoutParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    layoutParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                    supportActionBar?.hide()
                } else {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    supportActionBar?.show()

                    layoutParams.dimensionRatio = "16:9"
                    layoutParams.width = 0
                    layoutParams.height = 0
                }
                binding.playerView.layoutParams = layoutParams
            }
        }
    }
}