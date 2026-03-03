package com.tpstreams.player

import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpstreams.player.databinding.ActivityDownloadBinding
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadItem

@OptIn(UnstableApi::class)
class DownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadBinding
    private lateinit var downloadClient: DownloadClient
    private lateinit var downloadsAdapter: DownloadsAdapter

    private val downloadListener = object : DownloadClient.Listener {
        override fun onDownloadsChanged() {
            updateDownloadsList()
        }

        override fun onDownloadStateChanged(downloadItem: DownloadItem, error: Exception?) {
            updateDownloadsList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Downloads"

        downloadClient = DownloadClient.getInstance(this)
        setupDownloadsList()
    }

    private fun setupDownloadsList() {
        downloadsAdapter = DownloadsAdapter(
            emptyList(),
            onActionClick = { item -> handleDownloadAction(item) },
            onPlayClick = { item -> playOfflineVideo(item) }
        )
        binding.rvDownloads.layoutManager = LinearLayoutManager(this)
        binding.rvDownloads.adapter = downloadsAdapter

        downloadClient.addListener(downloadListener)
        updateDownloadsList()
    }

    private fun playOfflineVideo(item: DownloadItem) {
        startPlayer(item.assetId, "downloaded")
    }

    private fun startPlayer(assetId: String, accessToken: String) {
        val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ASSET_ID, assetId)
            putExtra(MainActivity.EXTRA_ACCESS_TOKEN, accessToken)
        }
        startActivity(intent)
    }

    private fun updateDownloadsList() {
        val allDownloads = downloadClient.getAllDownloadItems()
        if (allDownloads.isNotEmpty()) {
            binding.rvDownloads.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            downloadsAdapter.updateItems(allDownloads)
        } else {
            binding.rvDownloads.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    private fun handleDownloadAction(item: DownloadItem) {
        when (item.state) {
            Download.STATE_COMPLETED -> downloadClient.removeDownload(item.assetId)
            Download.STATE_DOWNLOADING -> downloadClient.pauseDownload(item.assetId)
            Download.STATE_STOPPED -> downloadClient.resumeDownload(item.assetId)
            else -> downloadClient.removeDownload(item.assetId)
        }
        updateDownloadsList()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadClient.removeListener(downloadListener)
    }
}
