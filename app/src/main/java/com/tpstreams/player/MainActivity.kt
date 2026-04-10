package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import android.widget.Toast
import com.tpstreams.player.databinding.ActivityMainBinding
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadItem

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var downloadClient: DownloadClient

    private val downloadListener = object : DownloadClient.Listener {
        override fun onDownloadsChanged() {}
        override fun onDownloadStateChanged(downloadItem: DownloadItem, error: Exception?) {}

        override fun onDownloadStarted(downloadItem: DownloadItem) {
            Toast.makeText(this@MainActivity, "Download started: ${downloadItem.title}", Toast.LENGTH_SHORT).show()
        }

        override fun onDownloadCompleted(downloadItem: DownloadItem) {
            Toast.makeText(this@MainActivity, "Download completed: ${downloadItem.title}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SDK once

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadClient = DownloadClient.getInstance(this)
        downloadClient.addListener(downloadListener)

        updateDownloadsButtonVisibility()

        binding.btnDrmVideo.setOnClickListener {
            TPStreamsSDK.init("9q94nm")
            startPlayer("42h2tZ5fmNf", "9327e2d0-fa13-4288-902d-840f32cd0eed")
        }

        binding.btnNonDrmVideo.setOnClickListener {
            TPStreamsSDK.init("9q94nm")
            startPlayer("4Zs4MNd5Ksj", "c4f36a4f-3859-4b24-aca8-189b7e8cfeb0")
        }

        binding.btnTestpressDrm.setOnClickListener {
            TPStreamsSDK.init("lmsdemo", TPStreamsSDK.Provider.TestPress)
            startPlayer("ATJfRdHIUC9", "a4c04ca8-9c0e-4c9c-a889-bd3bf8ea586a")
        }

        binding.btnTestpressNonDrm.setOnClickListener {
            TPStreamsSDK.init("lmsdemo", TPStreamsSDK.Provider.TestPress)
            startPlayer("z1TLpfuZzXh", "5c49285b-0557-4cef-b214-66034d0b77c3")
        }

        binding.btnDownloadDrm720.setOnClickListener {
            TPStreamsSDK.init("9q94nm")
            downloadClient.startDownload(this, "7xbZeQzR36h", "3d9838f3-db51-4fc3-8472-075ab5e40b64", "480p")
        }

        binding.btnDownloadDrmSelect.setOnClickListener {
            TPStreamsSDK.init("9q94nm")
            downloadClient.startDownload(this, "37jGF3sXcHh", "df9d3cd8-cec5-41db-bfae-748233d66313")
        }

        binding.btnDownloads.setOnClickListener {
            startActivity(Intent(this, DownloadActivity::class.java))
        }
    }

    private fun updateDownloadsButtonVisibility() {}

    private fun startPlayer(assetId: String, accessToken: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(EXTRA_ASSET_ID, assetId)
            putExtra(EXTRA_ACCESS_TOKEN, accessToken)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadClient.removeListener(downloadListener)
    }

    companion object {
        const val EXTRA_ASSET_ID = "extra_asset_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}