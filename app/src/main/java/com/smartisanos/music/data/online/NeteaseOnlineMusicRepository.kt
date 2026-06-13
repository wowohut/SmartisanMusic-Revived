package com.smartisanos.music.data.online

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisanos.music.data.settings.NeteaseAudioQuality
import com.smartisanos.music.data.settings.OnlineMusicSettingsStore
import com.smartisanos.music.data.settings.fallbackCandidates
import com.smartisanos.music.playback.LocalAudioLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val NeteaseSourceId = OnlineMusicProvider.Netease.sourceId
private const val SearchLimit = 30
private const val ArtistSearchLimit = 30
private const val AlbumSearchLimit = 30
private const val PlaylistSearchLimit = 30
private const val HotSearchLimit = 20
private const val FeaturedArtistLimit = 30
private const val FeaturedBannerLimit = 6
private const val FeaturedHomeTrackLimit = 12
private const val FeaturedPlaylistLimit = 18
private const val FeaturedChartLimit = 12
private const val FeaturedAlbumLimit = 18
private const val FeaturedRadioTrackLimit = 18
private const val FeaturedRadioLimit = 18
private const val ArtistTopTracksLimit = 60
private const val ArtistAlbumLimit = 12
private const val AlbumTracksLimit = 80
private const val RadioTracksLimit = 80
private const val FeaturedPlaylistId = "3778678"
private const val FeaturedLimit = 30
private const val AccountPlaylistLimit = 50
private const val AccountTracksLimit = 100
private const val HttpTimeoutMs = 15_000
private const val MinSongDurationForPreviewDetectionMs = 60_000L
private const val MaxKnownPreviewDurationMs = 45_000L
private const val MaxPreviewDurationRatio = 0.5
private const val OnlinePlaybackUrlMaxAgeMs = 15 * 60 * 1000L
private const val UserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

internal data class OnlineTrack(
    val source: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUrl: String?,
) {
    val mediaId: String = buildOnlineMediaId(source, trackId)
}

internal data class OnlineLyrics(
    val lyric: String?,
    val translatedLyric: String?,
)

internal data class OnlinePlaybackUrl(
    val url: String,
    val mimeType: String?,
)

internal data class NeteaseAccountProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
)

internal data class NeteasePlaylistSummary(
    val playlistId: String,
    val name: String,
    val trackCount: Int,
    val specialType: Int,
) {
    val isLikedSongs: Boolean
        get() = specialType == 5
}

internal enum class NeteasePlaybackParseStatus {
    Success,
    Preview,
    RequiresLogin,
    Unavailable,
}

internal data class NeteasePlaybackParseResult(
    val status: NeteasePlaybackParseStatus,
    val playbackUrl: OnlinePlaybackUrl? = null,
)

