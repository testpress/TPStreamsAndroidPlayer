package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.sentry.Sentry

class TestPlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_player)

        Sentry.configureScope { scope ->
            scope.setTag("is_test_apk", "true")
        }

        TPStreamsSDK.init("9q94nm", TPStreamsSDK.Provider.TPStreams)

        findViewById<android.view.View>(R.id.btn_drm).setOnClickListener {
            launchPlayer("42h2tZ5fmNf", "9327e2d0-fa13-4288-902d-840f32cd0eed")
        }

        findViewById<android.view.View>(R.id.btn_non_drm).setOnClickListener {
            launchPlayer("4Zs4MNd5Ksj", "c4f36a4f-3859-4b24-aca8-189b7e8cfeb0")
        }

        findViewById<android.view.View>(R.id.btn_aes).setOnClickListener {
            launchPlayer("5fK7bSaNYxq", "6dfcb1d2-8cea-468c-b09a-fa89a4a6fcac")
        }
    }

    private fun launchPlayer(assetId: String, accessToken: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ASSET_ID, assetId)
            putExtra(MainActivity.EXTRA_ACCESS_TOKEN, accessToken)
        }
        startActivity(intent)
    }
}
