package com.smartisanos.music.playback

import androidx.media3.common.Player

internal fun Player?.isPlaybackActiveForUi(): Boolean {
    val player = this ?: return false
    return playbackActiveForUiState(
        isPlaying = player.isPlaying,
        playWhenReady = player.playWhenReady,
        playbackState = player.playbackState,
    )
}

internal fun playbackActiveForUiState(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): Boolean {
    return isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)
}

internal fun shouldSkipOnlinePlaybackError(
    isCurrentOnline: Boolean,
    hasNextMediaItem: Boolean,
    repeatMode: Int,
): Boolean {
    return isCurrentOnline &&
        hasNextMediaItem &&
        repeatMode != Player.REPEAT_MODE_ONE
}
