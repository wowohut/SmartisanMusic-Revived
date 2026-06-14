package com.smartisanos.music.ui.shell.cloud

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.graphics.drawable.ColorDrawable
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.online.NeteaseAccountActionResult
import com.smartisanos.music.data.online.NeteaseAccountActionStatus
import com.smartisanos.music.data.online.OnlineAccountPlaylist
import com.smartisanos.music.data.online.OnlineMusicProviderRepository
import com.smartisanos.music.data.online.OnlineTrack
import com.smartisanos.music.data.online.onlineIdentityOrNull
import com.smartisanos.music.data.online.toMediaItem
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.ui.shell.LegacyPlaylistDeleteDialog
import com.smartisanos.music.ui.shell.LegacyPlaylistDeleteRequest
import com.smartisanos.music.ui.shell.LegacySlideSelectionStartArea
import com.smartisanos.music.ui.shell.addLegacyPortListFooter
import com.smartisanos.music.ui.shell.bindLegacyPortListFooter
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBlankState
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicCoverImage
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicDivider
import com.smartisanos.music.ui.shell.legacyPlaylistDetailActionButton
import com.smartisanos.music.ui.shell.legacySlideSelectionController
import com.smartisanos.music.ui.shell.legacyWrappedAdapter
import com.smartisanos.music.ui.shell.songs.LegacySongsAdapter
import com.smartisanos.music.ui.shell.songs.LegacySongsSectionMode
import com.smartisanos.music.ui.shell.songs.LegacySongsSortDisplayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal sealed interface CloudMusicState {
    object Idle : CloudMusicState
    object LoadingAccount : CloudMusicState
    object LoadingFeatured : CloudMusicState
    object LoadingRadio : CloudMusicState
    object AccountEmpty : CloudMusicState
    object AccountError : CloudMusicState
    object FeaturedEmpty : CloudMusicState
    object FeaturedError : CloudMusicState
    object RadioEmpty : CloudMusicState
    object RadioError : CloudMusicState
    object RadioLoginRequired : CloudMusicState
    data class Success(val tracks: List<OnlineTrack>) : CloudMusicState
}

@Composable
internal fun CloudMusicTrackDetailContent(
    state: CloudMusicState,
    title: String?,
    subtitle: String?,
    artworkUrl: String?,
    repository: OnlineMusicProviderRepository,
    authLoggedIn: Boolean,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!title.isNullOrBlank() || !subtitle.isNullOrBlank() || !artworkUrl.isNullOrBlank()) {
            CloudMusicDetailHeader(
                title = title.orEmpty(),
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        extraContent?.invoke()
        CloudMusicStateContent(
            state = state,
            repository = repository,
            authLoggedIn = authLoggedIn,
            active = active,
            playbackBarOverlayHeight = playbackBarOverlayHeight,
            onRetryClick = onRetryClick,
            onTrackMoreClick = onTrackMoreClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
internal fun CloudMusicDetailHeader(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(ComposeColor.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CloudDetailHeaderHeight)
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloudMusicCoverImage(
                imageUrl = artworkUrl,
                modifier = Modifier
                    .size(CloudDetailHeaderArtworkSize)
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
                    text = title,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = ComposeColor(0xCC000000),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf(String::isNotBlank)?.let { subtitleText ->
                    Text(
                        text = subtitleText,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = CloudSecondaryTextColor,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        CloudMusicDivider()
    }
}

@Composable
internal fun CloudMusicStateContent(
    state: CloudMusicState,
    repository: OnlineMusicProviderRepository,
    authLoggedIn: Boolean,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    accountPlaylist: OnlineAccountPlaylist? = null,
    onAccountPlaylistTracksChanged: () -> Unit = {},
    onAccountPlaylistDeleted: (OnlineAccountPlaylist) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudMusicState.Idle -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_empty_title),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudMusicState.LoadingAccount -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_account_tracks_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudMusicState.LoadingFeatured -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_featured_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudMusicState.LoadingRadio -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudMusicState.AccountEmpty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_liked_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudMusicState.AccountError -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_liked_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        CloudMusicState.FeaturedEmpty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_empty_title),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudMusicState.FeaturedError -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_featured_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        CloudMusicState.RadioEmpty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudMusicState.RadioError -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        CloudMusicState.RadioLoginRequired -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_radio_login_required),
            subtitle = stringResource(R.string.cloud_music_radio_login_required_subtitle),
            modifier = modifier,
        )
        is CloudMusicState.Success -> CloudMusicResultList(
            tracks = state.tracks,
            repository = repository,
            playFailedMessageRes = when {
                !authLoggedIn -> R.string.cloud_music_login_in_settings
                else -> R.string.netease_online_music_play_failed
            },
            active = active,
            playbackBarOverlayHeight = playbackBarOverlayHeight,
            onTrackMoreClick = onTrackMoreClick,
            editableAccountPlaylist = accountPlaylist?.takeIf(OnlineAccountPlaylist::isEditable),
            onAccountPlaylistTracksChanged = onAccountPlaylistTracksChanged,
            onAccountPlaylistDeleted = onAccountPlaylistDeleted,
            modifier = modifier,
        )
    }
}

