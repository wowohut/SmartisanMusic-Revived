package com.smartisanos.music.ui.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import androidx.media3.common.MediaItem
import com.smartisanos.music.data.online.onlineIdentityOrNull
import com.smartisanos.music.R
import com.smartisanos.music.playback.LocalAudioLibrary
import com.smartisanos.music.playback.loadArtworkUriBitmap
import com.smartisanos.music.ui.album.AlbumSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ImageView.bindLegacyAlbumArtwork(
    album: AlbumSummary,
    fallbackRes: Int,
    sizePx: Int,
    artworkLoader: LegacyAlbumArtworkLoader,
) {
    scaleType = ImageView.ScaleType.CENTER_CROP
    artworkLoader.bind(
        imageView = this,
        album = album,
        fallbackRes = fallbackRes,
        sizePx = sizePx,
    )
}

internal class LegacyAlbumArtworkLoader(context: Context) {
    private val appContext = context.applicationContext
    private val audioLibrary = LocalAudioLibrary(appContext)
    private var scope = newScope()
    private val pendingViews = mutableMapOf<String, MutableSet<ImageView>>()
    private val jobs = mutableMapOf<String, Job>()

    fun bind(
        imageView: ImageView,
        album: AlbumSummary,
        fallbackRes: Int,
        sizePx: Int,
    ) {
        bindRequest(
            imageView = imageView,
            request = buildArtworkRequest(album, sizePx),
            fallbackRes = fallbackRes,
        )
    }

    fun bind(
        imageView: ImageView,
        mediaItem: MediaItem,
        fallbackRes: Int,
        sizePx: Int,
    ) {
        bindRequest(
            imageView = imageView,
            request = buildArtworkRequest(mediaItem, sizePx),
            fallbackRes = fallbackRes,
        )
    }

    private fun bindRequest(
        imageView: ImageView,
        request: LegacyAlbumArtworkRequest?,
        fallbackRes: Int,
    ) {
        val previousKey = imageView.getTag(R.id.legacy_album_artwork_request) as? String
        if (request == null) {
            imageView.setTag(R.id.legacy_album_artwork_request, null)
            imageView.setImageResource(fallbackRes)
            return
        }

        imageView.setTag(R.id.legacy_album_artwork_request, request.jobKey)
        cache.get(request.cacheKey)?.let { cached ->
            imageView.setImageBitmap(cached)
            if (cached.width >= request.sizePx && cached.height >= request.sizePx) {
                return
            }
        } ?: run {
            if (previousKey != request.jobKey || imageView.drawable == null) {
                imageView.setImageResource(fallbackRes)
            }
        }
        pendingViews.getOrPut(request.jobKey) { linkedSetOf() } += imageView
        if (jobs.containsKey(request.jobKey)) {
            return
        }

        jobs[request.jobKey] = ensureScope().launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadBitmap(request)
            }
            jobs.remove(request.jobKey)
            if (bitmap != null) {
                bitmap.prepareToDraw()
                val cached = cache.get(request.cacheKey)
                if (cached == null || cached.width < bitmap.width || cached.height < bitmap.height) {
                    cache.put(request.cacheKey, bitmap)
                }
            }
            val views = pendingViews.remove(request.jobKey).orEmpty()
            views.forEach { pendingImageView ->
                if (pendingImageView.getTag(R.id.legacy_album_artwork_request) == request.jobKey) {
                    if (bitmap != null) {
                        pendingImageView.setImageBitmap(bitmap)
                    } else {
                        pendingImageView.setImageResource(fallbackRes)
                    }
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
        jobs.clear()
        pendingViews.clear()
    }

    private fun ensureScope(): CoroutineScope {
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = newScope()
        }
        return scope
    }

    private fun loadBitmap(request: LegacyAlbumArtworkRequest): Bitmap? {
        val size = Size(request.sizePx, request.sizePx)
        return request.artworkData?.let { artworkData ->
            runCatching {
                decodeByteArraySampled(artworkData, size)
            }.getOrNull()
        } ?: request.artworkUri?.let { artworkUri ->
            loadBitmapFromUri(artworkUri, size)
        } ?: request.trackArtworkUri?.let { trackArtworkUri ->
            loadBitmapFromUri(trackArtworkUri, size)
        } ?: request.mediaUri?.let { mediaUri ->
            runCatching {
                appContext.contentResolver.loadThumbnail(mediaUri, size, null)
            }.getOrNull()
        } ?: loadEmbeddedPicture(request.mediaItem, size)
            ?: loadResolvedMediaItemBitmap(request, size)
    }

    private fun loadBitmapFromUri(uri: Uri, size: Size): Bitmap? {
        return loadArtworkUriBitmap(appContext, uri, size)
    }

