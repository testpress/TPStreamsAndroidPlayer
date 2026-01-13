package com.tpstreams.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.download.DownloadClient
import androidx.media3.exoplayer.offline.Download
import android.widget.ImageView

@UnstableApi
class DownloadActionBottomSheet : BaseBottomSheet() {

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