internal class NeteaseOnlineMusicRepository(
    private val authStore: NeteaseAuthStore? = null,
    playbackQualityProvider: suspend () -> NeteaseAudioQuality = { NeteaseAudioQuality.ExHigh },
    private val client: NeteaseCloudMusicClient = NeteaseCloudMusicClient(
        cookieProvider = { authStore?.getCookies().orEmpty() },
        playbackQualityProvider = playbackQualityProvider,
    ),
) : OnlineMusicProviderRepository {

    override val provider: OnlineMusicProvider = OnlineMusicProvider.Netease

    constructor(context: Context) : this(
        authStore = NeteaseAuthStore(context.applicationContext),
        playbackQualityProvider = {
            OnlineMusicSettingsStore(context.applicationContext)
                .readSettings()
                .neteasePlaybackQuality
        },
    )

    override suspend fun search(query: String): List<OnlineTrack> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        return client.searchSongs(normalizedQuery, limit = SearchLimit)
    }

    override suspend fun searchAll(query: String): OnlineSearchResults {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return OnlineSearchResults(query = normalizedQuery)
        }
        return OnlineSearchResults(
            query = normalizedQuery,
            tracks = runCatching {
                client.searchSongs(normalizedQuery, limit = SearchLimit)
            }.getOrDefault(emptyList()),
            artists = runCatching {
                client.searchArtists(normalizedQuery, limit = ArtistSearchLimit)
            }.getOrDefault(emptyList()),
            albums = runCatching {
                client.searchAlbums(normalizedQuery, limit = AlbumSearchLimit)
            }.getOrDefault(emptyList()),
            playlists = runCatching {
                client.searchPlaylists(normalizedQuery, limit = PlaylistSearchLimit)
            }.getOrDefault(emptyList()),
        )
    }

    override suspend fun searchArtists(query: String): List<OnlineArtist> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        return client.searchArtists(normalizedQuery, limit = ArtistSearchLimit)
    }

    override suspend fun searchAlbums(query: String): List<OnlineAlbum> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        return client.searchAlbums(normalizedQuery, limit = AlbumSearchLimit)
    }

    override suspend fun searchPlaylists(query: String): List<OnlinePlaylist> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        return client.searchPlaylists(normalizedQuery, limit = PlaylistSearchLimit)
    }

    override suspend fun searchHotKeywords(): List<OnlineSearchHotKeyword> {
        return client.getHotSearchKeywords(limit = HotSearchLimit)
    }

    override suspend fun featuredTracks(): List<OnlineTrack> {
        return featuredSongs()
    }

    override suspend fun featuredHome(): OnlineMusicHome {
        return OnlineMusicHome(
            tracks = runCatching {
                featuredSongs().take(FeaturedHomeTrackLimit)
            }.getOrDefault(emptyList()),
            playlists = runCatching {
                featuredPlaylists()
            }.getOrDefault(emptyList()),
            charts = runCatching {
                featuredCharts()
            }.getOrDefault(emptyList()),
            albums = runCatching {
                featuredAlbums()
            }.getOrDefault(emptyList()),
            artists = runCatching {
                featuredArtists()
            }.getOrDefault(emptyList()),
        )
    }

    override suspend fun featuredBanners(): List<OnlineBanner> {
        return client.getBanners(limit = FeaturedBannerLimit)
    }

    override suspend fun featuredPlaylists(): List<OnlinePlaylist> {
        return client.getPersonalizedPlaylists(limit = FeaturedPlaylistLimit)
    }

    override suspend fun featuredCharts(): List<OnlinePlaylist> {
        return client.getToplists(limit = FeaturedChartLimit)
    }

    override suspend fun featuredAlbums(): List<OnlineAlbum> {
        return client.getNewAlbums(limit = FeaturedAlbumLimit)
    }

    override suspend fun featuredArtists(): List<OnlineArtist> {
        return client.getTopArtists(limit = FeaturedArtistLimit)
    }

    override suspend fun featuredRadioTracks(): List<OnlineTrack> {
        return client.getRecommendedRadioPrograms(limit = FeaturedRadioTrackLimit)
    }

    override suspend fun featuredRadios(): List<OnlineRadio> {
        return client.getRecommendedRadios(limit = FeaturedRadioLimit)
    }

    override suspend fun featuredRadioHome(): OnlineRadioHome {
        return OnlineRadioHome(
            tracks = runCatching {
                featuredRadioTracks()
            }.getOrDefault(emptyList()),
            radios = runCatching {
                featuredRadios()
            }.getOrDefault(emptyList()),
        )
    }

    override suspend fun track(trackId: String): OnlineTrack? {
        return getTrack(trackId)
    }

    suspend fun featuredSongs(): List<OnlineTrack> {
        return client.getPlaylistSongs(playlistId = FeaturedPlaylistId, limit = FeaturedLimit)
    }

    suspend fun getTrack(trackId: String): OnlineTrack? {
        val normalizedTrackId = trackId.trim().takeIf(String::isNotEmpty) ?: return null
        return client.getSongs(listOf(normalizedTrackId)).firstOrNull()
    }

    suspend fun getTracks(trackIds: List<String>): List<OnlineTrack> {
        val normalizedIds = trackIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (normalizedIds.isEmpty()) {
            return emptyList()
        }
        return client.getSongs(normalizedIds)
    }

    suspend fun currentUserProfile(): NeteaseAccountProfile? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = runCatching {
            client.getCurrentUserProfile()
        }.getOrNull()
        if (profile != null) {
            authStore.saveProfile(profile)
        }
        return profile ?: state.profile
    }

    suspend fun currentUserPlaylists(limit: Int = AccountPlaylistLimit): List<NeteasePlaylistSummary>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = currentUserProfile() ?: state.profile ?: return null
        return client.getUserPlaylists(
            userId = profile.userId,
            limit = limit,
        )
    }

    suspend fun currentUserLikedTracks(limit: Int = AccountTracksLimit): List<OnlineTrack>? {
        val likedPlaylist = currentUserPlaylists()
            ?.firstOrNull(NeteasePlaylistSummary::isLikedSongs)
            ?: return null
        return playlistTracks(
            playlist = likedPlaylist,
            limit = limit,
        )
    }

    override suspend fun accountPlaylists(): List<OnlineAccountPlaylist>? {
        return currentUserPlaylists()?.map { playlist ->
            OnlineAccountPlaylist(
                provider = provider,
                playlistId = playlist.playlistId,
                title = playlist.name,
                trackCount = playlist.trackCount,
                isLikedSongs = playlist.isLikedSongs,
            )
        }
    }

    override suspend fun accountPlaylistTracks(playlist: OnlineAccountPlaylist): List<OnlineTrack> {
        if (playlist.provider != provider) {
            return emptyList()
        }
        return client.getPlaylistSongs(
            playlistId = playlist.playlistId,
            limit = AccountTracksLimit,
        )
    }

    override suspend fun playlistTracks(playlist: OnlinePlaylist): List<OnlineTrack> {
        if (playlist.provider != provider) {
            return emptyList()
        }
        return client.getPlaylistSongs(
            playlistId = playlist.playlistId,
            limit = AccountTracksLimit,
        )
    }

    override suspend fun albumTracks(album: OnlineAlbum): List<OnlineTrack> {
        if (album.provider != provider) {
            return emptyList()
        }
        return client.getAlbumSongs(
            albumId = album.albumId,
            limit = AlbumTracksLimit,
        )
    }

    override suspend fun artistTopTracks(artist: OnlineArtist): List<OnlineTrack> {
        if (artist.provider != provider) {
            return emptyList()
        }
        return client.getArtistTopSongs(
            artistId = artist.artistId,
            limit = ArtistTopTracksLimit,
        )
    }

    override suspend fun artistAlbums(artist: OnlineArtist): List<OnlineAlbum> {
        if (artist.provider != provider) {
            return emptyList()
        }
        return client.getArtistAlbums(
            artistId = artist.artistId,
            limit = ArtistAlbumLimit,
        )
    }

    override suspend fun artistIntroduction(artist: OnlineArtist): List<OnlineArtistIntroduction> {
        if (artist.provider != provider) {
            return emptyList()
        }
        return client.getArtistIntroduction(artist.artistId)
    }

    override suspend fun radioTracks(radio: OnlineRadio): List<OnlineTrack> {
        if (radio.provider != provider) {
            return emptyList()
        }
        return client.getRadioPrograms(
            radioId = radio.radioId,
            limit = RadioTracksLimit,
        )
    }

    suspend fun playlistTracks(
        playlist: NeteasePlaylistSummary,
        limit: Int = AccountTracksLimit,
    ): List<OnlineTrack> {
        return client.getPlaylistSongs(
            playlistId = playlist.playlistId,
            limit = limit,
        )
    }

    suspend fun resolvePlayableTrack(
        track: OnlineTrack,
        includeLyrics: Boolean = true,
    ): MediaItem? {
        if (track.source != NeteaseSourceId) {
            return null
        }
        val playbackUrl = client.getPlaybackUrl(
            trackId = track.trackId,
            originalDurationMs = track.durationMs,
        ) ?: return null
        val lyrics = if (includeLyrics) {
            runCatching { client.getLyrics(track.trackId) }.getOrNull()
        } else {
            null
        }
        return track.toMediaItem(
            playbackUrl = playbackUrl.url,
            mimeType = playbackUrl.mimeType,
            lyrics = lyrics,
        )
    }

    override suspend fun resolvePlayableMediaItem(mediaItem: MediaItem): MediaItem? {
        val identity = mediaItem.onlineIdentityOrNull() ?: return null
        if (identity.source != NeteaseSourceId) {
            return null
        }
        val fallbackTrack = mediaItem.toOnlineTrackFallback(identity)
        val track = fallbackTrack ?: getTrack(identity.trackId) ?: return null
        return resolvePlayableTrack(track)
    }

    override suspend fun resolvePlayableMediaItems(
        mediaItems: List<MediaItem>,
        includeLyrics: Boolean,
    ): List<MediaItem> {
        val entries = mediaItems.mapNotNull { item ->
            val identity = item.onlineIdentityOrNull()
                ?.takeIf { identity -> identity.source == NeteaseSourceId }
                ?: return@mapNotNull null
            item to identity
        }
        if (entries.isEmpty()) {
            return emptyList()
        }
        val unresolvedEntries = entries.filter { (item, _) ->
            item.localConfiguration?.uri == null || item.shouldRefreshOnlinePlaybackUrl()
        }
        val detailsById = if (unresolvedEntries.isEmpty()) {
            emptyMap()
        } else {
            getTracks(unresolvedEntries.map { (_, identity) -> identity.trackId })
                .associateBy(OnlineTrack::trackId)
        }
        return entries.mapNotNull { (item, identity) ->
            if (item.localConfiguration?.uri != null && !item.shouldRefreshOnlinePlaybackUrl()) {
                return@mapNotNull item
            }
            val track = detailsById[identity.trackId]
                ?: item.toOnlineTrackFallback(identity)
                ?: return@mapNotNull null
            resolvePlayableTrack(
                track = track,
                includeLyrics = includeLyrics,
            )
        }
    }
}

