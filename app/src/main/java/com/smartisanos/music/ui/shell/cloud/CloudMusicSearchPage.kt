package com.smartisanos.music.ui.shell.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineAlbum
import com.smartisanos.music.data.online.OnlineArtist
import com.smartisanos.music.data.online.OnlineMusicProviderRepository
import com.smartisanos.music.data.online.OnlinePlaylist
import com.smartisanos.music.data.online.OnlineSearchHotKeyword
import com.smartisanos.music.data.online.OnlineSearchResults
import com.smartisanos.music.data.online.OnlineTrack
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeCoverCard
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeSectionHeader
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBlankState
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicCoverImage
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicDivider
import com.smartisanos.music.ui.shell.cloud.components.CloudHomeTrackPreviewRow

internal sealed interface CloudSearchResultsState {
    object Idle : CloudSearchResultsState
    object Loading : CloudSearchResultsState
    data class Empty(val query: String) : CloudSearchResultsState
    data class Error(val query: String) : CloudSearchResultsState
    data class Success(val results: OnlineSearchResults) : CloudSearchResultsState
}

internal sealed interface CloudHotSearchState {
    object Idle : CloudHotSearchState
    object Loading : CloudHotSearchState
    object Empty : CloudHotSearchState
    object Error : CloudHotSearchState
    data class Success(val keywords: List<OnlineSearchHotKeyword>) : CloudHotSearchState
}

internal enum class CloudSearchCategory(
    val labelRes: Int,
) {
    All(R.string.cloud_music_search_tab_all),
    Tracks(R.string.search_tab_songs),
    Artists(R.string.search_tab_artists),
    Albums(R.string.search_tab_albums),
    Playlists(R.string.cloud_music_entry_collection),
}

