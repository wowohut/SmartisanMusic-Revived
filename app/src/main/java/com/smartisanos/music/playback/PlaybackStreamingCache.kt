@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisanos.music.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.smartisanos.music.data.online.OnlineMediaIdPrefix
import com.smartisanos.music.data.online.OnlinePlaybackUriScheme
import java.io.File
import java.net.URI

internal object PlaybackStreamingCache {
    private const val CacheDirectoryName = "media_cache"

    private val lock = Any()

    @Volatile
    private var sharedCache: SimpleCache? = null

    fun createDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory,
    ): DataSource.Factory {
        val appContext = context.applicationContext
        val cache = runCatching {
            getOrCreateCache(appContext)
        }.getOrNull() ?: return upstreamFactory

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory(SmartisanPlaybackCacheKeyFactory)
            .setFlags(
                CacheDataSource.FLAG_BLOCK_ON_CACHE or
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            )
        return DefaultDataSource.Factory(appContext, cacheDataSourceFactory)
    }

    fun getOrCreateCache(context: Context): Cache {
        sharedCache?.let { cache -> return cache }
        return synchronized(lock) {
            val appContext = context.applicationContext
            val cacheDirectory = File(appContext.cacheDir, CacheDirectoryName)
            sharedCache ?: SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(
                    playbackStreamingMaxCacheSizeBytes(appContext.cacheDir.usableSpace),
                ),
                StandaloneDatabaseProvider(appContext),
            ).also { cache ->
                sharedCache = cache
            }
        }
    }
}

private val SmartisanPlaybackCacheKeyFactory = CacheKeyFactory { dataSpec ->
    playbackStreamingCacheKey(dataSpec)
}

internal fun playbackStreamingCacheKey(dataSpec: DataSpec): String {
    return playbackStreamingCacheKey(
        explicitKey = dataSpec.key,
        uri = dataSpec.uri.toString(),
    )
}

internal fun playbackStreamingCacheKey(
    explicitKey: String?,
    uri: String,
): String {
    return explicitKey
        ?: uri.onlinePlaybackCacheKeyOrNull()
        ?: uri
}

internal fun playbackStreamingMaxCacheSizeBytes(usableSpaceBytes: Long): Long {
    if (usableSpaceBytes <= 0L) {
        return DefaultMaxCacheSizeBytes
    }
    return (usableSpaceBytes / CacheUsableSpaceDivisor)
        .coerceIn(MinMaxCacheSizeBytes, MaxMaxCacheSizeBytes)
}

private fun String.onlinePlaybackCacheKeyOrNull(): String? {
    val parsedUri = runCatching { URI(this) }.getOrNull() ?: return null
    if (parsedUri.scheme != OnlinePlaybackUriScheme) {
        return null
    }
    val source = parsedUri.host?.takeIf(String::isNotBlank) ?: return null
    val trackId = parsedUri.path
        ?.trim('/')
        ?.substringBefore('/')
        ?.takeIf(String::isNotBlank)
        ?: return null
    return "$OnlineMediaIdPrefix$source:$trackId"
}

private const val CacheUsableSpaceDivisor = 8L
private const val MinMaxCacheSizeBytes = 128L * 1024L * 1024L
private const val DefaultMaxCacheSizeBytes = 1024L * 1024L * 1024L
private const val MaxMaxCacheSizeBytes = 2L * 1024L * 1024L * 1024L