internal class NeteaseCloudMusicClient(
    private val cookieProvider: () -> Map<String, String> = { emptyMap() },
    private val playbackQualityProvider: suspend () -> NeteaseAudioQuality = { NeteaseAudioQuality.ExHigh },
) {
    private val sessionCookieLock = Any()
    private val sessionCookies = linkedMapOf<String, String>()

    suspend fun searchSongs(query: String, limit: Int): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, SearchLimit)
        val url = "https://music.163.com/api/search/get/web" +
            "?s=${query.urlEncoded()}&type=1&limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        val songs = response.optJSONObject("result")
            ?.optJSONArray("songs")
            ?: return@withContext emptyList()
        val baseTracks = songs.toJsonObjects()
            .mapNotNull(::parseSong)
        val detailsById = getSongs(baseTracks.map(OnlineTrack::trackId))
            .associateBy(OnlineTrack::trackId)
        baseTracks.map { track -> detailsById[track.trackId] ?: track }
    }

    suspend fun searchArtists(query: String, limit: Int): List<OnlineArtist> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, ArtistSearchLimit)
        val url = "https://music.163.com/api/search/get/web" +
            "?s=${query.urlEncoded()}&type=100&limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        response.optJSONObject("result")
            ?.optJSONArray("artists")
            ?.toJsonObjects()
            ?.mapNotNull(::parseArtist)
            .orEmpty()
    }

    suspend fun searchAlbums(query: String, limit: Int): List<OnlineAlbum> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, AlbumSearchLimit)
        val url = "https://music.163.com/api/search/get/web" +
            "?s=${query.urlEncoded()}&type=10&limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        response.optJSONObject("result")
            ?.optJSONArray("albums")
            ?.toJsonObjects()
            ?.mapNotNull(::parseAlbum)
            .orEmpty()
    }

    suspend fun searchPlaylists(query: String, limit: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, PlaylistSearchLimit)
        val url = "https://music.163.com/api/search/get/web" +
            "?s=${query.urlEncoded()}&type=1000&limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        response.optJSONObject("result")
            ?.optJSONArray("playlists")
            ?.toJsonObjects()
            ?.mapNotNull { playlist ->
                parsePlaylist(
                    playlist = playlist,
                    kind = OnlinePlaylistKind.Featured,
                )
            }
            .orEmpty()
    }

    suspend fun getHotSearchKeywords(limit: Int): List<OnlineSearchHotKeyword> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, HotSearchLimit)
        val response = JSONObject(callWeApi("/hotsearchlist/get", emptyMap()))
        response.optJSONArray("data")
            ?.toJsonObjects()
            ?.mapNotNull(::parseHotSearchKeyword)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getTopArtists(limit: Int): List<OnlineArtist> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedArtistLimit)
        val url = "https://music.163.com/api/artist/top?limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        response.optJSONArray("artists")
            ?.toJsonObjects()
            ?.mapNotNull(::parseArtist)
            .orEmpty()
    }

    suspend fun getBanners(limit: Int): List<OnlineBanner> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedBannerLimit)
        val url = "https://music.163.com/api/v2/banner/get?clientType=pc"
        val response = JSONObject(readText(url))
        response.optJSONArray("banners")
            ?.toJsonObjects()
            ?.mapIndexedNotNull { index, banner -> parseBanner(banner, index) }
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getPersonalizedPlaylists(limit: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedPlaylistLimit)
        val url = "https://music.163.com/api/personalized/playlist?limit=$safeLimit"
        val response = JSONObject(readText(url))
        response.optJSONArray("result")
            ?.toJsonObjects()
            ?.mapNotNull { playlist ->
                parsePlaylist(
                    playlist = playlist,
                    kind = OnlinePlaylistKind.Featured,
                )
            }
            .orEmpty()
    }

    suspend fun getToplists(limit: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedChartLimit)
        val url = "https://music.163.com/api/toplist/detail"
        val response = JSONObject(readText(url))
        response.optJSONArray("list")
            ?.toJsonObjects()
            ?.mapNotNull { playlist ->
                parsePlaylist(
                    playlist = playlist,
                    kind = OnlinePlaylistKind.Chart,
                )
            }
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getNewAlbums(limit: Int): List<OnlineAlbum> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedAlbumLimit)
        val url = "https://music.163.com/api/album/new?area=ALL&limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        response.optJSONArray("albums")
            ?.toJsonObjects()
            ?.mapNotNull(::parseAlbum)
            .orEmpty()
    }

    suspend fun getArtistTopSongs(artistId: String, limit: Int): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val id = artistId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, ArtistTopTracksLimit)
        val url = "https://music.163.com/api/artist/${id.urlEncoded()}"
        val response = JSONObject(readText(url))
        response.optJSONArray("hotSongs")
            ?.toJsonObjects()
            ?.mapNotNull(::parseSong)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getArtistAlbums(artistId: String, limit: Int): List<OnlineAlbum> = withContext(Dispatchers.IO) {
        val id = artistId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, ArtistAlbumLimit)
        val url = "https://music.163.com/api/artist/albums/${id.urlEncoded()}?limit=$safeLimit&offset=0"
        val response = JSONObject(readText(url))
        response.optJSONArray("hotAlbums")
            ?.toJsonObjects()
            ?.mapNotNull(::parseAlbum)
            .orEmpty()
    }

    suspend fun getArtistIntroduction(artistId: String): List<OnlineArtistIntroduction> = withContext(Dispatchers.IO) {
        val id = artistId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val url = "https://music.163.com/api/artist/introduction?id=${id.urlEncoded()}"
        val response = JSONObject(readText(url))
        buildList {
            response.optNonBlankString("briefDesc")?.let { briefDesc ->
                add(
                    OnlineArtistIntroduction(
                        title = "简介",
                        text = briefDesc,
                    ),
                )
            }
            response.optJSONArray("introduction")
                ?.toJsonObjects()
                ?.mapNotNull(::parseArtistIntroduction)
                ?.let(::addAll)
        }
    }

    suspend fun getRecommendedRadioPrograms(limit: Int): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedRadioTrackLimit)
        val response = runCatching {
            JSONObject(readText("https://music.163.com/api/program/recommend/v1?limit=$safeLimit&offset=0"))
        }.getOrNull()
        val recommendedPrograms = response
            ?.optJSONArray("programs")
            ?.toJsonObjects()
            ?.mapNotNull(::parseProgramSong)
            .orEmpty()
        if (recommendedPrograms.isNotEmpty()) {
            return@withContext recommendedPrograms.take(safeLimit)
        }
        val fallbackResponse = JSONObject(readText("https://music.163.com/api/personalized/djprogram"))
        fallbackResponse.optJSONArray("result")
            ?.toJsonObjects()
            ?.mapNotNull { item -> item.optJSONObject("program") }
            ?.mapNotNull(::parseProgramSong)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getRecommendedRadios(limit: Int): List<OnlineRadio> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, FeaturedRadioLimit)
        val response = JSONObject(readText("https://music.163.com/api/djradio/recommend/v1"))
        response.optJSONArray("djRadios")
            ?.toJsonObjects()
            ?.mapNotNull(::parseRadio)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getRadioPrograms(radioId: String, limit: Int): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val id = radioId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, RadioTracksLimit)
        val url = "https://music.163.com/api/dj/program/byradio" +
            "?radioId=${id.urlEncoded()}&limit=$safeLimit&offset=0&asc=false"
        val response = JSONObject(readText(url))
        val code = response.optInt("code", 200)
        if (code != 200) {
            throw IOException("NetEase radio programs unavailable: code $code")
        }
        response.optJSONArray("programs")
            ?.toJsonObjects()
            ?.mapNotNull(::parseProgramSong)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getAlbumSongs(albumId: String, limit: Int): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val id = albumId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, AlbumTracksLimit)
        val url = "https://music.163.com/api/v1/album/${id.urlEncoded()}"
        val response = JSONObject(readText(url))
        response.optJSONArray("songs")
            ?.toJsonObjects()
            ?.mapNotNull(::parseSong)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getPlaylistSongs(playlistId: String, limit: Int): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val id = playlistId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 100)
        val url = "https://music.163.com/api/playlist/detail?id=${id.urlEncoded()}"
        val response = JSONObject(readText(url))
        response.optJSONObject("result")
            ?.optJSONArray("tracks")
            ?.toJsonObjects()
            ?.mapNotNull(::parseSong)
            ?.take(safeLimit)
            .orEmpty()
    }

    suspend fun getUserPlaylists(userId: Long, limit: Int): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        if (userId <= 0L) {
            return@withContext emptyList()
        }
        val safeLimit = limit.coerceIn(1, AccountPlaylistLimit)
        val url = "https://music.163.com/api/user/playlist/" +
            "?uid=$userId&limit=$safeLimit&offset=0"
        val response = readText(url)
        parseNeteaseUserPlaylistsResponse(response)
    }

    suspend fun getSongs(trackIds: List<String>): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val ids = trackIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (ids.isEmpty()) {
            return@withContext emptyList()
        }
        val idsJson = ids.joinToString(prefix = "[", postfix = "]")
        val url = "https://music.163.com/api/song/detail/?ids=${idsJson.urlEncoded()}"
        val response = JSONObject(readText(url))
        response.optJSONArray("songs")
            ?.toJsonObjects()
            ?.mapNotNull(::parseSong)
            .orEmpty()
    }

    suspend fun getPlaybackUrl(
        trackId: String,
        originalDurationMs: Long = 0L,
    ): OnlinePlaybackUrl? = withContext(Dispatchers.IO) {
        val id = trackId.trim().takeIf(String::isNotEmpty) ?: return@withContext null
        val idsJson = "[$id]"
        var restrictedPlaybackReturned = false
        for (quality in playbackQualityProvider().fallbackCandidates()) {
            val eapiResult = requestPlaybackUrlWithSessionRetry(originalDurationMs) {
                callEApi(
                    path = "/song/enhance/player/url/v1",
                    params = mapOf(
                        "ids" to idsJson,
                        "level" to quality.level,
                        "encodeType" to quality.encodeType,
                    ),
                )
            }
            when (eapiResult.status) {
                NeteasePlaybackParseStatus.Success -> return@withContext eapiResult.playbackUrl
                NeteasePlaybackParseStatus.Preview,
                NeteasePlaybackParseStatus.RequiresLogin -> restrictedPlaybackReturned = true
                NeteasePlaybackParseStatus.Unavailable -> Unit
            }
        }
        if (restrictedPlaybackReturned || hasLogin()) {
            null
        } else {
            resolveOuterPlaybackUrl(id)
        }
    }

    suspend fun getLyrics(trackId: String): OnlineLyrics = withContext(Dispatchers.IO) {
        val id = trackId.trim().takeIf(String::isNotEmpty) ?: return@withContext OnlineLyrics(null, null)
        val url = "https://music.163.com/api/song/lyric" +
            "?id=${id.urlEncoded()}&lv=-1&tv=-1&rv=-1&kv=-1&yv=-1&ytv=-1&yrv=-1"
        val response = JSONObject(readText(url))
        OnlineLyrics(
            lyric = response.optJSONObject("lrc")
                ?.optNonBlankString("lyric")
                ?: response.optJSONObject("yrc")?.optNonBlankString("lyric"),
            translatedLyric = response.optJSONObject("tlyric")
                ?.optNonBlankString("lyric")
                ?: response.optJSONObject("ytlrc")?.optNonBlankString("lyric"),
        )
    }

    suspend fun getCurrentUserProfile(): NeteaseAccountProfile? = withContext(Dispatchers.IO) {
        val response = callWeApi("/w/nuser/account/get", emptyMap())
        parseNeteaseAccountProfileResponse(response)
    }

    private fun resolveOuterPlaybackUrl(trackId: String): OnlinePlaybackUrl? {
        val url = "https://music.163.com/song/media/outer/url?id=${trackId.urlEncoded()}.mp3"
        val connection = openConnection(url, followRedirects = false).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
        }
        return connection.useResponse {
            val code = responseCode
            if (code !in 300..399) {
                return@useResponse null
            }
            val location = getHeaderField("Location")?.takeIf(String::isNotBlank)
                ?: return@useResponse null
            OnlinePlaybackUrl(
                url = location.normalizedPlayableUrl(),
                mimeType = "audio/mpeg",
            )
        }
    }

    private fun readText(url: String): String {
        val connection = openConnection(url, followRedirects = true)
        return connection.useResponse {
            val code = responseCode
            if (code !in 200..299) {
                throw IOException("NetEase request failed: HTTP $code")
            }
            inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }
    }

    private fun ensureWeapiSession() {
        runCatching {
            readText("https://music.163.com/")
        }
    }

    private fun callEApi(
        path: String,
        params: Map<String, String>,
    ): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val eapiPath = "/eapi$normalizedPath"
        val apiPath = "/api$normalizedPath"
        val url = "https://interface.music.163.com$eapiPath"
        val encryptedParams = NeteaseCrypto.encryptEApiParams(apiPath, params.toJsonObjectString())
        val body = "params=${encryptedParams.urlEncoded()}"
        val connection = openConnection(url, followRedirects = true).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return connection.useResponse {
            outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = responseCode
            if (code !in 200..299) {
                throw IOException("NetEase EAPI request failed: HTTP $code")
            }
            inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }
    }

    private fun callWeApi(
        path: String,
        params: Map<String, String>,
    ): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val csrf = effectiveCookies()["__csrf"].orEmpty()
        val url = "https://music.163.com/weapi$normalizedPath?csrf_token=${csrf.urlEncoded()}"
        val encryptedParams = NeteaseCrypto.encryptWeApiParams(params.toJsonObjectString())
        val body = encryptedParams.entries.joinToString("&") { (key, value) ->
            "${key.urlEncoded()}=${value.urlEncoded()}"
        }
        val connection = openConnection(url, followRedirects = true).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return connection.useResponse {
            outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = responseCode
            if (code !in 200..299) {
                throw IOException("NetEase WEAPI request failed: HTTP $code")
            }
            inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }
    }

    private fun openConnection(
        url: String,
        followRedirects: Boolean,
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = HttpTimeoutMs
            readTimeout = HttpTimeoutMs
            instanceFollowRedirects = followRedirects
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            setRequestProperty("Referer", "https://music.163.com/")
            setRequestProperty("User-Agent", UserAgent)
            buildCookieHeader().takeIf(String::isNotBlank)?.let { cookieHeader ->
                setRequestProperty("Cookie", cookieHeader)
            }
        }
    }

    private fun requestPlaybackUrlWithSessionRetry(
        originalDurationMs: Long,
        request: () -> String,
    ): NeteasePlaybackParseResult {
        var result = tryParsePlaybackUrlResponse(originalDurationMs, request)
        if (
            hasLogin() &&
            (
                result.status == NeteasePlaybackParseStatus.RequiresLogin ||
                    result.status == NeteasePlaybackParseStatus.Preview
                )
        ) {
            ensureWeapiSession()
            result = tryParsePlaybackUrlResponse(originalDurationMs, request)
        }
        return result
    }

    private fun tryParsePlaybackUrlResponse(
        originalDurationMs: Long,
        request: () -> String,
    ): NeteasePlaybackParseResult {
        return runCatching {
            parseNeteasePlaybackUrlResponse(
                response = request(),
                originalDurationMs = originalDurationMs,
            )
        }.getOrDefault(NeteasePlaybackParseResult(NeteasePlaybackParseStatus.Unavailable))
    }

    private fun hasLogin(): Boolean {
        return !effectiveCookies()["MUSIC_U"].isNullOrBlank()
    }

    private fun effectiveCookies(): Map<String, String> {
        val cookies = linkedMapOf<String, String>()
        cookieProvider().forEach { (key, value) ->
            val safeKey = key.trim()
            val safeValue = value.trim()
            if (safeKey.isNotEmpty() && safeValue.isNotEmpty()) {
                cookies[safeKey] = safeValue
            }
        }
        synchronized(sessionCookieLock) {
            sessionCookies.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    cookies[key] = value
                }
            }
        }
        return cookies
    }

    private fun buildCookieHeader(): String {
        val cookies = linkedMapOf<String, String>()
        effectiveCookies().forEach { (key, value) ->
            cookies[key] = value
        }
        cookies.putIfAbsent("os", "pc")
        cookies.putIfAbsent("appver", "8.10.35")
        return cookies.entries.joinToString("; ") { (key, value) -> "$key=$value" }
    }

    private inline fun <T> HttpURLConnection.useResponse(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            storeResponseCookies()
            disconnect()
        }
    }

    private fun HttpURLConnection.storeResponseCookies() {
        val setCookieHeaders = headerFields
            ?.filterKeys { key -> key.equals("Set-Cookie", ignoreCase = true) }
            ?.values
            ?.flatten()
            .orEmpty()
        if (setCookieHeaders.isEmpty()) {
            return
        }
        synchronized(sessionCookieLock) {
            setCookieHeaders
                .mapNotNull(::parseSetCookieHeader)
                .forEach { (key, value) -> sessionCookies[key] = value }
        }
    }

    private fun parseSong(song: JSONObject): OnlineTrack? {
        val id = song.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: return null
        val album = song.optJSONObject("album") ?: song.optJSONObject("al")
        val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar")
        val title = song.optNonBlankString("name") ?: return null
        val artist = artists
            ?.toJsonObjects()
            ?.mapNotNull { artist -> artist.optNonBlankString("name") }
            ?.takeIf(List<String>::isNotEmpty)
            ?.joinToString("/")
            ?: ""
        val duration = when {
            song.has("duration") -> song.optLong("duration", 0L)
            song.has("dt") -> song.optLong("dt", 0L)
            else -> 0L
        }
        return OnlineTrack(
            source = NeteaseSourceId,
            trackId = id,
            title = title,
            artist = artist,
            album = album?.optNonBlankString("name"),
            durationMs = duration.coerceAtLeast(0L),
            artworkUrl = album?.optArtworkUrl(),
        )
    }

    private fun parseProgramSong(program: JSONObject): OnlineTrack? {
        val song = program.optJSONObject("mainSong") ?: return null
        val parsedSong = parseSong(song) ?: return null
        val radio = program.optJSONObject("radio")
        val dj = program.optJSONObject("dj")
        val artist = parsedSong.artist.takeIf(String::isNotBlank)
            ?: radio?.optNonBlankString("name")
            ?: dj?.optNonBlankString("nickname")
            ?: ""
        val artworkUrl = parsedSong.artworkUrl
            ?: program.optNonBlankString("coverUrl")
            ?: program.optNonBlankString("picUrl")
            ?: radio?.optNonBlankString("picUrl")
        return parsedSong.copy(
            artist = artist,
            artworkUrl = artworkUrl,
        )
    }

    private fun parseHotSearchKeyword(item: JSONObject): OnlineSearchHotKeyword? {
        val keyword = item.optNonBlankString("searchWord")
            ?: item.optNonBlankString("first")
            ?: return null
        return OnlineSearchHotKeyword(
            keyword = keyword,
            subtitle = item.optNonBlankString("content")
                ?: item.optNonBlankString("second"),
            score = item.optLong("score", 0L).coerceAtLeast(0L),
        )
    }

    private fun parseArtist(artist: JSONObject): OnlineArtist? {
        val id = artist.optLong("id", 0L)
            .takeIf { artistId -> artistId > 0L }
            ?.toString()
            ?: return null
        val name = artist.optNonBlankString("name") ?: return null
        val aliases = artist.optJSONArray("alias")
            ?.toStrings()
            ?.filter(String::isNotBlank)
            .orEmpty()
        val subtitle = when {
            aliases.isNotEmpty() -> aliases.joinToString("/")
            artist.optInt("musicSize", 0) > 0 -> null
            else -> null
        }
        return OnlineArtist(
            provider = OnlineMusicProvider.Netease,
            artistId = id,
            name = name,
            subtitle = subtitle,
            artworkUrl = artist.optArtworkUrl(),
            trackCount = artist.optInt("musicSize", 0).coerceAtLeast(0),
            albumCount = artist.optInt("albumSize", 0).coerceAtLeast(0),
        )
    }

    private fun parseRadio(radio: JSONObject): OnlineRadio? {
        val id = radio.optLong("id", 0L)
            .takeIf { radioId -> radioId > 0L }
            ?.toString()
            ?: return null
        val title = radio.optNonBlankString("name") ?: return null
        val dj = radio.optJSONObject("dj")
        val category = radio.optNonBlankString("category")
        val creator = dj?.optNonBlankString("nickname")
        return OnlineRadio(
            provider = OnlineMusicProvider.Netease,
            radioId = id,
            title = title,
            subtitle = radio.optNonBlankString("rcmdtext")
                ?: radio.optNonBlankString("copywriter")
                ?: creator
                ?: category,
            category = category,
            creator = creator,
            artworkUrl = radio.optNonBlankString("picUrl"),
            programCount = radio.optInt("programCount", 0).coerceAtLeast(0),
            playCount = radio.optDouble("playCount", 0.0).toLong().coerceAtLeast(0L),
        )
    }

    private fun parseBanner(banner: JSONObject, index: Int): OnlineBanner? {
        val title = banner.optNonBlankString("typeTitle")
            ?: banner.optJSONObject("song")?.optNonBlankString("name")
            ?: return null
        val targetType = banner.optInt("targetType", 0)
        val targetId = banner.optLong("targetId", 0L)
            .takeIf { id -> id > 0L }
            ?.toString()
        val targetUrl = banner.optNonBlankString("url").orEmpty()
        val targetTrackId = banner.optJSONObject("song")
            ?.optLong("id", 0L)
            ?.takeIf { songId -> songId > 0L }
            ?.toString()
            ?: targetId.takeIf { targetType == 1 || targetUrl.startsWith("orpheus://song/") }
        val targetAlbumId = targetId.takeIf {
            targetType == 10 || targetUrl.startsWith("orpheus://album/")
        }
        val targetPlaylistId = targetId.takeIf {
            targetType == 1000 || targetUrl.startsWith("orpheus://playlist/")
        }
        return OnlineBanner(
            provider = OnlineMusicProvider.Netease,
            bannerId = banner.optNonBlankString("bannerId")
                ?: targetTrackId
                ?: targetAlbumId
                ?: targetPlaylistId
                ?: "netease-banner-$index",
            title = title,
            subtitle = banner.optJSONObject("song")?.optNonBlankString("name"),
            imageUrl = banner.optNonBlankString("imageUrl")
                ?: banner.optNonBlankString("bigImageUrl")
                ?: banner.optNonBlankString("pic")
                ?: banner.optNonBlankString("picUrl"),
            targetTrackId = targetTrackId,
            targetAlbumId = targetAlbumId,
            targetPlaylistId = targetPlaylistId,
        )
    }

    private fun parsePlaylist(
        playlist: JSONObject,
        kind: OnlinePlaylistKind,
    ): OnlinePlaylist? {
        val id = playlist.optLong("id", 0L)
            .takeIf { playlistId -> playlistId > 0L }
            ?.toString()
            ?: return null
        val title = playlist.optNonBlankString("name") ?: return null
        val topTracks = playlist.optJSONArray("tracks")
            ?.toJsonObjects()
            ?.mapNotNull { track ->
                val name = track.optNonBlankString("first") ?: return@mapNotNull null
                val artist = track.optNonBlankString("second")
                if (artist.isNullOrBlank()) {
                    name
                } else {
                    "$name - $artist"
                }
            }
            ?.take(3)
            ?.joinToString(" / ")
        return OnlinePlaylist(
            provider = OnlineMusicProvider.Netease,
            playlistId = id,
            title = title,
            subtitle = playlist.optNonBlankString("copywriter")
                ?: playlist.optNonBlankString("updateFrequency")
                ?: topTracks
                ?: playlist.optNonBlankString("description"),
            artworkUrl = playlist.optNonBlankString("picUrl")
                ?: playlist.optNonBlankString("coverImgUrl"),
            trackCount = playlist.optInt("trackCount", 0).coerceAtLeast(0),
            playCount = playlist.optDouble("playCount", 0.0).toLong().coerceAtLeast(0L),
            kind = kind,
        )
    }

    private fun parseAlbum(album: JSONObject): OnlineAlbum? {
        val id = album.optLong("id", 0L)
            .takeIf { albumId -> albumId > 0L }
            ?.toString()
            ?: album.optNonBlankString("idStr")
            ?: return null
        val title = album.optNonBlankString("name") ?: return null
        val artist = album.optJSONObject("artist")?.optNonBlankString("name")
            ?: album.optJSONArray("artists")
                ?.toJsonObjects()
                ?.mapNotNull { artist -> artist.optNonBlankString("name") }
                ?.takeIf(List<String>::isNotEmpty)
                ?.joinToString("/")
        return OnlineAlbum(
            provider = OnlineMusicProvider.Netease,
            albumId = id,
            title = title,
            artist = artist,
            artworkUrl = album.optArtworkUrl(),
            trackCount = album.optInt("size", 0).coerceAtLeast(0),
            publishTimeMs = album.optLong("publishTime", 0L).coerceAtLeast(0L),
        )
    }

    private fun parseArtistIntroduction(section: JSONObject): OnlineArtistIntroduction? {
        val title = section.optNonBlankString("ti") ?: return null
        val text = section.optNonBlankString("txt") ?: return null
        return OnlineArtistIntroduction(
            title = title,
            text = text,
        )
    }
}

