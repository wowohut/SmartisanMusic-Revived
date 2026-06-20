package com.smartisanos.music.ui.shell.songs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RelativeLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.isPlaybackActiveForUi
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.playback.replaceQueueAndPlayShuffled
import com.smartisanos.music.ui.shell.LegacySlideSelectionStartArea
import com.smartisanos.music.ui.shell.addLegacyPortListFooter
import com.smartisanos.music.ui.shell.bindLegacyPortListFooter
import com.smartisanos.music.ui.shell.legacyWrappedAdapter
import com.smartisanos.music.ui.shell.legacySlideSelectionController
import smartisanos.widget.ActionButtonGroup
import smartisanos.widget.letters.QuickBarEx

@Composable
internal fun LegacyPortSongsPage(
    mediaItems: List<MediaItem>,
    libraryLoaded: Boolean,
    active: Boolean,
    editMode: Boolean,
    selectedSongIds: Set<String>,
    hiddenMediaIds: Set<String>,
    onSongSelectionChange: (String, Boolean) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onRequestSongDeleteConfirmation: (Set<String>, (() -> Unit)?) -> Unit,
    playbackBarOverlayHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val browser = LocalPlaybackBrowser.current
    val playbackBarOverlayHeightPx = with(LocalDensity.current) { playbackBarOverlayHeight.roundToPx() }
    var selectedSortIndex by remember { mutableStateOf(0) }
    val visibleSongs = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
    }
    val sortedSongs = remember(visibleSongs, selectedSortIndex) {
        visibleSongs.sortedForLegacySort(selectedSortIndex)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                LayoutInflater.from(viewContext).inflate(R.layout.f_all_track, this, true)
                (getChildAt(1) as? RelativeLayout)?.setBackgroundColor(Color.TRANSPARENT)
                findViewById<FrameLayout>(R.id.all_track_fragment_container)?.apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    LayoutInflater.from(viewContext).inflate(R.layout.smart_pinnedlist, this, true)
                }
                findViewById<ListView>(R.id.list)?.apply {
                    divider = ColorDrawable(viewContext.getColor(R.color.listview_divider_color))
                    dividerHeight = viewContext.resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
                    selector = viewContext.getDrawable(R.drawable.listview_selector)
                    cacheColorHint = Color.TRANSPARENT
                    setBackgroundColor(Color.TRANSPARENT)
                    addLegacyPortListFooter()
                }
            }
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            val hasSongs = sortedSongs.isNotEmpty()
            val showEmptyState = libraryLoaded && !hasSongs
            val playActionsEnabled = hasSongs && !editMode
            root.findViewById<View>(R.id.fl_null_artist)?.visibility = if (showEmptyState) View.VISIBLE else View.GONE
            root.findViewById<ActionButtonGroup>(R.id.l_alltrack_header)?.apply {
                visibility = if (hasSongs) View.VISIBLE else View.GONE
                setupLegacySongsSortHeader(selectedSortIndex) { index ->
                    selectedSortIndex = index
                }
            }
            root.findViewById<View>(R.id.play_container)?.apply {
                visibility = if (hasSongs) View.VISIBLE else View.GONE
                alpha = if (editMode) 0.22f else 1f
            }
            root.findViewById<View>(R.id.view_divider)?.visibility = if (hasSongs) View.VISIBLE else View.GONE
            root.findViewById<FrameLayout>(R.id.all_track_fragment_container)?.apply {
                (layoutParams as? RelativeLayout.LayoutParams)?.let { params ->
                    if (params.bottomMargin != playbackBarOverlayHeightPx) {
                        params.bottomMargin = playbackBarOverlayHeightPx
                        layoutParams = params
                    }
                }
            }
            root.findViewById<View>(R.id.bt_play)?.apply {
                isEnabled = playActionsEnabled
                setOnClickListener {
                    if (!playActionsEnabled || sortedSongs.isEmpty()) {
                        return@setOnClickListener
                    }
                    browser.replaceQueueAndPlay(sortedSongs)
                }
            }
            root.findViewById<View>(R.id.bt_shuffle)?.apply {
                isEnabled = playActionsEnabled
                setOnClickListener {
                    if (!playActionsEnabled || sortedSongs.isEmpty()) {
                        return@setOnClickListener
                    }
                    browser.replaceQueueAndPlayShuffled(sortedSongs)
                }
            }
            val listView = root.findViewById<ListView>(R.id.list) ?: return@AndroidView
            listView.apply {
                if (paddingBottom != 0 || !clipToPadding) {
                    setPadding(paddingLeft, paddingTop, paddingRight, 0)
                    clipToPadding = true
                }
            }
            val sortDisplayMode = selectedSortIndex.toLegacySongsSortDisplayMode()
            val showQuickBar = hasSongs &&
                sortDisplayMode == LegacySongsSortDisplayMode.Name &&
                sortedSongs.size > LegacySongsQuickBarVisibilityLimit
            val quickBar = root.findViewById<QuickBarEx>(R.id.main_quickbar)
            listView.visibility = if (hasSongs || libraryLoaded) View.VISIBLE else View.INVISIBLE
            listView.bindLegacyPortListFooter(
                pluralsRes = R.plurals.track_count,
                count = sortedSongs.size,
            )
            val adapter = listView.legacyWrappedAdapter<LegacySongsAdapter>() ?: LegacySongsAdapter().also { adapter ->
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
                nextItems = sortedSongs,
                nextCurrentMediaId = browser?.currentMediaItem?.mediaId,
                nextCurrentIsPlaying = browser.isPlaybackActiveForUi(),
                nextDisplayMode = sortDisplayMode,
                nextSectionMode = sortDisplayMode.toSectionMode(),
                nextQuickBarCollapsedVisibleWidth = if (showQuickBar) quickBar?.collapsedVisibleWidth ?: 0 else 0,
                nextEditMode = editMode,
                nextSelectedMediaIds = selectedSongIds,
            )
            if (listContentChanged) {
                listView.setSelection(0)
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
                                nextCurrentIsPlaying = player.isPlaybackActiveForUi(),
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
            val swipeDeleteController = listView.legacySongSwipeDeleteController()
            swipeDeleteController.update(
                enabled = !editMode,
                keyAtPosition = { position ->
                    adapter.itemAt(position)?.mediaId
                },
                onDeleteClick = { mediaId, onDismiss ->
                    onRequestSongDeleteConfirmation(setOf(mediaId), onDismiss)
                },
                onSwipeActiveChange = { active ->
                    quickBar?.visibility = if (showQuickBar && !active) View.VISIBLE else View.GONE
                },
            )
            slideSelectionController.update(
                enabled = editMode,
                selectedKeys = selectedSongIds,
                keyAtPosition = { position ->
                    adapter.itemAt(position)?.mediaId
                },
                onSelectionChange = { mediaId, selected ->
                    onSongSelectionChange(mediaId, selected)
                },
            )
            listView.setOnTouchListener { _, event ->
                swipeDeleteController.handleTouch(event) ||
                    slideSelectionController.handleTouch(event)
            }
            quickBar?.apply {
                visibility = if (showQuickBar && !swipeDeleteController.isSwipeActive()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.gravity = Gravity.END
                    layoutParams = params
                }
                setLetters(QuickBarEx.DefaultLetters)
                setLongPressEnabled(false)
                setQBListener(
                    object : QuickBarEx.QBListener {
                        override fun onLetterChanged(letter: String, action: Int): Boolean {
                            val position = adapter.positionForLetter(letter)
                            if (position < 0) {
                                return false
                            }
                            listView.setSelection(position)
                            return true
                        }
                    },
                )
            }
            listView.setOnItemClickListener { _, _, position, _ ->
                val item = adapter.itemAt(position) ?: return@setOnItemClickListener
                if (editMode) {
                    onSongSelectionChange(item.mediaId, item.mediaId !in selectedSongIds)
                    return@setOnItemClickListener
                }
                val songIndex = adapter.songIndexAt(position) ?: return@setOnItemClickListener
                adapter.setPlaybackState(item.mediaId, true)
                adapter.updateVisiblePlaybackState(listView)
                browser.replaceQueueAndPlay(adapter.items, songIndex)
            }
        },
    )
}