@Composable
internal fun CloudMusicResultList(
    tracks: List<OnlineTrack>,
    repository: OnlineMusicProviderRepository,
    playFailedMessageRes: Int,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onTrackMoreClick: (MediaItem) -> Unit,
    editableAccountPlaylist: OnlineAccountPlaylist? = null,
    onAccountPlaylistTracksChanged: () -> Unit = {},
    onAccountPlaylistDeleted: (OnlineAccountPlaylist) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val scope = rememberCoroutineScope()
    var queuePopulateJob by remember { mutableStateOf<Job?>(null) }
    var queuePopulateToken by remember { mutableStateOf(0L) }
    var editMode by remember { mutableStateOf(false) }
    var selectedMediaIds by remember { mutableStateOf(emptySet<String>()) }
    var deleteRequest by remember { mutableStateOf<LegacyPlaylistDeleteRequest?>(null) }
    var removeInFlight by remember { mutableStateOf(false) }
    var deletePlaylistInFlight by remember { mutableStateOf(false) }
    val mediaItems = remember(tracks) {
        tracks.map { track -> track.toMediaItem() }
    }
    val mediaIds = remember(mediaItems) {
        mediaItems.mapTo(linkedSetOf(), MediaItem::mediaId)
    }
    val playbackBarOverlayHeightPx = with(LocalDensity.current) {
        playbackBarOverlayHeight.roundToPx()
    }

    LaunchedEffect(editableAccountPlaylist?.playlistId, mediaIds) {
        if (editableAccountPlaylist == null) {
            editMode = false
            selectedMediaIds = emptySet()
        } else {
            selectedMediaIds = selectedMediaIds.intersect(mediaIds)
        }
    }

    BackHandler(enabled = active && editMode) {
        editMode = false
        selectedMediaIds = emptySet()
    }

    fun showPlayFailed() {
        Toast.makeText(context, playFailedMessageRes, Toast.LENGTH_SHORT).show()
    }

    fun updateSelection(mediaId: String, selected: Boolean) {
        selectedMediaIds = if (selected) {
            selectedMediaIds + mediaId
        } else {
            selectedMediaIds - mediaId
        }
    }

    fun playCloudQueueFromIndex(
        startIndex: Int,
        shuffle: Boolean = false,
        onResolveFailed: (() -> Unit)? = null,
    ) {
        if (mediaItems.isEmpty()) {
            return
        }
        val safeStartIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        val requestToken = queuePopulateToken + 1L
        queuePopulateToken = requestToken
        queuePopulateJob?.cancel()
        queuePopulateJob = scope.launch {
            val startItem = withContext(Dispatchers.IO) {
                runCatching {
                    repository.resolvePlayableMediaItems(
                        mediaItems = listOf(mediaItems[safeStartIndex]),
                        includeLyrics = true,
                    ).firstOrNull()
                }.getOrNull()
            }
            if (queuePopulateToken != requestToken) {
                return@launch
            }
            if (startItem == null) {
                onResolveFailed?.invoke()
                showPlayFailed()
                return@launch
            }
            val queueItems = mediaItems.toMutableList().apply {
                this[safeStartIndex] = startItem
            }
            browser.replaceQueueAndPlay(
                mediaItems = queueItems,
                startIndex = safeStartIndex,
                shuffleModeEnabled = shuffle,
            )
        }
    }

    fun removeSelectedFromAccountPlaylist() {
        val playlist = editableAccountPlaylist ?: return
        if (removeInFlight || selectedMediaIds.isEmpty()) {
            return
        }
        val trackIds = mediaItems
            .asSequence()
            .filter { item -> item.mediaId in selectedMediaIds }
            .mapNotNull { item ->
                item.onlineIdentityOrNull()
                    ?.takeIf { identity -> identity.source == playlist.provider.sourceId }
                    ?.trackId
            }
            .distinct()
            .toList()
        if (trackIds.isEmpty()) {
            selectedMediaIds = emptySet()
            return
        }
        removeInFlight = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    repository.removeTracksFromAccountPlaylist(
                        playlist = playlist,
                        trackIds = trackIds,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
            } finally {
                removeInFlight = false
            }
            when (result.status) {
                NeteaseAccountActionStatus.Success -> {
                    editMode = false
                    selectedMediaIds = emptySet()
                    onAccountPlaylistTracksChanged()
                    Toast.makeText(
                        context,
                        R.string.netease_online_music_playlist_remove_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                NeteaseAccountActionStatus.RequiresLogin -> {
                    Toast.makeText(context, R.string.online_music_login_required, Toast.LENGTH_SHORT).show()
                }
                NeteaseAccountActionStatus.Failed -> {
                    Toast.makeText(
                        context,
                        R.string.netease_online_music_playlist_remove_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun deleteAccountPlaylist() {
        val playlist = editableAccountPlaylist ?: return
        if (deletePlaylistInFlight) {
            return
        }
        deletePlaylistInFlight = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    repository.deleteAccountPlaylist(playlist)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NeteaseAccountActionResult(NeteaseAccountActionStatus.Failed)
            } finally {
                deletePlaylistInFlight = false
            }
            when (result.status) {
                NeteaseAccountActionStatus.Success -> {
                    editMode = false
                    selectedMediaIds = emptySet()
                    onAccountPlaylistDeleted(playlist)
                    Toast.makeText(
                        context,
                        R.string.netease_online_music_playlist_delete_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                NeteaseAccountActionStatus.RequiresLogin -> {
                    Toast.makeText(context, R.string.online_music_login_required, Toast.LENGTH_SHORT).show()
                }
                NeteaseAccountActionStatus.Failed -> {
                    Toast.makeText(
                        context,
                        R.string.netease_online_music_playlist_delete_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    LegacyPlaylistDeleteDialog(
        request = deleteRequest,
        onDismiss = {
            deleteRequest = null
        },
        onConfirm = { request ->
            deleteRequest = null
            when (request) {
                LegacyPlaylistDeleteRequest.NeteaseDetailPlaylist -> deleteAccountPlaylist()
                LegacyPlaylistDeleteRequest.DetailTracks -> removeSelectedFromAccountPlaylist()
                LegacyPlaylistDeleteRequest.DetailPlaylist,
                LegacyPlaylistDeleteRequest.RootSelected -> Unit
            }
        },
    )

    Column(modifier = modifier) {
        val accountPlaylistEditable = editableAccountPlaylist != null
        if (accountPlaylistEditable) {
            CloudMusicAccountPlaylistActionBar(
                actionInFlight = removeInFlight || deletePlaylistInFlight,
                editMode = editMode,
                selectedCount = selectedMediaIds.size,
                totalCount = mediaItems.size,
                onShuffleClick = {
                    playCloudQueueFromIndex(
                        startIndex = Random.nextInt(mediaItems.size),
                        shuffle = true,
                    )
                },
                onDeletePlaylistClick = {
                    deleteRequest = LegacyPlaylistDeleteRequest.NeteaseDetailPlaylist
                },
                onEditClick = {
                    editMode = true
                    selectedMediaIds = emptySet()
                },
                onSelectAllClick = {
                    selectedMediaIds = mediaIds
                },
                onRemoveClick = {
                    if (selectedMediaIds.isNotEmpty()) {
                        deleteRequest = LegacyPlaylistDeleteRequest.DetailTracks
                    }
                },
                onCancelEditClick = {
                    editMode = false
                    selectedMediaIds = emptySet()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CloudMusicPlayActionBar(
                enabled = mediaItems.isNotEmpty(),
                onPlayAllClick = {
                    playCloudQueueFromIndex(startIndex = 0)
                },
                onShuffleClick = {
                    playCloudQueueFromIndex(
                        startIndex = Random.nextInt(mediaItems.size),
                        shuffle = true,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CloudMusicDivider()
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    LayoutInflater.from(viewContext).inflate(R.layout.smart_pinnedlist, this, true)
                    findViewById<ListView>(R.id.list)?.apply {
                        divider = ColorDrawable(viewContext.getColor(R.color.listview_divider_color))
                        dividerHeight = viewContext.resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
                        selector = viewContext.getDrawable(R.drawable.listview_selector)
                        cacheColorHint = Color.TRANSPARENT
                        setBackgroundColor(Color.TRANSPARENT)
                        layoutAnimation = AnimationUtils.loadLayoutAnimation(viewContext, R.anim.list_anim_layout)
                        addLegacyPortListFooter()
                    }
                }
            },
            update = { root ->
                root.visibility = if (active) View.VISIBLE else View.INVISIBLE
                val listView = root.findViewById<ListView>(R.id.list) ?: return@AndroidView
                listView.apply {
                    val nextPaddingBottom = playbackBarOverlayHeightPx
                    if (paddingBottom != nextPaddingBottom || clipToPadding) {
                        setPadding(paddingLeft, paddingTop, paddingRight, nextPaddingBottom)
                        clipToPadding = false
                    }
                }
                listView.bindLegacyPortListFooter(
                    pluralsRes = R.plurals.track_count,
                    count = mediaItems.size,
                )
                val adapter = listView.legacyWrappedAdapter<LegacySongsAdapter>()
                    ?: LegacySongsAdapter().also { adapter ->
                        listView.adapter = adapter
                    }
                adapter.onMoreClick = { item ->
                    if (!editMode) {
                        onTrackMoreClick(item)
                    }
                }
                val previousEditMode = listView.getTag(R.id.elvitem) as? Boolean
                val animateEditMode = previousEditMode != null && previousEditMode != editMode
                listView.setTag(R.id.elvitem, editMode)
                val listContentChanged = adapter.updateItems(
                    nextItems = mediaItems,
                    nextCurrentMediaId = browser?.currentMediaItem?.mediaId,
                    nextCurrentIsPlaying = browser?.isPlaying == true,
                    nextDisplayMode = LegacySongsSortDisplayMode.Name,
                    nextSectionMode = LegacySongsSectionMode.None,
                    nextQuickBarCollapsedVisibleWidth = 0,
                    nextEditMode = editMode,
                    nextSelectedMediaIds = selectedMediaIds,
                )
                if (listContentChanged) {
                    listView.setSelection(0)
                    listView.scheduleLayoutAnimation()
                } else {
                    adapter.updateVisibleSongRows(
                        listView = listView,
                        animateEditMode = animateEditMode,
                    )
                }
                if (listView.getTag(R.id.list) !== browser) {
                    (listView.getTag(R.id.text) as? Player.Listener)?.let { oldListener ->
                        (listView.getTag(R.id.list) as? Player)?.removeListener(oldListener)
                    }
                    if (browser != null) {
                        val playbackListener = object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                adapter.setPlaybackState(
                                    nextCurrentMediaId = player.currentMediaItem?.mediaId,
                                    nextCurrentIsPlaying = player.isPlaying,
                                )
                                adapter.updateVisiblePlaybackState(listView)
                            }
                        }
                        browser.addListener(playbackListener)
                        listView.setTag(R.id.text, playbackListener)
                    } else {
                        listView.setTag(R.id.text, null)
                    }
                    listView.setTag(R.id.list, browser)
                }
                val slideSelectionController = listView.legacySlideSelectionController(
                    startArea = LegacySlideSelectionStartArea.Checkbox,
                )
                slideSelectionController.update(
                    enabled = editMode,
                    selectedKeys = selectedMediaIds,
                    keyAtPosition = { position ->
                        adapter.itemAt(position)?.mediaId
                    },
                    onSelectionChange = { mediaId, selected ->
                        updateSelection(mediaId, selected)
                    },
                )
                listView.setOnTouchListener { _, event ->
                    slideSelectionController.handleTouch(event)
                }
                listView.setOnItemClickListener { _, _, position, _ ->
                    val item = adapter.itemAt(position) ?: return@setOnItemClickListener
                    if (editMode) {
                        updateSelection(item.mediaId, item.mediaId !in selectedMediaIds)
                        return@setOnItemClickListener
                    }
                    adapter.setPlaybackState(item.mediaId, true)
                    adapter.updateVisiblePlaybackState(listView)
                    val startIndex = mediaItems.indexOfFirst { candidate ->
                        candidate.mediaId == item.mediaId
                    }.takeIf { index -> index >= 0 } ?: 0
                    playCloudQueueFromIndex(
                        startIndex = startIndex,
                        onResolveFailed = {
                            adapter.setPlaybackState(
                                nextCurrentMediaId = browser?.currentMediaItem?.mediaId,
                                nextCurrentIsPlaying = browser?.isPlaying == true,
                            )
                            adapter.updateVisiblePlaybackState(listView)
                        },
                    )
                }
            },
            onRelease = { root ->
                val listView = root.findViewById<ListView>(R.id.list)
                if (listView != null) {
                    (listView.getTag(R.id.text) as? Player.Listener)?.let { oldListener ->
                        (listView.getTag(R.id.list) as? Player)?.removeListener(oldListener)
                    }
                }
            },
        )
    }
}

@Composable
internal fun CloudMusicPlayActionBar(
    enabled: Boolean,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier
            .background(ComposeColor.White)
            .padding(horizontal = 15.dp),
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                LayoutInflater.from(viewContext).inflate(R.layout.layout_play_container, this, true)
            }
        },
        update = { root ->
            root.findViewById<View>(R.id.bt_play)?.apply {
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.22f
                setOnClickListener {
                    if (enabled) {
                        onPlayAllClick()
                    }
                }
            }
            root.findViewById<View>(R.id.bt_shuffle)?.apply {
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.22f
                setOnClickListener {
                    if (enabled) {
                        onShuffleClick()
                    }
                }
            }
        },
    )
}

@Composable
internal fun CloudMusicAccountPlaylistActionBar(
    actionInFlight: Boolean,
    editMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onShuffleClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onEditClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier
            .height(CloudAccountPlaylistActionBarHeight)
            .background(ComposeColor.White)
            .padding(horizontal = 15.dp),
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                setBackgroundColor(Color.WHITE)
            }
        },
        update = { root ->
            root.removeAllViews()
            if (editMode) {
                root.addView(
                    cloudMusicAccountPlaylistEditActions(
                        context = root.context,
                        enabled = !actionInFlight,
                        selectedCount = selectedCount,
                        totalCount = totalCount,
                        onSelectAllClick = onSelectAllClick,
                        onRemoveClick = onRemoveClick,
                        onCancelEditClick = onCancelEditClick,
                    ),
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
            } else {
                root.addView(
                    cloudMusicAccountPlaylistNormalActions(
                        context = root.context,
                        trackActionsEnabled = totalCount > 0 && !actionInFlight,
                        deleteEnabled = !actionInFlight,
                        onShuffleClick = onShuffleClick,
                        onDeletePlaylistClick = onDeletePlaylistClick,
                        onEditClick = onEditClick,
                    ),
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
            }
        },
    )
}

private fun cloudMusicAccountPlaylistNormalActions(
    context: Context,
    trackActionsEnabled: Boolean,
    deleteEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onEditClick: () -> Unit,
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(
            legacyPlaylistDetailActionButton(context, R.drawable.btn_shuffle2_selector, R.string.s_random_play).apply {
                bindCloudMusicAccountActionEnabled(trackActionsEnabled)
                setOnClickListener {
                    if (isEnabled) {
                        onShuffleClick()
                    }
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        addView(
            legacyPlaylistDetailActionButton(context, R.drawable.btn_deletelist2_selector, R.string.netease_playlist_delete_action).apply {
                bindCloudMusicAccountActionEnabled(deleteEnabled)
                setOnClickListener {
                    if (isEnabled) {
                        onDeletePlaylistClick()
                    }
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = -context.dpPx(6)
            },
        )
        addView(
            legacyPlaylistDetailActionButton(context, R.drawable.btn_editlist2_selector, R.string.netease_playlist_edit_action).apply {
                bindCloudMusicAccountActionEnabled(trackActionsEnabled)
                setOnClickListener {
                    if (isEnabled) {
                        onEditClick()
                    }
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = -context.dpPx(6)
            },
        )
    }
}

private fun View.bindCloudMusicAccountActionEnabled(enabled: Boolean) {
    isEnabled = enabled
    alpha = if (enabled) 1f else 0.28f
    isClickable = enabled
    isFocusable = enabled
}

private fun cloudMusicAccountPlaylistEditActions(
    context: Context,
    enabled: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onSelectAllClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onCancelEditClick: () -> Unit,
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            cloudMusicAccountActionButton(
                context = context,
                iconRes = null,
                text = context.getString(R.string.cloud_music_select_all),
                enabled = enabled && totalCount > 0,
                onClick = onSelectAllClick,
            ),
            LinearLayout.LayoutParams(context.dpPx(76), LinearLayout.LayoutParams.MATCH_PARENT),
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.selected_item_format, selectedCount, totalCount)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(context.getColor(R.color.list_item_second_line))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_better))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        addView(
            cloudMusicAccountActionButton(
                context = context,
                iconRes = R.drawable.btn_delete_song2_selector,
                text = context.getString(R.string.delete_track),
                enabled = enabled && selectedCount > 0,
                destructive = true,
                onClick = onRemoveClick,
            ),
            LinearLayout.LayoutParams(context.dpPx(104), LinearLayout.LayoutParams.MATCH_PARENT),
        )
        addView(
            cloudMusicAccountActionButton(
                context = context,
                iconRes = null,
                text = context.getString(R.string.cancel),
                enabled = enabled,
                onClick = onCancelEditClick,
            ),
            LinearLayout.LayoutParams(context.dpPx(68), LinearLayout.LayoutParams.MATCH_PARENT),
        )
    }
}

private fun cloudMusicAccountActionButton(
    context: Context,
    iconRes: Int?,
    text: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
): LinearLayout {
    return LinearLayout(context).apply {
        gravity = Gravity.CENTER
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(if (destructive) R.drawable.btn_red_bg_selector else R.drawable.title_button_bg_selector)
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.28f
        isClickable = enabled
        isFocusable = enabled
        iconRes?.let { res ->
            addView(
                ImageView(context).apply {
                    setImageResource(res)
                    isDuplicateParentStateEnabled = true
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = context.dpPx(8)
                },
            )
        }
        addView(
            TextView(context).apply {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                this.text = text
                gravity = Gravity.CENTER
                setTextColor(
                    context.getColor(
                        when {
                            destructive -> R.color.btn_text_color_red
                            else -> R.color.btn_text_color_blue
                        },
                    ),
                )
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.button_text_size))
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        setOnClickListener {
            if (isEnabled) {
                onClick()
            }
        }
    }
}