internal fun parseNeteaseAccountProfileResponse(response: String): NeteaseAccountProfile? {
    val root = JSONObject(response)
    if (root.optInt("code", -1) != 200) {
        return null
    }
    val profile = root.optJSONObject("profile") ?: return null
    return parseNeteaseAccountProfileJson(profile.toString())
}

internal fun parseNeteaseUserPlaylistsResponse(response: String): List<NeteasePlaylistSummary> {
    val root = JSONObject(response)
    if (root.has("code") && root.optInt("code", 200) != 200) {
        return emptyList()
    }
    return root.optJSONArray("playlist")
        ?.toJsonObjects()
        ?.mapNotNull(::parseNeteasePlaylistSummary)
        .orEmpty()
}

internal fun OnlineTrack.toMediaItem(
    playbackUrl: String? = null,
    mimeType: String? = null,
    lyrics: OnlineLyrics? = null,
): MediaItem {
    val extras = Bundle().apply {
        putBoolean(OnlineTrackExtraKey, true)
        putString(OnlineProviderExtraKey, source)
        putString(OnlineSourceExtraKey, source)
        putString(OnlineTrackIdExtraKey, trackId)
        putString(LocalAudioLibrary.StableKeyExtraKey, mediaId)
        putString(LocalAudioLibrary.MediaIdExtraKey, mediaId)
        lyrics?.lyric?.takeIf(String::isNotBlank)?.let { lyric ->
            putString(OnlineLyricsExtraKey, lyric)
        }
        lyrics?.translatedLyric?.takeIf(String::isNotBlank)?.let { translatedLyric ->
            putString(OnlineTranslatedLyricsExtraKey, translatedLyric)
        }
        if (!playbackUrl.isNullOrBlank()) {
            putLong(OnlinePlaybackResolvedAtExtraKey, System.currentTimeMillis())
        }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setDisplayTitle(title)
        .setArtist(artist.takeIf(String::isNotBlank))
        .setSubtitle(artist.takeIf(String::isNotBlank))
        .setAlbumTitle(album?.takeIf(String::isNotBlank))
        .setDurationMs(durationMs)
        .setArtworkUri(artworkUrl?.normalizedArtworkUrl()?.let(Uri::parse))
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadata)
        .apply {
            playbackUrl?.takeIf(String::isNotBlank)?.let { url ->
                setUri(Uri.parse(url))
                setMimeType(mimeType ?: "audio/mpeg")
            }
        }
        .build()
}

