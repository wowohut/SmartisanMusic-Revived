package com.smartisanos.music.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUiStateTest {

    @Test
    fun playbackActiveForUiTreatsBufferingWithPlayIntentAsActive() {
        assertTrue(
            playbackActiveForUiState(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
    }

    @Test
    fun playbackActiveForUiKeepsPausedBufferingInactive() {
        assertFalse(
            playbackActiveForUiState(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
    }

    @Test
    fun onlinePlaybackErrorCanSkipWhenQueueHasNextTrack() {
        assertTrue(
            shouldSkipOnlinePlaybackError(
                isCurrentOnline = true,
                hasNextMediaItem = true,
                repeatMode = Player.REPEAT_MODE_OFF,
            ),
        )
    }

    @Test
    fun localPlaybackErrorDoesNotSkipAutomatically() {
        assertFalse(
            shouldSkipOnlinePlaybackError(
                isCurrentOnline = false,
                hasNextMediaItem = true,
                repeatMode = Player.REPEAT_MODE_OFF,
            ),
        )
    }

    @Test
    fun onlinePlaybackErrorKeepsSingleRepeatInPlace() {
        assertFalse(
            shouldSkipOnlinePlaybackError(
                isCurrentOnline = true,
                hasNextMediaItem = true,
                repeatMode = Player.REPEAT_MODE_ONE,
            ),
        )
    }

    @Test
    fun onlinePlaybackErrorDoesNotSkipWithoutNextTrack() {
        assertFalse(
            shouldSkipOnlinePlaybackError(
                isCurrentOnline = true,
                hasNextMediaItem = false,
                repeatMode = Player.REPEAT_MODE_OFF,
            ),
        )
    }
}
