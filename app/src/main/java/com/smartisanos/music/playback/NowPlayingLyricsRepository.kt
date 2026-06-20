package com.smartisanos.music.playback

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.media3.common.MediaItem
import com.smartisanos.music.data.online.OnlineLyricsExtraKey
import com.smartisanos.music.data.online.OnlineTranslatedLyricsExtraKey
import com.smartisanos.music.data.online.onlineIdentityOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal object NowPlayingLyricsRepository {
    private val cache = object : LruCache<LyricsRequestKey, EmbeddedLyrics>(MaxCachedLyricsLines) {
        override fun sizeOf(key: LyricsRequestKey, value: EmbeddedLyrics): Int {
            return value.lines.size.coerceAtLeast(1)
        }
    }
    private val missingKeys = LruCache<LyricsRequestKey, Long>(MissingLyricsCacheSize)
    private val inFlightLoads = ConcurrentHashMap<LyricsRequestKey, Deferred<EmbeddedLyrics?>>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun peek(mediaItem: MediaItem): EmbeddedLyrics? {
        return cache.get(mediaItem.lyricsRequestKey())
    }

    suspend fun load(
        context: Context,
        mediaItem: MediaItem,
        rememberMissing: Boolean = true,
    ): EmbeddedLyrics? {
        val appContext = context.applicationContext
        val key = mediaItem.lyricsRequestKey()
        cache.get(key)?.let { return it }
        if (isRecentlyMissing(key)) {
            return null
        }

        val lyrics = loadCoalesced(key) {
            loadEmbeddedLyrics(appContext, mediaItem)
        }
        if (lyrics != null) {
            cache.put(key, lyrics)
            missingKeys.remove(key)
        } else if (rememberMissing) {
            missingKeys.put(key, SystemClock.elapsedRealtime())
        }
        return lyrics
    }

    private fun isRecentlyMissing(key: LyricsRequestKey): Boolean {
        val missingAtMs = missingKeys.get(key) ?: return false
        return SystemClock.elapsedRealtime() - missingAtMs < MissingLyricsCooldownMs
    }

    private suspend fun loadCoalesced(
        key: LyricsRequestKey,
        loader: suspend () -> EmbeddedLyrics?,
    ): EmbeddedLyrics? {
        val newLoad = loadScope.async(start = CoroutineStart.LAZY) {
            cache.get(key) ?: loader()
        }
        val activeLoad = inFlightLoads.putIfAbsent(key, newLoad)
        val load = activeLoad ?: newLoad.also { pendingLoad ->
            pendingLoad.invokeOnCompletion {
                inFlightLoads.remove(key, pendingLoad)
            }
            pendingLoad.start()
        }
        if (activeLoad != null) {
            newLoad.cancel()
        }
        return load.await()
    }
}

private data class LyricsRequestKey(
    val mediaId: String?,
    val onlineSource: String?,
    val onlineTrackId: String?,
    val mediaUri: String?,
    val onlineLyricsHash: Int?,
    val onlineTranslatedLyricsHash: Int?,
)

private fun MediaItem.lyricsRequestKey(): LyricsRequestKey {
    val extras = mediaMetadata.extras
    val lyrics = extras?.getString(OnlineLyricsExtraKey)
    val translatedLyrics = extras?.getString(OnlineTranslatedLyricsExtraKey)
    val onlineIdentity = onlineIdentityOrNull()
    return LyricsRequestKey(
        mediaId = mediaId.trim().takeIf(String::isNotEmpty),
        onlineSource = onlineIdentity?.source,
        onlineTrackId = onlineIdentity?.trackId,
        mediaUri = if (onlineIdentity == null) localConfiguration?.uri?.toString() else null,
        onlineLyricsHash = lyrics?.hashCode(),
        onlineTranslatedLyricsHash = translatedLyrics?.hashCode(),
    )
}

private const val MaxCachedLyricsLines = 8_000
private const val MissingLyricsCacheSize = 128
private const val MissingLyricsCooldownMs = 5 * 60 * 1000L
