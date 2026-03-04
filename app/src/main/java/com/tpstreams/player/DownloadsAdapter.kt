package com.tpstreams.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.RecyclerView
import com.tpstreams.player.download.DownloadItem

@OptIn(UnstableApi::class)
class DownloadsAdapter(
    private var items: List<DownloadItem>,
    private val onActionClick: (DownloadItem) -> Unit,
    private val onPlayClick: (DownloadItem) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    fun updateItems(newItems: List<DownloadItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleTextView.text = item.title
        holder.progressBar.progress = item.progressPercentage.toInt()
        
        val statusText = when (item.state) {
            Download.STATE_COMPLETED -> "Downloaded"
            Download.STATE_DOWNLOADING -> "${item.progressPercentage.toInt()}%"
            Download.STATE_STOPPED -> "Paused"
            Download.STATE_QUEUED -> "Queued"
            Download.STATE_RESTARTING -> "Restarting"
            Download.STATE_FAILED -> "Failed"
            else -> "Processing"
        }
        holder.statusTextView.text = statusText
        
        val actionText = when (item.state) {
            Download.STATE_COMPLETED -> "Delete"
            Download.STATE_DOWNLOADING -> "Pause"
            Download.STATE_STOPPED -> "Resume"
            else -> "Remove"
        }
        holder.actionButton.text = actionText
        holder.actionButton.setOnClickListener { onActionClick(item) }

        if (item.state == Download.STATE_COMPLETED) {
            holder.playButton.visibility = View.VISIBLE
            holder.playButton.setOnClickListener { onPlayClick(item) }
        } else {
            holder.playButton.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.title_text)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
        val statusTextView: TextView = view.findViewById(R.id.status_text)
        val actionButton: Button = view.findViewById(R.id.action_button)
        val playButton: Button = view.findViewById(R.id.btn_play)
    }
}
