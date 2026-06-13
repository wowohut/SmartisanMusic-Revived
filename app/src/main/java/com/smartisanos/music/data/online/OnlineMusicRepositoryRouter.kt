package com.smartisanos.music.data.online

import android.content.Context
import androidx.media3.common.MediaItem

internal class OnlineMusicRepositoryRouter(
    context: Context,
    private val neteaseRepository: NeteaseOnlineMusicRepository =
        NeteaseOnlineMusicRepository(context.applicationContext),
) {

    fun repositoryFor(provider: OnlineMusicProvider): OnlineMusicProviderRepository {
        return when (provider) {
            OnlineMusicProvider.Netease -> neteaseRepository
        }
    }

    suspend fun resolvePlayableMediaItem(mediaItem: MediaItem): MediaItem? {
        val identity = mediaItem.onlineIdentityOrNull() ?: return null
        return when (identity.source) {
            OnlineMusicProvider.Netease.sourceId ->
                neteaseRepository.resolvePlayableMediaItem(mediaItem)
            else -> null
        }
    }

    suspend fun resolvePlayableMediaItems(
        mediaItems: List<MediaItem>,
        includeLyrics: Boolean,
    ): List<MediaItem> {
        return neteaseRepository.resolvePlayableMediaItems(
            mediaItems = mediaItems,
            includeLyrics = includeLyrics,
        )
    }

    suspend fun resolvePlayableItems(
        identities: List<OnlineTrackIdentity>,
        includeLyrics: Boolean,
    ): List<MediaItem> {
        val uniqueIdentities = identities.distinct()
        if (uniqueIdentities.isEmpty()) {
            return emptyList()
        }
        return resolveNeteaseItems(
            trackIds = uniqueIdentities
                .filter { identity -> identity.source == OnlineMusicProvider.Netease.sourceId }
                .map(OnlineTrackIdentity::trackId),
            includeLyrics = includeLyrics,
        )
    }

    suspend fun getMediaItem(identity: OnlineTrackIdentity): MediaItem? {
        val track = when (identity.source) {
            OnlineMusicProvider.Netease.sourceId -> neteaseRepository.getTrack(identity.trackId)
            else -> null
        }
        return track?.toMediaItem()
    }

    private suspend fun resolveNeteaseItems(
        trackIds: List<String>,
        includeLyrics: Boolean,
    ): List<MediaItem> {
        val normalizedTrackIds = trackIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (normalizedTrackIds.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            neteaseRepository.getTracks(normalizedTrackIds).mapNotNull { track ->
                neteaseRepository.resolvePlayableTrack(
                    track = track,
                    includeLyrics = includeLyrics,
                )
            }
        }.getOrDefault(emptyList())
    }
}
