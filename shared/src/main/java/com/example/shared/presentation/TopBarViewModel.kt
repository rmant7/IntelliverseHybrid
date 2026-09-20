package com.example.shared.presentation

import androidx.lifecycle.ViewModel
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.SpeechConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TopBarViewModel @Inject constructor(
    private val speechConverter: SpeechConverter,
    private val audioPlayer: AudioPlayer,
): ViewModel() {
    /**
     * Called when navigating back from a sub-app to the starting screen.
     * [AudioPlayer] and [SpeechConverter] are shared between all the sub-apps, thus,
     * we need to reset them and the values should be the default ones.
     */
    fun onExitSubApp() {
        audioPlayer.onPlaybackEnded?.invoke()
        audioPlayer.onPlaybackEnded = null
        audioPlayer.setPlaybackSpeed(1f)
        audioPlayer.resetPlayer()
        speechConverter.onUtteranceFinished = { }
    }
}