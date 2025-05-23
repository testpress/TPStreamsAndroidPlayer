package com.tpstreams.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import androidx.media3.ui.PlayerControlView

@androidx.media3.common.util.UnstableApi
class TPStreamsPlayerControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerControlView(context, attrs, defStyleAttr) {

    private var settingsIcon: ImageButton? = null
    private var settingsListener: PlayerSettingsBottomSheet.SettingsListener? = null

    fun setSettingsListener(listener: PlayerSettingsBottomSheet.SettingsListener) {
        this.settingsListener = listener
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        
        // Find and set up the settings icon
        settingsIcon = findViewById(R.id.exo_settings_icon)
        Log.d("TPStreamsPlayerControlView", "Settings icon found: ${settingsIcon != null}")
        
        settingsIcon?.setOnClickListener {
            Log.d("TPStreamsPlayerControlView", "Settings icon clicked")
            showSettingsBottomSheet()
        }
    }

    // Make this method accessible from outside
    fun showSettingsBottomSheet() {
        val activity = getActivity()
        if (activity == null) {
            Log.e("TPStreamsPlayerControlView", "Could not find activity")
            return
        }
        
        Log.d("TPStreamsPlayerControlView", "Showing bottom sheet")
        val bottomSheet = PlayerSettingsBottomSheet()
        settingsListener?.let { bottomSheet.setSettingsListener(it) }
        bottomSheet.show(activity.supportFragmentManager, PlayerSettingsBottomSheet.TAG)
    }

    private fun getActivity(): FragmentActivity? {
        var ctx = context
        while (ctx is Context) {
            if (ctx is FragmentActivity) {
                return ctx
            }
            if (ctx is android.content.ContextWrapper) {
                ctx = ctx.baseContext
            } else {
                break
            }
        }
        return null
    }
}
