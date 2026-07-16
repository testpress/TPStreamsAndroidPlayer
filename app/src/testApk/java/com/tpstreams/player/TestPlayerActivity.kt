package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.tpstreams.player.databinding.ActivityTestPlayerBinding
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

class TestPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SentryAndroid.init(this)
        Log.d("TestPlayerActivity", "Sentry initialized, isEnabled=${io.sentry.Sentry.isEnabled()}")

        Sentry.configureScope { scope ->
            scope.setTag("testApk", "true")
        }

        TPStreamsSDK.init("9q94nm", TPStreamsSDK.Provider.TPStreams)

        binding.btnDrm.setOnClickListener {
            launchPlayer("42h2tZ5fmNf", "9327e2d0-fa13-4288-902d-840f32cd0eed", false, "drm")
        }

        binding.btnNonDrm.setOnClickListener {
            launchPlayer("4Zs4MNd5Ksj", "c4f36a4f-3859-4b24-aca8-189b7e8cfeb0", false, "non_drm")
        }

        binding.btnAes.setOnClickListener {
            launchPlayer("5fK7bSaNYxq", "6dfcb1d2-8cea-468c-b09a-fa89a4a6fcac", false, "aes")
        }

        binding.btnLogProblem.setOnClickListener {
            launchPlayer("42h2tZ5fmNf", "9327e2d0-fa13-4288-902d-840f32cd0eed", true, "drm")
        }
    }

    private fun launchPlayer(assetId: String, accessToken: String, enableDiagnostics: Boolean = false, playbackType: String = "") {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ASSET_ID, assetId)
            putExtra(MainActivity.EXTRA_ACCESS_TOKEN, accessToken)
            putExtra(EXTRA_ENABLE_DIAGNOSTICS, enableDiagnostics)
            putExtra(EXTRA_PLAYBACK_TYPE, playbackType)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_ENABLE_DIAGNOSTICS = "extra_enable_diagnostics"
        const val EXTRA_PLAYBACK_TYPE = "extra_playback_type"
    }
}
