package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

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
        
        // Hide the estimated size text as it's not needed anymore
        downloadSizeText?.visibility = View.GONE
    }
    
    private fun updateDownloadSize() {
        val downloadSize = getDownloadSize(selectedResolution)
        downloadSizeText?.text = getString(R.string.download_size, downloadSize)
    }
    
    private fun getDownloadSize(resolution: String?): String {
        // This is just an example. In a real implementation, you would calculate the size
        // based on the video duration, bitrate, etc.
        return when(resolution) {
            "2160p" -> "1.2 GB"
            "1440p" -> "800 MB"
            "1080p" -> "500 MB"
            "720p" -> "250 MB"
            "480p" -> "150 MB"
            "360p" -> "100 MB"
            "240p" -> "60 MB"
            "144p" -> "30 MB"
            else -> "Unknown"
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