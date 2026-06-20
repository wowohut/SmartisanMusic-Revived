package com.smartisanos.music.ui.shell.loved

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HeaderViewListAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.favorite.FavoriteSongRecord
import com.smartisanos.music.playback.LocalAudioLibrary
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.isPlaybackActiveForUi
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.playback.replaceQueueAndPlayShuffled
import com.smartisanos.music.ui.loved.LovedSongEntry
import com.smartisanos.music.ui.loved.LovedSongsSortMode
import com.smartisanos.music.ui.loved.buildLovedSongEntries
import com.smartisanos.music.ui.loved.buildLovedSongsPlayRequest
import com.smartisanos.music.ui.loved.buildLovedSongsShuffleRequest
import com.smartisanos.music.ui.loved.sortLovedSongEntries
import com.smartisanos.music.ui.shell.LegacyAlbumArtworkLoader
import com.smartisanos.music.ui.shell.LegacyPortPredictiveBackHandler
import com.smartisanos.music.ui.shell.LegacyPortPredictiveBackState
import com.smartisanos.music.ui.shell.LegacySlideSelectionStartArea
import com.smartisanos.music.ui.shell.addLegacyPortListFooter
import com.smartisanos.music.ui.shell.bindLegacyPortListFooter
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisanos.music.ui.shell.legacySlideSelectionController
import com.smartisanos.music.ui.shell.songs.LegacyTitleNormalizer
import com.smartisanos.music.ui.widgets.EditableLayout
import com.smartisanos.music.ui.widgets.StretchTextView
import smartisanos.app.MenuDialog

