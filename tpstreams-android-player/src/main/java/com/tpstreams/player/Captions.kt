package com.tpstreams.player

import android.util.Log
import java.util.Locale

class Captions(private val view: TPStreamsPlayerView) {
    private var currentCaptionLanguage: String? = null
    private var availableCaptions: List<Pair<String, String>> = emptyList()

    fun showCaptionsBottomSheet() {
        val activity = view.getActivity() ?: return
        view.captionsBottomSheet.show(activity.supportFragmentManager)
    }

    fun updateAvailableCaptions() {
        val tpsPlayer = view.getPlayer() ?: return
        
        val textTracks = tpsPlayer.getAvailableTextTracks()
        
        if (textTracks.isNotEmpty()) {
            availableCaptions = textTracks
            view.captionsBottomSheet.setAvailableCaptions(textTracks)
            
            val activeTrack = tpsPlayer.getActiveTextTrack()
            if (activeTrack != null) {
                currentCaptionLanguage = activeTrack.first
                view.captionsBottomSheet.setCurrentLanguage(activeTrack.first)
            }
        } else {
            availableCaptions = emptyList()
            view.captionsBottomSheet.setAvailableCaptions(emptyList())
        }
    }
    
    fun setCurrentCaptionLanguage(language: String?) {
        if (this.currentCaptionLanguage == language) {
            return
        }
        
        this.currentCaptionLanguage = language
        view.captionsBottomSheet.setCurrentLanguage(language)
        
        view.getPlayer()?.setTextTrackByLanguage(language)
        
        val activity = view.getActivity()
        if (activity != null && view.settingsBottomSheet.isAdded) {
            view.settingsBottomSheet.dismiss()
            view.settingsBottomSheet.show(activity.supportFragmentManager)
        }
    }

    fun onCaptionsDisabled() {
        setCurrentCaptionLanguage(null)
    }
    
    fun onCaptionLanguageSelected(language: String) {
        setCurrentCaptionLanguage(language)
    }
    
    fun getCurrentCaptionLanguage(): String? {
        return currentCaptionLanguage
    }
    
    fun getCurrentCaptionStatus(): String {
        val tpsPlayer = view.getPlayer()
        val activeTrack = tpsPlayer?.getActiveTextTrack()
        
        if (activeTrack != null) {
            return getLanguageName(activeTrack.first)
        }

        return if (currentCaptionLanguage == null) {
            "Off"
        } else {
            getLanguageName(currentCaptionLanguage!!)
        }
    }
    
    private fun getLanguageName(languageCode: String): String {
        try {
            val locale = Locale(languageCode)
            val displayLanguage = locale.getDisplayLanguage(Locale.ENGLISH)
            
            if (displayLanguage.equals(languageCode, ignoreCase = true) || displayLanguage.isEmpty()) {
                return languageCode.replaceFirstChar { it.uppercase() }
            }
            
            return displayLanguage
        } catch (e: Exception) {
            Log.e("Captions", "Error getting language name for $languageCode", e)
            return languageCode.replaceFirstChar { it.uppercase() }
        }
    }
} 