private fun ActionButtonGroup.setupLegacySongsSortHeader(
    selectedSortIndex: Int,
    onSortSelected: (Int) -> Unit,
) {
    setActionButtonGroupBackgroundColor(Color.WHITE)
    getLeftActionButton().visibility = View.GONE
    val sidePadding = resources.getDimensionPixelSize(R.dimen.button_group_left_right_padding)
    setActionButtonGroupSidePadding(sidePadding, sidePadding)
    setShadowDrawable(R.drawable.smartisan_secondary_bar_shadow)
    setActionButtonGroupShadowVisibility(true)

    val labels = intArrayOf(
        R.string.sort_by_song_name,
        R.string.sort_by_song_score,
        R.string.sort_by_song_play_time,
        R.string.sort_by_song_update_time,
    )
    repeat(getButtonCount().coerceAtMost(labels.size)) { index ->
        getButton(index).apply {
            setButtonText(index, labels[index])
            gravity = android.view.Gravity.CENTER
            fun selectSort() {
                setButtonActivated(index)
                onSortSelected(index)
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !isActivated) {
                    selectSort()
                }
                false
            }
            setOnClickListener {
                if (!isActivated) {
                    selectSort()
                }
            }
        }
    }
    setButtonActivated(selectedSortIndex)
}

private const val LegacySongsQuickBarVisibilityLimit = 30