    private fun loadEmbeddedPicture(mediaItem: MediaItem, size: Size): Bitmap? {
        return runCatching {
            val mediaUri = mediaItem.localConfiguration?.uri ?: return@runCatching null
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, mediaUri)
                retriever.embeddedPicture?.let { bytes ->
                    decodeByteArraySampled(bytes, size)
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun loadResolvedMediaItemBitmap(
        request: LegacyAlbumArtworkRequest,
        size: Size,
    ): Bitmap? {
        val mediaId = request.mediaId ?: return null
        val resolvedItem = audioLibrary.getItem(mediaId.toString()) ?: return null
        val metadata = resolvedItem.mediaMetadata
        return metadata.artworkData?.let { artworkData ->
            runCatching {
                decodeByteArraySampled(artworkData, size)
            }.getOrNull()
        } ?: metadata.artworkUri?.let { artworkUri ->
            loadBitmapFromUri(artworkUri, size)
        } ?: resolvedItem.localConfiguration?.uri?.let { mediaUri ->
            runCatching {
                appContext.contentResolver.loadThumbnail(mediaUri, size, null)
            }.getOrNull()
        } ?: loadEmbeddedPicture(resolvedItem, size)
    }

    private fun buildArtworkRequest(
        album: AlbumSummary,
        sizePx: Int,
    ): LegacyAlbumArtworkRequest? {
        val representative = album.representative
        val metadata = representative.mediaMetadata
        val mediaId = representative.mediaId.toLongOrNull()
        val albumId = metadata.extras
            ?.getLong(LocalAudioLibrary.AlbumIdExtraKey)
            ?.takeIf { it > 0L }
        val artworkUri = metadata.artworkUri
            ?: albumId?.let(LocalAudioLibrary::albumArtworkUri)
        val trackArtworkUri = mediaId?.let(LocalAudioLibrary::trackArtworkUri)
        val mediaUri = representative.localConfiguration?.uri
        val artworkData = metadata.artworkData
        if (artworkUri == null && trackArtworkUri == null && mediaUri == null && artworkData == null) {
            return null
        }
        return LegacyAlbumArtworkRequest(
            mediaItem = representative,
            mediaId = mediaId,
            artworkUri = artworkUri,
            trackArtworkUri = trackArtworkUri,
            mediaUri = mediaUri,
            artworkData = artworkData,
            viewTag = albumId?.let { resolvedAlbumId -> "album:$resolvedAlbumId" }
                ?: mediaId?.let { resolvedMediaId -> "track:$resolvedMediaId" }
                ?: representative.onlineArtworkViewTag()
                ?: album.id,
            sizePx = sizePx,
        )
    }

    private fun buildArtworkRequest(
        mediaItem: MediaItem,
        sizePx: Int,
    ): LegacyAlbumArtworkRequest? {
        val metadata = mediaItem.mediaMetadata
        val mediaId = mediaItem.mediaId.toLongOrNull()
        val albumId = metadata.extras
            ?.getLong(LocalAudioLibrary.AlbumIdExtraKey)
            ?.takeIf { it > 0L }
        val artworkUri = metadata.artworkUri
            ?: albumId?.let(LocalAudioLibrary::albumArtworkUri)
        val trackArtworkUri = mediaId?.let(LocalAudioLibrary::trackArtworkUri)
        val mediaUri = mediaItem.localConfiguration?.uri
        val artworkData = metadata.artworkData
        if (artworkUri == null && trackArtworkUri == null && mediaUri == null && artworkData == null) {
            return null
        }
        return LegacyAlbumArtworkRequest(
            mediaItem = mediaItem,
            mediaId = mediaId,
            artworkUri = artworkUri,
            trackArtworkUri = trackArtworkUri,
            mediaUri = mediaUri,
            artworkData = artworkData,
            viewTag = albumId?.let { resolvedAlbumId -> "album:$resolvedAlbumId" }
                ?: mediaId?.let { resolvedMediaId -> "track:$resolvedMediaId" }
                ?: mediaItem.onlineArtworkViewTag()
                ?: mediaItem.localConfiguration?.uri?.toString()
                ?: mediaItem.mediaId,
            sizePx = sizePx,
        )
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private companion object {
        val cache = object : LruCache<String, Bitmap>(albumArtworkCacheSizeKb()) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }
}

private fun MediaItem.onlineArtworkViewTag(): String? {
    val identity = onlineIdentityOrNull() ?: return null
    val artworkUri = mediaMetadata.artworkUri?.toString().orEmpty()
    return "online:${identity.source}:${identity.trackId}:$artworkUri"
}

private data class LegacyAlbumArtworkRequest(
    val mediaItem: MediaItem,
    val mediaId: Long?,
    val artworkUri: Uri?,
    val trackArtworkUri: Uri?,
    val mediaUri: Uri?,
    val artworkData: ByteArray?,
    val viewTag: String,
    val sizePx: Int,
) {
    val cacheKey: String = viewTag
    val jobKey: String = "$viewTag@$sizePx"
}

private fun decodeByteArraySampled(
    bytes: ByteArray,
    size: Size,
): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val sampleOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(boundsOptions, size)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    size: Size,
): Int {
    val rawHeight = options.outHeight
    val rawWidth = options.outWidth
    val requestedHeight = size.height.coerceAtLeast(1)
    val requestedWidth = size.width.coerceAtLeast(1)
    var inSampleSize = 1

    if (rawHeight > requestedHeight || rawWidth > requestedWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while (
            halfHeight / inSampleSize >= requestedHeight &&
            halfWidth / inSampleSize >= requestedWidth
        ) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}

private fun albumArtworkCacheSizeKb(): Int {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    return (maxMemoryKb / 16).coerceAtLeast(4 * 1024)
}
