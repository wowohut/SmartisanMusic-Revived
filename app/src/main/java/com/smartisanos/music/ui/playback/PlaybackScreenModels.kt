package com.smartisanos.music.ui.playback

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal data class PlaybackScreenState(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
) {
    val isPlaybackActive: Boolean
        get() = isPlaying || (playWhenReady && isBuffering)
}

internal enum class PlaybackVisualPage {
    Cover,
    Lyrics,
}

internal enum class CoverDragMode {
    None,
    DiscScratch,
    NeedleSeek,
}

internal data class PlaybackDeleteTarget(
    val mediaId: String,
    val uri: Uri,
)

internal sealed interface PlaybackDeleteTargetResult {
    data class Available(val target: PlaybackDeleteTarget) : PlaybackDeleteTargetResult
    object CueFile : PlaybackDeleteTargetResult
    object Unavailable : PlaybackDeleteTargetResult
}

internal data class PlaybackNeedleGeometry(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val pivotLocal: Offset,
    val pivot: Offset,
)
