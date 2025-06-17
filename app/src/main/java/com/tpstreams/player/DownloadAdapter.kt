package com.tpstreams.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tpstreams.player.download.DownloadItem

class DownloadAdapter : ListAdapter<DownloadItem, DownloadAdapter.DownloadViewHolder>(DownloadDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.title_text)
        private val assetIdText: TextView = itemView.findViewById(R.id.asset_id_text)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.download_progress)
        private val progressText: TextView = itemView.findViewById(R.id.progress_text)
        private val stateText: TextView = itemView.findViewById(R.id.state_text)

        fun bind(downloadItem: DownloadItem) {
            titleText.text = downloadItem.title
            assetIdText.text = "Asset ID: ${downloadItem.assetId}"
            
            // Set progress
            val progress = downloadItem.progressPercentage.toInt()
            progressBar.progress = progress
            
            // Show only percentage
            progressText.text = "$progress%"
            
            // Set state text
            stateText.text = getStateString(downloadItem.state)
        }
        

        
        private fun getStateString(state: Int): String {
            return when (state) {
                Download.STATE_COMPLETED -> "COMPLETED"
                Download.STATE_DOWNLOADING -> "DOWNLOADING"
                Download.STATE_FAILED -> "FAILED"
                Download.STATE_QUEUED -> "QUEUED"
                Download.STATE_REMOVING -> "REMOVING"
                Download.STATE_RESTARTING -> "RESTARTING"
                Download.STATE_STOPPED -> "PAUSED"
                else -> "UNKNOWN"
            }
        }
    }
}

class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
    override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
        return oldItem.assetId == newItem.assetId
    }

    override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
        return oldItem == newItem
    }
} 