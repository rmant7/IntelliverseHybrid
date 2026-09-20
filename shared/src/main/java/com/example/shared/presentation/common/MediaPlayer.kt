package com.example.shared.presentation.common

import androidx.annotation.OptIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.shared.R.drawable
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.AudioPlayer.Companion.playbackSpeeds
import com.example.shared.presentation.common.button.RoundIconButton
import com.example.shared.presentation.screens.output.result.BaseResultViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayer(
    viewModel: BaseResultViewModel,
    filePath: String? = null,
    audioPlayer: AudioPlayer,
    isEnabled: Boolean
) {

    val isPlaying = audioPlayer.isPlaying.collectAsState()
    val timeStampSlider = remember { mutableFloatStateOf(audioPlayer.getTimeStamp()) }

    val currentSpeedIndex = viewModel.currentSpeedIndex.collectAsState()

    if (filePath == null) {
        audioPlayer.setPlaying(false)
        timeStampSlider.floatValue = 0f
    }

    LaunchedEffect(Unit) {
        audioPlayer.onPlaybackEnded = {
            timeStampSlider.floatValue = 0f // Reset slider
        }
    }

    LaunchedEffect(isPlaying.value) {
        while (isPlaying.value) {
            val timeStamp = audioPlayer.getTimeStamp()
            timeStampSlider.floatValue = timeStamp
            if (timeStamp >= 1f) {
                Timber.d("Finished playing.")
                audioPlayer.setPlaying(false)
            } else delay(1000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = {
            val updatedSpeedIndex = viewModel.cycleSpeed()
            audioPlayer.setPlaybackSpeed(playbackSpeeds[updatedSpeedIndex])
        }) {
            Text("${playbackSpeeds[currentSpeedIndex.value]}x")
        }
        RoundIconButton(
            icon = if (!isPlaying.value) Icons.Default.PlayArrow
            else drawable.ic_pause,
            isEnabled = isEnabled,
            onButtonClick = {
                if (!isPlaying.value) {
                    if (filePath != audioPlayer.getCurrentFilePath()) {
                        Timber.d("Should change audio file.")
                        audioPlayer.setNewFile(filePath)
                        audioPlayer.changeTimeStamp(0f)
                        timeStampSlider.floatValue = 0f
                    }
                    audioPlayer.play()
                } else {
                    audioPlayer.pause()
                    Timber.d("Media player was paused.")
                }
                audioPlayer.setPlaying(!isPlaying.value)
            }
        )

        Slider(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(horizontal = 10.dp),
            value = timeStampSlider.floatValue,
            onValueChange = {
                audioPlayer.changeTimeStamp(it)
                timeStampSlider.floatValue = it
            },
            onValueChangeFinished = {
                //onValueChangeFinished()
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                activeTrackColor = if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray,
                inactiveTrackColor = if (isSystemInDarkTheme()) Color.Gray else Color.LightGray,
            ),
            enabled = isEnabled
        )
    }

}