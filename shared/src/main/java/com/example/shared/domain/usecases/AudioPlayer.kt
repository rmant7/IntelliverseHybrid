package com.example.shared.domain.usecases

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext val context: Context
) {

    private val player = ExoPlayer.Builder(context).build()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private var currentFilePath: String? = null
    private var currentStartingMillis = 0L
    var onPlaybackEnded: (() -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    onPlaybackEnded?.invoke() // Trigger callback
                    resetPlayer() // Reset the player
                }
            }
        })
    }

    fun setPlaying(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    fun getCurrentFilePath(): String? {
        return currentFilePath
    }

    fun setNewFile(filePath: String?) {
        player.pause()
        currentFilePath = filePath
        setPlaying(false)

        if (filePath.isNullOrBlank()) return
        val mediaItem = MediaItem.fromUri(Uri.parse(filePath))
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    fun play() {
        player.seekTo(currentStartingMillis)
        player.play()
        setPlaying(true)
    }

    fun changeTimeStamp(percentage: Float) {
        currentStartingMillis = (player.duration * percentage).toLong()
        player.seekTo(currentStartingMillis)
    }

    fun getTimeStamp(): Float {
        return player.currentPosition.toFloat() / player.duration.toFloat()
    }

    fun pause() {
        setPlaying(false)
        currentStartingMillis = player.currentPosition
        player.pause()
    }

    fun resetPlayer() {
        player.pause()
        setPlaying(false)
        currentStartingMillis = 0L
        player.seekTo(0)
    }

    fun shutdown() {
        // necessary to avoid memory leak when the MainActivity recreated
        onPlaybackEnded = null
        player.pause()
        player.release()
        setPlaying(false)
    }

    fun setPlaybackSpeed(speed: Float) {
        val playbackParameters = PlaybackParameters(speed)
        player.playbackParameters = playbackParameters
    }

    companion object {
        val playbackSpeeds = listOf(0.5f, 1f, 1.5f, 2f)
    }

}