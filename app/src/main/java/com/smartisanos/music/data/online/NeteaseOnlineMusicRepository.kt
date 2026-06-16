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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val NeteaseSourceId = OnlineMusicProvider.Netease.sourceId
private const val NeteaseFeaturedCacheTtlMs = 10 * 60 * 1000L
private const val NeteaseDetailCacheTtlMs = 10 * 60 * 1000L
private const val NeteaseAccountCacheTtlMs = 2 * 60 * 1000L
private const val NeteaseSearchCacheTtlMs = 5 * 60 * 1000L
private const val NeteaseLyricsCacheTtlMs = 7L * 24L * 60L * 60L * 1000L
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
private const val ArtistAlbumPageSize = 50
private const val AlbumTracksLimit = 80
private const val RadioTracksLimit = 80
private const val FeaturedPlaylistId = "3778678"
private const val FeaturedLimit = 30
private const val AccountPlaylistLimit = 50
private const val AccountAlbumLimit = 50
private const val AccountRadioLimit = 50
private const val CompletePlaylistTrackRequestLimit = 100_000
private const val PlaylistSongDetailBatchSize = 300
private const val PlaylistSongDetailParallelism = 4
private const val PlaylistDetailSubscriberCount = 8
private const val HttpTimeoutMs = 15_000
private const val MinSongDurationForPreviewDetectionMs = 60_000L
private const val MaxKnownPreviewDurationMs = 45_000L
private const val MaxPreviewDurationRatio = 0.5
private const val OnlinePlaybackUrlMaxAgeMs = 15 * 60 * 1000L
private const val NeteaseLoginCookieName = "MUSIC_U"
private const val UserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

private object NeteaseOnlineMemoryCache {
    private val entries = ConcurrentHashMap<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getFreshValue(
        key: String,
        ttlMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): FreshValue<T>? {
        return entries[key]
            ?.takeIf { entry -> nowMs - entry.loadedAtMs <= ttlMs }
            ?.let { entry -> FreshValue(entry.unboxedValue() as T) }
    }

    fun <T : Any> getFresh(
        key: String,
        ttlMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): T? {
        return getFreshValue<T?>(key, ttlMs, nowMs)?.value
    }

