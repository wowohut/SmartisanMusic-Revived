package com.smartisanos.music.ui.shell

import com.smartisanos.music.data.online.OnlinePlaybackFailureReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlaybackErrorToastTest {

    @Test
    fun repeatedSameOnlinePlaybackErrorIsThrottled() {
        val key = OnlinePlaybackErrorToastKey(
            mediaId = "online:netease:1",
            reason = OnlinePlaybackFailureReason.LoginRequired,
        )

        assertFalse(
            shouldShowOnlinePlaybackErrorToast(
                lastKey = key,
                lastAtMs = 1_000L,
                nextKey = key,
                nowMs = 2_000L,
                cooldownMs = 3_500L,
            ),
        )
    }

    @Test
    fun sameOnlinePlaybackErrorShowsAfterCooldown() {
        val key = OnlinePlaybackErrorToastKey(
            mediaId = "online:netease:1",
            reason = OnlinePlaybackFailureReason.LoginRequired,
        )

        assertTrue(
            shouldShowOnlinePlaybackErrorToast(
                lastKey = key,
                lastAtMs = 1_000L,
                nextKey = key,
                nowMs = 4_500L,
                cooldownMs = 3_500L,
            ),
        )
    }

    @Test
    fun differentOnlinePlaybackErrorShowsImmediately() {
        val lastKey = OnlinePlaybackErrorToastKey(
            mediaId = "online:netease:1",
            reason = OnlinePlaybackFailureReason.LoginRequired,
        )
        val nextKey = OnlinePlaybackErrorToastKey(
            mediaId = "online:netease:2",
            reason = OnlinePlaybackFailureReason.LoginRequired,
        )

        assertTrue(
            shouldShowOnlinePlaybackErrorToast(
                lastKey = lastKey,
                lastAtMs = 1_000L,
                nextKey = nextKey,
                nowMs = 1_100L,
                cooldownMs = 3_500L,
            ),
        )
    }
}
