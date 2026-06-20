package com.smartisanos.music.data.online

import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.IOException

internal enum class OnlineMusicProvider(
    val sourceId: String,
) {
    Netease(sourceId = "netease"),
}

internal const val OnlineMediaIdPrefix = "online:"
internal const val OnlineTrackExtraKey = "com.smartisanos.music.extra.ONLINE_TRACK"
internal const val OnlineProviderExtraKey = "com.smartisanos.music.extra.ONLINE_PROVIDER"
internal const val OnlineSourceExtraKey = "com.smartisanos.music.extra.ONLINE_SOURCE"
internal const val OnlineTrackIdExtraKey = "com.smartisanos.music.extra.ONLINE_TRACK_ID"
internal const val OnlineLyricsExtraKey = "com.smartisanos.music.extra.ONLINE_LYRICS"
internal const val OnlineTranslatedLyricsExtraKey = "com.smartisanos.music.extra.ONLINE_TRANSLATED_LYRICS"
internal const val OnlinePlaybackResolvedAtExtraKey = "com.smartisanos.music.extra.ONLINE_PLAYBACK_RESOLVED_AT"
internal const val OnlinePlaybackUriScheme = "smartisan-online"

internal data class OnlineAccountPlaylist(
    val provider: OnlineMusicProvider,
    val playlistId: String,
    val title: String,
    val trackCount: Int,
    val isLikedSongs: Boolean = false,
    val isEditable: Boolean = false,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
)

internal data class OnlineAccountPlaylistCreateResult(
    val status: NeteaseAccountActionStatus,
    val playlist: OnlineAccountPlaylist? = null,
    val code: Int? = null,
)

internal data class OnlineArtist(
    val provider: OnlineMusicProvider,
    val artistId: String,
    val name: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
)

internal data class OnlineArtistIntroduction(
    val title: String,
    val text: String,
)

internal data class OnlineBanner(
    val provider: OnlineMusicProvider,
    val bannerId: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val targetTrackId: String? = null,
    val targetAlbumId: String? = null,
    val targetPlaylistId: String? = null,
)

internal enum class OnlinePlaylistKind {
    Featured,
    Chart,
}

internal data class OnlinePlaylist(
    val provider: OnlineMusicProvider,
    val playlistId: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val kind: OnlinePlaylistKind = OnlinePlaylistKind.Featured,
)

internal data class OnlineAlbum(
    val provider: OnlineMusicProvider,
    val albumId: String,
    val title: String,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val publishTimeMs: Long = 0L,
)

internal data class OnlineRadio(
    val provider: OnlineMusicProvider,
    val radioId: String,
    val title: String,
    val subtitle: String? = null,
    val category: String? = null,
    val creator: String? = null,
    val artworkUrl: String? = null,
    val programCount: Int = 0,
    val playCount: Long = 0L,
)

internal data class OnlineMusicHome(
    val tracks: List<OnlineTrack> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
    val charts: List<OnlinePlaylist> = emptyList(),
    val albums: List<OnlineAlbum> = emptyList(),
    val artists: List<OnlineArtist> = emptyList(),
)

internal data class OnlineRadioHome(
    val tracks: List<OnlineTrack> = emptyList(),
    val radios: List<OnlineRadio> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && radios.isEmpty()
}

internal data class OnlineSearchResults(
    val query: String,
    val tracks: List<OnlineTrack> = emptyList(),
    val artists: List<OnlineArtist> = emptyList(),
    val albums: List<OnlineAlbum> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
) {
    val hasResults: Boolean
        get() = tracks.isNotEmpty() ||
            artists.isNotEmpty() ||
            albums.isNotEmpty() ||
            playlists.isNotEmpty()
}

internal data class OnlineSearchHotKeyword(
    val keyword: String,
    val subtitle: String? = null,
    val score: Long = 0L,
)

internal enum class OnlinePlaybackFailureReason {
    LoginRequired,
    PreviewOnly,
    Unavailable,
}

internal class OnlinePlaybackResolutionException(
    val reason: OnlinePlaybackFailureReason,
    message: String,
) : IOException(message)

internal fun Throwable.onlinePlaybackFailureReasonOrNull(): OnlinePlaybackFailureReason? {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is OnlinePlaybackResolutionException) {
            return cause.reason
        }
        cause = cause.cause
    }
    return null
}

internal enum class OnlineCacheRefreshEventKind {
    Started,
    Finished,
}

internal data class OnlineCacheRefreshEvent(
    val provider: OnlineMusicProvider,
    val cacheKey: String,
    val kind: OnlineCacheRefreshEventKind,
)

internal interface OnlineMusicProviderRepository {
    val provider: OnlineMusicProvider

    val cacheRefreshEvents: Flow<OnlineCacheRefreshEvent>
        get() = emptyFlow()

    suspend fun search(query: String): List<OnlineTrack>

