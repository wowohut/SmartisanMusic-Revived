@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisanos.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

internal class MediaSessionArtworkBitmapLoader(
    context: Context,
) : BitmapLoader {
    private val appContext = context.applicationContext
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "MediaSessionArtworkBitmapLoader")
        },
    )

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executor.submit<Bitmap> {
            decodeArtworkData(data, NotificationArtworkSize)
                ?: throw IOException("Unable to decode notification artwork data.")
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return executor.submit<Bitmap> {
            loadArtworkUriBitmap(appContext, uri, NotificationArtworkSize)
                ?: throw IOException("Unable to load notification artwork: $uri")
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val artworkUri = metadata.artworkUri
        val mediaId = artworkUri?.mediaStoreAlbumArtMediaId()
            ?: metadata.extras.mediaId()
        if (mediaId == null && artworkUri == null && metadata.extras.albumArtworkUri() == null) {
            return null
        }
        return executor.submit<Bitmap> {
            val mediaItem = MediaItem.Builder()
                .setMediaId(mediaId.orEmpty())
                .setMediaMetadata(metadata)
                .apply {
                    mediaId
                        ?.let(::localAudioMediaUri)
                        ?.let(::setUri)
                }
                .build()
            runBlocking {
                NowPlayingArtworkRepository.load(
                    context = appContext,
                    mediaItem = mediaItem,
                    size = NotificationArtworkSize,
                )
            } ?: loadAlbumArtworkFromMetadata(metadata.extras)
                ?: throw IOException("Unable to load notification artwork.")
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun loadAlbumArtworkFromMetadata(extras: Bundle?): Bitmap? {
        val albumArtworkUri = extras.albumArtworkUri() ?: return null
        return loadArtworkUriBitmap(appContext, albumArtworkUri, NotificationArtworkSize)
    }
}

private fun Bundle?.mediaId(): String? {
    return this
        ?.getString(LocalAudioLibrary.MediaIdExtraKey)
        ?.takeIf { it.isNotBlank() }
}

private fun Uri.mediaStoreAlbumArtMediaId(): String? {
    if (scheme != "content" || authority != "media") {
        return null
    }
    val mediaIndex = pathSegments.indexOf("media").takeIf { it >= 0 } ?: return null
    val mediaId = pathSegments.getOrNull(mediaIndex + 1) ?: return null
    val artworkSegment = pathSegments.getOrNull(mediaIndex + 2)
    return mediaId.takeIf { artworkSegment == "albumart" }
}

private fun Bundle?.albumArtworkUri(): Uri? {
    val albumId = this
        ?.getLong(LocalAudioLibrary.AlbumIdExtraKey)
        ?.takeIf { it > 0L }
        ?: return null
    return LocalAudioLibrary.albumArtworkUri(albumId)
}

private val NotificationArtworkSize = Size(512, 512)
