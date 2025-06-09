package com.tpstreams.player.offline

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpstreams.player.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class DownloadListActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: DownloadAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_list)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Downloads"
        
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        
        adapter = DownloadAdapter(emptyList()) { download ->
            showDeleteConfirmation(download)
        }
        recyclerView.adapter = adapter
        
        // Initialize download manager
        TPStreamsPlayerDownloadExt.initializeDownloadManager(this)
        
        // Load downloads
        loadDownloads()
    }
    
    override fun onResume() {
        super.onResume()
        loadDownloads()
    }
    
    private fun loadDownloads() {
        val downloads = TPStreamsPlayerDownloadExt.getDownloads(this)
        
        if (downloads.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            adapter.updateDownloads(downloads)
        }
    }
    
    private fun showDeleteConfirmation(download: Download) {
        AlertDialog.Builder(this)
            .setTitle("Delete Download")
            .setMessage("Are you sure you want to delete this download?")
            .setPositiveButton("Delete") { _, _ ->
                TPStreamsPlayerDownloadExt.removeDownload(this, download.request.id)
                Toast.makeText(this, "Download deleted", Toast.LENGTH_SHORT).show()
                loadDownloads()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    inner class DownloadAdapter(
        private var downloads: List<Download>,
        private val onDeleteClick: (Download) -> Unit
    ) : RecyclerView.Adapter<DownloadAdapter.ViewHolder>() {
        
        fun updateDownloads(newDownloads: List<Download>) {
            downloads = newDownloads
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val download = downloads[position]
            holder.bind(download)
        }
        
        override fun getItemCount(): Int = downloads.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
            private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
            private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
            private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
            
            fun bind(download: Download) {
                titleTextView.text = download.request.id
                
                val status = when (download.state) {
                    Download.STATE_COMPLETED -> "Completed"
                    Download.STATE_DOWNLOADING -> "Downloading ${download.percentDownloaded.toInt()}%"
                    Download.STATE_FAILED -> "Failed"
                    Download.STATE_QUEUED -> "Queued"
                    Download.STATE_REMOVING -> "Removing"
                    Download.STATE_RESTARTING -> "Restarting"
                    Download.STATE_STOPPED -> "Stopped"
                    else -> "Unknown"
                }
                
                statusTextView.text = status
                
                if (download.state == Download.STATE_DOWNLOADING) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = download.percentDownloaded.toInt()
                } else {
                    progressBar.visibility = if (download.state == Download.STATE_COMPLETED) View.GONE else View.VISIBLE
                    progressBar.progress = 0
                }
                
                deleteButton.setOnClickListener {
                    onDeleteClick(download)
                }
            }
        }
    }
} 