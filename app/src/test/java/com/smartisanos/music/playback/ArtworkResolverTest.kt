package com.smartisanos.music.playback

import com.smartisanos.music.data.online.OnlineMusicProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkResolverTest {

    @Test
    fun onlineArtworkKeyIgnoresTemporaryPlaybackUri() {
        val placeholderKey = artworkRequestKeyState(
            mediaId = "online:netease:12345",
            artworkUri = "https://p1.music.126.net/cover.jpg",
            albumId = null,
            mediaUri = "smartisan-online://netease/12345",
            artworkData = null,
            onlineSource = OnlineMusicProvider.Netease.sourceId,
            onlineTrackId = "12345",
        )
        val resolvedKey = artworkRequestKeyState(
            mediaId = "online:netease:12345",
            artworkUri = "https://p1.music.126.net/cover.jpg",
            albumId = null,
            mediaUri = "https://m701.music.126.net/temp/song.mp3",
            artworkData = null,
            onlineSource = OnlineMusicProvider.Netease.sourceId,
            onlineTrackId = "12345",
        )

        assertEquals(placeholderKey, resolvedKey)
    }
}
