package com.tpstreams.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tpstreams.player.ui.theme.TPStreamsAndroidPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the SDK once
        TPStreamsPlayer.init("6332n7")

        setContent {
            TPStreamsAndroidPlayerTheme {
                val context = LocalContext.current

                // Create the player using SDK
                val player = remember {
                    TPStreamsPlayer.create(
                        context = context,
                        assetId = "8Ky3yJ2f6ke",
                        accessToken = "acd03746-594d-4177-b1f3-044328f0cc17",
                        shouldAutoPlay = false
                    )
                }

                DisposableEffect(Unit) {
                    onDispose { player.release() }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        // Show ExoPlayer view
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16 / 9f),
                            factory = {
                                TPStreamsPlayerView(it).apply {
                                    this.player = player
                                    useController = true
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { player.play() },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}