@Composable
internal fun LegacyPortLovedSongsPage(
    active: Boolean,
    mediaItems: List<MediaItem>,
    favoriteRecords: List<FavoriteSongRecord>,
    hiddenMediaIds: Set<String>,
    libraryLoaded: Boolean,
    onClose: () -> Unit,
    closePredictiveBackState: LegacyPortPredictiveBackState? = null,
    onTrackMoreClick: (MediaItem) -> Unit,
    onRemoveFavoriteMediaIds: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val artworkLoader = remember(context) {
        LegacyAlbumArtworkLoader(context)
    }
    var sortMode by remember { mutableStateOf(LovedSongsSortMode.Time) }
    var editMode by remember { mutableStateOf(false) }
    var selectedMediaIds by remember { mutableStateOf(emptySet<String>()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    DisposableEffect(artworkLoader) {
        onDispose {
            artworkLoader.clear()
        }
    }

    val visibleSongs = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
    }
    val lovedEntries = remember(favoriteRecords, visibleSongs) {
        buildLovedSongEntries(
            favorites = favoriteRecords,
            visibleSongs = visibleSongs,
        )
    }
    val sortedEntries = remember(lovedEntries, sortMode) {
        sortLovedSongEntries(
            entries = lovedEntries,
            sortMode = sortMode,
            titleComparator = Comparator { left, right ->
                LegacyTitleNormalizer.normalize(left)
                    .compareTo(LegacyTitleNormalizer.normalize(right))
            },
        )
    }

    LaunchedEffect(sortedEntries) {
        val visibleIds = sortedEntries.map { entry -> entry.mediaItem.mediaId }.toSet()
        selectedMediaIds = selectedMediaIds.intersect(visibleIds)
        if (visibleIds.isEmpty()) {
            editMode = false
        }
    }

    BackHandler(enabled = active && editMode) {
        editMode = false
        selectedMediaIds = emptySet()
    }
    if (closePredictiveBackState != null) {
        LegacyPortPredictiveBackHandler(
            enabled = active && !editMode,
            state = closePredictiveBackState,
            onBack = onClose,
        )
    } else {
        BackHandler(enabled = active && !editMode) {
            onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        LegacyLovedSongsTitleBar(
            editMode = editMode,
            hasSongs = sortedEntries.isNotEmpty(),
            selectedCount = selectedMediaIds.size,
            sortMode = sortMode,
            onBack = {
                if (editMode) {
                    editMode = false
                    selectedMediaIds = emptySet()
                } else {
                    onClose()
                }
            },
            onSortModeChanged = { nextSortMode ->
                sortMode = nextSortMode
            },
            onEnterEdit = {
                editMode = true
                selectedMediaIds = emptySet()
            },
            onRemoveSelected = {
                if (selectedMediaIds.isNotEmpty()) {
                    showRemoveConfirm = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    FrameLayout(viewContext).apply {
                        setBackgroundResource(R.drawable.account_background)
                        LayoutInflater.from(viewContext).inflate(R.layout.f_saved_songs, this, true)
                        findViewById<ListView>(android.R.id.list)?.apply {
                            divider = ColorDrawable(viewContext.getColor(R.color.listview_divider_color))
                            dividerHeight = viewContext.resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
                            selector = viewContext.getDrawable(R.drawable.listview_selector)
                            cacheColorHint = Color.TRANSPARENT
                            setBackgroundResource(R.drawable.account_background)
                            layoutAnimation = AnimationUtils.loadLayoutAnimation(viewContext, R.anim.list_anim_layout)
                            addLegacyPortListFooter()
                        }
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(view: View) = Unit

                            override fun onViewDetachedFromWindow(view: View) {
                                view.clearLovedSongsPlaybackListener()
                            }
                        })
                    }
                },
                update = { root ->
                    root.visibility = if (active) View.VISIBLE else View.INVISIBLE
                    val hasSongs = sortedEntries.isNotEmpty()
                    val showEmptyState = libraryLoaded && !hasSongs
                    root.findViewById<View>(R.id.fl_null_artist)?.visibility = if (showEmptyState) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    root.findViewById<View>(R.id.layout_play_container)?.apply {
                        visibility = if (hasSongs) View.VISIBLE else View.GONE
                        isEnabled = !editMode
                        alpha = if (editMode) 0.22f else 1f
                    }
                    root.findViewById<View>(R.id.view_divider)?.visibility = if (hasSongs) View.VISIBLE else View.GONE
                    root.findViewById<View>(R.id.bt_play)?.apply {
                        isEnabled = hasSongs && !editMode
                        setOnClickListener {
                            if (!isEnabled) {
                                return@setOnClickListener
                            }
                            buildLovedSongsPlayRequest(sortedEntries)?.let { request ->
                                browser.replaceQueueAndPlay(request.mediaItems, request.startIndex)
                            }
                        }
                    }
                    root.findViewById<View>(R.id.bt_shuffle)?.apply {
                        isEnabled = hasSongs && !editMode
                        setOnClickListener {
                            if (!isEnabled) {
                                return@setOnClickListener
                            }
                            buildLovedSongsShuffleRequest(sortedEntries)?.let { request ->
                                browser.replaceQueueAndPlayShuffled(request.mediaItems)
                            }
                        }
                    }

                    val listView = root.findViewById<ListView>(android.R.id.list) ?: return@AndroidView
                    listView.visibility = if (hasSongs) View.VISIBLE else View.INVISIBLE
                    val adapter = listView.legacyLovedSongsAdapter()
                        ?: LegacyLovedSongsAdapter(artworkLoader).also { nextAdapter ->
                            listView.adapter = nextAdapter
                            listView.scheduleLayoutAnimation()
                        }
                    adapter.onMoreClick = { item ->
                        if (!editMode) {
                            onTrackMoreClick(item)
                        }
                    }
                    val previousEditMode = listView.getTag(R.id.elvitem) as? Boolean
                    val animateEditMode = previousEditMode != null && previousEditMode != editMode
                    listView.setTag(R.id.elvitem, editMode)
                    val contentChanged = adapter.updateEntries(
                        nextEntries = sortedEntries,
                        nextCurrentMediaId = browser?.currentMediaItem?.mediaId,
                        nextCurrentIsPlaying = browser.isPlaybackActiveForUi(),
                        nextEditMode = editMode,
                        nextSelectedMediaIds = selectedMediaIds,
                    )
                    adapter.updateFooter(listView)
                    if (contentChanged) {
                        listView.setSelection(0)
                    } else {
                        adapter.updateVisibleRows(
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
                    slideSelectionController.update(
                        enabled = editMode,
                        selectedKeys = selectedMediaIds,
                        keyAtPosition = { position ->
                            adapter.entryAt(position)?.mediaItem?.mediaId
                        },
                        onSelectionChange = { mediaId, selected ->
                            selectedMediaIds = if (selected) {
                                selectedMediaIds + mediaId
                            } else {
                                selectedMediaIds - mediaId
                            }
                        },
                    )
                    listView.setOnTouchListener { _, event ->
                        slideSelectionController.handleTouch(event)
                    }
                    listView.setOnItemClickListener { _, _, position, _ ->
                        val entry = adapter.entryAt(position) ?: return@setOnItemClickListener
                        val mediaId = entry.mediaItem.mediaId
                        if (editMode) {
                            selectedMediaIds = selectedMediaIds.toggle(mediaId)
                            return@setOnItemClickListener
                        }
                        buildLovedSongsPlayRequest(sortedEntries, mediaId)?.let { request ->
                            adapter.setPlaybackState(mediaId, true)
                            adapter.updateVisiblePlaybackState(listView)
                            browser.replaceQueueAndPlay(request.mediaItems, request.startIndex)
                        }
                    }
                },
            )
        }
    }

    LovedSongsRemoveDialog(
        visible = showRemoveConfirm,
        onDismiss = {
            showRemoveConfirm = false
        },
        onConfirm = {
            val mediaIds = selectedMediaIds
            showRemoveConfirm = false
            selectedMediaIds = emptySet()
            editMode = false
            onRemoveFavoriteMediaIds(mediaIds)
        },
    )
}

@Composable
private fun LegacyLovedSongsTitleBar(
    editMode: Boolean,
    hasSongs: Boolean,
    selectedCount: Int,
    sortMode: LovedSongsSortMode,
    onBack: () -> Unit,
    onSortModeChanged: (LovedSongsSortMode) -> Unit,
    onEnterEdit: () -> Unit,
    onRemoveSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
    val shadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
    Column(
        modifier = modifier
            .zIndex(1f)
            .fillMaxWidth()
            .background(ComposeColor.White),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleContentHeight),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    FrameLayout(context).apply {
                        LayoutInflater.from(context).inflate(R.layout.title_saved_songs, this, true)
                        findViewById<Button>(R.id.bt_left)?.prepareTitleIconButton()
                        findViewById<Button>(R.id.bt_right)?.prepareTitleIconButton()
                        val rightButton = findViewById<Button>(R.id.bt_right)
                        val sortButton = ImageButton(context).apply {
                            id = R.id.saved_songs_sort_button
                            background = null
                            scaleType = ImageView.ScaleType.CENTER
                            setImageResource(R.drawable.saved_songs_sort_btn_selector)
                        }
                        val iconSize = resources.getDimensionPixelSize(R.dimen.standard_icon_size)
                        val marginView = resources.getDimensionPixelSize(R.dimen.title_bar_margin_view)
                        (getChildAt(0) as? RelativeLayout)?.addView(
                            sortButton,
                            RelativeLayout.LayoutParams(iconSize, iconSize).apply {
                                addRule(RelativeLayout.CENTER_VERTICAL)
                                if (rightButton != null) {
                                    addRule(RelativeLayout.LEFT_OF, R.id.bt_right)
                                } else {
                                    addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                                }
                                rightMargin = marginView
                            },
                        )
                    }
                },
                update = { root ->
                    val leftButton = root.findViewById<Button>(R.id.bt_left)
                    val rightButton = root.findViewById<Button>(R.id.bt_right)
                    val sortButton = root.findViewById<ImageButton>(R.id.saved_songs_sort_button)
                    root.findViewById<TextView>(R.id.tv_title)?.setText(R.string.collect_music)

                    leftButton?.apply {
                        setBackgroundResource(
                            if (editMode) {
                                R.drawable.standard_icon_cancel_selector
                            } else {
                                R.drawable.standard_icon_back_selector
                            },
                        )
                        setOnClickListener {
                            onBack()
                        }
                    }
                    rightButton?.apply {
                        setBackgroundResource(
                            if (editMode) {
                                R.drawable.titlebar_btn_delete_selector
                            } else {
                                R.drawable.standard_icon_multi_select_selector
                            },
                        )
                        isEnabled = if (editMode) selectedCount > 0 else hasSongs
                        setOnClickListener {
                            if (editMode) {
                                if (selectedCount > 0) {
                                    onRemoveSelected()
                                }
                            } else if (hasSongs) {
                                onEnterEdit()
                            }
                        }
                    }
                    sortButton?.apply {
                        isEnabled = hasSongs
                        setOnClickListener { anchor ->
                            if (hasSongs) {
                                showLovedSongsSortPopup(
                                    anchor = anchor,
                                    sortMode = sortMode,
                                    onSortModeChanged = onSortModeChanged,
                                )
                            }
                        }
                    }
                },
            )
            LegacyPortTitleBarShadow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = shadowHeight)
                    .fillMaxWidth()
                    .height(shadowHeight),
            )
        }
    }
}

