package com.smartisanos.music.data.online

import android.content.Context
import android.net.Uri
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

    suspend fun resolvePlayableMediaItem(
        mediaItem: MediaItem,
        includeLyrics: Boolean = true,
        forceRefresh: Boolean = false,
    ): MediaItem? {
        val identity = mediaItem.onlineIdentityOrNull() ?: return null
        return when (identity.source) {
            OnlineMusicProvider.Netease.sourceId ->
                neteaseRepository.resolvePlayableMediaItem(
                    mediaItem = mediaItem,
                    includeLyrics = includeLyrics,
                    forceRefresh = forceRefresh,
                )
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

    suspend fun resolvePlaybackUri(identity: OnlineTrackIdentity): Uri {
        return when (identity.source) {
            OnlineMusicProvider.Netease.sourceId -> neteaseRepository.resolvePlaybackUri(identity)
            else -> throw OnlinePlaybackResolutionException(
                reason = OnlinePlaybackFailureReason.Unavailable,
                message = "Unsupported online source ${identity.source}",
            )
        }
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

    suspend fun lyrics(identity: OnlineTrackIdentity): OnlineLyrics? {
        return when (identity.source) {
            OnlineMusicProvider.Netease.sourceId -> neteaseRepository.lyrics(identity)
            else -> null
        }
    }

    suspend fun setTrackLiked(
        identity: OnlineTrackIdentity,
        liked: Boolean,
    ): NeteaseAccountActionResult {
        return when (identity.source) {
            OnlineMusicProvider.Netease.sourceId -> neteaseRepository.setTrackLiked(
                trackId = identity.trackId,
                liked = liked,
            )
            else -> NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
    }

    suspend fun addTracksToAccountPlaylist(
        playlist: OnlineAccountPlaylist,
        identities: List<OnlineTrackIdentity>,
    ): NeteaseAccountActionResult {
        val trackIds = identities
            .asSequence()
            .filter { identity -> identity.source == playlist.provider.sourceId }
            .map(OnlineTrackIdentity::trackId)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (trackIds.isEmpty()) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        return repositoryFor(playlist.provider).addTracksToAccountPlaylist(
            playlist = playlist,
            trackIds = trackIds,
        )
    }

    suspend fun removeTracksFromAccountPlaylist(
        playlist: OnlineAccountPlaylist,
        identities: List<OnlineTrackIdentity>,
    ): NeteaseAccountActionResult {
        val trackIds = identities
            .asSequence()
            .filter { identity -> identity.source == playlist.provider.sourceId }
            .map(OnlineTrackIdentity::trackId)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (trackIds.isEmpty()) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        return repositoryFor(playlist.provider).removeTracksFromAccountPlaylist(
            playlist = playlist,
            trackIds = trackIds,
        )
    }

    suspend fun deleteAccountPlaylist(
        playlist: OnlineAccountPlaylist,
    ): NeteaseAccountActionResult {
        return repositoryFor(playlist.provider).deleteAccountPlaylist(playlist)
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
