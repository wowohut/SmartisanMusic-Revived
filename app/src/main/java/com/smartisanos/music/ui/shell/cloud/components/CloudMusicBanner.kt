package com.smartisanos.music.ui.shell.cloud.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineBanner
import com.smartisanos.music.data.online.OnlineMusicProvider
import com.smartisanos.music.playback.loadArtworkBitmap
import com.smartisanos.music.ui.shell.cloud.CloudBannerArtworkHeightPx
import com.smartisanos.music.ui.shell.cloud.CloudBannerArtworkWidthPx
import com.smartisanos.music.ui.shell.cloud.CloudBannerAutoScrollMs
import com.smartisanos.music.ui.shell.cloud.CloudBannerHeight
import com.smartisanos.music.ui.shell.cloud.CloudBannerMaxCount
import kotlinx.coroutines.delay

@Composable
internal fun CloudMusicBannerStrip(
    banners: List<OnlineBanner>,
    active: Boolean,
    onBannerClick: (OnlineBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackTitle = stringResource(R.string.cloud_music_banner_fallback_title)
    val fallbackSubtitle = stringResource(R.string.cloud_music_banner_fallback_subtitle)
    val fallbackBanner = remember(fallbackTitle, fallbackSubtitle) {
        OnlineBanner(
            provider = OnlineMusicProvider.Netease,
            bannerId = "netease-fallback",
            title = fallbackTitle,
            subtitle = fallbackSubtitle,
        )
    }
    val visibleBanners = remember(banners, fallbackBanner) {
        banners
            .filter { banner -> banner.title.isNotBlank() || !banner.imageUrl.isNullOrBlank() }
            .take(CloudBannerMaxCount)
            .ifEmpty { listOf(fallbackBanner) }
    }
    var currentIndex by remember(visibleBanners) { mutableStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, visibleBanners.lastIndex)

    LaunchedEffect(active, visibleBanners) {
        if (!active || visibleBanners.size <= 1) {
            return@LaunchedEffect
        }
        while (true) {
            delay(CloudBannerAutoScrollMs)
            currentIndex = (currentIndex + 1) % visibleBanners.size
        }
    }

    Box(
        modifier = modifier
            .height(CloudBannerHeight)
            .background(ComposeColor.White)
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(5.dp))
                .border(
                    width = 0.67.dp,
                    color = ComposeColor(0x1F000000),
                    shape = RoundedCornerShape(5.dp),
                ),
        ) {
            AnimatedContent(
                targetState = visibleBanners[safeIndex],
                transitionSpec = {
                    (
                        fadeIn(animationSpec = tween(220)) +
                            slideInVertically(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                initialOffsetY = { height -> height / 8 },
                            )
                        ) togetherWith (
                        fadeOut(animationSpec = tween(160)) +
                            slideOutVertically(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                targetOffsetY = { height -> -height / 10 },
                            )
                        )
                },
                label = "cloud music banner transition",
                modifier = Modifier.matchParentSize(),
            ) { banner ->
                val bannerClickable = !banner.targetTrackId.isNullOrBlank() ||
                    !banner.targetAlbumId.isNullOrBlank() ||
                    !banner.targetPlaylistId.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            enabled = bannerClickable,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onBannerClick(banner) },
                        ),
                ) {
                    CloudMusicBannerImage(
                        imageUrl = banner.imageUrl,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(ComposeColor(0x99000000))
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Text(
                                text = banner.title,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = ComposeColor.White,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            banner.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = ComposeColor(0xCCFFFFFF),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (visibleBanners.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 9.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visibleBanners.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .width(if (index == safeIndex) 12.dp else 5.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (index == safeIndex) {
                                        ComposeColor.White
                                    } else {
                                        ComposeColor(0x80FFFFFF)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
    CloudMusicDivider()
}

@Composable
internal fun CloudMusicBannerImage(
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
                size = Size(CloudBannerArtworkWidthPx, CloudBannerArtworkHeightPx),
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
            modifier = modifier.background(ComposeColor(0xFFB9312D)),
        )
    }
}
