package com.tpstreams.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager

class QualityOptionsBottomSheet : BaseBottomSheet() {

    interface QualityOptionsListener {
        fun onAutoQualitySelected()
        fun onHigherQualitySelected()
        fun onDataSaverSelected()
        fun onAdvancedSelected()
    }

    private var listener: QualityOptionsListener? = null
    private var currentQuality: String = QUALITY_AUTO

    fun setQualityOptionsListener(listener: QualityOptionsListener) {
        this.listener = listener
    }

    fun setCurrentQuality(quality: String) {
        this.currentQuality = quality
        Log.d(TAG, "Setting current quality to: $quality")
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.layout_quality_options_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Update the title with current quality using string resource
        val titleView = view.findViewById<TextView>(R.id.quality_title)
        titleView.text = getString(R.string.quality_title_format, currentQuality)
        
        // Set up click listeners
        view.findViewById<LinearLayout>(R.id.auto_quality_option)?.setOnClickListener {
            listener?.onAutoQualitySelected()
            dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.higher_quality_option)?.setOnClickListener {
            listener?.onHigherQualitySelected()
            dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.data_saver_option)?.setOnClickListener {
            listener?.onDataSaverSelected()
            dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.advanced_quality_option)?.setOnClickListener {
            listener?.onAdvancedSelected()
            dismiss()
        }
        
        // Show the appropriate selected indicator based on current quality
        updateSelectedIndicator(view)
    }
    
    private fun updateSelectedIndicator(view: View) {
        // Hide all indicators first
        view.findViewById<View>(R.id.auto_selected_indicator)?.visibility = View.GONE
        view.findViewById<View>(R.id.higher_selected_indicator)?.visibility = View.GONE
        view.findViewById<View>(R.id.data_saver_selected_indicator)?.visibility = View.GONE
        view.findViewById<View>(R.id.advanced_selected_indicator)?.visibility = View.GONE
        
        // Log the current quality for debugging
        Log.d(TAG, "Current quality: '$currentQuality'")
        
        // Show the appropriate indicator based on current quality
        when {
            currentQuality.equals(QUALITY_AUTO, ignoreCase = true) -> {
                Log.d(TAG, "Showing Auto indicator")
                view.findViewById<View>(R.id.auto_selected_indicator)?.visibility = View.VISIBLE
            }
                
            currentQuality.equals(QUALITY_HIGHER, ignoreCase = true) -> {
                Log.d(TAG, "Showing Higher quality indicator")
                view.findViewById<View>(R.id.higher_selected_indicator)?.visibility = View.VISIBLE
            }
                
            currentQuality.equals(QUALITY_DATA_SAVER, ignoreCase = true) -> {
                Log.d(TAG, "Showing Data saver indicator")
                view.findViewById<View>(R.id.data_saver_selected_indicator)?.visibility = View.VISIBLE
            }
                
            currentQuality.equals(QUALITY_ADVANCED, ignoreCase = true) || 
            currentQuality.contains("p", ignoreCase = true) || 
            currentQuality.contains("k", ignoreCase = true) -> {
                Log.d(TAG, "Showing Advanced indicator")
                view.findViewById<View>(R.id.advanced_selected_indicator)?.visibility = View.VISIBLE
            }
            
            else -> {
                Log.d(TAG, "No matching quality found for: $currentQuality")
            }
        }
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }

    companion object {
        const val TAG = "QualityOptionsSheet"
        
        // Quality option constants
        const val QUALITY_AUTO = "Auto"
        const val QUALITY_HIGHER = "Higher picture quality"
        const val QUALITY_DATA_SAVER = "Data saver"
        const val QUALITY_ADVANCED = "Advanced"
    }
} 