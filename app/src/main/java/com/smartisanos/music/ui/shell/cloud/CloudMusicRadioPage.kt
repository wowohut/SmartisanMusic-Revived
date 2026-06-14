package com.smartisanos.music.ui.shell.cloud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineMusicProviderRepository
import com.smartisanos.music.data.online.OnlineRadio
import com.smartisanos.music.data.online.OnlineRadioHome
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBlankState

internal sealed interface CloudRadioHomeState {
    object Loading : CloudRadioHomeState
    object Empty : CloudRadioHomeState
    object Error : CloudRadioHomeState
    data class Success(val home: OnlineRadioHome) : CloudRadioHomeState
}

@Composable
internal fun CloudRadioHomeContent(
    state: CloudRadioHomeState,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onTracksClick: () -> Unit,
    onRadioListClick: () -> Unit,
    onRadioClick: (OnlineRadio) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudRadioHomeState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudRadioHomeState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudRadioHomeState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudRadioHomeState.Success -> {
            Column(
                modifier = modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = playbackBarOverlayHeight + 10.dp),
            ) {
                CloudHomeTrackPreviewSection(
                    title = stringResource(R.string.cloud_music_section_radio_programs),
                    tracks = state.home.tracks,
                    onClick = onTracksClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                CloudHomeRadioSection(
                    title = stringResource(R.string.cloud_music_section_hot_radios),
                    radios = state.home.radios,
                    actionText = stringResource(R.string.cloud_music_section_view_all),
                    onActionClick = onRadioListClick,
                    onRadioClick = onRadioClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun CloudRadioListContent(
    state: CloudRadioHomeState,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onRadioClick: (OnlineRadio) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudRadioHomeState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudRadioHomeState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudRadioHomeState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudRadioHomeState.Success -> CloudSearchCoverResultList(
            items = state.home.radios,
            playbackBarOverlayHeight = playbackBarOverlayHeight,
            title = OnlineRadio::title,
            subtitle = { radio -> radio.subtitleText(LocalContext.current) },
            imageUrl = OnlineRadio::artworkUrl,
            onItemClick = onRadioClick,
            modifier = modifier,
        )
    }
}

@Composable
internal fun CloudRadioTrackStateContent(
    state: CloudRadioHomeState,
    repository: OnlineMusicProviderRepository,
    authLoggedIn: Boolean,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudRadioHomeState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudRadioHomeState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudRadioHomeState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudRadioHomeState.Success -> CloudMusicStateContent(
            state = if (state.home.tracks.isEmpty()) {
                CloudMusicState.RadioEmpty
            } else {
                CloudMusicState.Success(state.home.tracks)
            },
            repository = repository,
            authLoggedIn = authLoggedIn,
            active = active,
            playbackBarOverlayHeight = playbackBarOverlayHeight,
            onRetryClick = onRetryClick,
            onTrackMoreClick = onTrackMoreClick,
            modifier = modifier,
        )
    }
}
