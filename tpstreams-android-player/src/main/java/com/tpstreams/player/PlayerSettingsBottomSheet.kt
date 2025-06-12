package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerSettingsBottomSheet : BottomSheetDialogFragment() {

    interface SettingsListener {
        fun onQualitySelected()
        fun onCaptionsSelected()
        fun onPlaybackSpeedSelected()
        fun onDownloadSelected()
        fun getCurrentQuality(): String
        fun getCurrentCaptionStatus(): String
        fun getPlaybackSpeed(): Float
        fun getCurrentDownloadStatus(): String
        fun getDownloadIcon(): Int
    }

    private var listener: SettingsListener? = null

    fun setSettingsListener(listener: SettingsListener) {
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
        Log.d(TAG, "Creating bottom sheet view")
        return inflater.inflate(R.layout.layout_player_settings_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val qualityItem = view.findViewById<LinearLayout>(R.id.quality_item)
        val captionsItem = view.findViewById<LinearLayout>(R.id.captions_item)
        val playbackSpeedItem = view.findViewById<LinearLayout>(R.id.playback_speed_item)
        val downloadItem = view.findViewById<LinearLayout>(R.id.download_item)
        
        val qualityValue = view.findViewById<TextView>(R.id.quality_value)
        val captionsValue = view.findViewById<TextView>(R.id.captions_value)
        val playbackSpeedValue = view.findViewById<TextView>(R.id.playback_speed_value)
        val downloadIcon = view.findViewById<ImageView>(R.id.download_icon)
        val downloadText = view.findViewById<TextView>(R.id.download_text)
        
        // Update values from listener
        listener?.let { listener ->
            qualityValue.text = listener.getCurrentQuality()
            captionsValue.text = listener.getCurrentCaptionStatus()
            playbackSpeedValue.text = getString(R.string.playback_speed_format, listener.getPlaybackSpeed())
            
            // Update download text and icon based on download status
            downloadText.text = listener.getCurrentDownloadStatus()
            downloadIcon.setImageResource(listener.getDownloadIcon())
        }
        
        // Set click listeners
        qualityItem.setOnClickListener {
            listener?.onQualitySelected()
            dismiss()
        }
        
        captionsItem.setOnClickListener {
            listener?.onCaptionsSelected()
            dismiss()
        }
        
        playbackSpeedItem.setOnClickListener {
            listener?.onPlaybackSpeedSelected()
            dismiss()
        }
        
        downloadItem.setOnClickListener {
            listener?.onDownloadSelected()
            dismiss()
        }
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }

    companion object {
        const val TAG = "PlayerSettingsBottomSheet"
    }
} 