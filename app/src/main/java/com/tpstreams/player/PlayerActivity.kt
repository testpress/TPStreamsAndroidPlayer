package com.tpstreams.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.databinding.ActivityPlayerBinding

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerUIViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val assetId = intent.getStringExtra(MainActivity.EXTRA_ASSET_ID) ?: return
        val accessToken = intent.getStringExtra(MainActivity.EXTRA_ACCESS_TOKEN) ?: return
        val isTestpress = intent.getBooleanExtra(MainActivity.EXTRA_IS_TESTPRESS, false)

        viewModel.initPlayer(assetId, accessToken, isTestpress)
        viewModel.player?.setMaxResolution(1080)
        binding.playerView.setVideoResolution(720)
        binding.playerView.player = viewModel.player


        binding.playerView.setWatermarks(
            listOf(
                WatermarkConfig(
                    text = "This is a watermark",
                    x = 0,
                    y = 50,
                    color = Color.YELLOW,
                    textSize = 18f,
                    opacity = 0.5f,
                    animation = WatermarkAnimation(
                        type = WatermarkAnimationType.PING_PONG,
                        duration = 5_000L,
                    ),
                ),
                WatermarkConfig(
                    text = "Test 1",
                    x = 100,
                    y = 100,
                    opacity = 0.2f,
                ),
                WatermarkConfig(
                    text = "Test 2",
                    x = 0,
                    y = 0,
                    opacity = 1f,
                    animation = WatermarkAnimation(type = WatermarkAnimationType.PING_PONG,duration = 10000L)
                ),
            )
        )

    }
}
