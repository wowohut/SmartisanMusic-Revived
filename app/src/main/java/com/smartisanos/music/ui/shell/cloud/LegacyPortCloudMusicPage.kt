package com.smartisanos.music.ui.shell.cloud

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.smartisanos.music.R
import com.smartisanos.music.data.favorite.FavoriteSongsRepository
import com.smartisanos.music.data.online.NeteaseAuthStore
import com.smartisanos.music.data.online.OnlineAccountPlaylist
import com.smartisanos.music.data.online.OnlineAlbum
import com.smartisanos.music.data.online.OnlineArtist
import com.smartisanos.music.data.online.OnlineBanner
import com.smartisanos.music.data.online.OnlineMusicProvider
import com.smartisanos.music.data.online.OnlineMusicProviderRepository
import com.smartisanos.music.data.online.OnlineMusicRepositoryRouter
import com.smartisanos.music.data.online.OnlinePlaylist
import com.smartisanos.music.data.online.OnlineRadio
import com.smartisanos.music.data.online.buildOnlineMediaId
import com.smartisanos.music.ui.components.SmartisanDrawableBackground
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBannerStrip
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBlankState
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicDivider
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicSectionTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun LegacyPortCloudMusicPage(
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    searchOpenRequest: Int = 0,
    onSearchOpenRequestHandled: () -> Unit = {},
    accountRefreshRequest: Int = 0,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val authStore = remember(appContext) {
        NeteaseAuthStore(appContext)
    }
    val favoriteRepository = remember(appContext) {
        FavoriteSongsRepository.getInstance(appContext)
    }
    val repositoryRouter = remember(appContext) {
        OnlineMusicRepositoryRouter(appContext)
    }
    val activeRepository: OnlineMusicProviderRepository = remember(repositoryRouter) {
        repositoryRouter.repositoryFor(OnlineMusicProvider.Netease)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var authState by remember { mutableStateOf(authStore.load()) }
    var authRevision by remember { mutableStateOf(0) }
    var homeState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.LoadingFeatured) }
    var searchResultsState by remember { mutableStateOf<CloudSearchResultsState>(CloudSearchResultsState.Idle) }
    var hotSearchState by remember { mutableStateOf<CloudHotSearchState>(CloudHotSearchState.Idle) }
    var searchCategory by rememberSaveable { mutableStateOf(CloudSearchCategory.All) }
    var bannerItems by remember { mutableStateOf<List<OnlineBanner>>(emptyList()) }
    var featuredHomeState by remember { mutableStateOf<CloudFeaturedHomeState>(CloudFeaturedHomeState.Loading) }
    var radioHomeState by remember { mutableStateOf<CloudRadioHomeState>(CloudRadioHomeState.Loading) }
    var selectedRadio by remember { mutableStateOf<OnlineRadio?>(null) }
    var radioTrackState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.Idle) }
    var artistState by remember { mutableStateOf<CloudArtistState>(CloudArtistState.Idle) }
    var selectedArtist by remember { mutableStateOf<OnlineArtist?>(null) }
    var artistTracksState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.Idle) }
    var artistAlbums by remember { mutableStateOf<List<OnlineAlbum>>(emptyList()) }
    var artistIntroductions by remember { mutableStateOf<List<com.smartisanos.music.data.online.OnlineArtistIntroduction>>(emptyList()) }
    var selectedBanner by remember { mutableStateOf<OnlineBanner?>(null) }
    var bannerTrackState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.Idle) }
    var selectedOnlinePlaylist by remember { mutableStateOf<OnlinePlaylist?>(null) }
    var selectedOnlineAlbum by remember { mutableStateOf<OnlineAlbum?>(null) }
    var featuredTrackDetailState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.Idle) }
    var onlinePlaylistTracksState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.Idle) }
    var onlineAlbumTracksState by remember { mutableStateOf<CloudMusicState>(CloudMusicState.Idle) }
    var accountPlaylists by remember { mutableStateOf<List<OnlineAccountPlaylist>>(emptyList()) }
    var accountAlbums by remember { mutableStateOf<List<OnlineAlbum>>(emptyList()) }
    var accountRadios by remember { mutableStateOf<List<OnlineRadio>>(emptyList()) }
    var selectedAccountPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var handledAccountRefreshRequest by remember { mutableStateOf(0) }
    var currentRoute by rememberSaveable(stateSaver = CloudMusicRoute.Saver) { mutableStateOf<CloudMusicRoute>(CloudMusicRoute.Home) }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (active) {
            val latestAuthState = authStore.load()
            if (latestAuthState != authState) {
                authState = latestAuthState
                authRevision += 1
            }
        }
    }

    LaunchedEffect(active, authRevision, authState.isLoggedIn) {
        if (!active || !authState.isLoggedIn) {
            return@LaunchedEffect
        }
        val likedTrackIds = runSuspendCatching {
            activeRepository.accountLikedTrackIds()
        }.getOrNull().orEmpty()
        if (likedTrackIds.isNotEmpty()) {
            favoriteRepository.addMissing(
                likedTrackIds.mapTo(linkedSetOf()) { trackId ->
                    buildOnlineMediaId(OnlineMusicProvider.Netease.sourceId, trackId)
                },
            )
        }
    }

    LaunchedEffect(active, searchOpenRequest) {
        if (active && searchOpenRequest > 0) {
            searchActive = true
            currentRoute = CloudMusicRoute.Search
            onSearchOpenRequestHandled()
        }
    }

    LaunchedEffect(active, accountRefreshRequest) {
        if (!active || accountRefreshRequest <= handledAccountRefreshRequest) {
            return@LaunchedEffect
        }
        handledAccountRefreshRequest = accountRefreshRequest
        authState = authStore.load()
        authRevision += 1
    }

    val isDetailRoute = CloudMusicRoute.isDetail(currentRoute)
    val isSearchRoute = currentRoute == CloudMusicRoute.Search
    val onBack: () -> Unit = {
        val route = currentRoute
        when {
            searchActive -> {
                query = ""
                searchActive = false
                currentRoute = CloudMusicRoute.Home
            }
            route is CloudMusicRoute.ArtistTracks -> {
                selectedArtist = null
                artistTracksState = CloudMusicState.Idle
                artistAlbums = emptyList()
                artistIntroductions = emptyList()
                currentRoute = route.returnTo
            }
            route is CloudMusicRoute.ArtistAlbums -> {
                currentRoute = route.returnTo
            }
            route is CloudMusicRoute.RadioPrograms -> {
                selectedRadio = null
                radioTrackState = CloudMusicState.Idle
                currentRoute = route.returnTo
            }
            route is CloudMusicRoute.BannerTrack -> {
                selectedBanner = null
                bannerTrackState = CloudMusicState.Idle
                currentRoute = CloudMusicRoute.Home
            }
            route is CloudMusicRoute.OnlinePlaylistTracks -> {
                selectedOnlinePlaylist = null
                selectedOnlineAlbum = null
                featuredTrackDetailState = CloudMusicState.Idle
                onlinePlaylistTracksState = CloudMusicState.Idle
                onlineAlbumTracksState = CloudMusicState.Idle
                currentRoute = route.returnTo
            }
            route is CloudMusicRoute.OnlineAlbumTracks -> {
                selectedOnlinePlaylist = null
                selectedOnlineAlbum = null
                featuredTrackDetailState = CloudMusicState.Idle
                onlinePlaylistTracksState = CloudMusicState.Idle
                onlineAlbumTracksState = CloudMusicState.Idle
                currentRoute = route.returnTo
            }
            route == CloudMusicRoute.Search -> {
                query = ""
                searchActive = false
                currentRoute = CloudMusicRoute.Home
            }
            route == CloudMusicRoute.FeaturedTracks ||
                route == CloudMusicRoute.FeaturedPlaylists ||
                route == CloudMusicRoute.FeaturedCharts ||
                route == CloudMusicRoute.FeaturedAlbums ||
                route == CloudMusicRoute.FeaturedArtists -> {
                currentRoute = CloudMusicRoute.Home
            }
            route == CloudMusicRoute.RadioList ||
                route == CloudMusicRoute.RadioTracks -> {
                currentRoute = CloudMusicRoute.Radio
            }
            route == CloudMusicRoute.Mine && selectedAccountPlaylistId != null -> {
                selectedAccountPlaylistId = null
                homeState = CloudMusicState.Success(emptyList())
            }
            else -> Unit
        }
    }

    BackHandler(
        enabled = active && !searchActive && !isDetailRoute && !isSearchRoute &&
            currentRoute != CloudMusicRoute.Home &&
            currentRoute != CloudMusicRoute.Radio &&
            currentRoute != CloudMusicRoute.Artists &&
            currentRoute != CloudMusicRoute.Collections &&
            (currentRoute != CloudMusicRoute.Mine || selectedAccountPlaylistId != null),
    ) {
        onBack()
    }

    LaunchedEffect(authRevision) {
        bannerItems = runSuspendCatching {
            activeRepository.featuredBanners()
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(authRevision) {
        featuredHomeState = CloudFeaturedHomeState.Loading
        val result = runSuspendCatching {
            activeRepository.featuredHome()
        }
        featuredHomeState = result.fold(
            onSuccess = { home ->
                if (home.isEmpty()) {
                    CloudFeaturedHomeState.Empty
                } else {
                    CloudFeaturedHomeState.Success(home)
                }
            },
            onFailure = {
                CloudFeaturedHomeState.Error
            },
        )
    }

    LaunchedEffect(currentRoute, authRevision) {
        if (currentRoute != CloudMusicRoute.Radio) {
            return@LaunchedEffect
        }
        radioHomeState = CloudRadioHomeState.Loading
        val result = runSuspendCatching {
            activeRepository.featuredRadioHome()
        }
        radioHomeState = result.fold(
            onSuccess = { home ->
                if (home.isEmpty) {
                    CloudRadioHomeState.Empty
                } else {
                    CloudRadioHomeState.Success(home)
                }
            },
            onFailure = {
                CloudRadioHomeState.Error
            },
        )
    }

    LaunchedEffect(
        currentRoute,
        authRevision,
        authState.isLoggedIn,
        selectedAccountPlaylistId,
    ) {
        if (currentRoute != CloudMusicRoute.Mine || !authState.isLoggedIn) {
            accountPlaylists = emptyList()
            accountAlbums = emptyList()
            accountRadios = emptyList()
            selectedAccountPlaylistId = null
            homeState = CloudMusicState.Idle
            return@LaunchedEffect
        }

        homeState = CloudMusicState.LoadingAccount
        val playlistResult = runSuspendCatching {
            activeRepository.accountPlaylists()
        }
        val albumResult = runSuspendCatching {
            activeRepository.accountAlbums()
        }
        val radioResult = runSuspendCatching {
            activeRepository.accountRadios()
        }
        val cloudPlaylists = playlistResult.getOrNull()
        val cloudAlbums = albumResult.getOrNull().orEmpty()
        val cloudRadios = radioResult.getOrNull().orEmpty()
        if (
            playlistResult.isFailure && albumResult.isFailure && radioResult.isFailure ||
            cloudPlaylists == null && cloudAlbums.isEmpty() && cloudRadios.isEmpty()
        ) {
            accountPlaylists = emptyList()
            accountAlbums = emptyList()
            accountRadios = emptyList()
            homeState = CloudMusicState.AccountError
            return@LaunchedEffect
        }

        accountPlaylists = cloudPlaylists.orEmpty()
        accountAlbums = cloudAlbums
        accountRadios = cloudRadios
        authState = authStore.load()

        if (selectedAccountPlaylistId == null) {
            homeState = if (accountPlaylists.isEmpty() && accountAlbums.isEmpty() && accountRadios.isEmpty()) {
                CloudMusicState.AccountEmpty
            } else {
                CloudMusicState.Success(emptyList())
            }
            return@LaunchedEffect
        }

        val selectedPlaylist = accountPlaylists.firstOrNull { playlist ->
            playlist.playlistId == selectedAccountPlaylistId
        }
        if (selectedPlaylist == null) {
            homeState = CloudMusicState.AccountEmpty
            return@LaunchedEffect
        }

        val tracksResult = runSuspendCatching {
            activeRepository.accountPlaylistTracks(selectedPlaylist)
        }
        homeState = tracksResult.fold(
            onSuccess = { tracks ->
                if (tracks.isEmpty()) {
                    CloudMusicState.AccountEmpty
                } else {
                    CloudMusicState.Success(tracks)
                }
            },
            onFailure = {
                CloudMusicState.AccountError
            },
        )
    }

    LaunchedEffect(currentRoute, authRevision, searchActive, query) {
        if (currentRoute != CloudMusicRoute.Artists || searchActive && query.trim().isNotEmpty()) {
            return@LaunchedEffect
        }
        artistState = CloudArtistState.Loading
        val result = runSuspendCatching {
            activeRepository.featuredArtists()
        }
        artistState = result.fold(
            onSuccess = { artists ->
                if (artists.isEmpty()) {
                    CloudArtistState.Empty
                } else {
                    CloudArtistState.Success(artists)
                }
            },
            onFailure = {
                CloudArtistState.Error
            },
        )
    }

    LaunchedEffect(currentRoute, authRevision) {
        val artist = when (val route = currentRoute) {
            is CloudMusicRoute.ArtistTracks -> route.artist
            is CloudMusicRoute.ArtistAlbums -> route.artist
            else -> return@LaunchedEffect
        }
        artistTracksState = CloudMusicState.LoadingFeatured
        artistAlbums = emptyList()
        artistIntroductions = emptyList()
        val result = runSuspendCatching {
            activeRepository.artistTopTracks(artist)
        }
        artistTracksState = result.fold(
            onSuccess = { tracks ->
                if (tracks.isEmpty()) {
                    CloudMusicState.FeaturedEmpty
                } else {
                    CloudMusicState.Success(tracks)
                }
            },
            onFailure = {
                CloudMusicState.FeaturedError
            },
        )
        artistAlbums = runSuspendCatching {
            activeRepository.artistAlbums(artist)
        }.getOrDefault(emptyList())
        artistIntroductions = runSuspendCatching {
            activeRepository.artistIntroduction(artist)
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(currentRoute, featuredHomeState) {
        if (currentRoute != CloudMusicRoute.FeaturedTracks) {
            return@LaunchedEffect
        }
        featuredTrackDetailState = when (val state = featuredHomeState) {
            CloudFeaturedHomeState.Loading -> CloudMusicState.LoadingFeatured
            CloudFeaturedHomeState.Empty -> CloudMusicState.FeaturedEmpty
            CloudFeaturedHomeState.Error -> CloudMusicState.FeaturedError
            is CloudFeaturedHomeState.Success -> {
                if (state.home.tracks.isEmpty()) {
                    CloudMusicState.FeaturedEmpty
                } else {
                    CloudMusicState.Success(state.home.tracks)
                }
            }
        }
    }

    LaunchedEffect(currentRoute, authRevision, authState.isLoggedIn) {
        val radio = (currentRoute as? CloudMusicRoute.RadioPrograms)?.radio
            ?: return@LaunchedEffect
        if (!authState.isLoggedIn) {
            radioTrackState = CloudMusicState.RadioLoginRequired
            return@LaunchedEffect
        }
        radioTrackState = CloudMusicState.LoadingRadio
        val result = runSuspendCatching {
            activeRepository.radioTracks(radio)
        }
        radioTrackState = result.fold(
            onSuccess = { tracks ->
                if (tracks.isEmpty()) {
                    CloudMusicState.RadioEmpty
                } else {
                    CloudMusicState.Success(tracks)
                }
            },
            onFailure = {
                CloudMusicState.RadioError
            },
        )
    }

    LaunchedEffect(currentRoute, authRevision) {
        val banner = (currentRoute as? CloudMusicRoute.BannerTrack)?.banner
            ?: return@LaunchedEffect
        val trackId = banner.targetTrackId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (trackId == null) {
            bannerTrackState = CloudMusicState.FeaturedEmpty
            return@LaunchedEffect
        }
        bannerTrackState = CloudMusicState.LoadingFeatured
        val result = runSuspendCatching {
            activeRepository.track(trackId)
        }
        bannerTrackState = result.fold(
            onSuccess = { track ->
                if (track == null) {
                    CloudMusicState.FeaturedEmpty
                } else {
                    CloudMusicState.Success(listOf(track))
                }
            },
            onFailure = {
                CloudMusicState.FeaturedError
            },
        )
    }

    LaunchedEffect(currentRoute, authRevision) {
        when (val route = currentRoute) {
            is CloudMusicRoute.OnlinePlaylistTracks -> {
                val playlist = route.playlist
                onlinePlaylistTracksState = CloudMusicState.LoadingFeatured
                val result = runSuspendCatching {
                    activeRepository.playlistTracks(playlist)
                }
                onlinePlaylistTracksState = result.fold(
                    onSuccess = { tracks ->
                        if (tracks.isEmpty()) {
                            CloudMusicState.FeaturedEmpty
                        } else {
                            CloudMusicState.Success(tracks)
                        }
                    },
                    onFailure = {
                        CloudMusicState.FeaturedError
                    },
                )
            }
            is CloudMusicRoute.OnlineAlbumTracks -> {
                val album = route.album
                onlineAlbumTracksState = CloudMusicState.LoadingFeatured
                val result = runSuspendCatching {
                    activeRepository.albumTracks(album)
                }
                onlineAlbumTracksState = result.fold(
                    onSuccess = { tracks ->
                        if (tracks.isEmpty()) {
                            CloudMusicState.FeaturedEmpty
                        } else {
                            CloudMusicState.Success(tracks)
                        }
                    },
                    onFailure = {
                        CloudMusicState.FeaturedError
                    },
                )
            }
            else -> Unit
        }
    }

    LaunchedEffect(searchActive, query, authRevision) {
        if (!searchActive || query.trim().isNotEmpty()) {
            return@LaunchedEffect
        }
        hotSearchState = CloudHotSearchState.Loading
        val result = runSuspendCatching {
            activeRepository.searchHotKeywords()
        }
        hotSearchState = result.fold(
            onSuccess = { keywords ->
                if (keywords.isEmpty()) {
                    CloudHotSearchState.Empty
                } else {
                    CloudHotSearchState.Success(keywords)
                }
            },
            onFailure = {
                CloudHotSearchState.Error
            },
        )
    }

    LaunchedEffect(query, authRevision, searchActive) {
        val normalizedQuery = query.trim()
        if (!searchActive || normalizedQuery.isEmpty()) {
            searchResultsState = CloudSearchResultsState.Idle
            return@LaunchedEffect
        }
        searchResultsState = CloudSearchResultsState.Loading
        delay(CloudSearchDebounceMs)
        val result = runSuspendCatching {
            activeRepository.searchAll(normalizedQuery)
        }
        searchResultsState = result.fold(
            onSuccess = { results ->
                if (!results.hasResults) {
                    CloudSearchResultsState.Empty(normalizedQuery)
                } else {
                    CloudSearchResultsState.Success(results)
                }
            },
            onFailure = {
                CloudSearchResultsState.Error(normalizedQuery)
            },
        )
    }

    val selectedProviderLoggedIn = authState.isLoggedIn
    val normalizedQuery = query.trim()
    val searchResultVisible = searchActive && normalizedQuery.isNotEmpty()
    val searchPromptVisible = searchActive && normalizedQuery.isEmpty()
    val accountLibraryVisible = !searchResultVisible && selectedProviderLoggedIn
    val selectedAccountPlaylist = accountPlaylists.firstOrNull { playlist ->
        playlist.playlistId == selectedAccountPlaylistId
    }
    fun selectMineOrLogin() {
        if (!selectedProviderLoggedIn) {
            Toast.makeText(context, R.string.cloud_music_login_in_settings, Toast.LENGTH_SHORT).show()
            return
        }
        selectedAccountPlaylistId = null
        currentRoute = CloudMusicRoute.Mine
    }
    fun selectCollections() {
        query = ""
        selectedOnlinePlaylist = null
        selectedOnlineAlbum = null
        onlinePlaylistTracksState = CloudMusicState.Idle
        onlineAlbumTracksState = CloudMusicState.Idle
        currentRoute = CloudMusicRoute.Collections
    }
    fun selectArtists() {
        query = ""
        selectedArtist = null
        artistTracksState = CloudMusicState.Idle
        artistAlbums = emptyList()
        artistIntroductions = emptyList()
        currentRoute = CloudMusicRoute.Artists
    }
    fun selectRadio() {
        query = ""
        selectedRadio = null
        radioTrackState = CloudMusicState.Idle
        currentRoute = CloudMusicRoute.Radio
    }
    fun openRadioTracks() {
        currentRoute = CloudMusicRoute.RadioTracks
    }
    fun openRadioList() {
        currentRoute = CloudMusicRoute.RadioList
    }
    fun openRadioPrograms(
        radio: OnlineRadio,
        returnTo: CloudMusicRoute = CloudMusicRoute.Radio,
    ) {
        selectedRadio = radio
        radioTrackState = if (authState.isLoggedIn) {
            CloudMusicState.LoadingRadio
        } else {
            CloudMusicState.RadioLoginRequired
        }
        currentRoute = CloudMusicRoute.RadioPrograms(radio, returnTo)
    }
    fun openArtist(
        artist: OnlineArtist,
        returnTo: CloudMusicRoute = CloudMusicRoute.Artists,
    ) {
        selectedArtist = artist
        query = ""
        searchActive = false
        currentRoute = CloudMusicRoute.ArtistTracks(artist, returnTo)
    }
    fun openArtistAlbums() {
        val artist = when (val route = currentRoute) {
            is CloudMusicRoute.ArtistTracks -> route.artist
            is CloudMusicRoute.ArtistAlbums -> route.artist
            else -> selectedArtist
        }
        if (artist != null && artistAlbums.isNotEmpty()) {
            val returnTo = (currentRoute as? CloudMusicRoute.ArtistAlbums)?.returnTo ?: currentRoute
            currentRoute = CloudMusicRoute.ArtistAlbums(artist, returnTo)
        }
    }
    fun selectRecommendHome() {
        query = ""
        selectedArtist = null
        artistTracksState = CloudMusicState.Idle
        artistAlbums = emptyList()
        artistIntroductions = emptyList()
        selectedBanner = null
        bannerTrackState = CloudMusicState.Idle
        selectedRadio = null
        radioTrackState = CloudMusicState.Idle
        selectedAccountPlaylistId = null
        selectedOnlinePlaylist = null
        selectedOnlineAlbum = null
        featuredTrackDetailState = CloudMusicState.Idle
        onlinePlaylistTracksState = CloudMusicState.Idle
        onlineAlbumTracksState = CloudMusicState.Idle
        currentRoute = CloudMusicRoute.Home
    }
    fun openFeaturedTracks() {
        currentRoute = CloudMusicRoute.FeaturedTracks
    }
    fun openFeaturedPlaylists() {
        currentRoute = CloudMusicRoute.FeaturedPlaylists
    }
    fun openFeaturedCharts() {
        currentRoute = CloudMusicRoute.FeaturedCharts
    }
    fun openFeaturedAlbums() {
        currentRoute = CloudMusicRoute.FeaturedAlbums
    }
    fun openFeaturedArtists() {
        currentRoute = CloudMusicRoute.FeaturedArtists
    }
    fun openOnlinePlaylist(
        playlist: OnlinePlaylist,
        returnTo: CloudMusicRoute = CloudMusicRoute.Home,
    ) {
        selectedOnlinePlaylist = playlist
        selectedOnlineAlbum = null
        onlinePlaylistTracksState = CloudMusicState.LoadingFeatured
        currentRoute = CloudMusicRoute.OnlinePlaylistTracks(playlist, returnTo)
    }
    fun openOnlineAlbum(
        album: OnlineAlbum,
        returnTo: CloudMusicRoute = CloudMusicRoute.Home,
    ) {
        selectedOnlineAlbum = album
        selectedOnlinePlaylist = null
        onlineAlbumTracksState = CloudMusicState.LoadingFeatured
        currentRoute = CloudMusicRoute.OnlineAlbumTracks(album, returnTo)
    }
    fun openBannerTarget(banner: OnlineBanner) {
        when {
            !banner.targetTrackId.isNullOrBlank() -> {
                selectedBanner = banner
                bannerTrackState = CloudMusicState.LoadingFeatured
                currentRoute = CloudMusicRoute.BannerTrack(banner)
            }
            !banner.targetAlbumId.isNullOrBlank() -> {
                openOnlineAlbum(
                    album = OnlineAlbum(
                        provider = banner.provider,
                        albumId = banner.targetAlbumId,
                        title = banner.subtitle?.takeIf(String::isNotBlank) ?: banner.title,
                        artworkUrl = banner.imageUrl,
                    ),
                    returnTo = CloudMusicRoute.Home,
                )
            }
            !banner.targetPlaylistId.isNullOrBlank() -> {
                openOnlinePlaylist(
                    playlist = OnlinePlaylist(
                        provider = banner.provider,
                        playlistId = banner.targetPlaylistId,
                        title = banner.subtitle?.takeIf(String::isNotBlank) ?: banner.title,
                        artworkUrl = banner.imageUrl,
                    ),
                    returnTo = CloudMusicRoute.Home,
                )
            }
            else -> Unit
        }
    }
    val routeForUi = currentRoute
    val primaryRouteForUi = routeForUi.primaryRoute()
    val selectedQuickEntry = when {
        searchActive -> null
        primaryRouteForUi == CloudMusicRoute.Mine -> CloudMusicHomeEntry.Mine
        primaryRouteForUi == CloudMusicRoute.Collections -> CloudMusicHomeEntry.Collection
        primaryRouteForUi == CloudMusicRoute.Artists -> CloudMusicHomeEntry.Artist
        primaryRouteForUi == CloudMusicRoute.Radio ||
            primaryRouteForUi == CloudMusicRoute.RadioList ||
            primaryRouteForUi == CloudMusicRoute.RadioTracks -> CloudMusicHomeEntry.Radio
        else -> CloudMusicHomeEntry.Recommend
    }
    val sectionTitle = when {
        searchPromptVisible -> stringResource(R.string.cloud_music_section_search)
        searchResultVisible -> stringResource(R.string.cloud_music_section_search_results)
        routeForUi == CloudMusicRoute.Mine && selectedAccountPlaylist == null ->
            stringResource(R.string.cloud_music_section_account_library)
        routeForUi == CloudMusicRoute.Mine && selectedAccountPlaylist != null ->
            selectedAccountPlaylist.displayTitle(context)
        routeForUi == CloudMusicRoute.Artists -> stringResource(R.string.cloud_music_section_hot_artists)
        routeForUi is CloudMusicRoute.ArtistTracks -> routeForUi.artist.name
        routeForUi is CloudMusicRoute.ArtistAlbums -> stringResource(R.string.cloud_music_section_artist_albums)
        routeForUi == CloudMusicRoute.FeaturedTracks -> stringResource(R.string.cloud_music_section_daily_recommend)
        routeForUi == CloudMusicRoute.Collections -> stringResource(R.string.cloud_music_section_featured_playlists)
        routeForUi == CloudMusicRoute.FeaturedPlaylists ->
            stringResource(R.string.cloud_music_section_featured_playlists)
        routeForUi == CloudMusicRoute.FeaturedCharts -> stringResource(R.string.cloud_music_section_hot_charts)
        routeForUi == CloudMusicRoute.FeaturedAlbums -> stringResource(R.string.cloud_music_section_new_albums)
        routeForUi == CloudMusicRoute.FeaturedArtists -> stringResource(R.string.cloud_music_section_hot_artists)
        routeForUi is CloudMusicRoute.BannerTrack -> routeForUi.banner.title
        routeForUi == CloudMusicRoute.Radio -> stringResource(R.string.cloud_music_section_radio)
        routeForUi == CloudMusicRoute.RadioList -> stringResource(R.string.cloud_music_section_hot_radios)
        routeForUi == CloudMusicRoute.RadioTracks -> stringResource(R.string.cloud_music_section_radio_programs)
        routeForUi is CloudMusicRoute.RadioPrograms -> routeForUi.radio.title
        routeForUi is CloudMusicRoute.OnlinePlaylistTracks -> routeForUi.playlist.title
        routeForUi is CloudMusicRoute.OnlineAlbumTracks -> routeForUi.album.title
        !accountLibraryVisible || routeForUi == CloudMusicRoute.Home ->
            stringResource(R.string.cloud_music_section_netease_picks)
        else -> stringResource(R.string.cloud_music_liked_songs_entry)
    }
    val featuredHomeVisible = !searchActive &&
        normalizedQuery.isEmpty() &&
        (
            currentRoute == CloudMusicRoute.Home ||
                (!selectedProviderLoggedIn && currentRoute == CloudMusicRoute.Mine && selectedAccountPlaylistId != null)
            )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.Transparent),
    ) {
        AnimatedVisibility(
            visible = searchActive,
            enter = expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                fadeIn(animationSpec = tween(140)),
            exit = shrinkVertically(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                fadeOut(animationSpec = tween(120)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CloudMusicSearchField(
                query = query,
                hint = stringResource(R.string.cloud_music_search_hint_netease),
                active = active && searchActive,
                onQueryChange = { value -> query = value },
                onCancel = {
                    query = ""
                    searchActive = false
                    currentRoute = CloudMusicRoute.Home
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = featuredHomeVisible,
            enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                fadeOut(animationSpec = tween(120)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CloudMusicBannerStrip(
                banners = bannerItems,
                active = active,
                onBannerClick = ::openBannerTarget,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!searchActive) {
            CloudMusicHomeEntryRow(
                selectedEntry = selectedQuickEntry,
                onMineClick = ::selectMineOrLogin,
                onRecommendClick = ::selectRecommendHome,
                onRadioClick = ::selectRadio,
                onCollectionClick = ::selectCollections,
                onArtistClick = ::selectArtists,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            SmartisanDrawableBackground(
                drawableRes = R.drawable.account_background,
                modifier = Modifier.matchParentSize(),
            )
            Column(modifier = Modifier.matchParentSize()) {
                CloudMusicSectionTitle(
                    title = sectionTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    CloudMusicTransitionHost(
                        currentRoute = currentRoute,
                        onNavigate = { currentRoute = it },
                        onBack = onBack,
                        modifier = Modifier.matchParentSize(),
                        primaryContent = { route ->
                            when (route) {
                                CloudMusicRoute.Home -> {
                                    CloudFeaturedHomeContent(
                                        state = featuredHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTracksClick = ::openFeaturedTracks,
                                        onPlaylistsClick = ::openFeaturedPlaylists,
                                        onChartsClick = ::openFeaturedCharts,
                                        onAlbumsClick = ::openFeaturedAlbums,
                                        onArtistsClick = ::openFeaturedArtists,
                                        onPlaylistClick = { playlist ->
                                            openOnlinePlaylist(playlist, CloudMusicRoute.Home)
                                        },
                                        onAlbumClick = { album ->
                                            openOnlineAlbum(album, CloudMusicRoute.Home)
                                        },
                                        onArtistClick = { artist ->
                                            openArtist(artist, CloudMusicRoute.Home)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.Mine -> {
                                    if (selectedAccountPlaylistId == null) {
                                        when {
                                            homeState == CloudMusicState.LoadingAccount -> CloudMusicBlankState(
                                                title = stringResource(R.string.cloud_music_account_playlists_loading),
                                                subtitle = null,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            homeState == CloudMusicState.AccountError -> CloudMusicBlankState(
                                                title = stringResource(R.string.cloud_music_account_playlists_error),
                                                subtitle = stringResource(R.string.cloud_music_empty_subtitle),
                                                actionText = stringResource(R.string.cloud_music_retry),
                                                onActionClick = { authRevision += 1 },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            accountPlaylists.isEmpty() && accountAlbums.isEmpty() && accountRadios.isEmpty() -> CloudMusicBlankState(
                                                title = stringResource(R.string.cloud_music_playlists_empty),
                                                subtitle = stringResource(R.string.cloud_music_empty_subtitle),
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            else -> CloudMusicAccountLibraryList(
                                                playlists = accountPlaylists,
                                                albums = accountAlbums,
                                                radios = accountRadios,
                                                selectedPlaylistId = selectedAccountPlaylistId,
                                                active = active,
                                                playbackBarOverlayHeight = playbackBarOverlayHeight,
                                                onPlaylistClick = { playlist ->
                                                    selectedAccountPlaylistId = playlist.playlistId
                                                    currentRoute = CloudMusicRoute.Mine
                                                },
                                                onAlbumClick = { album ->
                                                    openOnlineAlbum(album, CloudMusicRoute.Mine)
                                                },
                                                onRadioClick = { radio ->
                                                    openRadioPrograms(radio, CloudMusicRoute.Mine)
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    } else {
                                        CloudMusicStateContent(
                                            state = homeState,
                                            repository = activeRepository,
                                            authLoggedIn = authState.isLoggedIn,
                                            active = active,
                                            playbackBarOverlayHeight = playbackBarOverlayHeight,
                                            onRetryClick = { authRevision += 1 },
                                            onTrackMoreClick = onTrackMoreClick,
                                            accountPlaylist = selectedAccountPlaylist,
                                            onAccountPlaylistTracksChanged = { authRevision += 1 },
                                            onAccountPlaylistDeleted = { deletedPlaylist ->
                                                accountPlaylists = accountPlaylists.filterNot { playlist ->
                                                    playlist.playlistId == deletedPlaylist.playlistId
                                                }
                                                selectedAccountPlaylistId = null
                                                currentRoute = CloudMusicRoute.Mine
                                                authRevision += 1
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                CloudMusicRoute.Collections -> {
                                    CloudFeaturedHomeCoverListContent(
                                        state = featuredHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        items = { home -> home.playlists },
                                        title = OnlinePlaylist::title,
                                        subtitle = { playlist -> playlist.homeSubtitle(LocalContext.current) },
                                        imageUrl = OnlinePlaylist::artworkUrl,
                                        onItemClick = { playlist ->
                                            openOnlinePlaylist(playlist, CloudMusicRoute.Collections)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.FeaturedTracks -> {
                                    CloudMusicStateContent(
                                        state = featuredTrackDetailState,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.FeaturedPlaylists -> {
                                    CloudFeaturedHomeCoverListContent(
                                        state = featuredHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        items = { home -> home.playlists },
                                        title = OnlinePlaylist::title,
                                        subtitle = { playlist -> playlist.homeSubtitle(LocalContext.current) },
                                        imageUrl = OnlinePlaylist::artworkUrl,
                                        onItemClick = { playlist ->
                                            openOnlinePlaylist(playlist, CloudMusicRoute.FeaturedPlaylists)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.FeaturedCharts -> {
                                    CloudFeaturedHomeCoverListContent(
                                        state = featuredHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        items = { home -> home.charts },
                                        title = OnlinePlaylist::title,
                                        subtitle = { playlist -> playlist.homeSubtitle(LocalContext.current) },
                                        imageUrl = OnlinePlaylist::artworkUrl,
                                        onItemClick = { playlist ->
                                            openOnlinePlaylist(playlist, CloudMusicRoute.FeaturedCharts)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.FeaturedAlbums -> {
                                    CloudFeaturedHomeCoverListContent(
                                        state = featuredHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        items = { home -> home.albums },
                                        title = OnlineAlbum::title,
                                        subtitle = { album -> album.albumSubtitle(LocalContext.current) },
                                        imageUrl = OnlineAlbum::artworkUrl,
                                        onItemClick = { album ->
                                            openOnlineAlbum(album, CloudMusicRoute.FeaturedAlbums)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.FeaturedArtists -> {
                                    CloudFeaturedHomeArtistListContent(
                                        state = featuredHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        active = active,
                                        onRetryClick = { authRevision += 1 },
                                        onArtistClick = { artist ->
                                            openArtist(artist, CloudMusicRoute.FeaturedArtists)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.Artists -> {
                                    CloudMusicArtistStateContent(
                                        state = artistState,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onArtistClick = { artist -> openArtist(artist, CloudMusicRoute.Artists) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.Radio -> {
                                    CloudRadioHomeContent(
                                        state = radioHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTracksClick = ::openRadioTracks,
                                        onRadioListClick = ::openRadioList,
                                        onRadioClick = { radio -> openRadioPrograms(radio) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.RadioList -> {
                                    CloudRadioListContent(
                                        state = radioHomeState,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onRadioClick = { radio -> openRadioPrograms(radio) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                CloudMusicRoute.RadioTracks -> {
                                    CloudRadioTrackStateContent(
                                        state = radioHomeState,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                else -> Unit
                            }
                        },
                        secondaryContent = { route ->
                            when (route) {
                                CloudMusicRoute.Search -> {
                                    if (searchPromptVisible) {
                                        CloudHotSearchContent(
                                            state = hotSearchState,
                                            playbackBarOverlayHeight = playbackBarOverlayHeight,
                                            onRetryClick = { authRevision += 1 },
                                            onKeywordClick = { keyword ->
                                                query = keyword
                                                searchCategory = CloudSearchCategory.All
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        CloudMusicSearchResultsContent(
                                            state = searchResultsState,
                                            selectedCategory = searchCategory,
                                            onCategoryChange = { category -> searchCategory = category },
                                            repository = activeRepository,
                                            authLoggedIn = authState.isLoggedIn,
                                            active = active,
                                            playbackBarOverlayHeight = playbackBarOverlayHeight,
                                            onRetryClick = { authRevision += 1 },
                                            onTrackMoreClick = onTrackMoreClick,
                                            onPlaylistClick = { playlist ->
                                                query = ""
                                                searchActive = false
                                                openOnlinePlaylist(playlist, CloudMusicRoute.Home)
                                            },
                                            onAlbumClick = { album ->
                                                query = ""
                                                searchActive = false
                                                openOnlineAlbum(album, CloudMusicRoute.Home)
                                            },
                                            onArtistClick = { artist ->
                                                openArtist(artist, CloudMusicRoute.Home)
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                is CloudMusicRoute.BannerTrack -> {
                                    val banner = route.banner
                                    CloudMusicTrackDetailContent(
                                        state = bannerTrackState,
                                        title = banner.title,
                                        subtitle = banner.subtitle,
                                        artworkUrl = banner.imageUrl,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is CloudMusicRoute.ArtistTracks -> {
                                    val artist = route.artist
                                    CloudMusicTrackDetailContent(
                                        state = artistTracksState,
                                        title = artist.name,
                                        subtitle = artist.subtitleText(context),
                                        artworkUrl = artist.artworkUrl,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        extraContent = if (artistAlbums.isNotEmpty() || artistIntroductions.isNotEmpty()) {
                                            {
                                                CloudArtistIntroductionSection(
                                                    sections = artistIntroductions,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                CloudHomeAlbumSection(
                                                    title = stringResource(R.string.cloud_music_section_artist_albums),
                                                    albums = artistAlbums,
                                                    actionText = stringResource(R.string.cloud_music_section_view_all),
                                                    onActionClick = ::openArtistAlbums,
                                                    onAlbumClick = { album ->
                                                        openOnlineAlbum(album, route)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is CloudMusicRoute.ArtistAlbums -> {
                                    CloudSearchCoverResultList(
                                        items = artistAlbums,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        title = OnlineAlbum::title,
                                        subtitle = { album -> album.albumSubtitle(context) },
                                        imageUrl = OnlineAlbum::artworkUrl,
                                        onItemClick = { album ->
                                            openOnlineAlbum(album, route)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is CloudMusicRoute.RadioPrograms -> {
                                    val radio = route.radio
                                    CloudMusicTrackDetailContent(
                                        state = radioTrackState,
                                        title = radio.title,
                                        subtitle = radio.subtitleText(context),
                                        artworkUrl = radio.artworkUrl,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is CloudMusicRoute.OnlinePlaylistTracks -> {
                                    val playlist = route.playlist
                                    CloudMusicTrackDetailContent(
                                        state = onlinePlaylistTracksState,
                                        title = playlist.title,
                                        subtitle = playlist.homeSubtitle(context),
                                        artworkUrl = playlist.artworkUrl,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is CloudMusicRoute.OnlineAlbumTracks -> {
                                    val album = route.album
                                    CloudMusicTrackDetailContent(
                                        state = onlineAlbumTracksState,
                                        title = album.title,
                                        subtitle = album.albumSubtitle(context),
                                        artworkUrl = album.artworkUrl,
                                        repository = activeRepository,
                                        authLoggedIn = authState.isLoggedIn,
                                        active = active,
                                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                                        onRetryClick = { authRevision += 1 },
                                        onTrackMoreClick = onTrackMoreClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudMusicSearchField(
    query: String,
    hint: String,
    active: Boolean,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(active) {
        if (active) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    Row(
        modifier = modifier
            .height(CloudSearchBarHeight)
            .background(ComposeColor.White)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = ComposeColor(0xCC000000),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                val clearInteractionSource = remember { MutableInteractionSource() }
                val clearPressed by clearInteractionSource.collectIsPressedAsState()
                Box(modifier = Modifier.fillMaxSize()) {
                    SmartisanDrawableBackground(
                        drawableRes = R.drawable.search_field,
                        modifier = Modifier.matchParentSize(),
                    )
                    Image(
                        painter = painterResource(R.drawable.search_bar_left_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .width(24.dp)
                            .height(30.dp),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(
                                start = 36.dp,
                                end = if (query.isNotEmpty()) 30.dp else 12.dp,
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = hint,
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    color = ComposeColor(0x66000000),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .align(Alignment.CenterEnd)
                                .clickable(
                                    interactionSource = clearInteractionSource,
                                    indication = null,
                                    onClick = { onQueryChange("") },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(
                                    if (clearPressed) {
                                        R.drawable.text_clear_btn_pressed
                                    } else {
                                        R.drawable.text_clear_btn
                                    },
                                ),
                                contentDescription = stringResource(R.string.clear_search_text),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            },
        )
        Text(
            text = stringResource(R.string.cloud_music_search_cancel),
            style = TextStyle(
                fontSize = 14.sp,
                color = CloudAccentColor,
            ),
            maxLines = 1,
            modifier = Modifier
                .padding(start = 12.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onCancel,
                ),
        )
    }
}

@Composable
private fun CloudMusicHomeEntryRow(
    selectedEntry: CloudMusicHomeEntry?,
    onMineClick: () -> Unit,
    onRecommendClick: () -> Unit,
    onRadioClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        CloudMusicHomeEntry.Mine to onMineClick,
        CloudMusicHomeEntry.Recommend to onRecommendClick,
        CloudMusicHomeEntry.Radio to onRadioClick,
        CloudMusicHomeEntry.Collection to onCollectionClick,
        CloudMusicHomeEntry.Artist to onArtistClick,
    )
    Column(modifier = modifier.background(ComposeColor.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CloudHomeEntryRowHeight)
                .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { (entry, onClick) ->
                CloudMusicHomeEntryButton(
                    entry = entry,
                    selected = selectedEntry == entry,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        CloudMusicDivider()
    }
}

@Composable
private fun CloudMusicHomeEntryButton(
    entry: CloudMusicHomeEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(entry.iconRes),
            contentDescription = stringResource(entry.labelRes),
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = stringResource(entry.labelRes),
            style = TextStyle(
                fontSize = 10.sp,
                color = if (selected) CloudAccentColor else ComposeColor(0x99000000),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

private enum class CloudMusicHomeEntry(
    val labelRes: Int,
    val iconRes: Int,
) {
    Mine(
        labelRes = R.string.cloud_music_entry_mine,
        iconRes = R.drawable.net_icon_my_music,
    ),
    Recommend(
        labelRes = R.string.cloud_music_entry_recommend,
        iconRes = R.drawable.net_icon_recommend,
    ),
    Radio(
        labelRes = R.string.cloud_music_entry_radio,
        iconRes = R.drawable.net_icon_radio,
    ),
    Collection(
        labelRes = R.string.cloud_music_entry_collection,
        iconRes = R.drawable.net_icon_collection,
    ),
    Artist(
        labelRes = R.string.cloud_music_entry_artist,
        iconRes = R.drawable.net_icon_artist,
    ),
}

private suspend inline fun <T> runSuspendCatching(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
