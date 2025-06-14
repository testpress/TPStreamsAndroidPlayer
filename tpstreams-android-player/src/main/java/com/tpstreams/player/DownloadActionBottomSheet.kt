package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
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
import com.google.android.material.button.MaterialButton
import com.tpstreams.player.download.DownloadTracker
import androidx.media3.exoplayer.offline.Download
import android.widget.ImageView

class DownloadActionBottomSheet : BottomSheetDialogFragment() {

    interface DownloadActionListener {
        fun onDeleteDownloadConfirmed()
        fun onPauseDownloadConfirmed()
        fun onResumeDownloadConfirmed()
        fun onCancelDownloadConfirmed()
    }

    private var listener: DownloadActionListener? = null
    private var currentAssetId: String? = null
    private var downloadState: Int = Download.STATE_COMPLETED

    fun setDownloadActionListener(listener: DownloadActionListener) {
        this.listener = listener
    }

    fun setDownloadAssetId(assetId: String) {
        this.currentAssetId = assetId
    }

    fun setDownloadState(state: Int) {
        this.downloadState = state
        updateUI()
    }

    private fun updateUI() {
        val view = view ?: return
        
        val downloadedLayout = view.findViewById<LinearLayout>(R.id.downloaded_layout)
        val downloadingLayout = view.findViewById<LinearLayout>(R.id.downloading_layout)
        
        when (downloadState) {
            Download.STATE_COMPLETED -> {
                downloadedLayout.visibility = View.VISIBLE
                downloadingLayout.visibility = View.GONE
            }
            Download.STATE_DOWNLOADING -> {
                downloadedLayout.visibility = View.GONE
                downloadingLayout.visibility = View.VISIBLE
                
                val pauseResumeContainer = view.findViewById<LinearLayout>(R.id.pause_resume_container)
                val pauseResumeIcon = view.findViewById<ImageView>(R.id.pause_resume_icon)
                val pauseResumeText = view.findViewById<TextView>(R.id.pause_resume_text)
                
                pauseResumeIcon.setImageResource(R.drawable.ic_pause_download)
                pauseResumeText.text = getString(R.string.pause_download)
                pauseResumeContainer.setOnClickListener {
                    listener?.onPauseDownloadConfirmed()
                    dismiss()
                }
            }
            Download.STATE_STOPPED -> {
                downloadedLayout.visibility = View.GONE
                downloadingLayout.visibility = View.VISIBLE
                
                val pauseResumeContainer = view.findViewById<LinearLayout>(R.id.pause_resume_container)
                val pauseResumeIcon = view.findViewById<ImageView>(R.id.pause_resume_icon)
                val pauseResumeText = view.findViewById<TextView>(R.id.pause_resume_text)
                
                pauseResumeIcon.setImageResource(R.drawable.ic_resume_download)
                pauseResumeText.text = getString(R.string.resume_download)
                pauseResumeContainer.setOnClickListener {
                    listener?.onResumeDownloadConfirmed()
                    dismiss()
                }
            }
            else -> {
                downloadedLayout.visibility = View.GONE
                downloadingLayout.visibility = View.VISIBLE
            }
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
        return inflater.inflate(R.layout.layout_download_action_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup delete container for downloaded content
        val downloadedDeleteContainer = view.findViewById<LinearLayout>(R.id.downloaded_delete_container)
        downloadedDeleteContainer.setOnClickListener {
            listener?.onDeleteDownloadConfirmed()
            dismiss()
        }
        
        // Setup delete container for downloading content
        val deleteContainer = view.findViewById<LinearLayout>(R.id.delete_container)
        deleteContainer.setOnClickListener {
            listener?.onCancelDownloadConfirmed()
            dismiss()
        }
        
        // Initial UI update
        updateUI()
    }
    
    fun show(fragmentManager: FragmentManager) {
        if (!isAdded) {
            show(fragmentManager, TAG)
        }
    }

    companion object {
        const val TAG = "DownloadActionBottomSheet"
    }
} 