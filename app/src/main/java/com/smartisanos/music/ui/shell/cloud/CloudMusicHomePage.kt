package com.smartisanos.music.ui.shell.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineAlbum
import com.smartisanos.music.data.online.OnlineArtist
import com.smartisanos.music.data.online.OnlineArtistIntroduction
import com.smartisanos.music.data.online.OnlineMusicHome
import com.smartisanos.music.data.online.OnlinePlaylist
import com.smartisanos.music.data.online.OnlineRadio
import com.smartisanos.music.data.online.OnlineTrack
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeCoverCard
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeCoverSection
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeSectionHeader
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBlankState
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicDivider
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeTrackPreviewRow

internal sealed interface CloudFeaturedHomeState {
    object Loading : CloudFeaturedHomeState
    object Empty : CloudFeaturedHomeState
    object Error : CloudFeaturedHomeState
    data class Success(val home: OnlineMusicHome) : CloudFeaturedHomeState
}

@Composable
internal fun CloudFeaturedHomeContent(
    state: CloudFeaturedHomeState,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onTracksClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onChartsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudFeaturedHomeState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_featured_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudFeaturedHomeState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_empty_title),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudFeaturedHomeState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_featured_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudFeaturedHomeState.Success -> {
            val bottomPadding = playbackBarOverlayHeight + 10.dp
            Column(
                modifier = modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomPadding),
            ) {
                CloudHomeTrackPreviewSection(
                    title = stringResource(R.string.cloud_music_section_daily_recommend),
                    tracks = state.home.tracks,
                    onClick = onTracksClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                CloudHomePlaylistSection(
                    title = stringResource(R.string.cloud_music_section_featured_playlists),
                    playlists = state.home.playlists,
                    actionText = stringResource(R.string.cloud_music_section_view_all),
                    onActionClick = onPlaylistsClick,
                    onPlaylistClick = onPlaylistClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                CloudHomePlaylistSection(
                    title = stringResource(R.string.cloud_music_section_hot_charts),
                    playlists = state.home.charts,
                    actionText = stringResource(R.string.cloud_music_section_view_all),
                    onActionClick = onChartsClick,
                    onPlaylistClick = onPlaylistClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                CloudHomeAlbumSection(
                    title = stringResource(R.string.cloud_music_section_new_albums),
                    albums = state.home.albums,
                    actionText = stringResource(R.string.cloud_music_section_view_all),
                    onActionClick = onAlbumsClick,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                CloudHomeArtistSection(
                    title = stringResource(R.string.cloud_music_section_hot_artists),
                    artists = state.home.artists,
                    actionText = stringResource(R.string.cloud_music_section_view_all),
                    onActionClick = onArtistsClick,
                    onArtistClick = onArtistClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun <T> CloudFeaturedHomeCoverListContent(
    state: CloudFeaturedHomeState,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    items: (OnlineMusicHome) -> List<T>,
    title: (T) -> String,
    subtitle: @Composable (T) -> String?,
    imageUrl: (T) -> String?,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudFeaturedHomeState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_featured_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudFeaturedHomeState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_empty_title),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudFeaturedHomeState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_featured_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudFeaturedHomeState.Success -> CloudSearchCoverResultList(
            items = items(state.home),
            playbackBarOverlayHeight = playbackBarOverlayHeight,
            title = title,
            subtitle = subtitle,
            imageUrl = imageUrl,
            onItemClick = onItemClick,
            modifier = modifier,
        )
    }
}

@Composable
internal fun CloudFeaturedHomeArtistListContent(
    state: CloudFeaturedHomeState,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudFeaturedHomeState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_artists_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudFeaturedHomeState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_artists_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudFeaturedHomeState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_artists_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudFeaturedHomeState.Success -> {
            if (state.home.artists.isEmpty()) {
                CloudMusicBlankState(
                    title = stringResource(R.string.cloud_music_artists_empty),
                    subtitle = stringResource(R.string.cloud_music_empty_subtitle),
                    modifier = modifier,
                )
            } else {
                CloudMusicArtistList(
                    artists = state.home.artists,
                    selectedArtistId = null,
                    active = active,
                    playbackBarOverlayHeight = playbackBarOverlayHeight,
                    onArtistClick = onArtistClick,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
internal fun CloudHomeTrackPreviewSection(
    title: String,
    tracks: List<OnlineTrack>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        return
    }
    Column(
        modifier = modifier.background(ComposeColor.White),
    ) {
        CloudHomeSectionHeader(
            title = title,
            actionText = stringResource(R.string.cloud_music_section_view_all),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        tracks.take(CloudHomeTrackPreviewCount).forEachIndexed { index, track ->
            CloudHomeTrackPreviewRow(
                index = index + 1,
                track = track,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
            CloudMusicDivider()
        }
    }
}

@Composable
internal fun CloudArtistIntroductionSection(
    sections: List<OnlineArtistIntroduction>,
    modifier: Modifier = Modifier,
) {
    val intro = remember(sections) {
        sections.firstOrNull { section -> section.title.contains("简介") }
            ?: sections.firstOrNull()
    } ?: return
    Column(
        modifier = modifier.background(ComposeColor.White),
    ) {
        CloudHomeSectionHeader(
            title = stringResource(R.string.cloud_music_section_artist_intro),
            actionText = null,
            onClick = null,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 9.dp, end = 12.dp, bottom = 12.dp),
        ) {
            if (intro.title.isNotBlank() && !intro.title.contains("简介")) {
                Text(
                    text = intro.title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ComposeColor(0xCC000000),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Text(
                text = intro.text,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = CloudSecondaryTextColor,
                    lineHeight = 18.sp,
                ),
                maxLines = CloudArtistIntroMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CloudMusicDivider()
    }
}

@Composable
internal fun CloudHomePlaylistSection(
    title: String,
    playlists: List<OnlinePlaylist>,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) {
        return
    }
    val context = LocalContext.current
    val visiblePlaylists = remember(playlists) {
        playlists.take(CloudHomeCoverPreviewCount)
    }
    CloudHomeCoverSection(
        title = title,
        actionText = actionText,
        onActionClick = onActionClick,
        modifier = modifier,
    ) {
        visiblePlaylists.forEachIndexed { index, playlist ->
            CloudHomeCoverCard(
                title = playlist.title,
                subtitle = playlist.homeSubtitle(context),
                imageUrl = playlist.artworkUrl,
                onClick = { onPlaylistClick(playlist) },
            )
            if (index != visiblePlaylists.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
internal fun CloudHomeAlbumSection(
    title: String,
    albums: List<OnlineAlbum>,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onAlbumClick: (OnlineAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        return
    }
    val visibleAlbums = remember(albums) {
        albums.take(CloudHomeCoverPreviewCount)
    }
    CloudHomeCoverSection(
        title = title,
        actionText = actionText,
        onActionClick = onActionClick,
        modifier = modifier,
    ) {
        visibleAlbums.forEachIndexed { index, album ->
            CloudHomeCoverCard(
                title = album.title,
                subtitle = album.artist ?: stringResource(R.string.cloud_music_album_provider_netease),
                imageUrl = album.artworkUrl,
                onClick = { onAlbumClick(album) },
            )
            if (index != visibleAlbums.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
internal fun CloudHomeArtistSection(
    title: String,
    artists: List<OnlineArtist>,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        return
    }
    val visibleArtists = remember(artists) {
        artists.take(CloudHomeCoverPreviewCount)
    }
    CloudHomeCoverSection(
        title = title,
        actionText = actionText,
        onActionClick = onActionClick,
        modifier = modifier,
    ) {
        visibleArtists.forEachIndexed { index, artist ->
            CloudHomeCoverCard(
                title = artist.name,
                subtitle = artist.subtitleText(LocalContext.current),
                imageUrl = artist.artworkUrl,
                onClick = { onArtistClick(artist) },
            )
            if (index != visibleArtists.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
internal fun CloudHomeRadioSection(
    title: String,
    radios: List<OnlineRadio>,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onRadioClick: (OnlineRadio) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (radios.isEmpty()) {
        return
    }
    val visibleRadios = remember(radios) {
        radios.take(CloudHomeCoverPreviewCount)
    }
    CloudHomeCoverSection(
        title = title,
        actionText = actionText,
        onActionClick = onActionClick,
        modifier = modifier,
    ) {
        visibleRadios.forEachIndexed { index, radio ->
            CloudHomeCoverCard(
                title = radio.title,
                subtitle = radio.cardSubtitle(LocalContext.current),
                imageUrl = radio.artworkUrl,
                onClick = { onRadioClick(radio) },
            )
            if (index != visibleRadios.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}