    fun put(
        key: String,
        value: Any?,
        loadedAtMs: Long = System.currentTimeMillis(),
    ) {
        entries[key] = CacheEntry(
            value = value ?: NullValue,
            loadedAtMs = loadedAtMs,
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getOrLoad(
        key: String,
        ttlMs: Long,
        loader: suspend () -> T,
    ): T {
        getFreshValue<T>(key, ttlMs)?.let { cached -> return cached.value }
        val value = loader()
        put(key, value)
        return value
    }

    fun invalidate(prefix: String) {
        entries.keys.removeIf { key -> key.startsWith(prefix) }
    }

    private data class CacheEntry(
        val value: Any,
        val loadedAtMs: Long,
    ) {
        fun unboxedValue(): Any? {
            return if (value === NullValue) null else value
        }
    }

    data class FreshValue<T>(
        val value: T,
    )

    private object NullValue
}

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
    val creatorUserId: Long? = null,
    val subscribed: Boolean = false,
) {
    val isLikedSongs: Boolean
        get() = specialType == 5

    fun isEditableBy(userId: Long): Boolean {
        return !isLikedSongs && !subscribed && creatorUserId == userId
    }
}

internal data class NeteasePlaylistDetail(
    val tracks: List<OnlineTrack>,
    val trackIds: List<String>,
    val trackCount: Int,
)

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

internal enum class NeteaseAccountActionStatus {
    Success,
    RequiresLogin,
    Failed,
}

internal data class NeteaseAccountActionResult(
    val status: NeteaseAccountActionStatus,
    val code: Int? = null,
)

internal data class NeteaseLikedTrackIdsResult(
    val status: NeteaseAccountActionStatus,
    val trackIds: Set<String> = emptySet(),
    val code: Int? = null,
)

internal data class NeteaseDailyRecommendedTracksResult(
    val status: NeteaseAccountActionStatus,
    val tracks: List<OnlineTrack> = emptyList(),
    val code: Int? = null,
)

internal enum class NeteasePlaylistTrackOperation(val apiValue: String) {
    Add("add"),
    Remove("del"),
}

internal class NeteaseOnlineMusicRepository(
    private val authStore: NeteaseAuthStore? = null,
    playbackQualityProvider: suspend () -> NeteaseAudioQuality = { NeteaseAudioQuality.ExHigh },
    private val client: NeteaseCloudMusicClient = NeteaseCloudMusicClient(
        cookieProvider = { authStore?.getCookies().orEmpty() },
        playbackQualityProvider = playbackQualityProvider,
    ),
    private val lyricsDiskCache: OnlineLyricsDiskCache? = null,
    private val pageDiskCache: OnlinePageDiskCache? = null,
    private val pageCacheRefreshScope: CoroutineScope? = null,
) : OnlineMusicProviderRepository {

    override val provider: OnlineMusicProvider = OnlineMusicProvider.Netease
    private val refreshingPageCacheKeys = ConcurrentHashMap.newKeySet<String>()

    constructor(context: Context) : this(
        authStore = NeteaseAuthStore(context.applicationContext),
        playbackQualityProvider = {
            OnlineMusicSettingsStore(context.applicationContext)
                .readSettings()
                .neteasePlaybackQuality
        },
        lyricsDiskCache = OnlineLyricsDiskCache(context.applicationContext),
        pageDiskCache = OnlinePageDiskCache(context.applicationContext),
        pageCacheRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private fun cacheKey(namespace: String, vararg parts: Any?): String {
        return buildString {
            append("netease:")
            append(authCacheScope())
            append(':')
            append(namespace)
            for (part in parts) {
                append(':')
                append(part?.toString().orEmpty())
            }
        }
    }

    private fun cachePrefix(namespace: String): String {
        return "netease:${authCacheScope()}:$namespace"
    }

    private fun authCacheScope(): String {
        val state = authStore?.load()
        return when {
            state == null || !state.isLoggedIn -> "anon"
            else -> "user:${state.savedAt}:${state.cookies[NeteaseLoginCookieName]?.hashCode() ?: 0}"
        }
    }

    private fun invalidateAccountCaches() {
        invalidatePageCache(cachePrefix("account"))
        invalidatePageCache(cachePrefix("liked"))
    }

    private fun invalidatePageCache(prefix: String) {
        NeteaseOnlineMemoryCache.invalidate(prefix)
        pageCacheRefreshScope?.launch {
            pageDiskCache?.removePrefix(prefix)
        }
    }

    private fun invalidatePlaylistTrackCaches(playlistId: String) {
        invalidatePageCache(cacheKey("playlist:tracks", playlistId))
        invalidatePageCache(cacheKey("account:playlist-tracks", playlistId))
    }

    private suspend fun <T : Any> cachedPage(
        key: String,
        ttlMs: Long,
        codec: OnlinePageCacheCodec<T>,
        loader: suspend () -> T,
    ): T {
        val nowMs = System.currentTimeMillis()
        NeteaseOnlineMemoryCache.getFresh<T>(key, ttlMs, nowMs)?.let { value ->
            return value
        }
        val diskEntry = pageDiskCache?.get(key, codec)
        if (diskEntry != null) {
            NeteaseOnlineMemoryCache.put(
                key = key,
                value = diskEntry.value,
                loadedAtMs = diskEntry.cachedAtMs,
            )
            if (nowMs - diskEntry.cachedAtMs > ttlMs) {
                refreshPageCacheInBackground(
                    key = key,
                    codec = codec,
                    loader = loader,
                )
            }
            return diskEntry.value
        }
        return loader().also { value ->
            val loadedAtMs = System.currentTimeMillis()
            NeteaseOnlineMemoryCache.put(key, value, loadedAtMs)
            pageDiskCache?.put(
                key = key,
                value = value,
                codec = codec,
                cachedAtMs = loadedAtMs,
            )
        }
    }

    private suspend fun <T : Any> cachedNullablePage(
        key: String,
        ttlMs: Long,
        codec: OnlinePageCacheCodec<T>,
        loader: suspend () -> T?,
    ): T? {
        val nowMs = System.currentTimeMillis()
        NeteaseOnlineMemoryCache.getFreshValue<T?>(key, ttlMs, nowMs)?.let { cached ->
            return cached.value
        }
        val diskEntry = pageDiskCache?.get(key, codec)
        if (diskEntry != null) {
            NeteaseOnlineMemoryCache.put(
                key = key,
                value = diskEntry.value,
                loadedAtMs = diskEntry.cachedAtMs,
            )
            if (nowMs - diskEntry.cachedAtMs > ttlMs) {
                refreshNullablePageCacheInBackground(
                    key = key,
                    codec = codec,
                    loader = loader,
                )
            }
            return diskEntry.value
        }
        val value = loader()
        NeteaseOnlineMemoryCache.put(key, value)
        if (value != null) {
            pageDiskCache?.put(
                key = key,
                value = value,
                codec = codec,
            )
        }
        return value
    }

    private fun <T : Any> refreshPageCacheInBackground(
        key: String,
        codec: OnlinePageCacheCodec<T>,
        loader: suspend () -> T,
    ) {
        val scope = pageCacheRefreshScope ?: return
        if (!refreshingPageCacheKeys.add(key)) {
            return
        }
        scope.launch {
            runCatching {
                val value = loader()
                val loadedAtMs = System.currentTimeMillis()
                NeteaseOnlineMemoryCache.put(key, value, loadedAtMs)
                pageDiskCache?.put(
                    key = key,
                    value = value,
                    codec = codec,
                    cachedAtMs = loadedAtMs,
                )
            }
            refreshingPageCacheKeys.remove(key)
        }
    }

    private fun <T : Any> refreshNullablePageCacheInBackground(
        key: String,
        codec: OnlinePageCacheCodec<T>,
        loader: suspend () -> T?,
    ) {
        val scope = pageCacheRefreshScope ?: return
        if (!refreshingPageCacheKeys.add(key)) {
            return
        }
        scope.launch {
            runCatching {
                val value = loader()
                val loadedAtMs = System.currentTimeMillis()
                NeteaseOnlineMemoryCache.put(key, value, loadedAtMs)
                if (value != null) {
                    pageDiskCache?.put(
                        key = key,
                        value = value,
                        codec = codec,
                        cachedAtMs = loadedAtMs,
                    )
                }
            }
            refreshingPageCacheKeys.remove(key)
        }
    }

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
        return cachedPage(
            key = cacheKey("search:all", normalizedQuery),
            ttlMs = NeteaseSearchCacheTtlMs,
            codec = OnlinePageCacheCodecs.SearchResults,
        ) {
            OnlineSearchResults(
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
        return cachedPage(
            key = cacheKey("search:hot"),
            ttlMs = NeteaseSearchCacheTtlMs,
            codec = OnlinePageCacheCodecs.HotKeywords,
        ) {
            client.getHotSearchKeywords(limit = HotSearchLimit)
        }
    }

    override suspend fun featuredTracks(): List<OnlineTrack> {
        return cachedPage(
            key = cacheKey("featured:tracks"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            currentUserDailyRecommendedTracks(limit = FeaturedLimit)
                ?.takeIf(List<OnlineTrack>::isNotEmpty)
                ?: featuredSongs()
        }
    }

    override suspend fun featuredHome(): OnlineMusicHome {
        return cachedPage(
            key = cacheKey("featured:home"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.MusicHome,
        ) {
            OnlineMusicHome(
                tracks = runCatching {
                    featuredTracks().take(FeaturedHomeTrackLimit)
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
    }

    override suspend fun featuredBanners(): List<OnlineBanner> {
        return cachedPage(
            key = cacheKey("featured:banners"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Banners,
        ) {
            client.getBanners(limit = FeaturedBannerLimit)
        }
    }

    override suspend fun featuredPlaylists(): List<OnlinePlaylist> {
        return cachedPage(
            key = cacheKey("featured:playlists"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Playlists,
        ) {
            client.getPersonalizedPlaylists(limit = FeaturedPlaylistLimit)
        }
    }

    override suspend fun featuredCharts(): List<OnlinePlaylist> {
        return cachedPage(
            key = cacheKey("featured:charts"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Playlists,
        ) {
            client.getToplists(limit = FeaturedChartLimit)
        }
    }

    override suspend fun featuredAlbums(): List<OnlineAlbum> {
        return cachedPage(
            key = cacheKey("featured:albums"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Albums,
        ) {
            client.getNewAlbums(limit = FeaturedAlbumLimit)
        }
    }

    override suspend fun featuredArtists(): List<OnlineArtist> {
        return cachedPage(
            key = cacheKey("featured:artists"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Artists,
        ) {
            client.getTopArtists(limit = FeaturedArtistLimit)
        }
    }

    override suspend fun featuredRadioTracks(): List<OnlineTrack> {
        return cachedPage(
            key = cacheKey("radio:tracks:featured"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getRecommendedRadioPrograms(limit = FeaturedRadioTrackLimit)
        }
    }

    override suspend fun featuredRadios(): List<OnlineRadio> {
        return cachedPage(
            key = cacheKey("radio:list:featured"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Radios,
        ) {
            client.getRecommendedRadios(limit = FeaturedRadioLimit)
        }
    }

    override suspend fun featuredRadioHome(): OnlineRadioHome {
        return cachedPage(
            key = cacheKey("radio:home"),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.RadioHome,
        ) {
            OnlineRadioHome(
                tracks = runCatching {
                    featuredRadioTracks()
                }.getOrDefault(emptyList()),
                radios = runCatching {
                    featuredRadios()
                }.getOrDefault(emptyList()),
            )
        }
    }

    override suspend fun track(trackId: String): OnlineTrack? {
        return NeteaseOnlineMemoryCache.getOrLoad(
            key = cacheKey("track", trackId.trim()),
            ttlMs = NeteaseDetailCacheTtlMs,
        ) {
            getTrack(trackId)
        }
    }

    suspend fun featuredSongs(): List<OnlineTrack> {
        return client.getPlaylistSongs(playlistId = FeaturedPlaylistId, limit = FeaturedLimit)
    }

    suspend fun getTrack(trackId: String): OnlineTrack? {
        val normalizedTrackId = trackId.trim().takeIf(String::isNotEmpty) ?: return null
        return NeteaseOnlineMemoryCache.getOrLoad(
            key = cacheKey("track", normalizedTrackId),
            ttlMs = NeteaseDetailCacheTtlMs,
        ) {
            client.getSongs(listOf(normalizedTrackId)).firstOrNull()
        }
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
        return NeteaseOnlineMemoryCache.getOrLoad(
            key = cacheKey("account:profile"),
            ttlMs = NeteaseAccountCacheTtlMs,
        ) {
            val profile = runCatching {
                client.getCurrentUserProfile()
            }.getOrNull()
            if (profile != null) {
                authStore.saveProfile(profile)
            }
            profile ?: state.profile
        }
    }

    suspend fun currentUserPlaylists(limit: Int = AccountPlaylistLimit): List<NeteasePlaylistSummary>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = currentUserProfile() ?: state.profile ?: return null
        return NeteaseOnlineMemoryCache.getOrLoad(
            key = cacheKey("account:playlist-summaries", limit),
            ttlMs = NeteaseAccountCacheTtlMs,
        ) {
            client.getUserPlaylists(
                userId = profile.userId,
                limit = limit,
            )
        }
    }

    suspend fun currentUserLikedTracks(limit: Int = Int.MAX_VALUE): List<OnlineTrack>? {
        val likedPlaylist = currentUserPlaylists()
            ?.firstOrNull(NeteasePlaylistSummary::isLikedSongs)
            ?: return null
        return playlistTracks(
            playlist = likedPlaylist,
            limit = likedPlaylist.trackFetchLimit(maxLimit = limit),
        )
    }

    suspend fun currentUserDailyRecommendedTracks(limit: Int = FeaturedLimit): List<OnlineTrack>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        return cachedNullablePage(
            key = cacheKey("featured:daily", limit),
            ttlMs = NeteaseFeaturedCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            val result = runCatching {
                client.getDailyRecommendedSongs(limit = limit)
            }.getOrDefault(NeteaseDailyRecommendedTracksResult(NeteaseAccountActionStatus.Failed))
            result.tracks.takeIf { result.status == NeteaseAccountActionStatus.Success }
        }
    }

    suspend fun currentUserLikedTrackIds(): Set<String>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = currentUserProfile() ?: state.profile ?: return null
        val result = runCatching {
            client.getUserLikedTrackIds(profile.userId)
        }.getOrDefault(NeteaseLikedTrackIdsResult(NeteaseAccountActionStatus.Failed))
        if (result.status == NeteaseAccountActionStatus.Success && result.trackIds.isNotEmpty()) {
            return result.trackIds
        }
        val playlistTrackIds = runCatching {
            currentUserLikedPlaylistTrackIds(profile.userId)
        }.getOrNull()
        return resolveNeteaseLikedTrackIds(result, playlistTrackIds)
    }

    private suspend fun currentUserLikedPlaylistTrackIds(userId: Long): Set<String>? {
        val likedPlaylist = client.getUserPlaylists(
            userId = userId,
            limit = AccountPlaylistLimit,
        ).firstOrNull(NeteasePlaylistSummary::isLikedSongs) ?: return null
        return client.getPlaylistTrackIds(
            playlistId = likedPlaylist.playlistId,
            limit = likedPlaylist.trackFetchLimit(),
        ).toSet()
    }

    suspend fun setTrackLiked(trackId: String, liked: Boolean): NeteaseAccountActionResult {
        val normalizedTrackId = trackId.trim().takeIf(String::isNotEmpty)
            ?: return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        val state = authStore?.load()
        if (state?.isLoggedIn != true) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.RequiresLogin, code = 301)
        }
        return runCatching {
            client.setSongLiked(
                trackId = normalizedTrackId,
                liked = liked,
            )
        }.getOrDefault(NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed))
            .also { result ->
                if (result.status == NeteaseAccountActionStatus.Success) {
                    invalidateAccountCaches()
                    invalidatePageCache(cachePrefix("playlist:tracks"))
                    invalidatePageCache(cachePrefix("account:playlist-tracks"))
                }
            }
    }

    suspend fun addTracksToPlaylist(
        playlistId: String,
        trackIds: List<String>,
    ): NeteaseAccountActionResult {
        val normalizedPlaylistId = playlistId.trim().takeIf(String::isNotEmpty)
            ?: return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        val normalizedTrackIds = normalizeNeteasePlaylistTrackIds(trackIds)
        if (normalizedTrackIds.isEmpty()) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        val state = authStore?.load()
        if (state?.isLoggedIn != true) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.RequiresLogin, code = 301)
        }
        return runCatching {
            client.manipulatePlaylistTracks(
                playlistId = normalizedPlaylistId,
                trackIds = normalizedTrackIds,
                operation = NeteasePlaylistTrackOperation.Add,
            )
        }.getOrDefault(NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed))
            .also { result ->
                if (result.status == NeteaseAccountActionStatus.Success) {
                    invalidateAccountCaches()
                    invalidatePlaylistTrackCaches(normalizedPlaylistId)
                }
            }
    }

    suspend fun removeTracksFromPlaylist(
        playlistId: String,
        trackIds: List<String>,
    ): NeteaseAccountActionResult {
        val normalizedPlaylistId = playlistId.trim().takeIf(String::isNotEmpty)
            ?: return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        val normalizedTrackIds = normalizeNeteasePlaylistTrackIds(trackIds)
        if (normalizedTrackIds.isEmpty()) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        val state = authStore?.load()
        if (state?.isLoggedIn != true) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.RequiresLogin, code = 301)
        }
        return runCatching {
            client.manipulatePlaylistTracks(
                playlistId = normalizedPlaylistId,
                trackIds = normalizedTrackIds,
                operation = NeteasePlaylistTrackOperation.Remove,
            )
        }.getOrDefault(NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed))
            .also { result ->
                if (result.status == NeteaseAccountActionStatus.Success) {
                    invalidateAccountCaches()
                    invalidatePlaylistTrackCaches(normalizedPlaylistId)
                }
            }
    }

    suspend fun deletePlaylist(playlistId: String): NeteaseAccountActionResult {
        val normalizedPlaylistId = playlistId.trim().takeIf(String::isNotEmpty)
            ?: return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        val state = authStore?.load()
        if (state?.isLoggedIn != true) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.RequiresLogin, code = 301)
        }
        return runCatching {
            client.deletePlaylist(normalizedPlaylistId)
        }.getOrDefault(NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed))
            .also { result ->
                if (result.status == NeteaseAccountActionStatus.Success) {
                    invalidateAccountCaches()
                    invalidatePlaylistTrackCaches(normalizedPlaylistId)
                }
            }
    }

