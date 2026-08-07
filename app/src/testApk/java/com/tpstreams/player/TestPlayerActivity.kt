package com.tpstreams.player

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tpstreams.player.databinding.ActivityTestPlayerBinding
import io.sentry.Sentry

class TestPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Sentry.configureScope { scope ->
            scope.setTag("is_test_apk", "true")
        }

        TPStreamsSDK.init("9q94nm", TPStreamsSDK.Provider.TPStreams)

        binding.btnDrm.setOnClickListener {
            launchPlayer("42h2tZ5fmNf", "9327e2d0-fa13-4288-902d-840f32cd0eed")
        }

        binding.btnNonDrm.setOnClickListener {
            launchPlayer("4Zs4MNd5Ksj", "c4f36a4f-3859-4b24-aca8-189b7e8cfeb0")
        }

        binding.btnAes.setOnClickListener {
            launchPlayer("5fK7bSaNYxq", "6dfcb1d2-8cea-468c-b09a-fa89a4a6fcac")
        }

        binding.btnCustom.setOnClickListener {
            showCustomInputDialog()
        }
    }

    private fun showCustomInputDialog() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        val orgIdInput = EditText(this).apply {
            hint = "Organization ID"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val assetIdInput = EditText(this).apply {
            hint = "Asset ID"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val accessTokenInput = EditText(this).apply {
            hint = "Access Token"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(orgIdInput)
        container.addView(assetIdInput)
        container.addView(accessTokenInput)

        AlertDialog.Builder(this)
            .setTitle("Play custom video")
            .setView(container)
            .setPositiveButton("Play") { _, _ ->
                val orgId = orgIdInput.text.toString().trim()
                val assetId = assetIdInput.text.toString().trim()
                val accessToken = accessTokenInput.text.toString().trim()
                if (orgId.isEmpty() || assetId.isEmpty() || accessToken.isEmpty()) {
                    Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                TPStreamsSDK.init(orgId)
                launchPlayer(assetId, accessToken)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchPlayer(assetId: String, accessToken: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ASSET_ID, assetId)
            putExtra(MainActivity.EXTRA_ACCESS_TOKEN, accessToken)
        }
        startActivity(intent)
    }
}
