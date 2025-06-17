package com.tpstreams.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.download.DownloadItem
import com.tpstreams.player.download.DownloadTracker

@UnstableApi
class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _downloads = MutableLiveData<List<DownloadItem>>()
    val downloads: LiveData<List<DownloadItem>> = _downloads
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val downloadTracker = DownloadTracker.getInstance(application)
    private val downloadListener = object : DownloadTracker.Listener {
        override fun onDownloadsChanged() {
            loadDownloads()
        }
    }
    
    init {
        downloadTracker.addListener(downloadListener)
        loadDownloads()
    }
    
    fun loadDownloads() {
        _isLoading.value = true
        val downloadItems = downloadTracker.getAllDownloadItems()
        _downloads.value = downloadItems
        _isLoading.value = false
    }
    
    fun pauseDownload(assetId: String) {
        downloadTracker.pauseDownload(assetId)
    }
    
    fun resumeDownload(assetId: String) {
        downloadTracker.resumeDownload(assetId)
    }
    
    fun removeDownload(assetId: String) {
        downloadTracker.removeDownload(assetId)
    }
    
    override fun onCleared() {
        super.onCleared()
        downloadTracker.removeListener(downloadListener)
    }
} 