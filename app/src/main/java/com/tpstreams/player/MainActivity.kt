package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Default TPStreams init
        TPStreamsSDK.init("9q94nm", TPStreamsSDK.Provider.TPStreams)

        // Single DRM video button
        findViewById<View>(R.id.btn_drm_video).setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ASSET_ID, "42h2tZ5fmNf")
                putExtra(EXTRA_ACCESS_TOKEN, "9327e2d0-fa13-4288-902d-840f32cd0eed")
            }
            startActivity(intent)
        }
    }

    companion object {
        const val EXTRA_ASSET_ID = "extra_asset_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_IS_TESTPRESS = "extra_is_testpress"
    }
}
