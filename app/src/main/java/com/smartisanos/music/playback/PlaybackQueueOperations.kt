package com.smartisanos.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.isExternalAudioLaunchItem
import kotlin.random.Random

internal fun Player?.replaceQueueAndPlay(
    mediaItems: List<MediaItem>,
    startIndex: Int = 0,
    shuffleModeEnabled: Boolean = false,
) {
    val player = this ?: return
    if (mediaItems.isEmpty()) {
        return
    }

    val safeStartIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
    player.shuffleModeEnabled = shuffleModeEnabled
    player.setMediaItems(mediaItems, safeStartIndex, 0L)
    player.prepare()
    player.play()
}

internal fun Player?.replaceQueueAndPlayShuffled(
    mediaItems: List<MediaItem>,
    random: Random = Random.Default,
) {
    val player = this ?: return
    if (mediaItems.isEmpty()) {
        return
    }
    player.replaceQueueAndPlay(
        mediaItems = mediaItems,
        startIndex = random.nextInt(mediaItems.size),
        shuffleModeEnabled = true,
    )
}

internal val MediaItem.stableKey: String?
    get() = mediaMetadata.extras
        ?.getString(LocalAudioLibrary.StableKeyExtraKey)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

internal fun Player.deduplicateQueueCandidates(candidates: List<MediaItem>): List<MediaItem> {
    if (candidates.isEmpty()) {
        return emptyList()
    }
    val seenStableKeys = linkedSetOf<String>()
    val seenMediaIds = linkedSetOf<String>()
    val seenMediaIdsWithoutStableKey = linkedSetOf<String>()
    for (index in 0 until mediaItemCount) {
        val item = getMediaItemAt(index)
        val mediaId = item.mediaId.trim().takeIf(String::isNotEmpty)
        val stableKey = item.stableKey
        stableKey?.let(seenStableKeys::add)
        mediaId?.let(seenMediaIds::add)
        if (stableKey == null) {
            mediaId?.let(seenMediaIdsWithoutStableKey::add)
        }
    }
    return candidates.filter { item ->
        if (item.isExternalAudioLaunchItem()) {
            return@filter true
        }
        val stableKey = item.stableKey
        val mediaId = item.mediaId.trim()
        val normalizedMediaId = mediaId.takeIf(String::isNotEmpty)
        val alreadyQueued = if (stableKey != null) {
            stableKey in seenStableKeys ||
                normalizedMediaId?.let(seenMediaIdsWithoutStableKey::contains) == true
        } else {
            normalizedMediaId?.let(seenMediaIds::contains) == true
        }
        if (alreadyQueued) {
            false
        } else {
            stableKey?.let(seenStableKeys::add)
            normalizedMediaId?.let(seenMediaIds::add)
            if (stableKey == null) {
                normalizedMediaId?.let(seenMediaIdsWithoutStableKey::add)
            }
            true
        }
    }
}

internal fun Player?.removeMediaItemsByMediaIds(mediaIds: Set<String>) {
    val player = this ?: return
    val normalizedMediaIds = mediaIds.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    if (normalizedMediaIds.isEmpty()) {
        return
    }

    for (index in player.mediaItemCount - 1 downTo 0) {
        if (player.getMediaItemAt(index).mediaId in normalizedMediaIds) {
            player.removeMediaItem(index)
        }
    }
}

internal fun Player?.removeMediaItemsMatching(predicate: (MediaItem) -> Boolean) {
    val player = this ?: return
    var rangeEnd = player.mediaItemCount
    var index = rangeEnd - 1
    while (index >= 0) {
        if (!predicate(player.getMediaItemAt(index))) {
            rangeEnd = index
            index -= 1
            continue
        }

        var rangeStart = index
        while (rangeStart > 0 && predicate(player.getMediaItemAt(rangeStart - 1))) {
            rangeStart -= 1
        }
        player.removeMediaItems(rangeStart, rangeEnd)
        rangeEnd = rangeStart
        index = rangeStart - 1
    }
}
