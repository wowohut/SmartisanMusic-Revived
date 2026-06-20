package com.smartisanos.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PlaybackMetadataPreloader(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var preloadJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
            ) {
                schedulePreload()
            }
        }
    }

    fun start() {
        player.addListener(listener)
        schedulePreload()
    }

    fun stop() {
        player.removeListener(listener)
        preloadJob?.cancel()
        preloadJob = null
    }

    private fun schedulePreload() {
        val mediaItems = player.nowPlayingPreloadItems()
        preloadJob?.cancel()
        if (mediaItems.isEmpty()) {
            preloadJob = null
            return
        }
        preloadJob = scope.launch(Dispatchers.IO) {
            delay(MetadataPreloadDebounceMs)
            for (mediaItem in mediaItems) {
                NowPlayingArtworkRepository.preload(appContext, mediaItem)
                NowPlayingLyricsRepository.load(
                    context = appContext,
                    mediaItem = mediaItem,
                    rememberMissing = false,
                )
            }
        }
    }
}

private fun Player.nowPlayingPreloadItems(): List<MediaItem> {
    val itemCount = mediaItemCount
    if (itemCount <= 0) {
        return emptyList()
    }
    val currentIndex = currentMediaItemIndex.takeIf { it in 0 until itemCount } ?: 0
    val candidateIndices = buildList {
        add(currentIndex)
        nextMediaItemIndex
            .takeIf { it in 0 until itemCount }
            ?.let(::add)
    }
    val seenArtworkKeys = linkedSetOf<ArtworkRequestKey>()
    return candidateIndices
        .asSequence()
        .map(::getMediaItemAt)
        .filter { mediaItem -> seenArtworkKeys.add(mediaItem.artworkRequestKey()) }
        .toList()
}

private const val MetadataPreloadDebounceMs = 150L