internal fun MediaItem.isOnlineMediaItem(): Boolean {
    return mediaMetadata.extras?.getBoolean(OnlineTrackExtraKey, false) == true ||
        mediaId.startsWith(OnlineMediaIdPrefix)
}

internal fun MediaItem.shouldRefreshOnlinePlaybackUrl(nowMs: Long = System.currentTimeMillis()): Boolean {
    val resolvedAtMs = mediaMetadata.extras
        ?.getLong(OnlinePlaybackResolvedAtExtraKey, 0L)
        ?: 0L
    return shouldRefreshOnlinePlaybackUrlState(
        isOnline = isOnlineMediaItem(),
        hasPlaybackUrl = localConfiguration?.uri != null,
        resolvedAtMs = resolvedAtMs,
        nowMs = nowMs,
    )
}

internal fun shouldRefreshOnlinePlaybackUrlState(
    isOnline: Boolean,
    hasPlaybackUrl: Boolean,
    resolvedAtMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    if (!isOnline) {
        return false
    }
    if (!hasPlaybackUrl) {
        return true
    }
    return resolvedAtMs <= 0L || nowMs - resolvedAtMs > OnlinePlaybackUrlMaxAgeMs
}

internal fun MediaItem.onlineIdentityOrNull(): OnlineTrackIdentity? {
    val extras = mediaMetadata.extras
    val source = extras
        ?.getString(OnlineSourceExtraKey)
        ?.takeIf(String::isNotBlank)
    val trackId = extras
        ?.getString(OnlineTrackIdExtraKey)
        ?.takeIf(String::isNotBlank)
    if (source != null && trackId != null) {
        return OnlineTrackIdentity(source = source, trackId = trackId)
    }
    return mediaId.onlineTrackIdentityOrNull()
}

