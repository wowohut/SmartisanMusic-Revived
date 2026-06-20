package com.smartisanos.music.data.online

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineCacheBehaviorTest {

    @Test
    fun onlinePlaybackCacheKeyMatchesStableOnlineMediaId() {
        val identity = OnlineTrackIdentity(
            source = OnlineMusicProvider.Netease.sourceId,
            trackId = "12345",
        )

        assertEquals(
            buildOnlineMediaId(identity.source, identity.trackId),
            identity.toOnlinePlaybackCacheKey(),
        )
    }

    @Test
    fun memoryCacheCoalescesConcurrentLoadsForSameKey() = runBlocking {
        val calls = AtomicInteger(0)
        val key = "test:coalesce:${System.nanoTime()}"

        val values = coroutineScope {
            List(8) {
                async {
                    NeteaseOnlineMemoryCache.getOrLoad(
                        key = key,
                        ttlMs = 60_000L,
                    ) {
                        calls.incrementAndGet()
                        delay(25L)
                        "loaded"
                    }
                }
            }.awaitAll()
        }

        assertEquals(List(8) { "loaded" }, values)
        assertEquals(1, calls.get())
    }

    @Test
    fun memoryCacheCoalescesConcurrentNullLoadsWithoutCachingResult() = runBlocking {
        val calls = AtomicInteger(0)
        val key = "test:coalesce-null:${System.nanoTime()}"

        val values = coroutineScope {
            List(8) {
                async {
                    NeteaseOnlineMemoryCache.getOrLoadNonNull<String>(
                        key = key,
                        ttlMs = 60_000L,
                    ) {
                        calls.incrementAndGet()
                        delay(25L)
                        null
                    }
                }
            }.awaitAll()
        }
        val retryValue = NeteaseOnlineMemoryCache.getOrLoadNonNull(
            key = key,
            ttlMs = 60_000L,
        ) {
            calls.incrementAndGet()
            "retry-loaded"
        }

        assertEquals(List<String?>(8) { null }, values)
        assertEquals("retry-loaded", retryValue)
        assertEquals(2, calls.get())
    }

    @Test
    fun memoryCacheCoalescedLoadSurvivesFirstAwaiterCancellation() = runBlocking {
        val calls = AtomicInteger(0)
        val key = "test:coalesce-cancel:${System.nanoTime()}"
        val started = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()

        val firstAwaiter = launch {
            NeteaseOnlineMemoryCache.getOrLoadNonNull(
                key = key,
                ttlMs = 60_000L,
            ) {
                calls.incrementAndGet()
                started.complete(Unit)
                allowFinish.await()
                "loaded-after-cancel"
            }
        }
        started.await()
        firstAwaiter.cancelAndJoin()
        val secondAwaiter = async {
            NeteaseOnlineMemoryCache.getOrLoadNonNull<String>(
                key = key,
                ttlMs = 60_000L,
            ) {
                calls.incrementAndGet()
                "duplicate-load"
            }
        }
        allowFinish.complete(Unit)

        assertEquals("loaded-after-cancel", secondAwaiter.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun lyricsDiskCachePersistsLyrics() = runBlocking {
        val directory = createTempCacheDirectory()
        try {
            val cache = OnlineLyricsDiskCache(
                directory = directory,
                ttlMs = 60_000L,
            )
            val identity = OnlineTrackIdentity(
                source = OnlineMusicProvider.Netease.sourceId,
                trackId = "67890",
            )

            cache.put(
                identity = identity,
                lyrics = OnlineLyrics(
                    lyric = "[00:01.00]第一句",
                    translatedLyric = "Translated",
                    wordLyric = "[1000,500](1000,250,0)第一(1250,250,0)句",
                    translatedWordLyric = "[1000,500](1000,500,0)Translated",
                ),
            )

            assertEquals(
                OnlineLyrics(
                    lyric = "[00:01.00]第一句",
                    translatedLyric = "Translated",
                    wordLyric = "[1000,500](1000,250,0)第一(1250,250,0)句",
                    translatedWordLyric = "[1000,500](1000,500,0)Translated",
                ),
                cache.get(identity),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun lyricsDiskCacheDropsExpiredLyrics() = runBlocking {
        val directory = createTempCacheDirectory()
        try {
            val expiredWriter = OnlineLyricsDiskCache(
                directory = directory,
                ttlMs = 0L,
            )
            val identity = OnlineTrackIdentity(
                source = OnlineMusicProvider.Netease.sourceId,
                trackId = "expired",
            )
            expiredWriter.put(
                identity = identity,
                lyrics = OnlineLyrics(
                    lyric = "[00:01.00]旧歌词",
                    translatedLyric = null,
                ),
            )

            assertNull(expiredWriter.get(identity))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun lyricsDiskCacheDoesNotPersistEmptyLyrics() = runBlocking {
        val directory = createTempCacheDirectory()
        try {
            val cache = OnlineLyricsDiskCache(
                directory = directory,
                ttlMs = 60_000L,
            )
            val identity = OnlineTrackIdentity(
                source = OnlineMusicProvider.Netease.sourceId,
                trackId = "empty",
            )

            cache.put(
                identity = identity,
                lyrics = OnlineLyrics(
                    lyric = null,
                    translatedLyric = null,
                ),
            )

            assertNull(cache.get(identity))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pageDiskCachePersistsStructuredHomeData() = runBlocking {
        val directory = createTempCacheDirectory()
        try {
            val cache = OnlinePageDiskCache(directory = directory)
            val key = "netease:anon:featured:home"
            val home = OnlineMusicHome(
                tracks = listOf(sampleTrack("1")),
                playlists = listOf(
                    OnlinePlaylist(
                        provider = OnlineMusicProvider.Netease,
                        playlistId = "3778678",
                        title = "推荐歌单",
                        subtitle = "每日更新",
                        artworkUrl = "https://example.com/playlist.jpg",
                        trackCount = 30,
                        playCount = 1000L,
                        kind = OnlinePlaylistKind.Featured,
                    ),
                ),
                albums = listOf(
                    OnlineAlbum(
                        provider = OnlineMusicProvider.Netease,
                        albumId = "10",
                        title = "专辑",
                        artist = "艺术家",
                        artworkUrl = "https://example.com/album.jpg",
                        trackCount = 12,
                        publishTimeMs = 123L,
                    ),
                ),
            )

            cache.put(
                key = key,
                value = home,
                codec = OnlinePageCacheCodecs.MusicHome,
                cachedAtMs = 42L,
            )

            assertEquals(
                OnlinePageCacheEntry(
                    value = home,
                    cachedAtMs = 42L,
                ),
                cache.get(
                    key = key,
                    codec = OnlinePageCacheCodecs.MusicHome,
                ),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pageDiskCacheRemovesEntriesByLogicalPrefix() = runBlocking {
        val directory = createTempCacheDirectory()
        try {
            val cache = OnlinePageDiskCache(directory = directory)
            val accountKey = "netease:user:1:account:playlists"
            val featuredKey = "netease:user:1:featured:home"

            cache.put(
                key = accountKey,
                value = listOf(
                    OnlineAccountPlaylist(
                        provider = OnlineMusicProvider.Netease,
                        playlistId = "1",
                        title = "我喜欢的音乐",
                        trackCount = 3,
                        isLikedSongs = true,
                    ),
                ),
                codec = OnlinePageCacheCodecs.AccountPlaylists,
            )
            cache.put(
                key = featuredKey,
                value = OnlineMusicHome(tracks = listOf(sampleTrack("2"))),
                codec = OnlinePageCacheCodecs.MusicHome,
            )

            cache.removePrefix("netease:user:1:account")

            assertNull(
                cache.get(
                    key = accountKey,
                    codec = OnlinePageCacheCodecs.AccountPlaylists,
                ),
            )
            assertEquals(
                OnlineMusicHome(tracks = listOf(sampleTrack("2"))),
                cache.get(
                    key = featuredKey,
                    codec = OnlinePageCacheCodecs.MusicHome,
                )?.value,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createTempCacheDirectory(): File {
        return Files.createTempDirectory("smartisan-lyrics-cache-test-").toFile()
    }

    private fun sampleTrack(trackId: String): OnlineTrack {
        return OnlineTrack(
            source = OnlineMusicProvider.Netease.sourceId,
            trackId = trackId,
            title = "歌曲 $trackId",
            artist = "艺术家",
            album = "专辑",
            durationMs = 180_000L,
            artworkUrl = "https://example.com/$trackId.jpg",
        )
    }
}
