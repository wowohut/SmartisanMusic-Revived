package com.smartisanos.music.ui.shell.cloud.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisanos.music.playback.loadArtworkBitmap
import com.smartisanos.music.ui.shell.cloud.CloudCoverArtworkSizePx

@Composable
internal fun CloudMusicCoverImage(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, imageUrl) {
        value = null
        val safeUrl = imageUrl?.takeIf(String::isNotBlank) ?: return@produceState
        value = runCatching {
            val mediaItem = MediaItem.Builder()
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtworkUri(Uri.parse(safeUrl))
                        .build(),
                )
                .build()
            loadArtworkBitmap(
                context = context.applicationContext,
                mediaItem = mediaItem,
                size = android.util.Size(CloudCoverArtworkSizePx, CloudCoverArtworkSizePx),
            )
        }.getOrNull()
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(ComposeColor(0xFFEDEDED)),
        )
    }
}
