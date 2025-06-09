package com.tpstreams.player

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tpstreams.player.databinding.ActivityDownloadsBinding
import com.tpstreams.player.offline.DownloadUtils
import com.tpstreams.player.utils.NetworkUtils

@UnstableApi
class DownloadsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDownloadsBinding
    private lateinit var adapter: DownloadAdapter
    private val TAG = "DownloadsActivity"
    
    // Map to track currently playing content
    private val playersMap = mutableMapOf<String, TPStreamsPlayer>()
    
    // Handler for updating download progress
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDownloadProgress()
            handler.postDelayed(this, 1000) // Update every second
        }
    }
    
    // Add this property to the DownloadsActivity class
    private val pausedDownloads = mutableMapOf<String, Boolean>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Downloads"
        
        // Set up RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        
        // Create adapter
        adapter = DownloadAdapter()
        binding.recyclerView.adapter = adapter
        
        // Load downloads
        loadDownloads()
        
        // Check network status
        if (NetworkUtils.isOfflineMode(this)) {
            Toast.makeText(this, "Offline mode - Playing downloaded videos only", Toast.LENGTH_SHORT).show()
        }
        
        // Restore saved state if available
        savedInstanceState?.let { restoreSavedState(it) }
        
        // Restore paused downloads from database
        restorePausedDownloadsState()
        
        // Start progress updates
        handler.post(updateRunnable)
    }
    
    override fun onResume() {
        super.onResume()
        loadDownloads()
        handler.post(updateRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove callbacks
        handler.removeCallbacks(updateRunnable)
        
        // Release all players
        playersMap.values.forEach { it.release() }
        playersMap.clear()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        
        // Save paused downloads state
        val pausedArray = pausedDownloads.keys.toTypedArray()
        outState.putStringArray("paused_downloads", pausedArray)
    }
    
    private fun restoreSavedState(savedInstanceState: Bundle) {
        // Restore paused downloads
        val pausedArray = savedInstanceState.getStringArray("paused_downloads")
        pausedArray?.forEach { contentId ->
            pausedDownloads[contentId] = true
        }
    }
    
    /**
     * Restore paused downloads state from the database
     */
    private fun restorePausedDownloadsState() {
        try {
            // Get all downloads from the database
            val downloads = DownloadUtils.getDownloads(this)
            
            // Check each download to see if it's paused
            downloads.forEach { download ->
                if (download.state == Download.STATE_STOPPED && download.stopReason == 1) {
                    // This download is paused, add it to the pausedDownloads map
                    pausedDownloads[download.request.id] = true
                    Log.d(TAG, "Restored paused state for download: ${download.request.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring paused downloads state: ${e.message}", e)
        }
    }
    
    private fun loadDownloads() {
        // Use DownloadUtils to get simplified download items
        val downloadItems = DownloadUtils.getDownloadItems(this)
        
        if (downloadItems.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            adapter.updateDownloads(downloadItems)
        }
    }
    
    private fun updateDownloadProgress() {
        try {
            val downloadItems = DownloadUtils.getDownloadItems(this)
            if (downloadItems.isNotEmpty()) {
                adapter.updateDownloadProgress(downloadItems)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating download progress: ${e.message}", e)
        }
    }
    
    private fun showDeleteConfirmation(downloadItem: DownloadUtils.DownloadItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Download")
            .setMessage("Are you sure you want to delete this download?")
            .setPositiveButton("Delete") { _, _ ->
                deleteDownload(downloadItem)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteDownload(downloadItem: DownloadUtils.DownloadItem) {
        // Show deletion progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Deleting")
            .setMessage("Deleting download...")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        // Stop playback if it's playing
        stopPlayback(downloadItem.contentId)
        
        // Use a background thread for deletion to avoid blocking the UI
        Thread {
            try {
                // Delete the download
                DownloadUtils.deleteDownload(this, downloadItem.contentId)
                
                // Run on UI thread to update the UI
                runOnUiThread {
                    progressDialog.dismiss()
                    
                    // Remove the item from the adapter
                    adapter.removeDownload(downloadItem.contentId)
                    
                    // Check if the list is now empty
                    if (adapter.itemCount == 0) {
                        binding.recyclerView.visibility = View.GONE
                        binding.emptyView.visibility = View.VISIBLE
                    }
                    
                    Toast.makeText(this, "Download deleted successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Handle any errors
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error deleting download: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error deleting download: ${e.message}", e)
                }
            }
        }.start()
    }
    
    private fun playDownload(contentId: String, viewHolder: DownloadAdapter.ViewHolder) {
        try {
            // First verify the download is complete and valid
            if (!DownloadUtils.verifyDownload(this, contentId)) {
                Toast.makeText(this, "Content not fully downloaded or download is invalid", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Launch PlayerActivity with the downloaded content
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CONTENT_TYPE, PlayerActivity.CONTENT_TYPE_DOWNLOAD)
                putExtra(PlayerActivity.EXTRA_CONTENT_ID, contentId)
            }
            startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing download: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Pause a download
     * @param contentId The ID of the content to pause
     */
    private fun pauseDownload(contentId: String) {
        try {
            Log.d(TAG, "Attempting to pause download for contentId: $contentId")
            
            // Call the actual pause download method from DownloadUtils
            DownloadUtils.pauseDownload(this, contentId)
            
            Toast.makeText(this, "Download paused", Toast.LENGTH_SHORT).show()
            
            // Store the paused state in a map
            pausedDownloads[contentId] = true
            Log.d(TAG, "Added contentId to pausedDownloads map: $contentId")
            
            // Force refresh the UI to show the paused state
            refreshDownloads()
            
            // Verify the download was actually paused
            val isPaused = DownloadUtils.isDownloadPaused(this, contentId)
            Log.d(TAG, "After pause operation, isDownloadPaused = $isPaused for contentId: $contentId")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing download: ${e.message}", e)
            Toast.makeText(this, "Error pausing download", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Resume a paused download
     * @param contentId The ID of the content to resume
     */
    private fun resumeDownload(contentId: String) {
        try {
            Log.d(TAG, "Attempting to resume download for contentId: $contentId")
            
            // Call the actual resume download method from DownloadUtils
            DownloadUtils.resumeDownload(this, contentId)
            
            // Remove the paused state from the map
            pausedDownloads.remove(contentId)
            Log.d(TAG, "Removed contentId from pausedDownloads map: $contentId")
            
            // Force refresh the UI to show the resumed state
            refreshDownloads()
            
            // Verify the download was actually resumed
            val isPaused = DownloadUtils.isDownloadPaused(this, contentId)
            Log.d(TAG, "After resume operation, isDownloadPaused = $isPaused for contentId: $contentId")
            
            // If the download is still paused, try restarting it
            if (isPaused) {
                Log.d(TAG, "Resume failed, attempting to restart download for contentId: $contentId")
                
                // Show a progress dialog while restarting
                val progressDialog = AlertDialog.Builder(this)
                    .setTitle("Restarting Download")
                    .setMessage("Attempting to restart download...")
                    .setCancelable(false)
                    .create()
                
                progressDialog.show()
                
                // Use a background thread for restarting
                Thread {
                    try {
                        // Restart the download
                        DownloadUtils.restartDownload(this, contentId)
                        
                        // Run on UI thread to update the UI
                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(this, "Download restarted", Toast.LENGTH_SHORT).show()
                            refreshDownloads()
                        }
                    } catch (e: Exception) {
                        // Handle any errors
                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(this, "Error restarting download: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Error restarting download: ${e.message}", e)
                        }
                    }
                }.start()
            } else {
                Toast.makeText(this, "Download resumed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming download: ${e.message}", e)
            Toast.makeText(this, "Error resuming download", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Refresh the downloads list
     */
    private fun refreshDownloads() {
        val downloads = DownloadUtils.getDownloadItems(this)
        adapter.updateDownloadProgress(downloads)
    }
    
    private fun stopPlayback(contentId: String) {
        try {
            val player = playersMap.remove(contentId) ?: return
            player.stop()
            player.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}", e)
        }
    }
    
    /**
     * RecyclerView adapter for displaying downloads
     */
    inner class DownloadAdapter : RecyclerView.Adapter<DownloadAdapter.ViewHolder>() {
        
        private var downloads: MutableList<DownloadUtils.DownloadItem> = mutableListOf()
        private val viewHolders = mutableMapOf<String, ViewHolder>()
        
        fun updateDownloads(newDownloads: List<DownloadUtils.DownloadItem>) {
            downloads.clear()
            downloads.addAll(newDownloads)
            notifyDataSetChanged()
        }
        
        fun removeDownload(contentId: String) {
            val position = downloads.indexOfFirst { it.contentId == contentId }
            if (position != -1) {
                downloads.removeAt(position)
                notifyItemRemoved(position)
                viewHolders.remove(contentId)
            }
        }
        
        fun updateDownloadProgress(updatedDownloads: List<DownloadUtils.DownloadItem>) {
            try {
                // Update progress for existing items without full refresh
                updatedDownloads.forEach { updatedItem ->
                    val existingItem = downloads.find { it.contentId == updatedItem.contentId }
                    if (existingItem != null && 
                        (existingItem.progress != updatedItem.progress || 
                         existingItem.status != updatedItem.status ||
                         existingItem.isComplete != updatedItem.isComplete)) {
                        
                        // Update the ViewHolder if it's visible
                        viewHolders[updatedItem.contentId]?.updateProgress(updatedItem)
                    }
                }
                
                // If the list has changed (items added/removed), do a full refresh
                if (downloads.map { it.contentId }.toSet() != updatedDownloads.map { it.contentId }.toSet()) {
                    downloads.clear()
                    downloads.addAll(updatedDownloads)
                    notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating download progress in adapter: ${e.message}", e)
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val downloadItem = downloads[position]
            holder.bind(downloadItem)
            viewHolders[downloadItem.contentId] = holder
        }
        
        override fun getItemCount(): Int = downloads.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
            private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
            private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
            private val playButton: MaterialButton = itemView.findViewById(R.id.playButton)
            private val pauseButton: MaterialButton = itemView.findViewById(R.id.pauseButton)
            private val resumeButton: MaterialButton = itemView.findViewById(R.id.resumeButton)
            private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
            
            private var currentContentId: String? = null
            private var currentItem: DownloadUtils.DownloadItem? = null
            
            fun bind(downloadItem: DownloadUtils.DownloadItem) {
                currentContentId = downloadItem.contentId
                currentItem = downloadItem
                
                titleTextView.text = downloadItem.contentId
                
                // Update UI based on download status
                updateProgress(downloadItem)
                
                // Set up click listeners
                playButton.setOnClickListener {
                    currentContentId?.let { contentId ->
                        playDownload(contentId, this)
                    }
                }
                
                pauseButton.setOnClickListener {
                    currentContentId?.let { contentId ->
                        pauseDownload(contentId)
                        pauseButton.visibility = View.GONE
                        resumeButton.visibility = View.VISIBLE
                    }
                }
                
                resumeButton.setOnClickListener {
                    currentContentId?.let { contentId ->
                        resumeDownload(contentId)
                        resumeButton.visibility = View.GONE
                        pauseButton.visibility = View.VISIBLE
                    }
                }
                
                deleteButton.setOnClickListener {
                    showDeleteConfirmation(downloadItem)
                }
            }
            
            fun updateProgress(downloadItem: DownloadUtils.DownloadItem) {
                currentItem = downloadItem
                
                // Show appropriate UI based on download status
                if (downloadItem.isComplete) {
                    // Download is complete - show play button, hide progress and pause/resume buttons
                    progressBar.visibility = View.GONE
                    statusTextView.text = "Downloaded"
                    
                    // Show play button, hide pause/resume buttons
                    playButton.visibility = View.VISIBLE
                    pauseButton.visibility = View.GONE
                    resumeButton.visibility = View.GONE
                    
                    Log.d(TAG, "Download complete for contentId: ${downloadItem.contentId}, showing play button")
                } else {
                    // Download is in progress - show progress and pause/resume button
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = downloadItem.progress
                    statusTextView.text = "Downloading ${downloadItem.progress}%"
                    
                    // Check if download is actually paused using the library function
                    val isPaused = DownloadUtils.isDownloadPaused(itemView.context, downloadItem.contentId)
                    Log.d(TAG, "Download in progress for contentId: ${downloadItem.contentId}, progress: ${downloadItem.progress}%, isPaused: $isPaused")
                    
                    // Show play button if complete, otherwise hide it
                    playButton.visibility = View.GONE
                    
                    // Show pause button if not paused, otherwise show resume button
                    if (isPaused) {
                        pauseButton.visibility = View.GONE
                        resumeButton.visibility = View.VISIBLE
                        statusTextView.text = "Paused ${downloadItem.progress}%"
                        Log.d(TAG, "Showing resume button for contentId: ${downloadItem.contentId}")
                    } else {
                        pauseButton.visibility = View.VISIBLE
                        resumeButton.visibility = View.GONE
                        Log.d(TAG, "Showing pause button for contentId: ${downloadItem.contentId}")
                    }
                }
            }
            
            fun updatePlaybackControls(isPlaying: Boolean) {
                // This method is only relevant for completed downloads being played
                if (currentItem?.isComplete == true) {
                    if (isPlaying) {
                        playButton.visibility = View.GONE
                        pauseButton.visibility = View.VISIBLE
                        resumeButton.visibility = View.GONE
                    } else {
                        // Check if we have a paused player for this content
                        val hasPausedPlayer = currentContentId?.let { contentId ->
                            val player = playersMap[contentId]
                            player != null && player.playbackState != Player.STATE_IDLE
                        } ?: false
                        
                        if (hasPausedPlayer) {
                            playButton.visibility = View.GONE
                            pauseButton.visibility = View.GONE
                            resumeButton.visibility = View.VISIBLE
                        } else {
                            playButton.visibility = View.VISIBLE
                            pauseButton.visibility = View.GONE
                            resumeButton.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
} 