internal fun String.onlineTrackIdentityOrNull(): OnlineTrackIdentity? {
    if (!startsWith(OnlineMediaIdPrefix)) {
        return null
    }
    val parts = removePrefix(OnlineMediaIdPrefix).split(':', limit = 2)
    val source = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
    val trackId = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    return OnlineTrackIdentity(source = source, trackId = trackId)
}

internal data class OnlineTrackIdentity(
    val source: String,
    val trackId: String,
)

private fun buildOnlineMediaId(source: String, trackId: String): String {
    return "$OnlineMediaIdPrefix$source:$trackId"
}

internal fun MediaItem.toOnlineTrackFallback(identity: OnlineTrackIdentity): OnlineTrack? {
    val title = mediaMetadata.title?.toString()
        ?: mediaMetadata.displayTitle?.toString()
        ?: return null
    return OnlineTrack(
        source = identity.source,
        trackId = identity.trackId,
        title = title,
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString(),
        durationMs = mediaMetadata.durationMs ?: 0L,
        artworkUrl = mediaMetadata.artworkUri?.toString(),
    )
}

private fun JSONObject.optArtworkUrl(): String? {
    return optNonBlankString("picUrl")
        ?: optNonBlankString("blurPicUrl")
        ?: optNonBlankString("img1v1Url")
}