private fun Button.prepareTitleIconButton() {
    text = null
    minWidth = 0
    minHeight = 0
    minimumWidth = 0
    minimumHeight = 0
    setPadding(0, 0, 0, 0)
    val iconSize = resources.getDimensionPixelSize(R.dimen.standard_icon_size)
    layoutParams = (layoutParams as? RelativeLayout.LayoutParams)?.apply {
        width = iconSize
        height = iconSize
    }
}

private class LegacyLovedSongsAdapter(
    private val artworkLoader: LegacyAlbumArtworkLoader,
) : BaseAdapter() {
    var onMoreClick: (MediaItem) -> Unit = {}
    private var entries: List<LovedSongEntry> = emptyList()
    private var currentMediaId: String? = null
    private var currentIsPlaying = false
    private var editMode = false
    private var selectedMediaIds: Set<String> = emptySet()

    fun updateEntries(
        nextEntries: List<LovedSongEntry>,
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
        nextEditMode: Boolean,
        nextSelectedMediaIds: Set<String>,
    ): Boolean {
        val contentChanged = entries != nextEntries
        val playbackChanged = currentMediaId != nextCurrentMediaId ||
            currentIsPlaying != nextCurrentIsPlaying
        val editModeChanged = editMode != nextEditMode
        val selectionChanged = selectedMediaIds != nextSelectedMediaIds
        if (!contentChanged && !playbackChanged && !editModeChanged && !selectionChanged) {
            return false
        }
        entries = nextEntries
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
        editMode = nextEditMode
        selectedMediaIds = nextSelectedMediaIds
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun entryAt(position: Int): LovedSongEntry? = entries.getOrNull(position)

    fun setPlaybackState(
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
    ) {
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
    }

    fun updateVisiblePlaybackState(listView: ListView) {
        updateVisibleRows(listView, animateEditMode = false)
    }

    fun updateVisibleRows(
        listView: ListView,
        animateEditMode: Boolean,
    ) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex
            val child = listView.getChildAt(childIndex) ?: continue
            val entry = entryAt(position) ?: continue
            bindPlaybackState(child, entry.mediaItem)
            child.bindEditState(entry.mediaItem, animateEditMode)
        }
    }

    fun updateFooter(listView: ListView) {
        listView.bindLegacyPortListFooter(
            pluralsRes = R.plurals.track_count,
            count = entries.size,
        )
    }

    override fun getCount(): Int = entries.size

    override fun getItem(position: Int): Any = entries[position]

    override fun getItemId(position: Int): Long = entries[position].mediaItem.mediaId.toLongOrNull() ?: position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_songs, parent, false)
        val item = entries[position].mediaItem
        val metadata = item.mediaMetadata
        val title = metadata.displayTitle?.toString()
            ?: metadata.title?.toString()
            ?: parent.context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()
            ?: metadata.subtitle?.toString()
            ?: parent.context.getString(R.string.unknown_artist)
        val album = metadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
        val subtitle = if (album.isNullOrBlank()) {
            artist
        } else {
            "$artist - $album"
        }

        view.isSelected = false
        view.isActivated = false
        view.findViewById<TextView>(R.id.track_name)?.apply {
            text = title
            isSelected = true
            if (this is StretchTextView) {
                bindPlaybackState(view, item)
            }
        }
        view.findViewById<TextView>(R.id.artist_name)?.text = subtitle
        view.findViewById<ImageView>(R.id.circle_cover)?.let { cover ->
            artworkLoader.bind(
                imageView = cover,
                mediaItem = item,
                fallbackRes = R.drawable.noalbumcover_120,
                sizePx = parent.resources.getDimensionPixelSize(R.dimen.listview_item_image_width),
            )
        }
        view.findViewById<ImageView>(R.id.song_quality)?.apply {
            val badgeRes = item.lovedQualityBadgeRes()
            if (badgeRes == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                setImageResource(badgeRes)
            }
        }
        view.findViewById<ImageView>(R.id.song_vip)?.visibility = View.GONE
        view.findViewById<CheckBox>(R.id.cb_del)?.isChecked = item.mediaId in selectedMediaIds
        view.findViewById<View>(R.id.btn_more)?.apply {
            visibility = if (editMode) View.GONE else View.VISIBLE
            isClickable = true
            isFocusable = false
            setOnClickListener {
                onMoreClick(item)
            }
        }
        view.bindEditState(item, animate = false)
        return view
    }

    private fun bindPlaybackState(view: View, item: MediaItem) {
        val titleView = view.findViewById<TextView>(R.id.track_name) as? StretchTextView ?: return
        if (!editMode && item.mediaId == currentMediaId) {
            titleView.c(currentIsPlaying)
        } else {
            titleView.setShowingPlayImage(false)
        }
    }

    private fun View.bindEditState(
        item: MediaItem,
        animate: Boolean,
    ) {
        val checked = item.mediaId in selectedMediaIds
        findViewById<View>(R.id.ll_left)?.visibility = View.VISIBLE
        findViewById<View>(R.id.rl_right)?.visibility = View.GONE
        findViewById<View>(R.id.btn_more)?.visibility = if (editMode) View.GONE else View.VISIBLE
        (this as? EditableLayout)?.bindLegacyEditState(
            enabled = editMode,
            checked = checked,
            animate = animate,
        )
    }
}

