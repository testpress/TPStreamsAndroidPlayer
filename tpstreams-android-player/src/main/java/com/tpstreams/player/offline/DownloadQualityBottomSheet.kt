package com.tpstreams.player.offline

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tpstreams.player.R

class DownloadQualityBottomSheet : BottomSheetDialogFragment() {

    interface DownloadQualityListener {
        fun onDownloadQualitySelected(videoUrl: String, contentId: String, quality: String)
        fun getAvailableQualities(): List<String>
        fun getVideoUrl(): String
        fun getContentId(): String
    }

    private var listener: DownloadQualityListener? = null

    fun setDownloadQualityListener(listener: DownloadQualityListener) {
        this.listener = listener
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
        return inflater.inflate(R.layout.layout_download_quality_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "Setting up download quality bottom sheet")
        
        val titleText = view.findViewById<TextView>(R.id.title_text)
        titleText.text = "Select Download Quality"
        
        val radioGroup = view.findViewById<RadioGroup>(R.id.quality_radio_group)
        radioGroup.removeAllViews()
        
        // Get available qualities from listener
        val qualities = listener?.getAvailableQualities() ?: listOf("Auto")
        Log.d(TAG, "Available qualities: $qualities")
        
        // Add radio buttons for each quality
        qualities.forEachIndexed { index, quality ->
            val radioButton = RadioButton(context).apply {
                id = View.generateViewId()
                text = quality
                textSize = 16f
                setPadding(0, 24, 0, 24)
            }
            radioGroup.addView(radioButton)
            
            // Select the first option by default
            if (index == 0) {
                radioButton.isChecked = true
            }
        }
        
        // Add apply button click listener
        view.findViewById<View>(R.id.apply_button).setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val selectedRadioButton = view.findViewById<RadioButton>(selectedId)
            val selectedQuality = selectedRadioButton?.text?.toString() ?: "Auto"
            
            val videoUrl = listener?.getVideoUrl() ?: ""
            val contentId = listener?.getContentId() ?: ""
            
            Log.d(TAG, "Selected quality: $selectedQuality for video: $videoUrl, contentId: $contentId")
            
            if (videoUrl.isNotEmpty() && contentId.isNotEmpty()) {
                // Start download directly with selected quality
                TPStreamsPlayerDownloadExt.startDownload(
                    context = requireContext(),
                    videoUrl = videoUrl,
                    contentId = contentId,
                    selectedQuality = selectedQuality
                )
                
                listener?.onDownloadQualitySelected(videoUrl, contentId, selectedQuality)
                dismiss()
            } else {
                Log.e(TAG, "Cannot start download: videoUrl or contentId is empty")
            }
        }
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }

    companion object {
        const val TAG = "DownloadQualityBottomSheet"
    }
} 