internal fun parseNeteaseAccountProfileJson(profileJson: String): NeteaseAccountProfile? {
    val profile = JSONObject(profileJson)
    val userId = profile.optLong("userId", 0L)
    val nickname = profile.optNonBlankString("nickname")
    if (userId <= 0L || nickname == null) {
        return null
    }
    return NeteaseAccountProfile(
        userId = userId,
        nickname = nickname,
        avatarUrl = profile.optNonBlankString("avatarUrl"),
    )
}

private fun parseNeteasePlaylistSummary(playlist: JSONObject): NeteasePlaylistSummary? {
    val playlistId = playlist.optLong("id", 0L)
        .takeIf { id -> id > 0L }
        ?.toString()
        ?: return null
    val name = playlist.optNonBlankString("name") ?: return null
    return NeteasePlaylistSummary(
        playlistId = playlistId,
        name = name,
        trackCount = playlist.optInt("trackCount", 0).coerceAtLeast(0),
        specialType = playlist.optInt("specialType", 0),
    )
}

private fun JSONObject.optNonBlankString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name)
        .takeIf(String::isNotBlank)
        ?.takeUnless { it.equals("null", ignoreCase = true) }
}

private fun JSONObject.hasNonNullValue(name: String): Boolean {
    return has(name) && !isNull(name)
}

