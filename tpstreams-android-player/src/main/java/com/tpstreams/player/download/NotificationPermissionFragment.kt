package com.tpstreams.player.download

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationPermissionFragment : Fragment() {

    private var onGrantedCallback: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGrantedCallback?.invoke()
        } else {
            Toast.makeText(requireContext(), "Allow notification permission in App settings to download videos", Toast.LENGTH_LONG).show()
        }
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    fun requestPermission(callback: () -> Unit) {
        onGrantedCallback = callback
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        private const val TAG = "NotificationPermissionFragment"

        fun request(activity: FragmentActivity, callback: () -> Unit) {
            val fragment = NotificationPermissionFragment()
            fragment.onGrantedCallback = callback

            activity.supportFragmentManager.beginTransaction()
                .add(fragment, TAG)
                .commitAllowingStateLoss()

            activity.supportFragmentManager.executePendingTransactions()
            fragment.requestPermission(callback)
        }
    }
} 