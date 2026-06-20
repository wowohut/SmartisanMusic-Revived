package com.smartisanos.music.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlinePlaybackFailureTest {

    @Test
    fun playbackFailureReasonCanBeReadFromNestedCause() {
        val error = RuntimeException(
            "player failed",
            OnlinePlaybackResolutionException(
                reason = OnlinePlaybackFailureReason.PreviewOnly,
                message = "preview only",
            ),
        )

        assertEquals(
            OnlinePlaybackFailureReason.PreviewOnly,
            error.onlinePlaybackFailureReasonOrNull(),
        )
    }

    @Test
    fun unrelatedFailureHasNoPlaybackFailureReason() {
        assertNull(IllegalStateException("unrelated").onlinePlaybackFailureReasonOrNull())
    }
}
