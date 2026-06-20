package com.smartisanos.music.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.favorite.FavoriteSongsRepository
import com.smartisanos.music.data.online.NeteaseAccountActionStatus
import com.smartisanos.music.data.online.OnlineMusicProvider
import com.smartisanos.music.data.online.OnlineMusicRepositoryRouter
import com.smartisanos.music.data.online.onlineIdentityOrNull
import com.smartisanos.music.isExternalAudioLaunchItem
import com.smartisanos.music.playback.LocalPlaybackController
import com.smartisanos.music.playback.artworkRequestKey
import com.smartisanos.music.ui.shell.playback.LegacyPortPlaybackBar
import com.smartisanos.music.ui.shell.playback.legacyPlaybackBarSnapshot
import com.smartisanos.music.ui.shell.playback.loadLegacyArtworkBitmap
import com.smartisanos.music.ui.shell.playback.peekLegacyArtworkBitmap
import kotlinx.coroutines.launch

@Composable
fun GlobalPlaybackBar(
    modifier: Modifier = Modifier,
    onOpenPlayback: () -> Unit = {},
) {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current ?: return
    val favoriteRepository = remember(context.applicationContext) {
        FavoriteSongsRepository.getInstance(context.applicationContext)
    }
    val onlineMusicRepository = remember(context.applicationContext) {
        OnlineMusicRepositoryRouter(context.applicationContext)
    }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var snapshot by remember(controller) {
        mutableStateOf(controller.legacyPlaybackBarSnapshot())
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                snapshot = player.legacyPlaybackBarSnapshot()
            }
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }

    val mediaItem = snapshot.mediaItem ?: return
    val artworkRequestKey = mediaItem.artworkRequestKey()
    val artworkBitmap by produceState<Bitmap?>(
        initialValue = peekLegacyArtworkBitmap(mediaItem),
        artworkRequestKey,
    ) {
        value = peekLegacyArtworkBitmap(mediaItem) ?: value
        value = loadLegacyArtworkBitmap(context.applicationContext, mediaItem)
    }

    suspend fun setLocalFavoriteState(mediaId: String, liked: Boolean) {
        if (liked) {
            favoriteRepository.add(mediaId)
        } else {
            favoriteRepository.remove(mediaId)
        }
    }

    LegacyPortPlaybackBar(
        snapshot = snapshot,
        shown = true,
        favoriteIds = favoriteIds,
        artworkBitmap = artworkBitmap,
        onHidden = {},
        onOpenPlayback = onOpenPlayback,
        onToggleFavorite = { item ->
            if (item.isExternalAudioLaunchItem()) {
                return@LegacyPortPlaybackBar
            }
            val mediaId = item.mediaId.takeIf(String::isNotBlank) ?: return@LegacyPortPlaybackBar
            val shouldLike = mediaId !in favoriteIds
            val identity = item.onlineIdentityOrNull()
            scope.launch {
                if (identity?.source == OnlineMusicProvider.Netease.sourceId) {
                    val result = onlineMusicRepository.setTrackLiked(
                        identity = identity,
                        liked = shouldLike,
                    )
                    when (result.status) {
                        NeteaseAccountActionStatus.Success -> setLocalFavoriteState(mediaId, shouldLike)
                        NeteaseAccountActionStatus.RequiresLogin -> {
                            Toast.makeText(
                                context,
                                R.string.online_music_login_required,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        NeteaseAccountActionStatus.Failed -> {
                            Toast.makeText(
                                context,
                                R.string.netease_online_music_like_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                } else {
                    favoriteRepository.toggle(mediaId)
                }
            }
        },
        onPrevious = {
            controller.seekToPrevious()
        },
        onPlayPause = {
            if (snapshot.isPlaybackActive) {
                controller.pause()
            } else {
                controller.play()
            }
        },
        onNext = {
            controller.seekToNext()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(GlobalPlaybackBarHeight),
    )
}

private val GlobalPlaybackBarHeight = 67.dp
