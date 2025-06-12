package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class DownloadOptionsBottomSheet : BottomSheetDialogFragment() {

    interface DownloadSelectionListener {
        fun onDownloadResolutionSelected(resolution: String)
    }

    private var listener: DownloadSelectionListener? = null
    private var availableResolutions: List<String> = emptyList()
    private lateinit var adapter: DownloadResolutionAdapter
    private var selectedResolution: String? = null
    private var downloadSizeText: TextView? = null
    private var downloadButton: MaterialButton? = null
    private var mediaItem: MediaItem? = null
    private var videoDurationMs: Long = 0
    private var trackBitrates = mutableMapOf<String, Int>()
    
    fun setMediaItem(mediaItem: MediaItem, durationMs: Long) {
        this.mediaItem = mediaItem
        this.videoDurationMs = durationMs
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    fun setDownloadSelectionListener(listener: DownloadSelectionListener) {
        this.listener = listener
    }

    fun setAvailableResolutions(resolutions: List<String>) {
        this.availableResolutions = resolutions
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
        
        // If no resolution is selected yet, select the highest quality by default
        if (selectedResolution == null && resolutions.isNotEmpty()) {
            selectedResolution = resolutions.first()
            updateDownloadSize()
        }
    }
    
    fun setTrackBitrates(bitrates: Map<String, Int>) {
        this.trackBitrates.clear()
        this.trackBitrates.putAll(bitrates)
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
        // Only call updateDownloadSize if the fragment is attached
        if (isAdded) {
            updateDownloadSize()
        }
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.layout_download_options_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.download_resolutions_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        downloadSizeText = view.findViewById(R.id.download_size_text)
        downloadButton = view.findViewById(R.id.download_button)
        
        adapter = DownloadResolutionAdapter(availableResolutions, selectedResolution) { resolution ->
            selectedResolution = resolution
            adapter.setSelectedResolution(resolution)
            updateDownloadSize()
        }
        
        recyclerView.adapter = adapter
        
        downloadButton?.setOnClickListener {
            selectedResolution?.let { resolution ->
                listener?.onDownloadResolutionSelected(resolution)
                dismiss()
            }
        }
        
        // Make size text visible to show calculated size
        downloadSizeText?.visibility = View.VISIBLE
        updateDownloadSize()
    }
    
    private fun updateDownloadSize() {
        // Only update if fragment is attached to prevent IllegalStateException
        if (!isAdded) return
        
        val downloadSize = getDownloadSize(selectedResolution)
        downloadSizeText?.text = getString(R.string.download_size, downloadSize)
    }
    
    private fun getDownloadSize(resolution: String?): String {
        if (resolution == null || videoDurationMs <= 0) return "Unknown"
        
        // Check if we have actual bitrate information from the player
        val actualBitrate = trackBitrates[resolution]
        if (actualBitrate == null) return "Unknown"
        
        // Calculate size in bytes (bitrate * duration in seconds / 8 bits per byte)
        // Add 10% for audio and other streams
        val durationSeconds = videoDurationMs / 1000.0
        val videoSizeBytes = (actualBitrate * durationSeconds / 8.0).roundToInt()
        val totalSizeBytes = (videoSizeBytes * 1.1).toLong() // Add 10% for audio
        
        return formatFileSize(totalSizeBytes)
    }
    
    private fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "Unknown"
        
        val kilobyte = 1024L
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024
        
        return when {
            sizeBytes < megabyte -> String.format("%.1f MB", sizeBytes.toFloat() / kilobyte)
            sizeBytes < gigabyte -> String.format("%.1f MB", sizeBytes.toFloat() / megabyte)
            else -> String.format("%.2f GB", sizeBytes.toFloat() / gigabyte)
        }
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }

    private inner class DownloadResolutionAdapter(
        private val resolutions: List<String>,
        private var selectedResolution: String?,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<DownloadResolutionAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val radioButton: RadioButton = itemView.findViewById(R.id.resolution_radio)
            val resolutionText: TextView = itemView.findViewById(R.id.resolution_text)
            val downloadSizeText: TextView = itemView.findViewById(R.id.download_size_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download_resolution, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val resolution = resolutions[position]
            holder.resolutionText.text = resolution
            
            // Set estimated download size
            holder.downloadSizeText.text = getDownloadSize(resolution)
            
            // Set radio button state
            holder.radioButton.isChecked = resolution == selectedResolution
            
            holder.itemView.setOnClickListener {
                onItemClick(resolution)
            }
        }
        
        fun setSelectedResolution(resolution: String) {
            val oldSelectedPosition = resolutions.indexOf(selectedResolution)
            this.selectedResolution = resolution
            val newSelectedPosition = resolutions.indexOf(resolution)
            
            if (oldSelectedPosition >= 0) {
                notifyItemChanged(oldSelectedPosition)
            }
            if (newSelectedPosition >= 0) {
                notifyItemChanged(newSelectedPosition)
            }
        }

        override fun getItemCount(): Int = resolutions.size
    }

    companion object {
        const val TAG = "DownloadOptionsBottomSheet"
    }
} 