private fun View.clearLovedSongsPlaybackListener() {
    val listView = findViewById<ListView>(android.R.id.list) ?: return
    (listView.getTag(R.id.text) as? Player.Listener)?.let { listener ->
        (listView.getTag(R.id.list) as? Player)?.removeListener(listener)
    }
    listView.setTag(R.id.text, null)
    listView.setTag(R.id.list, null)
}

private fun ListView.legacyLovedSongsAdapter(): LegacyLovedSongsAdapter? {
    return when (val currentAdapter = adapter) {
        is LegacyLovedSongsAdapter -> currentAdapter
        is HeaderViewListAdapter -> currentAdapter.wrappedAdapter as? LegacyLovedSongsAdapter
        else -> null
    }
}

@Composable
private fun LovedSongsRemoveDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnConfirm by rememberUpdatedState(onConfirm)
    DisposableEffect(visible) {
        if (!visible) {
            return@DisposableEffect onDispose { }
        }
        val dialog = MenuDialog(context).apply {
            setTitle(R.string.uncollect_song_dialog_title)
            setPositiveButton(R.string.uncollect_song_confirm) {
                latestOnConfirm()
            }
            setNegativeButton(
                View.OnClickListener {
                    latestOnDismiss()
                },
            )
            setOnCancelListener {
                latestOnDismiss()
            }
        }
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
}

private fun MediaItem.lovedQualityBadgeRes(): Int? {
    return when (mediaMetadata.extras?.getString(LocalAudioLibrary.AudioQualityBadgeExtraKey)) {
        LocalAudioLibrary.AudioQualityBadgeFlac -> R.drawable.audio_quality_flac
        LocalAudioLibrary.AudioQualityBadgeApe -> R.drawable.audio_quality_ape
        LocalAudioLibrary.AudioQualityBadgeWav -> R.drawable.audio_quality_wav
        LocalAudioLibrary.AudioQualityBadgeAiff -> R.drawable.audio_quality_aiff
        LocalAudioLibrary.AudioQualityBadgeAlac -> R.drawable.audio_quality_alac
        LocalAudioLibrary.AudioQualityBadgeCue -> R.drawable.audio_quality_cue
        else -> null
    }
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) {
        this - value
    } else {
        this + value
    }
}

private fun View.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}