    suspend fun createPlaylist(name: String): OnlineAccountPlaylistCreateResult {
        val normalizedName = name.trim().takeIf(String::isNotEmpty)
            ?: return OnlineAccountPlaylistCreateResult(NeteaseAccountActionStatus.Failed)
        val state = authStore?.load()
        if (state?.isLoggedIn != true) {
            return OnlineAccountPlaylistCreateResult(NeteaseAccountActionStatus.RequiresLogin, code = 301)
        }
        return runCatching {
            client.createPlaylist(normalizedName)
        }.getOrDefault(OnlineAccountPlaylistCreateResult(NeteaseAccountActionStatus.Failed))
    }

    override suspend fun accountPlaylists(): List<OnlineAccountPlaylist>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = currentUserProfile() ?: state.profile ?: return null
        return cachedPage(
            key = cacheKey("account:playlists"),
            ttlMs = NeteaseAccountCacheTtlMs,
            codec = OnlinePageCacheCodecs.AccountPlaylists,
        ) {
            client.getUserPlaylists(
                userId = profile.userId,
                limit = AccountPlaylistLimit,
            ).map { playlist ->
                OnlineAccountPlaylist(
                    provider = provider,
                    playlistId = playlist.playlistId,
                    title = playlist.name,
                    trackCount = playlist.trackCount,
                    isLikedSongs = playlist.isLikedSongs,
                    isEditable = playlist.isEditableBy(profile.userId),
                )
            }
        }
    }

    override suspend fun accountAlbums(): List<OnlineAlbum>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = currentUserProfile() ?: state.profile ?: return null
        return cachedPage(
            key = cacheKey("account:albums"),
            ttlMs = NeteaseAccountCacheTtlMs,
            codec = OnlinePageCacheCodecs.Albums,
        ) {
            client.getUserAlbums(
                userId = profile.userId,
                limit = AccountAlbumLimit,
            )
        }
    }

    override suspend fun accountRadios(): List<OnlineRadio>? {
        val state = authStore?.load() ?: return null
        if (!state.isLoggedIn) {
            return null
        }
        val profile = currentUserProfile() ?: state.profile ?: return null
        return cachedPage(
            key = cacheKey("account:radios"),
            ttlMs = NeteaseAccountCacheTtlMs,
            codec = OnlinePageCacheCodecs.Radios,
        ) {
            client.getUserRadios(
                userId = profile.userId,
                limit = AccountRadioLimit,
            )
        }
    }

    override suspend fun accountLikedTrackIds(): Set<String>? {
        return cachedNullablePage(
            key = cacheKey("liked:track-ids"),
            ttlMs = NeteaseAccountCacheTtlMs,
            codec = OnlinePageCacheCodecs.TrackIds,
        ) {
            currentUserLikedTrackIds()
        }
    }

    override suspend fun addTracksToAccountPlaylist(
        playlist: OnlineAccountPlaylist,
        trackIds: List<String>,
    ): NeteaseAccountActionResult {
        if (playlist.provider != provider || !playlist.isEditable) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        val result = addTracksToPlaylist(
            playlistId = playlist.playlistId,
            trackIds = trackIds,
        )
        return result
    }

    override suspend fun removeTracksFromAccountPlaylist(
        playlist: OnlineAccountPlaylist,
        trackIds: List<String>,
    ): NeteaseAccountActionResult {
        if (playlist.provider != provider || !playlist.isEditable) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        val result = removeTracksFromPlaylist(
            playlistId = playlist.playlistId,
            trackIds = trackIds,
        )
        return result
    }

    override suspend fun deleteAccountPlaylist(
        playlist: OnlineAccountPlaylist,
    ): NeteaseAccountActionResult {
        if (playlist.provider != provider || !playlist.isEditable) {
            return NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        val result = deletePlaylist(playlist.playlistId)
        return result
    }

    override suspend fun createAccountPlaylist(name: String): OnlineAccountPlaylistCreateResult {
        val result = createPlaylist(name)
        if (result.status == NeteaseAccountActionStatus.Success) {
            invalidateAccountCaches()
        }
        return result
    }

    override suspend fun accountPlaylistTracks(playlist: OnlineAccountPlaylist): List<OnlineTrack> {
        if (playlist.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("account:playlist-tracks", playlist.playlistId),
            ttlMs = NeteaseAccountCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getPlaylistSongs(
                playlistId = playlist.playlistId,
                limit = playlist.trackFetchLimit(),
            )
        }
    }

    override suspend fun playlistTracks(playlist: OnlinePlaylist): List<OnlineTrack> {
        if (playlist.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("playlist:tracks", playlist.playlistId),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getPlaylistSongs(
                playlistId = playlist.playlistId,
                limit = playlist.trackFetchLimit(),
            )
        }
    }

    override suspend fun albumTracks(album: OnlineAlbum): List<OnlineTrack> {
        if (album.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("album:tracks", album.albumId),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getAlbumSongs(
                albumId = album.albumId,
                limit = AlbumTracksLimit,
            )
        }
    }

    override suspend fun artistTopTracks(artist: OnlineArtist): List<OnlineTrack> {
        if (artist.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("artist:tracks", artist.artistId),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getArtistTopSongs(
                artistId = artist.artistId,
                limit = ArtistTopTracksLimit,
            )
        }
    }

    override suspend fun artistAlbums(artist: OnlineArtist): List<OnlineAlbum> {
        if (artist.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("artist:albums", artist.artistId),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.Albums,
        ) {
            client.getArtistAlbums(
                artistId = artist.artistId,
                expectedCount = artist.albumCount.takeIf { albumCount -> albumCount > 0 },
            )
        }
    }

    override suspend fun artistIntroduction(artist: OnlineArtist): List<OnlineArtistIntroduction> {
        if (artist.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("artist:introduction", artist.artistId),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.ArtistIntroductions,
        ) {
            client.getArtistIntroduction(artist.artistId)
        }
    }

    override suspend fun radioTracks(radio: OnlineRadio): List<OnlineTrack> {
        if (radio.provider != provider) {
            return emptyList()
        }
        return cachedPage(
            key = cacheKey("radio:tracks", radio.radioId),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getRadioPrograms(
                radioId = radio.radioId,
                limit = RadioTracksLimit,
            )
        }
    }

    suspend fun playlistTracks(
        playlist: NeteasePlaylistSummary,
        limit: Int = playlist.trackFetchLimit(),
    ): List<OnlineTrack> {
        return cachedPage(
            key = cacheKey("playlist:tracks", playlist.playlistId, limit),
            ttlMs = NeteaseDetailCacheTtlMs,
            codec = OnlinePageCacheCodecs.Tracks,
        ) {
            client.getPlaylistSongs(
                playlistId = playlist.playlistId,
                limit = limit,
            )
        }
    }

    override suspend fun lyrics(identity: OnlineTrackIdentity): OnlineLyrics? {
        if (identity.source != NeteaseSourceId) {
            return null
        }
        return cachedLyrics(identity)
    }

    suspend fun resolvePlayableTrack(
        track: OnlineTrack,
        includeLyrics: Boolean = true,
    ): MediaItem? {
        if (track.source != NeteaseSourceId) {
            return null
        }
        val identity = OnlineTrackIdentity(source = track.source, trackId = track.trackId)
        val playbackUrl = client.getPlaybackUrl(
            trackId = track.trackId,
            originalDurationMs = track.durationMs,
        ) ?: return null
        val lyrics = if (includeLyrics) {
            runCatching { cachedLyrics(identity) }.getOrNull()
        } else {
            null
        }
        return track.toMediaItem(
            playbackUrl = playbackUrl.url,
            mimeType = playbackUrl.mimeType,
            lyrics = lyrics,
        )
    }

    private suspend fun cachedLyrics(identity: OnlineTrackIdentity): OnlineLyrics {
        return NeteaseOnlineMemoryCache.getOrLoad(
            key = cacheKey("lyrics", identity.trackId),
            ttlMs = NeteaseLyricsCacheTtlMs,
        ) {
            lyricsDiskCache?.get(identity)
                ?: client.getLyrics(identity.trackId).also { lyrics ->
                    lyricsDiskCache?.put(identity, lyrics)
                }
        }
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

    suspend fun getArtistAlbums(artistId: String, expectedCount: Int?): List<OnlineAlbum> = withContext(Dispatchers.IO) {
        val id = artistId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val albums = mutableListOf<OnlineAlbum>()
        val seenAlbumIds = linkedSetOf<String>()
        var expectedTotalCount = expectedCount
        var offset = 0
        var more = true
        while (more) {
            val url = "https://music.163.com/api/artist/albums/${id.urlEncoded()}" +
                "?limit=$ArtistAlbumPageSize&offset=$offset"
            val response = JSONObject(readText(url))
            val rawAlbums = response.optJSONArray("hotAlbums") ?: break
            val rawCount = rawAlbums.length()
            if (rawCount == 0) {
                break
            }
            var addedCount = 0
            rawAlbums
                .toJsonObjects()
                .mapNotNull(::parseAlbum)
                .forEach { album ->
                    if (seenAlbumIds.add(album.albumId)) {
                        albums += album
                        addedCount += 1
                    }
                }
            if (addedCount == 0) {
                break
            }
            offset += rawCount
            val reportedAlbumCount = response.optJSONObject("artist")
                ?.optInt("albumSize", 0)
                ?.coerceAtLeast(0)
                ?: 0
            if (expectedTotalCount == null && reportedAlbumCount > 0) {
                expectedTotalCount = reportedAlbumCount
            }
            val serverHasMore = response.optBoolean("more", false)
            val countSuggestsMore = expectedTotalCount?.let { totalCount ->
                albums.size < totalCount && rawCount >= ArtistAlbumPageSize
            } ?: (rawCount >= ArtistAlbumPageSize)
            more = serverHasMore || countSuggestsMore
        }
        albums
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
        val safeLimit = limit.coerceAtLeast(1)
        val url = "https://music.163.com/api/v6/playlist/detail" +
            "?id=${id.urlEncoded()}&n=$safeLimit&s=$PlaylistDetailSubscriberCount"
        val detail = parseNeteasePlaylistDetailResponse(readText(url))
        val trackIds = detail.trackIds.take(safeLimit)
        if (trackIds.isEmpty()) {
            if (detail.trackCount == 0) {
                return@withContext emptyList()
            }
            error("NetEase playlist detail response missing trackIds")
        }
        val embeddedTracksById = detail.tracks.associateBy(OnlineTrack::trackId)
        val missingTracks = fetchSongDetails(
            trackIds = trackIds.filterNot(embeddedTracksById::containsKey),
        )
        val missingTracksById = missingTracks.associateBy(OnlineTrack::trackId)
        trackIds.mapNotNull { trackId ->
            embeddedTracksById[trackId] ?: missingTracksById[trackId]
        }
    }

    suspend fun getPlaylistTrackIds(playlistId: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val id = playlistId.trim().takeIf(String::isNotEmpty) ?: return@withContext emptyList()
        val safeLimit = limit.coerceAtLeast(1)
        val url = "https://music.163.com/api/v6/playlist/detail" +
            "?id=${id.urlEncoded()}&n=$safeLimit&s=$PlaylistDetailSubscriberCount"
        parseNeteasePlaylistDetailResponse(readText(url)).trackIds.take(safeLimit)
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

    suspend fun getUserAlbums(userId: Long, limit: Int): List<OnlineAlbum> = withContext(Dispatchers.IO) {
        if (userId <= 0L) {
            return@withContext emptyList()
        }
        val safeLimit = limit.coerceIn(1, AccountAlbumLimit)
        val response = callEApi(
            path = "/mine/rn/resource/list",
            params = mapOf(
                "userId" to userId.toString(),
                "offset" to "0",
                "limit" to safeLimit.toString(),
                "pageType" to "3",
                "needRcmd" to "0",
                "isVistor" to "false",
                "includeStarPodcast" to "true",
            ),
            host = "interface3.music.163.com",
        )
        parseNeteaseAccountAlbumsResponse(response).take(safeLimit)
    }

    suspend fun getUserRadios(userId: Long, limit: Int): List<OnlineRadio> = withContext(Dispatchers.IO) {
        if (userId <= 0L) {
            return@withContext emptyList()
        }
        val safeLimit = limit.coerceIn(1, AccountRadioLimit)
        val response = callWeApi(
            path = "/user/djradio/get/subed",
            params = mapOf(
                "uid" to userId.toString(),
                "offset" to "0",
                "limit" to safeLimit.toString(),
            ),
        )
        parseNeteaseAccountRadiosResponse(response).take(safeLimit)
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
        val response = JSONObject(requestSongDetails(ids))
        response.optJSONArray("songs")
            ?.toJsonObjects()
            ?.mapNotNull(::parseSong)
            .orEmpty()
    }

    private suspend fun fetchSongDetails(trackIds: List<String>): List<OnlineTrack> = coroutineScope {
        val ids = trackIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (ids.isEmpty()) {
            return@coroutineScope emptyList()
        }
        val results = mutableListOf<Pair<Int, List<OnlineTrack>>>()
        ids.chunked(PlaylistSongDetailBatchSize)
            .mapIndexed { index, chunk -> index to chunk }
            .chunked(PlaylistSongDetailParallelism)
            .forEach { window ->
                results += window
                    .map { (index, chunk) ->
                        async(Dispatchers.IO) {
                            index to getSongs(chunk)
                        }
                    }
                    .awaitAll()
            }
        results
            .sortedBy { (index, _) -> index }
            .flatMap { (_, tracks) -> tracks }
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

    suspend fun setSongLiked(trackId: String, liked: Boolean): NeteaseAccountActionResult = withContext(Dispatchers.IO) {
        val id = trackId.trim().takeIf(String::isNotEmpty)
            ?: return@withContext NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        requestAccountActionWithSessionRetry {
            callWeApi(
                path = "/song/like",
                params = mapOf(
                    "trackId" to id,
                    "like" to liked.toString(),
                ),
            )
        }
    }

    suspend fun getUserLikedTrackIds(userId: Long): NeteaseLikedTrackIdsResult = withContext(Dispatchers.IO) {
        if (userId <= 0L) {
            return@withContext NeteaseLikedTrackIdsResult(NeteaseAccountActionStatus.Failed)
        }
        requestLikedTrackIdsWithSessionRetry {
            callWeApi(
                path = "/song/like/get",
                params = mapOf("uid" to userId.toString()),
            )
        }
    }

    suspend fun getDailyRecommendedSongs(limit: Int): NeteaseDailyRecommendedTracksResult = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceAtLeast(1)
        requestDailyRecommendedTracksWithSessionRetry {
            callWeApi(
                path = "/v3/discovery/recommend/songs",
                params = mapOf(
                    "total" to "true",
                    "limit" to safeLimit.toString(),
                ),
            )
        }.let { result ->
            if (result.status == NeteaseAccountActionStatus.Success) {
                result.copy(tracks = result.tracks.take(safeLimit))
            } else {
                result
            }
        }
    }

    suspend fun manipulatePlaylistTracks(
        playlistId: String,
        trackIds: List<String>,
        operation: NeteasePlaylistTrackOperation,
    ): NeteaseAccountActionResult = withContext(Dispatchers.IO) {
        val id = playlistId.trim().takeIf(String::isNotEmpty)
            ?: return@withContext NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        val ids = normalizeNeteasePlaylistTrackIds(trackIds)
        if (ids.isEmpty()) {
            return@withContext NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        }
        requestAccountActionWithSessionRetry {
            callWeApi(
                path = "/playlist/manipulate/tracks",
                params = mapOf(
                    "op" to operation.apiValue,
                    "pid" to id,
                    "trackIds" to buildNeteasePlaylistTrackIdsJson(ids),
                    "imme" to "true",
                ),
            )
        }
    }

    suspend fun createPlaylist(name: String): OnlineAccountPlaylistCreateResult = withContext(Dispatchers.IO) {
        val normalizedName = name.trim().takeIf(String::isNotEmpty)
            ?: return@withContext OnlineAccountPlaylistCreateResult(NeteaseAccountActionStatus.Failed)
        requestPlaylistCreateWithSessionRetry {
            callWeApi(
                path = "/playlist/create",
                params = mapOf(
                    "name" to normalizedName,
                    "privacy" to "0",
                    "type" to "NORMAL",
                ),
            )
        }
    }

    suspend fun deletePlaylist(playlistId: String): NeteaseAccountActionResult = withContext(Dispatchers.IO) {
        val id = playlistId.trim().takeIf(String::isNotEmpty)
            ?: return@withContext NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
        requestAccountActionWithSessionRetry {
            callWeApi(
                path = "/playlist/remove",
                params = mapOf("ids" to buildNeteasePlaylistIdsJson(listOf(id))),
            )
        }
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
        host: String = "interface.music.163.com",
    ): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val eapiPath = "/eapi$normalizedPath"
        val apiPath = "/api$normalizedPath"
        val url = "https://$host$eapiPath"
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

    private fun requestSongDetails(ids: List<String>): String {
        require(ids.isNotEmpty()) { "ids must not be empty" }
        val detailParam = ids.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { id -> """{"id":$id}""" }
        return callWeApi(
            path = "/v3/song/detail",
            params = mapOf(
                "c" to detailParam,
                "ids" to ids.joinToString(prefix = "[", postfix = "]"),
            ),
        )
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

    private fun requestAccountActionWithSessionRetry(
        request: () -> String,
    ): NeteaseAccountActionResult {
        var result = tryParseAccountActionResponse(request)
        if (hasLogin() && result.status == NeteaseAccountActionStatus.RequiresLogin) {
            ensureWeapiSession()
            result = tryParseAccountActionResponse(request)
        }
        return result
    }

    private fun tryParseAccountActionResponse(
        request: () -> String,
    ): NeteaseAccountActionResult {
        return runCatching {
            parseNeteaseAccountActionResponse(request())
        }.getOrDefault(NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed))
    }

    private fun requestLikedTrackIdsWithSessionRetry(
        request: () -> String,
    ): NeteaseLikedTrackIdsResult {
        var result = tryParseLikedTrackIdsResponse(request)
        if (hasLogin() && result.status == NeteaseAccountActionStatus.RequiresLogin) {
            ensureWeapiSession()
            result = tryParseLikedTrackIdsResponse(request)
        }
        return result
    }

    private fun tryParseLikedTrackIdsResponse(
        request: () -> String,
    ): NeteaseLikedTrackIdsResult {
        return runCatching {
            parseNeteaseLikedTrackIdsResponse(request())
        }.getOrDefault(NeteaseLikedTrackIdsResult(NeteaseAccountActionStatus.Failed))
    }

    private fun requestDailyRecommendedTracksWithSessionRetry(
        request: () -> String,
    ): NeteaseDailyRecommendedTracksResult {
        var result = tryParseDailyRecommendedTracksResponse(request)
        if (hasLogin() && result.status == NeteaseAccountActionStatus.RequiresLogin) {
            ensureWeapiSession()
            result = tryParseDailyRecommendedTracksResponse(request)
        }
        return result
    }

    private fun tryParseDailyRecommendedTracksResponse(
        request: () -> String,
    ): NeteaseDailyRecommendedTracksResult {
        return runCatching {
            parseNeteaseDailyRecommendedTracksResponse(request())
        }.getOrDefault(NeteaseDailyRecommendedTracksResult(NeteaseAccountActionStatus.Failed))
    }

    private fun requestPlaylistCreateWithSessionRetry(
        request: () -> String,
    ): OnlineAccountPlaylistCreateResult {
        var result = tryParsePlaylistCreateResponse(request)
        if (hasLogin() && result.status == NeteaseAccountActionStatus.RequiresLogin) {
            ensureWeapiSession()
            result = tryParsePlaylistCreateResponse(request)
        }
        return result
    }

    private fun tryParsePlaylistCreateResponse(
        request: () -> String,
    ): OnlineAccountPlaylistCreateResult {
        return runCatching {
            parseNeteasePlaylistCreateResponse(request())
        }.getOrDefault(OnlineAccountPlaylistCreateResult(NeteaseAccountActionStatus.Failed))
    }

    private fun hasLogin(): Boolean {
        return !effectiveCookies()[NeteaseLoginCookieName].isNullOrBlank()
    }

    private fun effectiveCookies(): Map<String, String> {
        val currentSessionCookies = synchronized(sessionCookieLock) {
            sessionCookies.toMap()
        }
        return buildNeteaseEffectiveCookies(
            persistedCookies = cookieProvider(),
            sessionCookies = currentSessionCookies,
        )
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

    private fun parseSong(song: JSONObject): OnlineTrack? = parseNeteaseSong(song)

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
            artworkUrl = artist.optArtistArtworkUrl(),
            trackCount = artist.optInt("musicSize", 0).coerceAtLeast(0),
            albumCount = artist.optInt("albumSize", 0).coerceAtLeast(0),
        )
    }

    private fun parseRadio(radio: JSONObject): OnlineRadio? {
        return parseNeteaseRadio(radio)
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
        return parseNeteaseAlbum(album)
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

internal fun parseNeteaseAccountAlbumsResponse(response: String): List<OnlineAlbum> {
    val root = JSONObject(response)
    if (root.has("code") && root.optInt("code", 200) != 200) {
        return emptyList()
    }
    val albums = root.optJSONArray("playlist")
        ?: root.optJSONObject("data")
            ?.optJSONObject("mainCollectInfo")
            ?.optJSONObject("mineAllTabDto")
            ?.optJSONArray("dataList")
        ?: root.optJSONObject("data")?.optJSONArray("dataList")
        ?: root.optJSONArray("data")
        ?: return emptyList()
    return albums
        .toJsonObjects()
        .mapNotNull(::parseNeteaseAccountAlbumItem)
}

internal fun parseNeteaseAccountRadiosResponse(response: String): List<OnlineRadio> {
    val root = JSONObject(response)
    if (root.has("code") && root.optInt("code", 200) != 200) {
        return emptyList()
    }
    val radios = root.optJSONArray("djRadios")
        ?: root.optJSONObject("data")?.optJSONArray("djRadios")
        ?: root.optJSONObject("data")?.optJSONArray("radios")
        ?: root.optJSONArray("radios")
        ?: return emptyList()
    return radios
        .toJsonObjects()
        .mapNotNull(::parseNeteaseRadio)
}

internal fun parseNeteasePlaylistDetailResponse(response: String): NeteasePlaylistDetail {
    val root = JSONObject(response)
    val code = root.optInt("code", 200)
    require(code == 200) { "NetEase playlist detail unavailable: code $code" }
    val playlist = root.optJSONObject("playlist")
        ?: error("NetEase playlist detail response missing playlist")
    return NeteasePlaylistDetail(
        tracks = playlist.optJSONArray("tracks")
            ?.toJsonObjects()
            ?.mapNotNull(::parseNeteaseSong)
            .orEmpty(),
        trackIds = playlist.optPlaylistTrackIds(),
        trackCount = playlist.optInt("trackCount", 0).coerceAtLeast(0),
    )
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
    val cacheKey = OnlineTrackIdentity(source = source, trackId = trackId)
        .toOnlinePlaybackCacheKey()
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadata)
        .apply {
            playbackUrl?.takeIf(String::isNotBlank)?.let { url ->
                setUri(Uri.parse(url))
                setMimeType(mimeType ?: "audio/mpeg")
                setCustomCacheKey(cacheKey)
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

internal fun OnlineTrackIdentity.toOnlinePlaybackCacheKey(): String {
    return "$OnlineMediaIdPrefix$source:$trackId"
}

internal fun OnlineTrackIdentity.toOnlinePlaybackPlaceholderMediaItem(): MediaItem {
    return OnlineTrack(
        source = source,
        trackId = trackId,
        title = trackId,
        artist = "",
        album = null,
        durationMs = 0L,
        artworkUrl = null,
    )
        .toMediaItem()
        .withOnlinePlaybackPlaceholderUri()
}

internal fun buildOnlineMediaId(source: String, trackId: String): String {
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

private fun JSONObject.optArtistArtworkUrl(): String? {
    return optNonBlankString("img1v1Url")
        ?: optNonBlankString("picUrl")
        ?: optNonBlankString("blurPicUrl")
}

private fun parseNeteaseSong(song: JSONObject): OnlineTrack? {
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
        creatorUserId = playlist.optJSONObject("creator")
            ?.optLongOrNull("userId")
            ?.takeIf { userId -> userId > 0L },
        subscribed = playlist.optBoolean("subscribed", false),
    )
}

private fun parseNeteaseAccountAlbumItem(item: JSONObject): OnlineAlbum? {
    val dataInfo = item.optJSONObject("dataInfo")
    val album = dataInfo?.optJSONObject("data")
        ?: item.optJSONObject("album")
        ?: item
    val parsed = parseNeteaseAlbum(album) ?: return null
    val coverUrl = dataInfo?.optNonBlankString("picUrl")
        ?: parsed.artworkUrl
    return parsed.copy(
        artworkUrl = coverUrl?.normalizedPlayableUrl(),
    )
}

private fun parseNeteaseAlbum(album: JSONObject): OnlineAlbum? {
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

private fun parseNeteaseRadio(radio: JSONObject): OnlineRadio? {
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

private fun NeteasePlaylistSummary.trackFetchLimit(maxLimit: Int = Int.MAX_VALUE): Int {
    return playlistTrackFetchLimit(trackCount = trackCount, maxLimit = maxLimit)
}

private fun OnlineAccountPlaylist.trackFetchLimit(): Int {
    return playlistTrackFetchLimit(trackCount = trackCount)
}

private fun OnlinePlaylist.trackFetchLimit(): Int {
    return playlistTrackFetchLimit(trackCount = trackCount)
}

private fun playlistTrackFetchLimit(trackCount: Int, maxLimit: Int = Int.MAX_VALUE): Int {
    val normalizedMaxLimit = maxLimit.coerceAtLeast(1)
    val requestedLimit = if (trackCount > 0) {
        trackCount
    } else {
        CompletePlaylistTrackRequestLimit
    }
    return requestedLimit.coerceAtMost(normalizedMaxLimit).coerceAtLeast(1)
}

private fun JSONObject.optPlaylistTrackIds(): List<String> {
    return optJSONArray("trackIds")
        ?.toJsonObjects()
        ?.mapNotNull { track ->
            track.optLongOrNull("id")
                ?.takeIf { id -> id > 0L }
                ?.toString()
        }
        .orEmpty()
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
    val value = opt(name)
    return when {
        value == null || value == JSONObject.NULL -> null
        value is Number -> value.toInt()
        value is String -> value.toIntOrNull()
        else -> null
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    val value = opt(name)
    return when {
        value == null || value == JSONObject.NULL -> null
        value is Number -> value.toLong()
        value is String -> value.toLongOrNull()
        else -> null
    }
}

private fun JSONObject.optPlaybackDataObject(): JSONObject? {
    val data = opt("data")
    return when {
        data is JSONObject -> data
        data is JSONArray -> data.toJsonObjects().firstOrNull()
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

internal fun parseNeteaseAccountActionResponse(response: String): NeteaseAccountActionResult {
    val root = JSONObject(response)
    val code = root.optInt("code", -1)
    val status = when (code) {
        200 -> NeteaseAccountActionStatus.Success
        301 -> NeteaseAccountActionStatus.RequiresLogin
        else -> NeteaseAccountActionStatus.Failed
    }
    return NeteaseAccountActionResult(
        status = status,
        code = code.takeIf { value -> value >= 0 },
    )
}

internal fun parseNeteaseLikedTrackIdsResponse(response: String): NeteaseLikedTrackIdsResult {
    val root = JSONObject(response)
    val code = root.optInt("code", -1)
    val status = when (code) {
        200 -> NeteaseAccountActionStatus.Success
        301 -> NeteaseAccountActionStatus.RequiresLogin
        else -> NeteaseAccountActionStatus.Failed
    }
    if (status != NeteaseAccountActionStatus.Success) {
        return NeteaseLikedTrackIdsResult(
            status = status,
            code = code.takeIf { value -> value >= 0 },
        )
    }
    val idsArray = root.optJSONArray("ids")
        ?: root.optJSONObject("data")?.optJSONArray("ids")
        ?: root.optJSONArray("data")
    return NeteaseLikedTrackIdsResult(
        status = NeteaseAccountActionStatus.Success,
        trackIds = idsArray?.toPositiveIdStrings().orEmpty(),
        code = code,
    )
}

internal fun resolveNeteaseLikedTrackIds(
    directResult: NeteaseLikedTrackIdsResult,
    playlistTrackIds: Set<String>?,
): Set<String>? {
    if (directResult.status == NeteaseAccountActionStatus.Success && directResult.trackIds.isNotEmpty()) {
        return directResult.trackIds
    }
    return when {
        playlistTrackIds != null -> playlistTrackIds
        directResult.status == NeteaseAccountActionStatus.Success -> directResult.trackIds
        else -> null
    }
}

internal fun normalizeNeteasePlaylistTrackIds(trackIds: List<String>): List<String> {
    return trackIds
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
}

internal fun buildNeteasePlaylistTrackIdsJson(trackIds: List<String>): String {
    return buildNeteaseNumericIdsJson(normalizeNeteasePlaylistTrackIds(trackIds))
}

internal fun buildNeteasePlaylistIdsJson(playlistIds: List<String>): String {
    return buildNeteaseNumericIdsJson(normalizeNeteasePlaylistTrackIds(playlistIds))
}

private fun buildNeteaseNumericIdsJson(ids: List<String>): String {
    return JSONArray().also { array ->
        ids.forEach { id ->
            array.put(id.toLongOrNull() ?: id)
        }
    }.toString()
}

internal fun parseNeteaseDailyRecommendedTracksResponse(response: String): NeteaseDailyRecommendedTracksResult {
    val root = JSONObject(response)
    val code = root.optInt("code", -1)
    val status = when (code) {
        200 -> NeteaseAccountActionStatus.Success
        301 -> NeteaseAccountActionStatus.RequiresLogin
        else -> NeteaseAccountActionStatus.Failed
    }
    if (status != NeteaseAccountActionStatus.Success) {
        return NeteaseDailyRecommendedTracksResult(
            status = status,
            code = code.takeIf { value -> value >= 0 },
        )
    }
    val data = root.optJSONObject("data")
    val songs = data?.optJSONArray("dailySongs")
        ?: data?.optJSONArray("recommend")
        ?: root.optJSONArray("recommend")
        ?: root.optJSONArray("dailySongs")
    return NeteaseDailyRecommendedTracksResult(
        status = NeteaseAccountActionStatus.Success,
        tracks = songs
            ?.toJsonObjects()
            ?.mapNotNull(::parseNeteaseSong)
            .orEmpty(),
        code = code,
    )
}

internal fun parseNeteasePlaylistCreateResponse(response: String): OnlineAccountPlaylistCreateResult {
    val root = JSONObject(response)
    val code = root.optInt("code", -1)
    val status = when (code) {
        200 -> NeteaseAccountActionStatus.Success
        301 -> NeteaseAccountActionStatus.RequiresLogin
        else -> NeteaseAccountActionStatus.Failed
    }
    if (status != NeteaseAccountActionStatus.Success) {
        return OnlineAccountPlaylistCreateResult(
            status = status,
            code = code.takeIf { value -> value >= 0 },
        )
    }
    val playlist = root.optJSONObject("playlist")
        ?.let(::parseNeteasePlaylistSummary)
        ?.let { summary ->
            OnlineAccountPlaylist(
                provider = OnlineMusicProvider.Netease,
                playlistId = summary.playlistId,
                title = summary.name,
                trackCount = summary.trackCount,
                isLikedSongs = summary.isLikedSongs,
                isEditable = true,
            )
        }
    return OnlineAccountPlaylistCreateResult(
        status = if (playlist == null) NeteaseAccountActionStatus.Failed else NeteaseAccountActionStatus.Success,
        playlist = playlist,
        code = code,
    )
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

internal fun buildNeteaseEffectiveCookies(
    persistedCookies: Map<String, String>,
    sessionCookies: Map<String, String>,
): Map<String, String> {
    val cookies = linkedMapOf<String, String>()
    persistedCookies.addSanitizedCookiesTo(cookies)
    val hasPersistedLogin = !cookies[NeteaseLoginCookieName].isNullOrBlank()
    sessionCookies.addSanitizedCookiesTo(cookies) { key ->
        key != NeteaseLoginCookieName || hasPersistedLogin
    }
    return cookies
}

private inline fun Map<String, String>.addSanitizedCookiesTo(
    target: MutableMap<String, String>,
    keyFilter: (String) -> Boolean = { true },
) {
    forEach { (key, value) ->
        val safeKey = key.trim()
        val safeValue = value.trim()
        if (safeKey.isNotEmpty() && safeValue.isNotEmpty() && keyFilter(safeKey)) {
            target[safeKey] = safeValue
        }
    }
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

private fun JSONArray.toPositiveIdStrings(): Set<String> {
    return buildSet {
        for (index in 0 until length()) {
            optLong(index, 0L)
                .takeIf { id -> id > 0L }
                ?.toString()
                ?.let(::add)
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
