package com.smartisanos.music.data.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteasePlaybackUrlParserTest {

    @Test
    fun previewDurationIsRejectedWhenOriginalSongIsMuchLonger() {
        assertTrue(
            isNeteasePreviewDuration(
                returnedDurationMs = 30_000L,
                originalDurationMs = 210_000L,
            ),
        )
    }

    @Test
    fun fullDurationIsAccepted() {
        assertFalse(
            isNeteasePreviewDuration(
                returnedDurationMs = 185_040L,
                originalDurationMs = 185_040L,
            ),
        )
    }

    @Test
    fun missingReturnedDurationDoesNotRejectPlayableUrl() {
        assertFalse(
            isNeteasePreviewDuration(
                returnedDurationMs = null,
                originalDurationMs = 185_040L,
            ),
        )
    }

    @Test
    fun shortOriginalSongDoesNotLookLikePreview() {
        assertFalse(
            isNeteasePreviewDuration(
                returnedDurationMs = 30_000L,
                originalDurationMs = 45_000L,
            ),
        )
    }

    @Test
    fun playbackUrlResponseAcceptsFullUrlFromArrayData() {
        val result = parseNeteasePlaybackUrlResponse(
            response = """
            {
              "code": 200,
              "data": [
                {
                  "id": 123,
                  "url": "http://m801.music.126.net/song.flac",
                  "type": "flac",
                  "time": 210000
                }
              ]
            }
            """.trimIndent(),
            originalDurationMs = 210_000L,
        )

        assertEquals(NeteasePlaybackParseStatus.Success, result.status)
        assertEquals("https://m801.music.126.net/song.flac", result.playbackUrl?.url)
        assertEquals("audio/flac", result.playbackUrl?.mimeType)
    }

    @Test
    fun playbackUrlResponseRejectsExplicitPreviewClip() {
        val result = parseNeteasePlaybackUrlResponse(
            response = """
            {
              "code": 200,
              "data": [
                {
                  "id": 123,
                  "url": "https://m801.music.126.net/preview.mp3",
                  "type": "mp3",
                  "time": 30000,
                  "freeTrialInfo": {"start": 0, "end": 30000}
                }
              ]
            }
            """.trimIndent(),
            originalDurationMs = 210_000L,
        )

        assertEquals(NeteasePlaybackParseStatus.Preview, result.status)
        assertNull(result.playbackUrl)
    }

    @Test
    fun playbackUrlResponseTreatsNoPermissionAsLoginRequired() {
        val result = parseNeteasePlaybackUrlResponse(
            response = """
            {
              "code": 200,
              "data": {
                "id": 123,
                "url": null,
                "code": 404,
                "fee": 1,
                "freeTrialPrivilege": {"cannotListenReason": 1}
              }
            }
            """.trimIndent(),
            originalDurationMs = 210_000L,
        )

        assertEquals(NeteasePlaybackParseStatus.RequiresLogin, result.status)
        assertNull(result.playbackUrl)
    }

    @Test
    fun unresolvedOnlineMediaItemNeedsPlaybackUrlRefresh() {
        assertTrue(
            shouldRefreshOnlinePlaybackUrlState(
                isOnline = true,
                hasPlaybackUrl = false,
                resolvedAtMs = 0L,
            ),
        )
    }

    @Test
    fun resolvedOnlineMediaItemRefreshesAfterUrlBecomesStale() {
        val resolvedAtMs = System.currentTimeMillis()

        assertFalse(
            shouldRefreshOnlinePlaybackUrlState(
                isOnline = true,
                hasPlaybackUrl = true,
                resolvedAtMs = resolvedAtMs,
                nowMs = resolvedAtMs,
            ),
        )
        assertTrue(
            shouldRefreshOnlinePlaybackUrlState(
                isOnline = true,
                hasPlaybackUrl = true,
                resolvedAtMs = resolvedAtMs,
                nowMs = resolvedAtMs + 16 * 60 * 1000L,
            ),
        )
    }

    @Test
    fun accountProfileResponseParsesNicknameAndUserId() {
        val profile = parseNeteaseAccountProfileResponse(
            """
            {
              "code": 200,
              "profile": {
                "userId": 42,
                "nickname": "Smartisan User",
                "avatarUrl": "https://p1.music.126.net/avatar.jpg"
              }
            }
            """.trimIndent(),
        )

        assertEquals(42L, profile?.userId)
        assertEquals("Smartisan User", profile?.nickname)
        assertEquals("https://p1.music.126.net/avatar.jpg", profile?.avatarUrl)
    }

    @Test
    fun loggedOutAccountProfileResponseIsEmpty() {
        assertNull(
            parseNeteaseAccountProfileResponse(
                """{"code":200,"account":null,"profile":null}""",
            ),
        )
    }

    @Test
    fun userPlaylistResponseMarksLikedSongsPlaylist() {
        val playlists = parseNeteaseUserPlaylistsResponse(
            """
            {
              "more": false,
              "playlist": [
                {
                  "id": 100,
                  "name": "Smartisan User喜欢的音乐",
                  "trackCount": 37,
                  "specialType": 5
                },
                {
                  "id": 200,
                  "name": "普通歌单",
                  "trackCount": 8,
                  "specialType": 0
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("100", playlists.first().playlistId)
        assertEquals("Smartisan User喜欢的音乐", playlists.first().name)
        assertEquals(37, playlists.first().trackCount)
        assertTrue(playlists.first().isLikedSongs)
        assertFalse(playlists.last().isLikedSongs)
    }

}
