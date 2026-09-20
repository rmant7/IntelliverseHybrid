package com.intelliverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.ui.unit.dp
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.SpeechConverter
import com.intelliverse.theme.IntelliverseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var speechConverter: SpeechConverter
    @Inject
    lateinit var audioPlayer: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.IO).launch {
            speechConverter.initialize() // Initialize early.
        }

        enableEdgeToEdge()
        setContent {
            IntelliverseTheme {
                MaterialTheme(
                    shapes = shapes.copy(RoundedCornerShape(16.dp))
                ) {
                    Navigation()
                }
            }
        }
    }

    override fun onDestroy() {
        // if the app definitely closed
        if (isFinishing) {
            speechConverter.shutdown()
            audioPlayer.shutdown()
        }
        super.onDestroy()
    }

}