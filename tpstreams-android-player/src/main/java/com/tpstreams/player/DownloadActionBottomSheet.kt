package com.tpstreams.player

import android.app.Dialog
import android.net.Uri
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

class DownloadActionBottomSheet : BottomSheetDialogFragment() {

    interface DownloadActionListener {
        fun onDeleteDownloadConfirmed()
        fun onPauseDownloadConfirmed()
        fun onResumeDownloadConfirmed()
        fun onCancelDownloadConfirmed()
    }

    private var listener: DownloadActionListener? = null
    private var currentUri: Uri? = null
    private var downloadState: Int = Download.STATE_COMPLETED

    fun setDownloadActionListener(listener: DownloadActionListener) {
        this.listener = listener
    }

    fun setDownloadUri(uri: Uri) {
        this.currentUri = uri
    }

    fun setDownloadState(state: Int) {
        this.downloadState = state
        updateUI()
    }

    private fun updateUI() {
        val view = view ?: return
        
        val titleTextView = view.findViewById<TextView>(R.id.title_text)
        val messageTextView = view.findViewById<TextView>(R.id.message_text)
        val downloadedLayout = view.findViewById<LinearLayout>(R.id.downloaded_layout)
        val downloadingLayout = view.findViewById<LinearLayout>(R.id.downloading_layout)
        
        when (downloadState) {
            Download.STATE_COMPLETED -> {
                titleTextView.text = getString(R.string.delete_download_title)
                messageTextView.text = getString(R.string.delete_download_message)
                downloadedLayout.visibility = View.VISIBLE
                downloadingLayout.visibility = View.GONE
            }
            Download.STATE_DOWNLOADING -> {
                titleTextView.text = getString(R.string.downloading_title)
                messageTextView.text = getString(R.string.downloading_message)
                downloadedLayout.visibility = View.GONE
                downloadingLayout.visibility = View.VISIBLE
                
                val pauseResumeButton = view.findViewById<MaterialButton>(R.id.pause_resume_button)
                pauseResumeButton.text = getString(R.string.pause_download)
                pauseResumeButton.setOnClickListener {
                    listener?.onPauseDownloadConfirmed()
                    dismiss()
                }
            }
            Download.STATE_STOPPED -> {
                titleTextView.text = getString(R.string.paused_download_title)
                messageTextView.text = getString(R.string.paused_download_message)
                downloadedLayout.visibility = View.GONE
                downloadingLayout.visibility = View.VISIBLE
                
                val pauseResumeButton = view.findViewById<MaterialButton>(R.id.pause_resume_button)
                pauseResumeButton.text = getString(R.string.resume_download)
                pauseResumeButton.setOnClickListener {
                    listener?.onResumeDownloadConfirmed()
                    dismiss()
                }
            }
            else -> {
                titleTextView.text = getString(R.string.download_status_title)
                messageTextView.text = getString(R.string.download_status_message)
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
        
        // Setup delete button for downloaded content
        val deleteButton = view.findViewById<MaterialButton>(R.id.delete_button)
        deleteButton.setOnClickListener {
            listener?.onDeleteDownloadConfirmed()
            dismiss()
        }
        
        // Setup cancel button for downloading content
        val cancelButton = view.findViewById<MaterialButton>(R.id.cancel_button)
        cancelButton.setOnClickListener {
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