@Composable
internal fun CloudMusicSearchResultsContent(
    state: CloudSearchResultsState,
    selectedCategory: CloudSearchCategory,
    onCategoryChange: (CloudSearchCategory) -> Unit,
    repository: OnlineMusicProviderRepository,
    authLoggedIn: Boolean,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudSearchResultsState.Idle -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_empty_title),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudSearchResultsState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_loading),
            subtitle = null,
            modifier = modifier,
        )
        is CloudSearchResultsState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_no_result),
            subtitle = state.query,
            modifier = modifier,
        )
        is CloudSearchResultsState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_error),
            subtitle = state.query,
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudSearchResultsState.Success -> {
            Column(modifier = modifier) {
                CloudSearchCategoryBar(
                    selectedCategory = selectedCategory,
                    onCategoryChange = onCategoryChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (selectedCategory) {
                    CloudSearchCategory.All -> CloudSearchAllResults(
                        results = state.results,
                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                        onTracksClick = { onCategoryChange(CloudSearchCategory.Tracks) },
                        onArtistsClick = { onCategoryChange(CloudSearchCategory.Artists) },
                        onAlbumsClick = { onCategoryChange(CloudSearchCategory.Albums) },
                        onPlaylistsClick = { onCategoryChange(CloudSearchCategory.Playlists) },
                        onPlaylistClick = onPlaylistClick,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    CloudSearchCategory.Tracks -> {
                        if (state.results.tracks.isEmpty()) {
                            CloudMusicBlankState(
                                title = stringResource(R.string.cloud_music_no_result),
                                subtitle = state.results.query,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        } else {
                            CloudMusicResultList(
                                tracks = state.results.tracks,
                                repository = repository,
                                playFailedMessageRes = when {
                                    !authLoggedIn -> R.string.cloud_music_login_in_settings
                                    else -> R.string.netease_online_music_play_failed
                                },
                                active = active,
                                playbackBarOverlayHeight = playbackBarOverlayHeight,
                                onTrackMoreClick = onTrackMoreClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }
                    CloudSearchCategory.Artists -> {
                        if (state.results.artists.isEmpty()) {
                            CloudMusicBlankState(
                                title = stringResource(R.string.cloud_music_no_result),
                                subtitle = state.results.query,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        } else {
                            CloudMusicArtistList(
                                artists = state.results.artists,
                                selectedArtistId = null,
                                active = active,
                                playbackBarOverlayHeight = playbackBarOverlayHeight,
                                onArtistClick = onArtistClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }
                    CloudSearchCategory.Albums -> CloudSearchCoverResultList(
                        items = state.results.albums,
                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                        title = OnlineAlbum::title,
                        subtitle = { album -> album.albumSubtitle(LocalContext.current) },
                        imageUrl = OnlineAlbum::artworkUrl,
                        onItemClick = onAlbumClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    CloudSearchCategory.Playlists -> CloudSearchCoverResultList(
                        items = state.results.playlists,
                        playbackBarOverlayHeight = playbackBarOverlayHeight,
                        title = OnlinePlaylist::title,
                        subtitle = { playlist -> playlist.homeSubtitle(LocalContext.current) },
                        imageUrl = OnlinePlaylist::artworkUrl,
                        onItemClick = onPlaylistClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CloudHotSearchContent(
    state: CloudHotSearchState,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onKeywordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudHotSearchState.Idle,
        CloudHotSearchState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_hot_search_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudHotSearchState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_hot_search_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudHotSearchState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_hot_search_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudHotSearchState.Success -> {
            Column(
                modifier = modifier
                    .verticalScroll(rememberScrollState())
                    .background(ComposeColor.White)
                    .padding(bottom = playbackBarOverlayHeight + 10.dp),
            ) {
                CloudHomeSectionHeader(
                    title = stringResource(R.string.cloud_music_section_hot_search),
                    actionText = null,
                    onClick = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.keywords.forEachIndexed { index, keyword ->
                    CloudHotSearchRow(
                        index = index + 1,
                        keyword = keyword,
                        onClick = { onKeywordClick(keyword.keyword) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CloudMusicDivider()
                }
            }
        }
    }
}

@Composable
internal fun CloudHotSearchRow(
    index: Int,
    keyword: OnlineSearchHotKeyword,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val heatText = keyword.score.toHotSearchHeatText(context)
    Row(
        modifier = modifier
            .height(CloudHotSearchRowHeight)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = TextStyle(
                fontSize = 14.sp,
                color = if (index <= 3) CloudAccentColor else ComposeColor(0x66000000),
            ),
            modifier = Modifier.width(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = keyword.keyword,
                style = TextStyle(
                    fontSize = 15.sp,
                    color = ComposeColor(0xCC000000),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            keyword.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = CloudSecondaryTextColor,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        if (heatText != null) {
            Text(
                text = heatText,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ComposeColor(0x4D000000),
                ),
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
internal fun CloudSearchCategoryBar(
    selectedCategory: CloudSearchCategory,
    onCategoryChange: (CloudSearchCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(CloudSearchCategoryBarHeight)
            .background(ComposeColor.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloudSearchCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onCategoryChange(category) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(category.labelRes),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = if (selected) CloudAccentColor else ComposeColor(0x99000000),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(22.dp)
                            .height(2.dp)
                            .background(CloudAccentColor),
                    )
                }
            }
        }
    }
    CloudMusicDivider()
}

@Composable
internal fun CloudSearchAllResults(
    results: OnlineSearchResults,
    playbackBarOverlayHeight: Dp,
    onTracksClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = playbackBarOverlayHeight + 10.dp),
    ) {
        CloudHomeTrackPreviewSection(
            title = stringResource(R.string.search_tab_songs),
            tracks = results.tracks,
            onClick = onTracksClick,
            modifier = Modifier.fillMaxWidth(),
        )
        CloudHomeArtistSection(
            title = stringResource(R.string.search_tab_artists),
            artists = results.artists,
            actionText = stringResource(R.string.cloud_music_section_view_all),
            onActionClick = onArtistsClick,
            onArtistClick = onArtistClick,
            modifier = Modifier.fillMaxWidth(),
        )
        CloudHomeAlbumSection(
            title = stringResource(R.string.search_tab_albums),
            albums = results.albums,
            actionText = stringResource(R.string.cloud_music_section_view_all),
            onActionClick = onAlbumsClick,
            onAlbumClick = onAlbumClick,
            modifier = Modifier.fillMaxWidth(),
        )
        CloudHomePlaylistSection(
            title = stringResource(R.string.cloud_music_entry_collection),
            playlists = results.playlists,
            actionText = stringResource(R.string.cloud_music_section_view_all),
            onActionClick = onPlaylistsClick,
            onPlaylistClick = onPlaylistClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun <T> CloudSearchCoverResultList(
    items: List<T>,
    playbackBarOverlayHeight: Dp,
    title: (T) -> String,
    subtitle: @Composable (T) -> String?,
    imageUrl: (T) -> String?,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_no_result),
            subtitle = null,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(ComposeColor.White)
            .padding(bottom = playbackBarOverlayHeight + 10.dp),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloudSearchCoverRowHeight)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onItemClick(item) },
                    )
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CloudMusicCoverImage(
                    imageUrl = imageUrl(item),
                    modifier = Modifier
                        .size(CloudSearchCoverArtworkSize)
                        .clip(RoundedCornerShape(4.dp))
                        .border(
                            width = 0.67.dp,
                            color = ComposeColor(0x1F000000),
                            shape = RoundedCornerShape(4.dp),
                        ),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title(item),
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = ComposeColor(0xCC000000),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle(item)?.takeIf(String::isNotBlank)?.let { subtitleText ->
                        Text(
                            text = subtitleText,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = CloudSecondaryTextColor,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
            CloudMusicDivider()
        }
    }
}
