package com.smartisanos.music.ui.shell.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.smartisanos.music.data.favorite.FavoriteSongRecord
import com.smartisanos.music.data.settings.ArtistSettings
import com.smartisanos.music.data.settings.PlaybackSettings
import com.smartisanos.music.ui.album.AlbumViewMode
import com.smartisanos.music.ui.navigation.MusicDestination
import com.smartisanos.music.ui.shell.LegacyArtistTarget
import com.smartisanos.music.ui.shell.LegacyPortAlbumPage
import com.smartisanos.music.ui.shell.LegacyPortArtistPage
import com.smartisanos.music.ui.shell.LegacyPortMorePage
import com.smartisanos.music.ui.shell.LegacyPortPlaylistPage
import com.smartisanos.music.ui.shell.songs.LegacyPortSongsPage

@Composable
internal fun LegacyPortTabContent(
    destination: MusicDestination,
    mediaItems: List<MediaItem>,
    favoriteRecords: List<FavoriteSongRecord>,
    libraryLoaded: Boolean,
    songsEditMode: Boolean,
    selectedSongIds: Set<String>,
    albumViewMode: AlbumViewMode,
    albumEditMode: Boolean,
    selectedAlbumId: String?,
    selectedAlbumIds: Set<String>,
    albumPredictiveBackProgress: Float?,
    albumPredictiveBackExitConsumed: Boolean,
    onAlbumPredictiveBackExitConsumedReset: () -> Unit,
    artistAlbumViewMode: AlbumViewMode,
    selectedArtistTarget: LegacyArtistTarget?,
    artistRootPredictiveBackProgress: Float?,
    artistRootPredictiveBackExitConsumed: Boolean,
    onArtistRootPredictiveBackExitConsumedReset: () -> Unit,
    artistNestedPredictiveBackProgress: Float?,
    artistNestedPredictiveBackExitConsumed: Boolean,
    onArtistNestedPredictiveBackExitConsumedReset: () -> Unit,
    playbackBarOverlayHeight: Dp = 0.dp,
    hiddenMediaIds: Set<String>,
    libraryRefreshVersion: Int,
    libraryRefreshing: Boolean,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    onRefreshLibrary: () -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onMediaIdsHidden: (Set<String>) -> Unit,
    onRequestDeleteMediaIds: (Set<String>) -> Unit,
    onRequestSongDeleteConfirmation: (Set<String>, (() -> Unit)?) -> Unit,
    onLibraryTrackMoreClick: (MediaItem) -> Unit,
    onLovedSongsTrackMoreClick: (MediaItem) -> Unit,
    onPlaylistTrackMoreClick: (MediaItem) -> Unit,
    onRemoveFavoriteMediaIds: (Set<String>) -> Unit,
    onMoreSettingsPageActiveChanged: (Boolean) -> Unit,
    onSongSelectionChange: (String, Boolean) -> Unit,
    onAlbumSelectionChange: (String, Boolean) -> Unit,
    onAlbumSelected: (String, String) -> Unit,
    onArtistTargetChanged: (LegacyArtistTarget?) -> Unit,
    onPlaylistAddModeActiveChanged: (Boolean) -> Unit,
    onLibraryNeeded: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var songsPageMounted by remember { mutableStateOf(destination == MusicDestination.Songs) }
    LaunchedEffect(destination) {
        if (destination == MusicDestination.Songs) {
            songsPageMounted = true
        }
    }

    Box(modifier = modifier) {
        if (songsPageMounted) {
            LegacyPortSongsPage(
                mediaItems = mediaItems,
                libraryLoaded = libraryLoaded,
                active = destination == MusicDestination.Songs,
                editMode = songsEditMode,
                selectedSongIds = selectedSongIds,
                hiddenMediaIds = hiddenMediaIds,
                onSongSelectionChange = onSongSelectionChange,
                onTrackMoreClick = onLibraryTrackMoreClick,
                onRequestSongDeleteConfirmation = onRequestSongDeleteConfirmation,
                playbackBarOverlayHeight = playbackBarOverlayHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (destination) {
            MusicDestination.Songs -> Unit
            MusicDestination.Album -> LegacyPortAlbumPage(
                mediaItems = mediaItems,
                active = true,
                viewMode = albumViewMode,
                editMode = albumEditMode,
                selectedAlbumId = selectedAlbumId,
                selectedAlbumIds = selectedAlbumIds,
                predictiveBackProgress = albumPredictiveBackProgress,
                predictiveBackExitConsumed = albumPredictiveBackExitConsumed,
                onPredictiveBackExitConsumedReset = onAlbumPredictiveBackExitConsumedReset,
                hiddenMediaIds = hiddenMediaIds,
                onAlbumSelected = onAlbumSelected,
                onAlbumSelectionChange = onAlbumSelectionChange,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onTrackMoreClick = onLibraryTrackMoreClick,
                artistSettings = artistSettings,
                modifier = Modifier.fillMaxSize(),
            )
            MusicDestination.Artist -> LegacyPortArtistPage(
                mediaItems = mediaItems,
                active = true,
                selectedTarget = selectedArtistTarget,
                albumViewMode = artistAlbumViewMode,
                rootPredictiveBackProgress = artistRootPredictiveBackProgress,
                rootPredictiveBackExitConsumed = artistRootPredictiveBackExitConsumed,
                onRootPredictiveBackExitConsumedReset = onArtistRootPredictiveBackExitConsumedReset,
                nestedPredictiveBackProgress = artistNestedPredictiveBackProgress,
                nestedPredictiveBackExitConsumed = artistNestedPredictiveBackExitConsumed,
                onNestedPredictiveBackExitConsumedReset = onArtistNestedPredictiveBackExitConsumedReset,
                hiddenMediaIds = hiddenMediaIds,
                onTargetChanged = onArtistTargetChanged,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onTrackMoreClick = onLibraryTrackMoreClick,
                artistSettings = artistSettings,
                modifier = Modifier.fillMaxSize(),
            )
            MusicDestination.Playlist -> LegacyPortPlaylistPage(
                mediaItems = mediaItems,
                libraryLoaded = libraryLoaded,
                active = true,
                hiddenMediaIds = hiddenMediaIds,
                onTrackMoreClick = onPlaylistTrackMoreClick,
                onAddModeActiveChanged = onPlaylistAddModeActiveChanged,
                onLibraryNeeded = onLibraryNeeded,
                onSearchClick = onSearchClick,
                modifier = Modifier.fillMaxSize(),
            )
            MusicDestination.More -> LegacyPortMorePage(
                active = true,
                mediaItems = mediaItems,
                favoriteRecords = favoriteRecords,
                hiddenMediaIds = hiddenMediaIds,
                playbackSettings = playbackSettings,
                artistSettings = artistSettings,
                libraryLoaded = libraryLoaded,
                libraryRefreshVersion = libraryRefreshVersion,
                libraryRefreshing = libraryRefreshing,
                onRefreshLibrary = onRefreshLibrary,
                onScratchEnabledChange = onScratchEnabledChange,
                onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                onArtistSeparatorsChange = onArtistSeparatorsChange,
                onMediaIdsHidden = onMediaIdsHidden,
                onRequestDeleteMediaIds = onRequestDeleteMediaIds,
                onLovedSongsTrackMoreClick = onLovedSongsTrackMoreClick,
                onFolderTrackMoreClick = onLibraryTrackMoreClick,
                onRemoveFavoriteMediaIds = onRemoveFavoriteMediaIds,
                onSettingsPageActiveChanged = onMoreSettingsPageActiveChanged,
                onLibraryNeeded = onLibraryNeeded,
                onSearchClick = onSearchClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
