package com.smartisanos.music.ui.shell

import android.app.Activity
import android.content.ContentUris
import android.net.Uri
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.SessionResult
import com.smartisanos.music.ExternalAudioLaunchRequest
import com.smartisanos.music.R
import com.smartisanos.music.data.favorite.FavoriteSongsRepository
import com.smartisanos.music.data.library.LibraryExclusions
import com.smartisanos.music.data.library.LibraryExclusionsStore
import com.smartisanos.music.data.online.OnlineMusicRepositoryRouter
import com.smartisanos.music.data.online.isOnlineMediaItem
import com.smartisanos.music.data.playlist.PlaylistCreateResult
import com.smartisanos.music.data.playlist.PlaylistRepository
import com.smartisanos.music.data.settings.ArtistSettings
import com.smartisanos.music.data.settings.ArtistSettingsStore
import com.smartisanos.music.data.settings.LibraryDisplaySettings
import com.smartisanos.music.data.settings.LibraryDisplaySettingsStore
import com.smartisanos.music.data.settings.OnlineMusicSettings
import com.smartisanos.music.data.settings.OnlineMusicSettingsStore
import com.smartisanos.music.data.settings.PlaybackSettings
import com.smartisanos.music.data.settings.PlaybackSettingsStore
import com.smartisanos.music.isExternalAudioLaunchItem
import com.smartisanos.music.playback.LocalAudioLibrary
import com.smartisanos.music.playback.LocalPlaybackController
import com.smartisanos.music.playback.ProvidePlaybackController
import com.smartisanos.music.playback.artworkRequestKey
import com.smartisanos.music.playback.await
import com.smartisanos.music.playback.deduplicateQueueCandidates
import com.smartisanos.music.playback.invalidateLibrary
import com.smartisanos.music.playback.refreshLibrary
import com.smartisanos.music.playback.removeMediaItemsByMediaIds
import com.smartisanos.music.playback.withPlaybackRating
import com.smartisanos.music.resolveExternalAudioMediaStoreIds
import com.smartisanos.music.resolveExternalAudioArtist
import com.smartisanos.music.ui.components.LegacyTrackActionItem
import com.smartisanos.music.ui.components.LegacyTrackActionsOverlay
import com.smartisanos.music.ui.album.AlbumViewMode
import com.smartisanos.music.ui.navigation.MusicDestination
import com.smartisanos.music.ui.shell.dialogs.LegacySongDeleteConfirmOverlay
import com.smartisanos.music.ui.shell.library.rememberLegacyLibraryMediaState
import com.smartisanos.music.ui.shell.playback.LegacyPlaybackBarSnapshot
import com.smartisanos.music.ui.shell.playback.LegacyPortPlaybackBar
import com.smartisanos.music.ui.shell.playback.loadLegacyArtworkBitmap
import com.smartisanos.music.ui.shell.playback.toExternalAudioMediaItem
import com.smartisanos.music.ui.shell.search.LegacyPortSearchOverlay
import com.smartisanos.music.ui.shell.search.LegacySearchDrilldownTarget
import com.smartisanos.music.ui.shell.tabs.LegacyPortBottomBar
import com.smartisanos.music.ui.shell.tabs.LegacyPortTabContent
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LegacyTrackActionSource {
    Library,
    CloudMusic,
    Loved,
    Playlist,
}

@Composable
fun LegacyPortMainShell(
    playbackLaunchRequest: Int = 0,
    externalAudioLaunchRequest: ExternalAudioLaunchRequest? = null,
    onExternalAudioLaunchConsumed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ProvidePlaybackController {
        LegacyPortMainShellContent(
            playbackLaunchRequest = playbackLaunchRequest,
            externalAudioLaunchRequest = externalAudioLaunchRequest,
            onExternalAudioLaunchConsumed = onExternalAudioLaunchConsumed,
            modifier = modifier,
        )
    }
}

