package com.smartisanos.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import android.util.Size
import androidx.media3.common.MediaItem
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal object NowPlayingArtworkRepository {
    private val cache = object : LruCache<ArtworkCacheKey, Bitmap>(artworkCacheSizeKb()) {
        override fun sizeOf(key: ArtworkCacheKey, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }
    private val missingIdentities = LruCache<ArtworkRequestKey, Long>(MissingArtworkIdentityCacheSize)
    private val inFlightLoads = ConcurrentHashMap<ArtworkCacheKey, Deferred<Bitmap?>>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun peek(
        mediaItem: MediaItem,
        size: Size,
        allowAnySize: Boolean = true,
    ): Bitmap? {
        val identity = mediaItem.artworkRequestKey()
        val exactKey = ArtworkCacheKey(identity, size.width, size.height)
        cache.get(exactKey)?.let { return it }
        if (!allowAnySize) {
            return null
        }
        return cache.snapshot()
            .asSequence()
            .filter { (key, _) -> key.identity == identity }
            .map { (_, bitmap) -> bitmap }
            .maxByOrNull { bitmap -> bitmap.width * bitmap.height }
    }

    suspend fun load(
        context: Context,
        mediaItem: MediaItem,
        size: Size,
        rememberMissing: Boolean = true,
    ): Bitmap? {
        val appContext = context.applicationContext
        val identity = mediaItem.artworkRequestKey()
        val cacheKey = ArtworkCacheKey(identity, size.width, size.height)
        cache.get(cacheKey)?.let { return it }
        val reusableBitmap = peek(mediaItem, size)
        if (isRecentlyMissing(identity)) {
            return reusableBitmap
        }

        val bitmap = loadCoalesced(cacheKey) {
            loadBitmap(appContext, mediaItem, size)
        }?.also { loaded ->
            loaded.prepareToDraw()
            cache.put(cacheKey, loaded)
            missingIdentities.remove(identity)
        }
        if (bitmap == null && rememberMissing) {
            missingIdentities.put(identity, SystemClock.elapsedRealtime())
        }
        return bitmap ?: reusableBitmap
    }

    suspend fun preload(context: Context, mediaItem: MediaItem) {
        for (size in NowPlayingArtworkPreloadSizes) {
            load(
                context = context,
                mediaItem = mediaItem,
                size = size,
                rememberMissing = false,
            )
        }
    }

    private suspend fun loadBitmap(
        context: Context,
        mediaItem: MediaItem,
        size: Size,
    ): Bitmap? {
        val metadata = mediaItem.mediaMetadata
        return decodeArtworkData(metadata.artworkData, size)
            ?: loadNetworkArtworkBitmap(context, metadata.artworkUri, size)
            ?: loadArtworkBitmapSync(context, mediaItem, size)
    }

    private suspend fun loadNetworkArtworkBitmap(
        context: Context,
        uri: Uri?,
        size: Size,
    ): Bitmap? {
        uri ?: return null
        if (!uri.isNetworkUri()) {
            return null
        }
        return runCatching {
            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(uri.toString())
                    .size(size.width, size.height)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
            val image = (result as? SuccessResult)?.image ?: return@runCatching null
            image.toBitmap(
                width = image.width.coerceAtLeast(1),
                height = image.height.coerceAtLeast(1),
            ).scaledToFit(size)
        }.getOrNull()
    }

    private fun isRecentlyMissing(identity: ArtworkRequestKey): Boolean {
        val missingAtMs = missingIdentities.get(identity) ?: return false
        return SystemClock.elapsedRealtime() - missingAtMs < MissingArtworkCooldownMs
    }

    private suspend fun loadCoalesced(
        cacheKey: ArtworkCacheKey,
        loader: suspend () -> Bitmap?,
    ): Bitmap? {
        val newLoad = loadScope.async(start = CoroutineStart.LAZY) {
            cache.get(cacheKey) ?: loader()
        }
        val activeLoad = inFlightLoads.putIfAbsent(cacheKey, newLoad)
        val load = activeLoad ?: newLoad.also { pendingLoad ->
            pendingLoad.invokeOnCompletion {
                inFlightLoads.remove(cacheKey, pendingLoad)
            }
            pendingLoad.start()
        }
        if (activeLoad != null) {
            newLoad.cancel()
        }
        return load.await()
    }
}

private data class ArtworkCacheKey(
    val identity: ArtworkRequestKey,
    val width: Int,
    val height: Int,
)

private fun Uri.isNetworkUri(): Boolean {
    return scheme == "http" || scheme == "https"
}

private fun Bitmap.scaledToFit(size: Size): Bitmap {
    val maxWidth = size.width.coerceAtLeast(1)
    val maxHeight = size.height.coerceAtLeast(1)
    if (width <= maxWidth && height <= maxHeight) {
        return this
    }
    val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
}

private fun artworkCacheSizeKb(): Int {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    return (maxMemoryKb / 16).coerceAtLeast(4 * 1024)
}

private val NowPlayingArtworkPreloadSizes = listOf(
    Size(512, 512),
    Size(128, 128),
)
private const val MissingArtworkIdentityCacheSize = 128
private const val MissingArtworkCooldownMs = 5 * 60 * 1000L
