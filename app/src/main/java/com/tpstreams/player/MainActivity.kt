package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.databinding.ActivityMainBinding
import com.tpstreams.player.utils.NetworkUtils

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"
    
    // DRM content credentials
    private val drmContentId = "3G2p5NdMaRu" // DRM content ID
    private val drmAccessToken = "328f6f1c-c188-4c3f-8e38-345c9aaa1a51" // DRM access token
    
    // Non-DRM content credentials
    private val nonDrmContentId = "ACGhHuD7DEa" // Non-DRM content ID
    private val nonDrmAccessToken = "5bea276d-7882-4f8f-951a-c628622817e0" // Non-DRM access token

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SDK once with application context
        TPStreamsPlayer.init("9q94nm", applicationContext)

        // Set up button click listeners
        setupButtonListeners()
        
        // Check network status
        if (NetworkUtils.isOfflineMode(this)) {
            Toast.makeText(this, "You are in offline mode. Only downloaded videos can be played.", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupButtonListeners() {
        // DRM button click listener
        binding.drmButton.setOnClickListener {
            if (NetworkUtils.isOfflineMode(this)) {
                Toast.makeText(this, "Cannot play DRM content in offline mode", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CONTENT_TYPE, PlayerActivity.CONTENT_TYPE_DRM)
                putExtra(PlayerActivity.EXTRA_CONTENT_ID, drmContentId)
                putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, drmAccessToken)
            }
            startActivity(intent)
        }
        
        // Non-DRM button click listener
        binding.nonDrmButton.setOnClickListener {
            if (NetworkUtils.isOfflineMode(this)) {
                Toast.makeText(this, "Cannot play streaming content in offline mode", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CONTENT_TYPE, PlayerActivity.CONTENT_TYPE_NON_DRM)
                putExtra(PlayerActivity.EXTRA_CONTENT_ID, nonDrmContentId)
                putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, nonDrmAccessToken)
            }
            startActivity(intent)
        }
        
        // Downloads button click listener
        binding.downloadsButton.setOnClickListener {
            val intent = Intent(this, DownloadsActivity::class.java)
            startActivity(intent)
        }
    }
}