package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tpstreams.player.download.DownloadClient
import com.tpstreams.player.download.DownloadItem
import com.tpstreams.player.TestpressSDK

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_main)

        downloadClient = DownloadClient.getInstance(this)
        
        // Default initialization
        initTPStreams()

        setupBottomNavigation()
        setupDownloadsList()
        setupButtons()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tpstreams -> {
                    showLayout(R.id.layout_tpstreams)
                    initTPStreams()
                    true
                }
                R.id.nav_testpress -> {
                    showLayout(R.id.layout_testpress)
                    initTestpress()
                    true
                }
                R.id.nav_downloads -> {
                    showLayout(R.id.layout_downloads)
                    updateDownloadsList()
                    true
                }
                else -> false
            }
        }
    }

    private fun showLayout(layoutId: Int) {
        findViewById<View>(R.id.layout_tpstreams).visibility = if (layoutId == R.id.layout_tpstreams) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layout_testpress).visibility = if (layoutId == R.id.layout_testpress) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layout_downloads).visibility = if (layoutId == R.id.layout_downloads) View.VISIBLE else View.GONE
    }

    private fun initTPStreams() {
        TPStreamsSDK.init("9q94nm", TPStreamsSDK.Provider.TPStreams)
    }

    private fun initTestpress() {
        TestpressSDK.init("lmsdemo")
    }

    private fun setupDownloadsList() {
        val rvDownloads = findViewById<RecyclerView>(R.id.rv_downloads)
        downloadsAdapter = DownloadsAdapter(
            emptyList(),
            onActionClick = { item -> handleDownloadAction(item) },
            onPlayClick = { item -> playOfflineVideo(item) }
        )
        rvDownloads.layoutManager = LinearLayoutManager(this)
        rvDownloads.adapter = downloadsAdapter

        downloadClient.addListener(downloadListener)
        updateDownloadsList()
    }

    private fun updateDownloadsList() {
        val rvDownloads = findViewById<RecyclerView>(R.id.rv_downloads)
        val tvEmpty = findViewById<TextView>(R.id.tv_empty_downloads)
        
        val allDownloads = downloadClient.getAllDownloadItems()
        if (allDownloads.isNotEmpty()) {
            rvDownloads.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            downloadsAdapter.updateItems(allDownloads)
        } else {
            rvDownloads.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
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

    private fun playOfflineVideo(item: DownloadItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(EXTRA_ASSET_ID, item.assetId)
            putExtra(EXTRA_ACCESS_TOKEN, "downloaded")
        }
        startActivity(intent)
    }

    private fun setupButtons() {
        // TPStreams Tab Buttons
        findViewById<View>(R.id.btn_drm_video).setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ASSET_ID, "42h2tZ5fmNf")
                putExtra(EXTRA_ACCESS_TOKEN, "9327e2d0-fa13-4288-902d-840f32cd0eed")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_non_drm_video).setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ASSET_ID, "4Zs4MNd5Ksj")
                putExtra(EXTRA_ACCESS_TOKEN, "c4f36a4f-3859-4b24-aca8-189b7e8cfeb0")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_download_drm_720).setOnClickListener {
            DownloadClient.getInstance(this).startDownload(
                this,
                "42h2tZ5fmNf",
                "9327e2d0-fa13-4288-902d-840f32cd0eed",
                "720"
            )
        }

        findViewById<View>(R.id.btn_download_drm_select).setOnClickListener {
            DownloadClient.getInstance(this).startDownload(
                this,
                "42h2tZ5fmNf",
                "9327e2d0-fa13-4288-902d-840f32cd0eed"
            )
        }

        // Testpress Tab Buttons
        findViewById<View>(R.id.btn_testpress_drm).setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ASSET_ID, "ATJfRdHIUC9")
                putExtra(EXTRA_ACCESS_TOKEN, "a4c04ca8-9c0e-4c9c-a889-bd3bf8ea586a")
                putExtra(EXTRA_IS_TESTPRESS, true)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_testpress_non_drm).setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ASSET_ID, "z1TLpfuZzXh")
                putExtra(EXTRA_ACCESS_TOKEN, "5c49285b-0557-4cef-b214-66034d0b77c3")
                putExtra(EXTRA_IS_TESTPRESS, true)
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadClient.removeListener(downloadListener)
    }

    companion object {
        const val EXTRA_ASSET_ID = "extra_asset_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_IS_TESTPRESS = "extra_is_testpress"
    }
}