    suspend fun searchAll(query: String): OnlineSearchResults {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return OnlineSearchResults(query = normalizedQuery)
        }
        return OnlineSearchResults(
            query = normalizedQuery,
            tracks = search(normalizedQuery),
            artists = searchArtists(normalizedQuery),
            albums = searchAlbums(normalizedQuery),
            playlists = searchPlaylists(normalizedQuery),
        )
    }

    suspend fun searchArtists(query: String): List<OnlineArtist> = emptyList()

    suspend fun searchAlbums(query: String): List<OnlineAlbum> = emptyList()

    suspend fun searchPlaylists(query: String): List<OnlinePlaylist> = emptyList()

    suspend fun searchHotKeywords(): List<OnlineSearchHotKeyword> = emptyList()

    suspend fun track(trackId: String): OnlineTrack? = null

    suspend fun featuredTracks(): List<OnlineTrack>

    suspend fun featuredHome(): OnlineMusicHome = OnlineMusicHome(
        tracks = featuredTracks(),
        playlists = featuredPlaylists(),
        charts = featuredCharts(),
        albums = featuredAlbums(),
        artists = featuredArtists(),
    )

    suspend fun featuredBanners(): List<OnlineBanner> = emptyList()

    suspend fun featuredPlaylists(): List<OnlinePlaylist> = emptyList()

    suspend fun featuredCharts(): List<OnlinePlaylist> = emptyList()

    suspend fun featuredAlbums(): List<OnlineAlbum> = emptyList()

    suspend fun featuredArtists(): List<OnlineArtist> = emptyList()

    suspend fun featuredRadioTracks(): List<OnlineTrack> = emptyList()

    suspend fun featuredRadios(): List<OnlineRadio> = emptyList()

    suspend fun featuredRadioHome(): OnlineRadioHome = OnlineRadioHome(
        tracks = featuredRadioTracks(),
        radios = featuredRadios(),
    )

    suspend fun accountPlaylists(): List<OnlineAccountPlaylist>? = null

    suspend fun accountAlbums(): List<OnlineAlbum>? = null

    suspend fun accountRadios(): List<OnlineRadio>? = null

    suspend fun accountLikedTrackIds(): Set<String>? = null

    suspend fun addTracksToAccountPlaylist(
        playlist: OnlineAccountPlaylist,
        trackIds: List<String>,
    ): NeteaseAccountActionResult = NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)

    suspend fun removeTracksFromAccountPlaylist(
        playlist: OnlineAccountPlaylist,
        trackIds: List<String>,
    ): NeteaseAccountActionResult = NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)

    suspend fun deleteAccountPlaylist(
        playlist: OnlineAccountPlaylist,
    ): NeteaseAccountActionResult = NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)

    suspend fun createAccountPlaylist(name: String): OnlineAccountPlaylistCreateResult {
        return OnlineAccountPlaylistCreateResult(NeteaseAccountActionStatus.Failed)
    }

    suspend fun accountPlaylistTracks(playlist: OnlineAccountPlaylist): List<OnlineTrack> = emptyList()

    suspend fun playlistTracks(playlist: OnlinePlaylist): List<OnlineTrack> = emptyList()

    suspend fun albumTracks(album: OnlineAlbum): List<OnlineTrack> = emptyList()

    suspend fun artistTopTracks(artist: OnlineArtist): List<OnlineTrack> = emptyList()

    suspend fun artistAlbums(artist: OnlineArtist): List<OnlineAlbum> = emptyList()

    suspend fun artistIntroduction(artist: OnlineArtist): List<OnlineArtistIntroduction> = emptyList()

    suspend fun radioTracks(radio: OnlineRadio): List<OnlineTrack> = emptyList()

    suspend fun lyrics(identity: OnlineTrackIdentity): OnlineLyrics? = null

    suspend fun resolvePlayableMediaItem(
        mediaItem: MediaItem,
        includeLyrics: Boolean = true,
        forceRefresh: Boolean = false,
    ): MediaItem?

    suspend fun resolvePlayableMediaItems(
        mediaItems: List<MediaItem>,
        includeLyrics: Boolean = false,
    ): List<MediaItem> {
        return mediaItems.mapNotNull { item ->
            resolvePlayableMediaItem(
                mediaItem = item,
                includeLyrics = includeLyrics,
            )
        }
    }
}

internal fun MediaItem.withOnlinePlaybackPlaceholderUri(): MediaItem {
    if (!isOnlineMediaItem() || localConfiguration?.uri != null) {
        return this
    }
    val identity = onlineIdentityOrNull() ?: return this
    return buildUpon()
        .setUri(identity.toOnlinePlaybackUri())
        .setMimeType("audio/mpeg")
        .setCustomCacheKey(identity.toOnlinePlaybackCacheKey())
        .build()
}

internal fun Uri.onlinePlaybackUriIdentityOrNull(): OnlineTrackIdentity? {
    if (scheme != OnlinePlaybackUriScheme) {
        return null
    }
    val source = authority?.takeIf(String::isNotBlank) ?: return null
    val trackId = pathSegments.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
    return OnlineTrackIdentity(source = source, trackId = trackId)
}

private fun OnlineTrackIdentity.toOnlinePlaybackUri(): Uri {
    return Uri.Builder()
        .scheme(OnlinePlaybackUriScheme)
        .authority(source)
        .appendPath(trackId)
        .build()
}