@Composable
private fun LegacyPortMainShellContent(
    playbackLaunchRequest: Int,
    externalAudioLaunchRequest: ExternalAudioLaunchRequest?,
    onExternalAudioLaunchConsumed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val favoriteRepository = remember(context.applicationContext) {
        FavoriteSongsRepository.getInstance(context.applicationContext)
    }
    val playlistRepository = remember(context.applicationContext) {
        PlaylistRepository.getInstance(context.applicationContext)
    }
    val onlineMusicRepository = remember(context.applicationContext) {
        OnlineMusicRepositoryRouter(context.applicationContext)
    }
    val libraryExclusionsStore = remember(context.applicationContext) {
        LibraryExclusionsStore(context.applicationContext)
    }
    val playbackSettingsStore = remember(context.applicationContext) {
        PlaybackSettingsStore(context.applicationContext)
    }
    val onlineMusicSettingsStore = remember(context.applicationContext) {
        OnlineMusicSettingsStore(context.applicationContext)
    }
    val artistSettingsStore = remember(context.applicationContext) {
        ArtistSettingsStore(context.applicationContext)
    }
    val libraryDisplaySettingsStore = remember(context.applicationContext) {
        LibraryDisplaySettingsStore(context.applicationContext)
    }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    val libraryExclusions by libraryExclusionsStore.exclusions.collectAsState(initial = LibraryExclusions())
    val playbackSettings by playbackSettingsStore.settings.collectAsState(initial = PlaybackSettings())
    val onlineMusicSettings by onlineMusicSettingsStore.settings.collectAsState(initial = OnlineMusicSettings())
    val artistSettings by artistSettingsStore.settings.collectAsState(initial = ArtistSettings())
    val libraryDisplaySettings by libraryDisplaySettingsStore.settings.collectAsState(initial = LibraryDisplaySettings())
    val albumViewMode = libraryDisplaySettings.albumViewMode
    val artistAlbumViewMode = libraryDisplaySettings.artistAlbumViewMode
    val unknownSongTitle = stringResource(R.string.unknown_song_title)
    val favoriteRecords by favoriteRepository.observeFavorites().collectAsState(initial = emptyList())
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var playbackVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDrilldownTarget by remember { mutableStateOf<LegacySearchDrilldownTarget?>(null) }
    var currentDestination by remember { mutableStateOf(MusicDestination.Playlist) }
    var playlistAddModeActive by remember { mutableStateOf(false) }
    var moreSettingsPageActive by remember { mutableStateOf(false) }
    var cloudMusicSearchOpenRequest by remember { mutableStateOf(0) }
    var songsEditMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(emptySet<String>()) }
    var albumEditMode by remember { mutableStateOf(false) }
    var selectedAlbumIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedAlbumTitle by remember { mutableStateOf<String?>(null) }
    var selectedArtistTarget by remember { mutableStateOf<LegacyArtistTarget?>(null) }
    var libraryRefreshVersion by remember { mutableStateOf(0) }
    var libraryRefreshing by remember { mutableStateOf(false) }
    var libraryLoadRequested by remember { mutableStateOf(false) }
    var showSongDeleteConfirm by remember { mutableStateOf(false) }
    var pendingSongDeleteMediaIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingSongDeleteDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSystemDeleteSongIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingPlaylistPickerMediaItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pendingTrackActionItem by remember { mutableStateOf<MediaItem?>(null) }
    var pendingTrackActionSource by remember { mutableStateOf(LegacyTrackActionSource.Library) }
    var playbackPlaylistCreateRequest by remember { mutableStateOf<LegacyPlaylistNameDialogRequest.Create?>(null) }
    var ratingOverrides by remember { mutableStateOf(emptyMap<String, Int>()) }
    var snapshot by remember(controller) {
        mutableStateOf(
            LegacyPlaybackBarSnapshot(
                mediaItem = controller?.currentMediaItem,
                isPlaying = controller?.isPlaying == true,
            ),
        )
    }
    var playbackBarContentSnapshot by remember(controller) {
        mutableStateOf(snapshot)
    }
    val legacyLibrary = rememberLegacyLibraryMediaState(
        loadRequested = libraryLoadRequested,
        libraryRefreshVersion = libraryRefreshVersion,
    )
    val legacyLibraryItems = remember(legacyLibrary.items, ratingOverrides) {
        legacyLibrary.items.withRatingOverrides(ratingOverrides)
    }
    val artworkRequestKey = playbackBarContentSnapshot.mediaItem?.artworkRequestKey()
    val artworkBitmap by produceState<Bitmap?>(initialValue = null, artworkRequestKey) {
        value = playbackBarContentSnapshot.mediaItem?.let { mediaItem ->
            loadLegacyArtworkBitmap(context.applicationContext, mediaItem)
        }
    }
    val albumPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val artistRootPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val artistNestedPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val playbackBarRequestedVisible = snapshot.mediaItem != null
    val playbackBarHeight = 67.dp
    var playbackBarComposed by remember { mutableStateOf(false) }
    val openSearchOverlay = {
        libraryLoadRequested = true
        searchQuery = ""
        searchDrilldownTarget = null
        searchVisible = true
    }
    fun openCurrentSearch() {
        if (currentDestination == MusicDestination.CloudMusic) {
            cloudMusicSearchOpenRequest += 1
        } else {
            openSearchOverlay()
        }
    }
    val closeSearchOverlay = {
        searchVisible = false
        searchDrilldownTarget = null
    }

    fun closeAlbumDetail() {
        selectedAlbumId = null
        selectedAlbumTitle = null
    }

    fun closeArtistDetail() {
        selectedArtistTarget = selectedArtistTarget?.parentTarget()
    }

    DisposableEffect(controller) {
        if (controller == null) {
            snapshot = LegacyPlaybackBarSnapshot()
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val nextSnapshot = LegacyPlaybackBarSnapshot(
                    mediaItem = player.currentMediaItem,
                    isPlaying = player.isPlaying,
                )
                snapshot = nextSnapshot
                if (nextSnapshot.mediaItem != null) {
                    playbackBarContentSnapshot = nextSnapshot
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val failedItem = controller.currentMediaItem
                if (failedItem?.isOnlineMediaItem() == true) {
                    Toast.makeText(context, R.string.online_music_play_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        controller.addListener(listener)
        val initialSnapshot = LegacyPlaybackBarSnapshot(
            mediaItem = controller.currentMediaItem,
            isPlaying = controller.isPlaying,
        )
        snapshot = initialSnapshot
        if (initialSnapshot.mediaItem != null) {
            playbackBarContentSnapshot = initialSnapshot
        }
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(playbackBarRequestedVisible) {
        if (playbackBarRequestedVisible) {
            playbackBarComposed = true
        }
    }

    fun cleanupDeletedSongs(mediaIds: Set<String>, hideFromLibrary: Boolean) {
        if (mediaIds.isEmpty()) {
            return
        }
        controller.removeMediaItemsByMediaIds(mediaIds)
        scope.launch {
            if (hideFromLibrary) {
                libraryExclusionsStore.hideMediaIds(mediaIds)
            }
            favoriteRepository.removeAll(mediaIds)
            playlistRepository.removeMediaIdsFromAll(mediaIds)
            runCatching {
                controller?.invalidateLibrary()?.await(context)
            }
            libraryRefreshVersion += 1
        }
    }

    fun reclaimHiddenMediaIds(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) {
            return
        }
        controller.removeMediaItemsByMediaIds(mediaIds)
    }

    fun enqueueResolvedMediaItems(items: List<MediaItem>) {
        if (items.isEmpty()) {
            return
        }
        if (controller?.repeatMode == Player.REPEAT_MODE_ONE) {
            Toast.makeText(context, R.string.can_not_add_to_queue_single_repeat, Toast.LENGTH_SHORT).show()
        } else {
            val deduplicatedItems = controller?.deduplicateQueueCandidates(items).orEmpty()
            if (deduplicatedItems.isNotEmpty()) {
                controller?.addMediaItems(deduplicatedItems)
                Toast.makeText(context, R.string.add_to_queue_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enqueueMediaItems(items: List<MediaItem>) {
        if (items.none(MediaItem::isOnlineMediaItem)) {
            enqueueResolvedMediaItems(items)
            return
        }
        scope.launch {
            val resolvedItems = withContext(Dispatchers.IO) {
                items.mapNotNull { item ->
                    when {
                        !item.isOnlineMediaItem() -> item
                        item.localConfiguration?.uri != null -> item
                        else -> runCatching {
                            onlineMusicRepository.resolvePlayableMediaItem(item)
                        }.getOrNull()
                    }
                }
            }
            if (resolvedItems.isEmpty()) {
                Toast.makeText(context, R.string.online_music_play_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            enqueueResolvedMediaItems(resolvedItems)
        }
    }

    fun requestAddToPlaylist(items: List<MediaItem>) {
        val candidates = items.filter { item ->
            item.mediaId.isNotBlank() && !item.isExternalAudioLaunchItem() && !item.isOnlineMediaItem()
        }
        if (candidates.isNotEmpty()) {
            pendingPlaylistPickerMediaItems = candidates
        }
    }

    fun showTrackActions(
        item: MediaItem,
        source: LegacyTrackActionSource,
    ) {
        if (item.mediaId.isBlank()) {
            return
        }
        pendingTrackActionItem = item
        pendingTrackActionSource = source
    }

    fun dismissTrackActions() {
        pendingTrackActionItem = null
    }

    fun dismissSongDeleteConfirmation() {
        val dismissAction = pendingSongDeleteDismissAction
        showSongDeleteConfirm = false
        pendingSongDeleteMediaIds = emptySet()
        pendingSongDeleteDismissAction = null
        dismissAction?.invoke()
    }

    fun requestSongDeleteConfirmation(
        mediaIds: Set<String>,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (mediaIds.isEmpty()) {
            return
        }
        pendingSongDeleteMediaIds = mediaIds
        pendingSongDeleteDismissAction = onDismiss
        showSongDeleteConfirm = true
    }

    fun removeFavoriteMediaIds(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) {
            return
        }
        scope.launch {
            if (mediaIds.size == 1) {
                favoriteRepository.remove(mediaIds.first())
            } else {
                favoriteRepository.removeAll(mediaIds)
            }
        }
    }

    fun refreshLegacyLibrary() {
        if (libraryRefreshing) {
            return
        }
        val playbackController = controller
        if (playbackController == null) {
            Toast.makeText(context, R.string.library_refresh_failed, Toast.LENGTH_SHORT).show()
            return
        }
        libraryRefreshing = true
        scope.launch {
            val result = runCatching {
                playbackController.refreshLibrary().await(context)
            }.getOrNull()
            libraryRefreshing = false
            if (result?.resultCode == SessionResult.RESULT_SUCCESS) {
                libraryRefreshVersion += 1
                Toast.makeText(context, R.string.library_refresh_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.library_refresh_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val mediaIds = pendingSystemDeleteSongIds
        pendingSystemDeleteSongIds = emptySet()
        if (result.resultCode == Activity.RESULT_OK) {
            cleanupDeletedSongs(mediaIds, hideFromLibrary = false)
        }
    }

    fun requestSystemDeleteMediaIds(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) {
            return
        }
        val deleteUris = mediaIds.mapNotNull { mediaId ->
            mediaId.toLegacyMediaStoreDeleteUri()
        }
        if (deleteUris.isEmpty()) {
            cleanupDeletedSongs(mediaIds, hideFromLibrary = true)
            return
        }
        val request = runCatching {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                deleteUris,
            )
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        }.getOrElse {
            cleanupDeletedSongs(mediaIds, hideFromLibrary = true)
            return
        }
        pendingSystemDeleteSongIds = mediaIds
        runCatching {
            deleteMediaLauncher.launch(request)
        }.onFailure {
            pendingSystemDeleteSongIds = emptySet()
            cleanupDeletedSongs(mediaIds, hideFromLibrary = true)
        }
    }

    LaunchedEffect(playbackLaunchRequest) {
        if (playbackLaunchRequest > 0) {
            playbackVisible = true
        }
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination.requiresFullLibraryItems()) {
            libraryLoadRequested = true
        }
        if (currentDestination != MusicDestination.Songs) {
            songsEditMode = false
            selectedSongIds = emptySet()
            showSongDeleteConfirm = false
            pendingSongDeleteMediaIds = emptySet()
        }
        if (currentDestination != MusicDestination.Album) {
            albumEditMode = false
            selectedAlbumIds = emptySet()
            selectedAlbumId = null
            selectedAlbumTitle = null
        }
        if (currentDestination != MusicDestination.Artist) {
            selectedArtistTarget = null
        }
        if (currentDestination != MusicDestination.Playlist) {
            playlistAddModeActive = false
        }
        dismissTrackActions()
    }

    LegacyPortPredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Album && selectedAlbumId != null,
        state = albumPredictiveBackState,
    ) {
        closeAlbumDetail()
    }

    val selectedArtistParentTarget = selectedArtistTarget?.parentTarget()
    LegacyPortPredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Artist &&
            selectedArtistTarget != null &&
            selectedArtistParentTarget == null,
        state = artistRootPredictiveBackState,
    ) {
        closeArtistDetail()
    }
    LegacyPortPredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Artist &&
            selectedArtistTarget != null &&
            selectedArtistParentTarget != null,
        state = artistNestedPredictiveBackState,
    ) {
        closeArtistDetail()
    }

    LaunchedEffect(externalAudioLaunchRequest, controller) {
        val request = externalAudioLaunchRequest ?: return@LaunchedEffect
        playbackVisible = true
        val playbackController = controller ?: return@LaunchedEffect
        val (artist, mediaStoreIds) = withContext(Dispatchers.IO) {
            request.resolveExternalAudioArtist(context.applicationContext) to
                request.resolveExternalAudioMediaStoreIds(context.applicationContext)
        }
        val mediaItem = request.toExternalAudioMediaItem(
            fallbackTitle = unknownSongTitle,
            artist = artist,
            mediaStoreId = mediaStoreIds.mediaStoreId,
            albumId = mediaStoreIds.albumId,
        )
        playbackController.setMediaItem(mediaItem)
        playbackController.prepare()
        playbackController.play()
        onExternalAudioLaunchConsumed(request.requestId)
    }

    val bottomNavigationHeight = dimensionResource(R.dimen.realtabcontent_margin_bottom) +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val realTabContentBottomMargin = if (playbackBarComposed) {
        bottomNavigationHeight - 6.dp
    } else {
        bottomNavigationHeight
    }
    val playbackBarOverlayHeight = if (playbackBarComposed) playbackBarHeight else 0.dp
    val hideBottomChrome = currentDestination == MusicDestination.More && moreSettingsPageActive

    LaunchedEffect(currentDestination) {
        if (currentDestination != MusicDestination.More) {
            moreSettingsPageActive = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                View(viewContext).apply {
                    setBackgroundResource(R.drawable.account_background)
                }
            },
        )
        val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
        val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
        val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + titleContentHeight
        val mainTitleShadowVisible = currentDestination == MusicDestination.Artist ||
            currentDestination == MusicDestination.Album
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (hideBottomChrome) 0.dp else realTabContentBottomMargin),
        ) {
            val titleBarContent: @Composable (String?, LegacyArtistTarget?, Modifier) -> Unit = { albumDetailTitle, artistTarget, titleModifier ->
                LegacyPortTitleBar(
                    destination = currentDestination,
                    songsEditMode = currentDestination == MusicDestination.Songs && songsEditMode,
                    selectedSongCount = selectedSongIds.size,
                    albumEditMode = currentDestination == MusicDestination.Album && albumEditMode,
                    selectedAlbumCount = selectedAlbumIds.size,
                    albumDetailTitle = albumDetailTitle,
                    albumViewMode = albumViewMode,
                    artistTarget = artistTarget,
                    artistAlbumViewMode = artistAlbumViewMode,
                    onEnterSongsEditMode = {
                        songsEditMode = true
                        selectedSongIds = emptySet()
                    },
                    onExitSongsEditMode = {
                        songsEditMode = false
                        selectedSongIds = emptySet()
                        showSongDeleteConfirm = false
                    },
                    onRequestDeleteSelected = {
                        if (selectedSongIds.isNotEmpty()) {
                            requestSongDeleteConfirmation(selectedSongIds)
                        }
                    },
                    onEnterAlbumEditMode = {
                        albumEditMode = true
                        selectedAlbumIds = emptySet()
                    },
                    onExitAlbumEditMode = {
                        albumEditMode = false
                        selectedAlbumIds = emptySet()
                    },
                    onToggleAlbumViewMode = {
                        val nextMode = if (albumViewMode == AlbumViewMode.List) {
                            AlbumViewMode.Tile
                        } else {
                            AlbumViewMode.List
                        }
                        scope.launch {
                            libraryDisplaySettingsStore.setAlbumViewMode(nextMode)
                        }
                    },
                    onAlbumDetailBack = {
                        closeAlbumDetail()
                    },
                    onArtistBack = {
                        closeArtistDetail()
                    },
                    onToggleArtistAlbumViewMode = {
                        val nextMode = if (artistAlbumViewMode == AlbumViewMode.List) {
                            AlbumViewMode.Tile
                        } else {
                            AlbumViewMode.List
                        }
                        scope.launch {
                            libraryDisplaySettingsStore.setArtistAlbumViewMode(nextMode)
                        }
                    },
                    onSearchClick = ::openCurrentSearch,
                    modifier = titleModifier,
                )
            }
            if (currentDestination == MusicDestination.Playlist || currentDestination == MusicDestination.More) {
                // 播放列表页和更多二级页需要复刻原版自身的标题栏、详情栈和加歌/文件夹过渡。
            } else if (currentDestination == MusicDestination.Album) {
                LegacyPortPageStackTransition(
                    secondaryKey = selectedAlbumTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(titleAreaHeight),
                    label = "legacy album title transition",
                    predictiveBackProgress = albumPredictiveBackState.progress,
                    predictiveBackExitConsumed = albumPredictiveBackState.exitConsumed,
                    onPredictiveBackExitConsumedReset = albumPredictiveBackState::reset,
                    primaryContent = {
                        titleBarContent(null, null, Modifier.fillMaxSize())
                    },
                    secondaryContent = { detailTitle ->
                        titleBarContent(detailTitle, null, Modifier.fillMaxSize())
                    },
                )
            } else if (currentDestination == MusicDestination.Artist) {
                LegacyPortArtistTitleStack(
                    selectedTarget = selectedArtistTarget,
                    rootPredictiveBackProgress = artistRootPredictiveBackState.progress,
                    rootPredictiveBackExitConsumed = artistRootPredictiveBackState.exitConsumed,
                    onRootPredictiveBackExitConsumedReset = artistRootPredictiveBackState::reset,
                    nestedPredictiveBackProgress = artistNestedPredictiveBackState.progress,
                    nestedPredictiveBackExitConsumed = artistNestedPredictiveBackState.exitConsumed,
                    onNestedPredictiveBackExitConsumedReset = artistNestedPredictiveBackState::reset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(titleAreaHeight),
                ) { artistTarget, titleModifier ->
                    titleBarContent(null, artistTarget, titleModifier)
                }
            } else {
                titleBarContent(null, null, Modifier.fillMaxWidth())
            }
            LegacyPortTabContent(
                destination = currentDestination,
                mediaItems = legacyLibraryItems,
                favoriteRecords = favoriteRecords,
                libraryLoaded = legacyLibrary.loaded,
                songsEditMode = currentDestination == MusicDestination.Songs && songsEditMode,
                selectedSongIds = selectedSongIds,
                albumViewMode = albumViewMode,
                albumEditMode = currentDestination == MusicDestination.Album && albumEditMode,
                selectedAlbumId = selectedAlbumId,
                selectedAlbumIds = selectedAlbumIds,
                albumPredictiveBackProgress = albumPredictiveBackState.progress,
                albumPredictiveBackExitConsumed = albumPredictiveBackState.exitConsumed,
                onAlbumPredictiveBackExitConsumedReset = albumPredictiveBackState::reset,
                artistAlbumViewMode = artistAlbumViewMode,
                selectedArtistTarget = selectedArtistTarget,
                artistRootPredictiveBackProgress = artistRootPredictiveBackState.progress,
                artistRootPredictiveBackExitConsumed = artistRootPredictiveBackState.exitConsumed,
                onArtistRootPredictiveBackExitConsumedReset = artistRootPredictiveBackState::reset,
                artistNestedPredictiveBackProgress = artistNestedPredictiveBackState.progress,
                artistNestedPredictiveBackExitConsumed = artistNestedPredictiveBackState.exitConsumed,
                onArtistNestedPredictiveBackExitConsumedReset = artistNestedPredictiveBackState::reset,
                playbackBarOverlayHeight = if (hideBottomChrome) 0.dp else playbackBarOverlayHeight,
                hiddenMediaIds = libraryExclusions.hiddenMediaIds,
                libraryRefreshVersion = libraryRefreshVersion,
                libraryRefreshing = libraryRefreshing,
                playbackSettings = playbackSettings,
                onlineMusicSettings = onlineMusicSettings,
                artistSettings = artistSettings,
                cloudMusicSearchOpenRequest = cloudMusicSearchOpenRequest,
                onCloudMusicSearchOpenRequestHandled = {
                    cloudMusicSearchOpenRequest = 0
                },
                onRefreshLibrary = ::refreshLegacyLibrary,
                onRequestAddToPlaylist = ::requestAddToPlaylist,
                onRequestAddToQueue = ::enqueueMediaItems,
                onScratchEnabledChange = { enabled ->
                    scope.launch {
                        playbackSettingsStore.setScratchEnabled(enabled)
                    }
                },
                onHidePlayerAxisEnabledChange = { enabled ->
                    scope.launch {
                        playbackSettingsStore.setHidePlayerAxisEnabled(enabled)
                    }
                },
                onPopcornSoundEnabledChange = { enabled ->
                    scope.launch {
                        playbackSettingsStore.setPopcornSoundEnabled(enabled)
                    }
                },
                onAudioFxEnabledChange = { enabled ->
                    scope.launch {
                        playbackSettingsStore.setAudioFxEnabled(enabled)
                    }
                },
                onAudioFxPresetChange = { preset ->
                    scope.launch {
                        playbackSettingsStore.setAudioFxPreset(preset)
                    }
                },
                onAudioFxCustomGainDbPointsChange = { gains ->
                    scope.launch {
                        playbackSettingsStore.setAudioFxCustomGainDbPoints(gains)
                    }
                },
                onArtistSeparatorsChange = { separators ->
                    scope.launch {
                        artistSettingsStore.setSeparators(separators)
                    }
                    selectedArtistTarget = null
                    searchDrilldownTarget = null
                },
                onNeteasePlaybackQualityChange = { quality ->
                    scope.launch {
                        onlineMusicSettingsStore.setNeteasePlaybackQuality(quality)
                    }
                },
                onMediaIdsHidden = ::reclaimHiddenMediaIds,
                onRequestDeleteMediaIds = ::requestSystemDeleteMediaIds,
                onRequestSongDeleteConfirmation = { mediaIds, onDismiss ->
                    requestSongDeleteConfirmation(mediaIds, onDismiss)
                },
                onLibraryTrackMoreClick = { item ->
                    showTrackActions(item, LegacyTrackActionSource.Library)
                },
                onCloudMusicTrackMoreClick = { item ->
                    showTrackActions(item, LegacyTrackActionSource.CloudMusic)
                },
                onLovedSongsTrackMoreClick = { item ->
                    showTrackActions(item, LegacyTrackActionSource.Loved)
                },
                onPlaylistTrackMoreClick = { item ->
                    showTrackActions(item, LegacyTrackActionSource.Playlist)
                },
                onRemoveFavoriteMediaIds = ::removeFavoriteMediaIds,
                onMoreSettingsPageActiveChanged = { active ->
                    moreSettingsPageActive = active
                },
                onSongSelectionChange = { mediaId, selected ->
                    selectedSongIds = selectedSongIds.withSelection(mediaId, selected)
                },
                onAlbumSelectionChange = { albumId, selected ->
                    selectedAlbumIds = selectedAlbumIds.withSelection(albumId, selected)
                },
                onAlbumSelected = { albumId, albumTitle ->
                    albumEditMode = false
                    selectedAlbumIds = emptySet()
                    selectedAlbumId = albumId
                    selectedAlbumTitle = albumTitle
                },
                onArtistTargetChanged = { target ->
                    selectedArtistTarget = target
                },
                onPlaylistAddModeActiveChanged = { active ->
                    playlistAddModeActive = active
                },
                onLibraryNeeded = {
                    libraryLoadRequested = true
                },
                onSearchClick = ::openCurrentSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        if (mainTitleShadowVisible) {
            LegacyPortTitleBarShadow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = titleAreaHeight)
                    .fillMaxWidth()
                    .height(titleShadowHeight)
                    .zIndex(1f),
            )
        }
        if (!hideBottomChrome) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                if (playbackBarComposed) {
                    LegacyPortPlaybackBar(
                        snapshot = playbackBarContentSnapshot,
                        shown = playbackBarRequestedVisible,
                        favoriteIds = favoriteIds,
                        artworkBitmap = artworkBitmap,
                        onHidden = {
                            if (!playbackBarRequestedVisible) {
                                playbackBarComposed = false
                            }
                        },
                        onOpenPlayback = {
                            playbackVisible = true
                        },
                        onToggleFavorite = { mediaItem ->
                            if (mediaItem.isExternalAudioLaunchItem()) {
                                return@LegacyPortPlaybackBar
                            }
                            val mediaId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return@LegacyPortPlaybackBar
                            scope.launch {
                                favoriteRepository.toggle(mediaId)
                            }
                        },
                        onPrevious = {
                            controller?.seekToPrevious()
                        },
                        onPlayPause = {
                            if (controller?.isPlaying == true) {
                                controller.pause()
                            } else {
                                controller?.play()
                            }
                        },
                        onNext = {
                            controller?.seekToNext()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(playbackBarHeight),
                        bottomDividerVisible = false,
                    )
                }
                LegacyPortBottomBar(
                    currentDestination = if (playlistAddModeActive) MusicDestination.Songs else currentDestination,
                    onDestinationSelected = { destination ->
                        currentDestination = destination
                    },
                    topChromeVisible = !playbackBarComposed,
                )
            }
        }
        val trackActionItems = pendingTrackActionItem?.let { actionItem ->
            val mediaId = actionItem.mediaId
            val isFavorite = mediaId in favoriteIds
            val canAddToPlaylist = mediaId.isNotBlank() &&
                !actionItem.isExternalAudioLaunchItem() &&
                !actionItem.isOnlineMediaItem()
            val canFavorite = mediaId.isNotBlank() &&
                !actionItem.isExternalAudioLaunchItem()
            val actions = mutableListOf(
                LegacyTrackActionItem(
                    labelRes = R.string.add_to_playlist,
                    iconRes = R.drawable.more_select_icon_addlist,
                    pressedIconRes = R.drawable.more_select_icon_addlist_down,
                    enabled = canAddToPlaylist,
                    onClick = {
                        dismissTrackActions()
                        requestAddToPlaylist(listOf(actionItem))
                    },
                ),
                LegacyTrackActionItem(
                    labelRes = R.string.add_to_queue,
                    iconRes = R.drawable.more_select_icon_addplay,
                    pressedIconRes = R.drawable.more_select_icon_addplay_down,
                    onClick = {
                        dismissTrackActions()
                        enqueueMediaItems(listOf(actionItem))
                    },
                ),
                LegacyTrackActionItem(
                    labelRes = if (isFavorite) R.string.cancel_love else R.string.love,
                    iconRes = if (isFavorite) {
                        R.drawable.more_select_icon_favorite_cancel
                    } else {
                        R.drawable.more_select_icon_favorite_add
                    },
                    pressedIconRes = if (isFavorite) {
                        R.drawable.more_select_icon_favorite_cancel_down
                    } else {
                        R.drawable.more_select_icon_favorite_add_down
                    },
                    enabled = canFavorite,
                    selected = isFavorite,
                    onClick = {
                        dismissTrackActions()
                        if (canFavorite) {
                            scope.launch {
                                favoriteRepository.toggle(mediaId)
                            }
                        }
                    },
                ),
            )
            if (pendingTrackActionSource == LegacyTrackActionSource.Library) {
                actions += LegacyTrackActionItem(
                    labelRes = R.string.delete,
                    iconRes = R.drawable.more_select_icon_delete,
                    onClick = {
                        dismissTrackActions()
                        requestSongDeleteConfirmation(setOf(mediaId))
                    },
                )
            }
            actions
        }.orEmpty()
        LegacyTrackActionsOverlay(
            visible = pendingTrackActionItem != null,
            actions = trackActionItems,
            onDismissRequest = ::dismissTrackActions,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2.4f),
        )
        LegacyPortPlaybackOverlay(
            visible = playbackVisible,
            playbackSettings = playbackSettings,
            ratingOverrides = ratingOverrides,
            onRequestAddToPlaylist = ::requestAddToPlaylist,
            onRequestAddToQueue = ::enqueueMediaItems,
            onScratchEnabledChange = { enabled ->
                scope.launch {
                    playbackSettingsStore.setScratchEnabled(enabled)
                }
            },
            onTrackRatingChanged = { mediaId, score ->
                ratingOverrides = ratingOverrides + (mediaId to score.coerceIn(0, 5))
            },
            onCollapse = {
                playbackVisible = false
            },
            modifier = Modifier.zIndex(3f),
        )
        LegacyPortSearchOverlay(
            visible = searchVisible,
            query = searchQuery,
            mediaItems = legacyLibraryItems,
            hiddenMediaIds = libraryExclusions.hiddenMediaIds,
            drilldownTarget = searchDrilldownTarget,
            libraryRefreshVersion = libraryRefreshVersion,
            artistAlbumViewMode = artistAlbumViewMode,
            artistSettings = artistSettings,
            onQueryChange = { value ->
                searchQuery = value
            },
            onDismiss = closeSearchOverlay,
            onOpenPlayback = {
                playbackVisible = true
            },
            onRequestAddToPlaylist = ::requestAddToPlaylist,
            onRequestAddToQueue = ::enqueueMediaItems,
            onTrackMoreClick = { item ->
                showTrackActions(item, LegacyTrackActionSource.Library)
            },
            onDrilldownTargetChanged = { target ->
                searchDrilldownTarget = target
            },
            onAlbumClick = { albumId, albumTitle ->
                searchDrilldownTarget = LegacySearchDrilldownTarget.Album(
                    albumId = albumId,
                    albumTitle = albumTitle,
                )
            },
            onArtistClick = { artistId, artistName ->
                searchDrilldownTarget = LegacySearchDrilldownTarget.Artist(
                    target = LegacyArtistTarget.Albums(
                        artistId = artistId,
                        artistName = artistName,
                    ),
                )
            },
            onToggleArtistAlbumViewMode = {
                val nextMode = if (artistAlbumViewMode == AlbumViewMode.List) {
                    AlbumViewMode.Tile
                } else {
                    AlbumViewMode.List
                }
                scope.launch {
                    libraryDisplaySettingsStore.setArtistAlbumViewMode(nextMode)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
        )
        LegacyPlaybackPlaylistPickerOverlay(
            visible = pendingPlaylistPickerMediaItems != null && playbackPlaylistCreateRequest == null,
            playlists = playlists,
            onDismiss = {
                pendingPlaylistPickerMediaItems = null
            },
            onCreateNewPlaylist = {
                scope.launch {
                    playbackPlaylistCreateRequest = LegacyPlaylistNameDialogRequest.Create(
                        initialName = playlistRepository.suggestNextUntitledName(),
                    )
                }
            },
            onPlaylistSelected = { playlistId ->
                val mediaIds = pendingPlaylistPickerMediaItems?.map(MediaItem::mediaId).orEmpty()
                scope.launch {
                    val result = playlistRepository.addMediaIds(playlistId, mediaIds)
                    when {
                        result.addedCount > 0 -> {
                            Toast.makeText(context, R.string.playlist_added, Toast.LENGTH_SHORT).show()
                        }
                        result.duplicateCount > 0 -> {
                            Toast.makeText(context, R.string.playlist_song_exists, Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingPlaylistPickerMediaItems = null
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f),
        )
        LegacyPlaylistNameDialogOverlay(
            request = playbackPlaylistCreateRequest,
            onDismiss = {
                playbackPlaylistCreateRequest = null
            },
            onConfirm = { _, input ->
                val mediaIds = pendingPlaylistPickerMediaItems?.map(MediaItem::mediaId).orEmpty()
                scope.launch {
                    when (val result = playlistRepository.createPlaylist(input, mediaIds)) {
                        PlaylistCreateResult.EmptyName -> {
                            Toast.makeText(context, R.string.playlist_create_failed, Toast.LENGTH_SHORT).show()
                        }
                        PlaylistCreateResult.DuplicateName -> {
                            Toast.makeText(context, R.string.playlist_duplicate_name, Toast.LENGTH_SHORT).show()
                        }
                        is PlaylistCreateResult.Success -> {
                            playbackPlaylistCreateRequest = null
                            pendingPlaylistPickerMediaItems = null
                            Toast.makeText(context, R.string.playlist_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )
        if (showSongDeleteConfirm) {
            LegacySongDeleteConfirmOverlay(
                onDismiss = {
                    dismissSongDeleteConfirmation()
                },
                onConfirm = {
                    val mediaIds = pendingSongDeleteMediaIds
                    val dismissAction = pendingSongDeleteDismissAction
                    if (mediaIds.isEmpty()) {
                        dismissSongDeleteConfirmation()
                        return@LegacySongDeleteConfirmOverlay
                    }
                    showSongDeleteConfirm = false
                    pendingSongDeleteMediaIds = emptySet()
                    pendingSongDeleteDismissAction = null
                    songsEditMode = false
                    selectedSongIds = emptySet()
                    requestSystemDeleteMediaIds(mediaIds)
                    dismissAction?.invoke()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
            )
        }
    }
}

private fun String.toLegacyMediaStoreDeleteUri(): Uri? {
    val mediaStoreId = trim().toLongOrNull() ?: return null
    return ContentUris.withAppendedId(
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        mediaStoreId,
    )
}

private fun MusicDestination.requiresFullLibraryItems(): Boolean {
    return when (this) {
        MusicDestination.Songs,
        MusicDestination.Album,
        MusicDestination.Artist,
        -> true
        MusicDestination.Playlist,
        MusicDestination.CloudMusic,
        MusicDestination.More,
        -> false
    }
}

private fun List<MediaItem>.withRatingOverrides(ratingOverrides: Map<String, Int>): List<MediaItem> {
    if (isEmpty() || ratingOverrides.isEmpty()) {
        return this
    }
    return map { item ->
        val score = ratingOverrides[item.mediaId] ?: return@map item
        item.withPlaybackRating(score)
    }
}
