@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisanos.music.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

internal object PlaybackStreamingCache {
    private const val CacheDirectoryName = "media_cache"
    private const val DefaultMaxCacheSizeBytes = 1024L * 1024L * 1024L

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
            .setFlags(
                CacheDataSource.FLAG_BLOCK_ON_CACHE or
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            )
        return DefaultDataSource.Factory(appContext, cacheDataSourceFactory)
    }

    fun getOrCreateCache(context: Context): Cache {
        sharedCache?.let { cache -> return cache }
        return synchronized(lock) {
            sharedCache ?: SimpleCache(
                File(context.applicationContext.cacheDir, CacheDirectoryName),
                LeastRecentlyUsedCacheEvictor(DefaultMaxCacheSizeBytes),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache ->
                sharedCache = cache
            }
        }
    }
}
