package com.tpstreams.player

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpstreams.player.databinding.ActivityDownloadsBinding
import com.tpstreams.player.download.DownloadItem

@OptIn(UnstableApi::class)
class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private val viewModel: DownloadViewModel by viewModels()
    private lateinit var adapter: DownloadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup RecyclerView
        adapter = DownloadAdapter()
        binding.downloadsRecyclerView.adapter = adapter
        binding.downloadsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Setup item click listener
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                checkEmptyState()
            }
        })

        // Observe downloads
        viewModel.downloads.observe(this) { downloads ->
            adapter.submitList(downloads)
            checkEmptyState()
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Setup item click listener with popup menu
        setupItemClickListener()
    }

    private fun setupItemClickListener() {
        binding.downloadsRecyclerView.addOnItemTouchListener(
            RecyclerItemClickListener(this, binding.downloadsRecyclerView,
                object : RecyclerItemClickListener.OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) {
                        val downloadItem = adapter.currentList[position]
                        showPopupMenu(view, downloadItem)
                    }

                    override fun onLongItemClick(view: View, position: Int) {
                        // Not needed for now
                    }
                })
        )
    }

    private fun showPopupMenu(view: View, downloadItem: DownloadItem) {
        val popupMenu = PopupMenu(this, view)
        
        when (downloadItem.state) {
            Download.STATE_COMPLETED -> {
                popupMenu.menu.add(getString(R.string.delete_download_title))
            }
            Download.STATE_DOWNLOADING -> {
                popupMenu.menu.add(getString(R.string.pause_download))
                popupMenu.menu.add(getString(R.string.cancel_download))
            }
            Download.STATE_STOPPED -> {
                popupMenu.menu.add(getString(R.string.resume_download))
                popupMenu.menu.add(getString(R.string.cancel_download))
            }
            else -> {
                popupMenu.menu.add(getString(R.string.cancel_download))
            }
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                getString(R.string.delete_download_title), getString(R.string.cancel_download) -> {
                    viewModel.removeDownload(downloadItem.assetId)
                }
                getString(R.string.pause_download) -> {
                    viewModel.pauseDownload(downloadItem.assetId)
                }
                getString(R.string.resume_download) -> {
                    viewModel.resumeDownload(downloadItem.assetId)
                }
            }
            true
        }

        popupMenu.show()
    }

    private fun checkEmptyState() {
        if (adapter.itemCount == 0) {
            binding.emptyView.visibility = View.VISIBLE
            binding.downloadsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.downloadsRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 