private fun JSONObject.optIntOrNull(name: String): Int? {
    return when (val value = opt(name)) {
        null, JSONObject.NULL -> null
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    return when (val value = opt(name)) {
        null, JSONObject.NULL -> null
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun JSONObject.optPlaybackDataObject(): JSONObject? {
    return when (val data = opt("data")) {
        is JSONObject -> data
        is JSONArray -> data.toJsonObjects().firstOrNull()
        else -> null
    }
}

internal fun parseNeteasePlaybackUrlResponse(
    response: String,
    originalDurationMs: Long,
): NeteasePlaybackParseResult {
    val root = JSONObject(response)
    if (root.optInt("code", -1) == 301) {
        return NeteasePlaybackParseResult(NeteasePlaybackParseStatus.RequiresLogin)
    }
    val item = root.optPlaybackDataObject()
        ?: return NeteasePlaybackParseResult(NeteasePlaybackParseStatus.Unavailable)
    if (item.hasNonNullValue("freeTrialInfo")) {
        return NeteasePlaybackParseResult(NeteasePlaybackParseStatus.Preview)
    }
    val returnedDurationMs = item.optLongOrNull("time")
        ?: item.optLongOrNull("duration")
    if (isNeteasePreviewDuration(returnedDurationMs, originalDurationMs)) {
        return NeteasePlaybackParseResult(NeteasePlaybackParseStatus.Preview)
    }
    val dataCode = item.optInt("code", -1)
    val cannotListenReason = item.optJSONObject("freeTrialPrivilege")
        ?.optIntOrNull("cannotListenReason")
    val streamUrl = item.optNonBlankString("url")
        ?: return if (dataCode == 404 || cannotListenReason == 1 || item.optInt("fee", 0) > 0) {
            NeteasePlaybackParseResult(NeteasePlaybackParseStatus.RequiresLogin)
        } else {
            NeteasePlaybackParseResult(NeteasePlaybackParseStatus.Unavailable)
        }
    return OnlinePlaybackUrl(
        url = streamUrl.normalizedPlayableUrl(),
        mimeType = item.optNonBlankString("type")?.toAudioMimeType(),
    ).let { playbackUrl ->
        NeteasePlaybackParseResult(
            status = NeteasePlaybackParseStatus.Success,
            playbackUrl = playbackUrl,
        )
    }
}

internal fun isNeteasePreviewDuration(
    returnedDurationMs: Long?,
    originalDurationMs: Long,
): Boolean {
    val returnedDurationMs = returnedDurationMs ?: return false
    if (originalDurationMs < MinSongDurationForPreviewDetectionMs || returnedDurationMs <= 0L) {
        return false
    }
    val durationRatio = returnedDurationMs.toDouble() / originalDurationMs.toDouble()
    return returnedDurationMs <= MaxKnownPreviewDurationMs &&
        durationRatio <= MaxPreviewDurationRatio
}

private fun Map<String, String>.toJsonObjectString(): String {
    return JSONObject().also { root ->
        forEach { (key, value) -> root.put(key, value) }
    }.toString()
}

private fun parseSetCookieHeader(header: String): Pair<String, String>? {
    val firstPart = header.substringBefore(';').trim()
    if ('=' !in firstPart) {
        return null
    }
    val key = firstPart.substringBefore('=').trim()
    val value = firstPart.substringAfter('=').trim()
    if (key.isBlank() || value.isBlank() || value.any(Char::isISOControl)) {
        return null
    }
    return key to value
}

private fun JSONArray.toJsonObjects(): List<JSONObject> {
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}

private fun JSONArray.toStrings(): List<String> {
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun String.urlEncoded(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private fun String.normalizedPlayableUrl(): String {
    return replaceFirst("http://", "https://")
}

private fun String.normalizedArtworkUrl(): String {
    val httpsUrl = normalizedPlayableUrl()
    if (httpsUrl.contains("?param=")) {
        return httpsUrl
    }
    val separator = if (httpsUrl.contains('?')) "&" else "?"
    return "${httpsUrl}${separator}param=512y512"
}

private fun String.toAudioMimeType(): String {
    return when (lowercase(Locale.ROOT)) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "mp4" -> "audio/mp4"
        else -> "audio/mpeg"
    }
}
