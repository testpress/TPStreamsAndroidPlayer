package com.tpstreams.player.download

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationPermissionFragment : Fragment() {

    private var onGrantedCallback: (() -> Unit)? = null
    private var pendingPermissionRequest = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGrantedCallback?.invoke()
        } else {
            Toast.makeText(requireContext(), "Allow notification permission in App settings to download videos", Toast.LENGTH_LONG).show()
        }
        if (isAdded) {
            parentFragmentManager.beginTransaction().remove(this).commit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (pendingPermissionRequest) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestPermission(callback: () -> Unit) {
        onGrantedCallback = callback
        
        if (isAdded && lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pendingPermissionRequest = true
        }
    }

    companion object {
        private const val TAG = "NotificationPermissionFragment"

        fun request(activity: FragmentActivity, callback: () -> Unit) {
            val fragment = NotificationPermissionFragment()

            activity.supportFragmentManager.beginTransaction()
                .add(fragment, TAG)
                .commit()

            fragment.requestPermission(callback)
        }
    }
} 