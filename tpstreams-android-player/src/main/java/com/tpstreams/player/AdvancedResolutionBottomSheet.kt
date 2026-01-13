package com.tpstreams.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AdvancedResolutionBottomSheet : BaseBottomSheet() {

    interface ResolutionSelectionListener {
        fun onResolutionSelected(resolution: String)
    }

    private var listener: ResolutionSelectionListener? = null
    private var availableResolutions: List<String> = listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
    private var selectedResolution: String = "1080p"
    private lateinit var adapter: ResolutionAdapter

    fun setResolutionSelectionListener(listener: ResolutionSelectionListener) {
        this.listener = listener
    }

    fun setAvailableResolutions(resolutions: List<String>) {
        this.availableResolutions = resolutions
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    fun setSelectedResolution(resolution: String) {
        this.selectedResolution = resolution
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.layout_advanced_resolution_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.resolutions_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        adapter = ResolutionAdapter(availableResolutions, selectedResolution) { resolution ->
            listener?.onResolutionSelected(resolution)
            dismiss()
        }
        
        recyclerView.adapter = adapter
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }

    private inner class ResolutionAdapter(
        private val resolutions: List<String>,
        private val selectedResolution: String,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<ResolutionAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val resolutionText: TextView = itemView.findViewById(R.id.resolution_text)
            val selectedIndicator: ImageView = itemView.findViewById(R.id.selected_indicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_resolution, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val resolution = resolutions[position]
            holder.resolutionText.text = resolution
            
            // Show check mark for selected resolution
            holder.selectedIndicator.visibility = if (resolution == selectedResolution) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            holder.itemView.setOnClickListener {
                onItemClick(resolution)
            }
        }

        override fun getItemCount(): Int = resolutions.size
    }

    companion object {
        const val TAG = "AdvancedResolutionBottomSheet"
    }
} 