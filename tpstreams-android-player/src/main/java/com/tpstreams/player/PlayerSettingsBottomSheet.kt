package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
        fun getCurrentQuality(): String
        fun getCurrentCaptionStatus(): String
        fun getPlaybackSpeed(): Float
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
        
        Log.d(TAG, "Setting up click listeners")
        
        // Update current quality text
        val currentQualityText = view.findViewById<TextView>(R.id.current_quality_text)
        currentQualityText.text = listener?.getCurrentQuality() ?: "Auto"
        
        // Update current caption text
        val currentCaptionText = view.findViewById<TextView>(R.id.current_caption_text)
        currentCaptionText.text = listener?.getCurrentCaptionStatus() ?: "Off"
        
        // Update current playback speed text
        val currentSpeedText = view.findViewById<TextView>(R.id.current_speed_text)
        val currentSpeed = listener?.getPlaybackSpeed() ?: 1.0f
        currentSpeedText.text = String.format("%.2fx", currentSpeed)
        
        view.findViewById<LinearLayout>(R.id.quality_option)?.setOnClickListener {
            Log.d(TAG, "Quality option clicked")
            showQualityOptions()
            dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.captions_option)?.setOnClickListener {
            Log.d(TAG, "Captions option clicked")
            listener?.onCaptionsSelected()
            dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.playback_speed_option)?.setOnClickListener {
            Log.d(TAG, "Playback speed option clicked")
            listener?.onPlaybackSpeedSelected()
            dismiss()
        }
    }
    
    private fun showQualityOptions() {
        listener?.onQualitySelected()
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