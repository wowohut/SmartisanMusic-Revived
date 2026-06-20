package com.smartisanos.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStreamingCacheTest {

    @Test
    fun onlinePlaceholderUriUsesStableTrackCacheKey() {
        assertEquals(
            "online:netease:12345",
            playbackStreamingCacheKey(
                explicitKey = null,
                uri = "smartisan-online://netease/12345",
            ),
        )
    }

    @Test
    fun explicitMediaItemCacheKeyWinsOverUrl() {
        assertEquals(
            "online:netease:12345",
            playbackStreamingCacheKey(
                explicitKey = "online:netease:12345",
                uri = "https://m801.music.126.net/song.mp3?temporary=1",
            ),
        )
    }

    @Test
    fun streamingCacheSizeFallsBackWhenUsableSpaceUnknown() {
        assertEquals(
            1024L * 1024L * 1024L,
            playbackStreamingMaxCacheSizeBytes(usableSpaceBytes = 0L),
        )
    }

    @Test
    fun streamingCacheSizeKeepsSmallDevicesConservative() {
        assertEquals(
            128L * 1024L * 1024L,
            playbackStreamingMaxCacheSizeBytes(usableSpaceBytes = 512L * 1024L * 1024L),
        )
    }

    @Test
    fun streamingCacheSizeAllowsMoreRecentSongsOnLargeStorage() {
        assertEquals(
            2L * 1024L * 1024L * 1024L,
            playbackStreamingMaxCacheSizeBytes(usableSpaceBytes = 64L * 1024L * 1024L * 1024L),
        )
    }
}
