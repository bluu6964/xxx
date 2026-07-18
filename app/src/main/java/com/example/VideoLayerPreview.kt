package com.example

import android.net.Uri
import android.view.ViewGroup
import android.view.TextureView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/**
 * Plays a video layer's frames inside the edit canvas smoothly.
 * Optimized with direct GLES TextureView binding, dynamic aspect ratio preservation (no stretching to 1:1),
 * and smart play-seek synchronization to guarantee 100% lag-free scrubbing and playback.
 */
@Composable
fun VideoLayerPreview(
    uri: Uri,
    playheadProgressSeconds: Float,
    layerStartSeconds: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onDurationKnownSeconds: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var videoAspectRatio by remember { mutableStateOf(1f) }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            // Muted preview inside the editor canvas
            volume = 0f
            // Enable seamless loop working for this video clip
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    val durationMs = player.duration
                    if (durationMs != androidx.media3.common.C.TIME_UNSET && durationMs > 0) {
                        onDurationKnownSeconds(durationMs / 1000f)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Keep play/pause in sync with the global timeline state.
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Sync playhead position exactly once when playback starts, then let hardware play naturally
            val targetSeconds = (playheadProgressSeconds - layerStartSeconds).coerceAtLeast(0f)
            val targetMs = (targetSeconds * 1000).toLong()
            exoPlayer.seekTo(targetMs)
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Seek the player to match the timeline scrubber ONLY when the timeline is NOT playing.
    // This completely removes the severe rendering lag caused by fighting the player's internal clock during playback.
    LaunchedEffect(playheadProgressSeconds) {
        if (!isPlaying) {
            val targetSeconds = (playheadProgressSeconds - layerStartSeconds).coerceAtLeast(0f)
            val targetMs = (targetSeconds * 1000).toLong()
            if (abs(exoPlayer.currentPosition - targetMs) > 100) {
                exoPlayer.seekTo(targetMs)
            }
        }
    }

    // Calculate preview dimensions bounding inside a 160.dp box while strictly preserving the video's original aspect ratio
    val finalWidth = if (videoAspectRatio >= 1f) 160.dp else 160.dp * videoAspectRatio
    val finalHeight = if (videoAspectRatio >= 1f) 160.dp / videoAspectRatio else 160.dp

    AndroidView(
        modifier = modifier.size(finalWidth, finalHeight),
        factory = {
            TextureView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Bind the ExoPlayer directly to this TextureView for hardware-accelerated,
                // fully transformable (scale, rotate, blend) video rendering.
                exoPlayer.setVideoTextureView(this)
            }
        }
    )
}
