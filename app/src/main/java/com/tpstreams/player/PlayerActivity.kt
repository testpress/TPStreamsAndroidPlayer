package com.tpstreams.player

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

        viewModel.initPlayer(assetId, accessToken)
        binding.playerView.player = viewModel.player
    }
}
