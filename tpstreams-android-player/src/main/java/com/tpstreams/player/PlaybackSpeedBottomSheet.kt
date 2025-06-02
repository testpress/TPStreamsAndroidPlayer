package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PlaybackSpeedBottomSheet : BottomSheetDialogFragment() {

    interface PlaybackSpeedListener {
        fun onSpeedSelected(speed: Float)
        fun getPlaybackSpeed(): Float
    }

    private var listener: PlaybackSpeedListener? = null
    private var currentSpeed: Float = 1.0f
    
    // UI components
    private lateinit var currentSpeedText: TextView
    private lateinit var speedSeekBar: SeekBar
    
    // Constants for speed range
    companion object {
        const val TAG = "PlaybackSpeedBottomSheet"
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4.0f
        const val SPEED_STEP = 0.05f
        
        // Calculate the number of steps for the SeekBar
        const val SEEKBAR_STEPS = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP).toInt()
    }
    
    fun setPlaybackSpeedListener(listener: PlaybackSpeedListener) {
        this.listener = listener
    }
    
    fun setCurrentSpeed(speed: Float) {
        this.currentSpeed = clampSpeed(speed)
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
        return inflater.inflate(R.layout.bottom_sheet_playback_speed, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Sync with player's current speed when shown
        listener?.let {
            currentSpeed = it.getPlaybackSpeed()
        }
        
        // Initialize UI components
        currentSpeedText = view.findViewById(R.id.current_speed_text)
        speedSeekBar = view.findViewById(R.id.speed_seekbar)
        val decreaseButton = view.findViewById<ImageButton>(R.id.decrease_speed_button)
        val increaseButton = view.findViewById<ImageButton>(R.id.increase_speed_button)
        
        // Set up SeekBar
        speedSeekBar.max = SEEKBAR_STEPS
        val progress = speedToProgress(currentSpeed)
        speedSeekBar.progress = progress
        
        // Update current speed text
        updateSpeedText()
        
        // Set up SeekBar listener
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                currentSpeed = progressToSpeed(progress)
                updateSpeedText()
                updatePresetButtonsState()
                if (fromUser) {
                    applySpeedChange()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                applySpeedChange()
            }
        })
        
        // Set up decrease button
        decreaseButton.setOnClickListener {
            decreaseSpeed()
            applySpeedChange()
        }
        
        // Set up increase button
        increaseButton.setOnClickListener {
            increaseSpeed()
            applySpeedChange()
        }
        
        // Get preset buttons
        val speed1xButton = view.findViewById<TextView>(R.id.speed_1x_button)
        val speed125xButton = view.findViewById<TextView>(R.id.speed_1_25x_button)
        val speed15xButton = view.findViewById<TextView>(R.id.speed_1_5x_button)
        val speed2xButton = view.findViewById<TextView>(R.id.speed_2x_button)
        val speed3xButton = view.findViewById<TextView>(R.id.speed_3x_button)
        
        // Set up preset speed buttons
        speed1xButton.setOnClickListener {
            setSpeed(1.0f)
            applySpeedChange()
        }
        
        speed125xButton.setOnClickListener {
            setSpeed(1.25f)
            applySpeedChange()
        }
        
        speed15xButton.setOnClickListener {
            setSpeed(1.5f)
            applySpeedChange()
        }
        
        speed2xButton.setOnClickListener {
            setSpeed(2.0f)
            applySpeedChange()
        }
        
        speed3xButton.setOnClickListener {
            setSpeed(3.0f)
            applySpeedChange()
        }
        
        // Update the preset buttons state
        updatePresetButtonsState()
    }
    
    // Add method to sync with current player speed
    override fun show(fragmentManager: FragmentManager, tag: String?) {
        // Get the latest speed from the player before showing
        listener?.let {
            currentSpeed = it.getPlaybackSpeed()
        }
        super.show(fragmentManager, tag)
    }
    
    private fun updateSpeedText() {
        currentSpeedText.text = String.format("%.2fx", currentSpeed)
    }
    
    private fun updatePresetButtonsState() {
        val view = view ?: return
        
        // Get preset buttons
        val speed1xButton = view.findViewById<TextView>(R.id.speed_1x_button)
        val speed125xButton = view.findViewById<TextView>(R.id.speed_1_25x_button)
        val speed15xButton = view.findViewById<TextView>(R.id.speed_1_5x_button)
        val speed2xButton = view.findViewById<TextView>(R.id.speed_2x_button)
        val speed3xButton = view.findViewById<TextView>(R.id.speed_3x_button)
        
        // Reset all buttons to default background
        speed1xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill)
        speed125xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill)
        speed15xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill)
        speed2xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill)
        speed3xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill)
        
        // Highlight the selected preset button
        when (currentSpeed) {
            1.0f -> speed1xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill_selected)
            1.25f -> speed125xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill_selected)
            1.5f -> speed15xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill_selected)
            2.0f -> speed2xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill_selected)
            3.0f -> speed3xButton.setBackgroundResource(R.drawable.bg_speed_preset_pill_selected)
        }
    }
    
    private fun setSpeed(speed: Float) {
        currentSpeed = clampSpeed(speed)
        speedSeekBar.progress = speedToProgress(currentSpeed)
        updateSpeedText()
        updatePresetButtonsState()
    }
    
    private fun decreaseSpeed() {
        val newSpeed = (currentSpeed * 100 - SPEED_STEP * 100).roundToInt() / 100f
        setSpeed(newSpeed)
    }
    
    private fun increaseSpeed() {
        val newSpeed = (currentSpeed * 100 + SPEED_STEP * 100).roundToInt() / 100f
        setSpeed(newSpeed)
    }
    
    private fun clampSpeed(speed: Float): Float {
        return max(MIN_SPEED, min(MAX_SPEED, speed))
    }
    
    private fun speedToProgress(speed: Float): Int {
        return ((speed - MIN_SPEED) / SPEED_STEP).roundToInt()
    }
    
    private fun progressToSpeed(progress: Int): Float {
        return MIN_SPEED + progress * SPEED_STEP
    }
    
    private fun applySpeedChange() {
        listener?.onSpeedSelected(currentSpeed)
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }
} 