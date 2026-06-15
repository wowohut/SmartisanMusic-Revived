package com.smartisanos.music.ui.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.icu.text.Transliterator
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.settings.PlaybackSettings
import com.smartisanos.music.data.library.LibraryExclusions
import com.smartisanos.music.data.library.LibraryExclusionsStore
import com.smartisanos.music.playback.LocalAudioLibrary
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.playback.replaceQueueAndPlayShuffled
import com.smartisanos.music.ui.components.hasAudioPermission
import com.smartisanos.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBarTransition
import com.smartisanos.music.ui.folder.DirectoryEntry
import com.smartisanos.music.ui.folder.buildDirectoryEntries
import com.smartisanos.music.ui.folder.filterDirectoryEntriesForDisplay
import com.smartisanos.music.ui.folder.filterMediaItemsForDirectory
import com.smartisanos.music.ui.folder.mediaIdsInDirectory
import com.smartisanos.music.ui.widgets.EditableLayout
import com.smartisanos.music.ui.widgets.StretchTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smartisanos.app.MenuDialog
import smartisanos.widget.ListContentItemText
import smartisanos.widget.TitleBar
import java.text.Normalizer
import java.util.Locale

private const val FolderStorageLabel = "Phone Storage"
private const val FolderHiddenAlpha = 0.3f
private const val FolderVisibilityAnimationMillis = 300L
private const val FolderEditTransitionMillis = 200L

private val FolderPrimaryTextColor = Color.rgb(0x35, 0x35, 0x39)
private val FolderSecondaryTextColor = Color.rgb(0xa4, 0xa7, 0xac)

private data class LegacyFolderTarget(
    val key: String,
    val title: String,
)

@Composable
internal fun LegacyPortFolderPage(
    active: Boolean,
    libraryRefreshVersion: Int,
    libraryRefreshing: Boolean,
    onClose: () -> Unit,
    closePredictiveBackState: LegacyPortPredictiveBackState? = null,
    onRefreshLibrary: () -> Unit,
    onMediaIdsHidden: (Set<String>) -> Unit,
    onRequestDeleteMediaIds: (Set<String>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browser = LocalPlaybackBrowser.current
    val audioLibrary = remember(context.applicationContext) {
        LocalAudioLibrary(context.applicationContext)
    }
    val exclusionsStore = remember(context.applicationContext) {
        LibraryExclusionsStore(context.applicationContext)
    }
    val directoryTitle = stringResource(R.string.tab_directory)
    val exclusions by exclusionsStore.exclusions.collectAsState(initial = LibraryExclusions())
    val hasPermission = remember(context) {
        hasAudioPermission(context)
    }
    var mediaItems by remember(audioLibrary) { mutableStateOf(emptyList<MediaItem>()) }
    var target by remember { mutableStateOf<LegacyFolderTarget?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var selectedDirectoryKeys by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val detailPredictiveBackState = rememberLegacyPortPredictiveBackState()

    LaunchedEffect(active, hasPermission, libraryRefreshVersion, audioLibrary) {
        if (!active || !hasPermission) {
            mediaItems = emptyList()
            return@LaunchedEffect
        }
        mediaItems = withContext(Dispatchers.IO) {
            audioLibrary.getAudioItems(forceRefresh = libraryRefreshVersion > 0)
        }
    }

    val allDirectories = remember(mediaItems, exclusions) {
        buildDirectoryEntries(
            mediaItems = mediaItems,
            exclusions = exclusions,
            storageLabel = FolderStorageLabel,
        )
    }

    LegacyPortPredictiveBackHandler(
        enabled = active && target != null,
        state = detailPredictiveBackState,
    ) {
        target = null
    }
    BackHandler(enabled = active && target == null && editMode) {
        editMode = false
        selectedDirectoryKeys = emptySet()
    }
    if (closePredictiveBackState != null) {
        LegacyPortPredictiveBackHandler(
            enabled = active && target == null && !editMode,
            state = closePredictiveBackState,
            onBack = onClose,
        )
    } else {
        BackHandler(enabled = active && target == null && !editMode) {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            dimensionResource(R.dimen.title_bar_height)
        val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
        val handleBack = {
            when {
                target != null -> target = null
                editMode -> {
                    editMode = false
                    selectedDirectoryKeys = emptySet()
                }
                else -> onClose()
            }
        }
        val enterEdit = {
            editMode = true
            selectedDirectoryKeys = emptySet()
        }
        val deleteSelected = {
            if (selectedDirectoryKeys.isNotEmpty()) {
                showDeleteConfirm = true
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            LegacyPortTitleBarTransition(
                secondaryKey = target,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleAreaHeight),
                label = "legacy folder title stack",
                predictiveBackProgress = detailPredictiveBackState.progress,
                predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                primaryContent = {
                    LegacyPortSmartisanTitleBar(
                        modifier = Modifier.fillMaxSize(),
                    ) { titleBar ->
                        titleBar.setupLegacyFolderTitleBar(
                            title = directoryTitle,
                            editMode = editMode,
                            selectedCount = selectedDirectoryKeys.size,
                            libraryRefreshing = libraryRefreshing,
                            onBack = handleBack,
                            onEnterEdit = enterEdit,
                            onDeleteSelected = deleteSelected,
                            onRefreshLibrary = onRefreshLibrary,
                        )
                    }
                },
                secondaryContent = { folderTarget ->
                    LegacyPortSmartisanTitleBar(
                        modifier = Modifier.fillMaxSize(),
                    ) { titleBar ->
                        titleBar.setupLegacyFolderTitleBar(
                            title = folderTarget.title,
                            editMode = false,
                            selectedCount = 0,
                            libraryRefreshing = libraryRefreshing,
                            showRightActions = false,
                            onBack = handleBack,
                            onEnterEdit = enterEdit,
                            onDeleteSelected = deleteSelected,
                            onRefreshLibrary = onRefreshLibrary,
                        )
                    }
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LegacyPortPageStackTransition(
                    secondaryKey = target,
                    modifier = Modifier.fillMaxSize(),
                    label = "legacy folder detail stack",
                    predictiveBackProgress = detailPredictiveBackState.progress,
                    predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                    onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                    primaryContent = {
                        LegacyFolderRootPage(
                            active = active,
                            directories = allDirectories,
                            editMode = editMode,
                            selectedDirectoryKeys = selectedDirectoryKeys,
                            onDirectoryClick = { entry ->
                                if (editMode) {
                                    selectedDirectoryKeys = selectedDirectoryKeys.toggle(entry.key)
                                } else {
                                    target = LegacyFolderTarget(
                                        key = entry.key,
                                        title = entry.name,
                                    )
                                }
                            },
                            onDirectorySelectionChange = { directoryKey, selected ->
                                selectedDirectoryKeys = selectedDirectoryKeys.withSelection(directoryKey, selected)
                            },
                            onDirectoryVisibilityChange = { entry, hidden ->
                                val affectedMediaIds = if (hidden) {
                                    mediaIdsInDirectory(mediaItems = mediaItems, directoryKey = entry.key)
                                } else {
                                    emptySet()
                                }
                                scope.launch {
                                    exclusionsStore.setDirectoryKeysHidden(
                                        directoryKeys = setOf(entry.key),
                                        hidden = hidden,
                                    )
                                    if (affectedMediaIds.isNotEmpty()) {
                                        onMediaIdsHidden(affectedMediaIds)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    secondaryContent = { folderTarget ->
                        val songs = remember(mediaItems, exclusions, folderTarget.key) {
                            filterMediaItemsForDirectory(
                                mediaItems = mediaItems,
                                directoryKey = folderTarget.key,
                                exclusions = exclusions,
                            ).sortedForFolder()
                        }
                        LegacyFolderDetailPage(
                            active = active && target == folderTarget,
                            tracks = songs,
                            browser = browser,
                            onTrackMoreClick = onTrackMoreClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
        LegacyPortTitleBarShadow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = titleAreaHeight)
                .fillMaxWidth()
                .height(titleShadowHeight)
                .zIndex(1f),
        )
    }

    LegacyFolderDeleteDialog(
        visible = showDeleteConfirm,
        onDismiss = {
            showDeleteConfirm = false
        },
        onConfirm = {
            val keys = selectedDirectoryKeys
            if (keys.isEmpty()) {
                showDeleteConfirm = false
                return@LegacyFolderDeleteDialog
            }
            val affectedMediaIds = keys.flatMap { key ->
                mediaIdsInDirectory(mediaItems = mediaItems, directoryKey = key)
            }.toSet()
            onRequestDeleteMediaIds(affectedMediaIds)
            selectedDirectoryKeys = emptySet()
            editMode = false
            showDeleteConfirm = false
        },
    )
}

private fun TitleBar.setupLegacyFolderTitleBar(
    title: String,
    editMode: Boolean,
    selectedCount: Int,
    libraryRefreshing: Boolean,
    showRightActions: Boolean = true,
    onBack: () -> Unit,
    onEnterEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    setCenterText(title)

    when {
        editMode -> {
            addLeftImageView(R.drawable.standard_icon_cancel_selector).apply {
                setOnClickListener {
                    onBack()
                }
            }
            addRightImageView(R.drawable.titlebar_btn_delete_selector).apply {
                isEnabled = selectedCount > 0
                setOnClickListener {
                    if (selectedCount > 0) {
                        onDeleteSelected()
                    }
                }
            }
        }
        else -> {
            addLeftImageView(R.drawable.standard_icon_back_selector).apply {
                setOnClickListener {
                    onBack()
                }
            }
            if (showRightActions) {
                addRightImageView(R.drawable.standard_icon_multi_select_selector, 0).apply {
                    setOnClickListener {
                        onEnterEdit()
                    }
                }
                addRightImageView(R.drawable.standard_icon_refresh_selector, 1).apply {
                    isEnabled = !libraryRefreshing
                    contentDescription = context.getString(R.string.library_rescan_full)
                    setOnClickListener {
                        if (!libraryRefreshing) {
                            onRefreshLibrary()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyFolderRootPage(
    active: Boolean,
    directories: List<DirectoryEntry>,
    editMode: Boolean,
    selectedDirectoryKeys: Set<String>,
    onDirectoryClick: (DirectoryEntry) -> Unit,
    onDirectorySelectionChange: (String, Boolean) -> Unit,
    onDirectoryVisibilityChange: (DirectoryEntry, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyFolderRootView(context)
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            root.bind(
                directories = directories,
                editMode = editMode,
                selectedDirectoryKeys = selectedDirectoryKeys,
                onDirectoryClick = onDirectoryClick,
                onDirectorySelectionChange = onDirectorySelectionChange,
                onDirectoryVisibilityChange = onDirectoryVisibilityChange,
            )
        },
    )
}

private class LegacyFolderRootView(context: Context) : FrameLayout(context) {
    private val listView = ListView(context)
    private val directoryAdapter = LegacyFolderDirectoryAdapter()
    private val slideSelectionController = listView.legacySlideSelectionController(
        startArea = LegacySlideSelectionStartArea.Checkbox,
    )
    private var boundEditMode: Boolean? = null
    private var boundDirectories: List<DirectoryEntry>? = null
    private var boundSelectedDirectoryKeys: Set<String> = emptySet()
    private var onDirectoryClick: (DirectoryEntry) -> Unit = {}
    private var onDirectorySelectionChange: (String, Boolean) -> Unit = { _, _ -> }
    private var onDirectoryVisibilityChange: (DirectoryEntry, Boolean) -> Unit = { _, _ -> }
    private var hiddenRowsExpanded = false
    private var pendingHiddenRowsMode: Boolean? = null
    private var pendingHiddenRowsRunnable: Runnable? = null
    private val blankView = LegacyFolderBlankView(
        context = context,
        iconRes = R.drawable.blank_folder,
        primaryText = context.getString(R.string.no_folder),
        secondaryText = context.getString(R.string.show_folder),
    )
    init {
        setBackgroundResource(R.drawable.account_background)
        listView.apply {
            divider = null
            dividerHeight = 0
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            adapter = directoryAdapter
            setOnTouchListener { _, event ->
                slideSelectionController.handleTouch(event)
            }
            setOnItemClickListener { _, _, position, _ ->
                val entry = directoryAdapter.itemAt(position) ?: return@setOnItemClickListener
                onDirectoryClick(entry)
            }
        }
        addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(blankView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(
        directories: List<DirectoryEntry>,
        editMode: Boolean,
        selectedDirectoryKeys: Set<String>,
        onDirectoryClick: (DirectoryEntry) -> Unit,
        onDirectorySelectionChange: (String, Boolean) -> Unit,
        onDirectoryVisibilityChange: (DirectoryEntry, Boolean) -> Unit,
    ) {
        this.onDirectoryClick = onDirectoryClick
        this.onDirectorySelectionChange = onDirectorySelectionChange
        this.onDirectoryVisibilityChange = onDirectoryVisibilityChange
        directoryAdapter.updateVisibilityChange(this.onDirectoryVisibilityChange)
        if (
            boundDirectories === directories &&
            boundEditMode == editMode &&
            boundSelectedDirectoryKeys == selectedDirectoryKeys
        ) {
            return
        }

        val visibleDirectories = filterDirectoryEntriesForDisplay(directories, editMode = false)
        val previousEditMode = boundEditMode
        val firstBind = previousEditMode == null
        val animateEditMode = previousEditMode != null && previousEditMode != editMode
        val displayDirectories = filterDirectoryEntriesForDisplay(directories, editMode = true)
        val displayCount = if (editMode) displayDirectories.size else visibleDirectories.size
        blankView.visibility = if (displayCount == 0) View.VISIBLE else View.GONE
        listView.visibility = if (displayCount == 0) View.INVISIBLE else View.VISIBLE

        boundEditMode = editMode
        if (firstBind) {
            hiddenRowsExpanded = editMode
        } else if (!animateEditMode && pendingHiddenRowsMode == null) {
            hiddenRowsExpanded = editMode
        }
        boundDirectories = directories
        boundSelectedDirectoryKeys = selectedDirectoryKeys
        val contentChanged = directoryAdapter.updateItems(
            nextItems = displayDirectories,
            nextEditMode = editMode,
            nextHiddenRowsExpanded = hiddenRowsExpanded,
            nextSelectedDirectoryKeys = selectedDirectoryKeys,
            nextVisibilityChange = this.onDirectoryVisibilityChange,
        )
        directoryAdapter.updateVisibleRows(
            listView = listView,
            animateEditMode = animateEditMode && !contentChanged,
            animateHeight = false,
        )
        if (animateEditMode) {
            scheduleHiddenRowsTransition(editMode)
        }
        slideSelectionController.update(
            enabled = editMode,
            selectedKeys = selectedDirectoryKeys,
            keyAtPosition = { position ->
                directoryAdapter.itemAt(position)?.key
            },
            onSelectionChange = { directoryKey, selected ->
                this.onDirectorySelectionChange(directoryKey, selected)
            },
        )
    }

    private fun scheduleHiddenRowsTransition(expanded: Boolean) {
        pendingHiddenRowsRunnable?.let(listView::removeCallbacks)
        pendingHiddenRowsMode = expanded
        val startHeightPhase = Runnable {
            if (pendingHiddenRowsMode != expanded) {
                return@Runnable
            }
            hiddenRowsExpanded = expanded
            directoryAdapter.setHiddenRowsExpanded(expanded)
            directoryAdapter.updateVisibleRows(
                listView = listView,
                animateEditMode = false,
                animateHeight = true,
            )
            listView.postDelayed(
                {
                    if (pendingHiddenRowsMode == expanded) {
                        pendingHiddenRowsMode = null
                        directoryAdapter.updateVisibleRows(
                            listView = listView,
                            animateEditMode = false,
                            animateHeight = false,
                        )
                    }
                },
                FolderEditTransitionMillis,
            )
        }
        pendingHiddenRowsRunnable = startHeightPhase
        listView.postDelayed(startHeightPhase, FolderEditTransitionMillis)
    }
}

private class LegacyFolderDirectoryAdapter : BaseAdapter() {
    private var items: List<DirectoryEntry> = emptyList()
    private var editMode = false
    private var hiddenRowsExpanded = false
    private var selectedDirectoryKeys: Set<String> = emptySet()
    private var onVisibilityChange: (DirectoryEntry, Boolean) -> Unit = { _, _ -> }

    fun updateVisibilityChange(nextVisibilityChange: (DirectoryEntry, Boolean) -> Unit) {
        onVisibilityChange = nextVisibilityChange
    }

    fun updateItems(
        nextItems: List<DirectoryEntry>,
        nextEditMode: Boolean,
        nextHiddenRowsExpanded: Boolean,
        nextSelectedDirectoryKeys: Set<String>,
        nextVisibilityChange: (DirectoryEntry, Boolean) -> Unit,
    ): Boolean {
        val keysChanged = items.map(DirectoryEntry::key) != nextItems.map(DirectoryEntry::key)
        val totalCountChanged = items.map(DirectoryEntry::totalCount) != nextItems.map(DirectoryEntry::totalCount)
        val visibleCountChanged = items.map(DirectoryEntry::visibleCount) != nextItems.map(DirectoryEntry::visibleCount)
        val contentChanged = keysChanged ||
            totalCountChanged ||
            (!editMode && !nextEditMode && visibleCountChanged)
        val stateChanged = editMode != nextEditMode ||
            hiddenRowsExpanded != nextHiddenRowsExpanded ||
            selectedDirectoryKeys != nextSelectedDirectoryKeys ||
            items.map(DirectoryEntry::hidden) != nextItems.map(DirectoryEntry::hidden)
        updateVisibilityChange(nextVisibilityChange)
        if (!contentChanged && !stateChanged) {
            return false
        }
        items = nextItems
        editMode = nextEditMode
        hiddenRowsExpanded = nextHiddenRowsExpanded
        selectedDirectoryKeys = nextSelectedDirectoryKeys
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun setHiddenRowsExpanded(expanded: Boolean) {
        hiddenRowsExpanded = expanded
    }

    fun updateVisibleRows(
        listView: ListView,
        animateEditMode: Boolean,
        animateHeight: Boolean,
    ) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex
            val entry = itemAt(position)
            val child = listView.getChildAt(childIndex)
            if (entry != null && child != null) {
                bindRow(
                    view = child,
                    entry = entry,
                    position = position,
                    animateEditMode = animateEditMode,
                    animateHeight = animateHeight,
                )
            }
        }
    }

    fun itemAt(position: Int): DirectoryEntry? = items.getOrNull(position)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dir_listview, parent, false)
        val entry = items[position]
        bindRow(
            view = view,
            entry = entry,
            position = position,
            animateEditMode = false,
            animateHeight = false,
        )
        return view
    }

    private fun bindRow(
        view: View,
        entry: DirectoryEntry,
        position: Int,
        animateEditMode: Boolean,
        animateHeight: Boolean,
    ) {
        val context = view.context
        val selected = entry.key in selectedDirectoryKeys
        val count = if (editMode || hiddenRowsExpanded || animateEditMode) {
            entry.totalCount
        } else {
            entry.visibleCount
        }
        val trackCount = context.resources.getQuantityString(
            R.plurals.album_track_count,
            count,
            count,
        )
        val bright = view.findViewById<View>(R.id.ll_bright)
        val arrow = view.findViewById<View>(R.id.arrow)
        val eye = view.findViewById<ImageView>(R.id.iv_right_view)
        bindDirectoryRowHeight(
            view = view,
            hidden = entry.hidden,
            expanded = hiddenRowsExpanded,
            animateHeight = animateHeight,
        )

        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = entry.name
            setTextColor(FolderPrimaryTextColor)
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            text = "$trackCount  ${entry.displayPath}"
            setTextColor(FolderSecondaryTextColor)
        }
        view.findViewById<View>(R.id.folder_row_divider)?.visibility =
            if (hasFollowingExpandedRow(position)) View.VISIBLE else View.GONE
        if (!animateEditMode) {
            arrow?.visibility = if (editMode) View.GONE else View.VISIBLE
            arrow?.alpha = if (editMode) 0f else 1f
        }
        eye?.apply {
            if (!animateEditMode) {
                visibility = if (editMode) View.VISIBLE else View.GONE
                alpha = if (editMode) 1f else 0f
            }
            isEnabled = true
            setImageResource(if (entry.hidden) R.drawable.eye_icon_0016 else R.drawable.eye_icon_0001)
            setOnClickListener {
                val shouldHide = !entry.hidden
                Toast.makeText(
                    context.applicationContext,
                    context.getString(if (shouldHide) R.string.hiden_dir else R.string.shown_dir),
                    Toast.LENGTH_SHORT,
                ).show()
                playFolderEyeTransition(
                    bright = bright,
                    eye = this,
                    hide = shouldHide,
                )
                postDelayed(
                    {
                        onVisibilityChange(entry, shouldHide)
                    },
                    FolderVisibilityAnimationMillis,
                )
            }
        }
        bright?.alpha = if ((editMode || hiddenRowsExpanded || animateEditMode) && entry.hidden) {
            FolderHiddenAlpha
        } else {
            1f
        }
        (view as? EditableLayout)?.bindLegacyEditState(
            enabled = editMode,
            checked = selected,
            animate = animateEditMode,
        )
    }

    private fun hasFollowingExpandedRow(position: Int): Boolean {
        return items.asSequence()
            .drop(position + 1)
            .any { entry -> editMode || hiddenRowsExpanded || !entry.hidden }
    }
}

private fun bindDirectoryRowHeight(
    view: View,
    hidden: Boolean,
    expanded: Boolean,
    animateHeight: Boolean,
) {
    (view.getTag(R.id.legacy_folder_row_height_animator) as? Animator)?.let { animator ->
        view.setTag(R.id.legacy_folder_row_height_animator, null)
        animator.cancel()
    }
    val rowHeight = view.resources.getDimensionPixelSize(R.dimen.listview_item_height)
    val targetCollapsed = !expanded && hidden
    val targetHeight = if (targetCollapsed) 0 else rowHeight
    view.minimumHeight = 0
    view.visibility = View.VISIBLE
    val params = (view.layoutParams as? AbsListView.LayoutParams)
        ?: AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeight)
    val currentHeight = params.height.takeIf { it >= 0 } ?: targetHeight
    if (!animateHeight || !hidden || currentHeight == targetHeight) {
        view.setTag(R.id.legacy_folder_row_force_zero_height, targetCollapsed)
        params.height = targetHeight
        view.layoutParams = params
        return
    }
    view.setTag(R.id.legacy_folder_row_force_zero_height, false)

    val heightAnimator = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
        duration = FolderEditTransitionMillis
        addUpdateListener { animator ->
            if (view.getTag(R.id.legacy_folder_row_height_animator) !== animator) {
                return@addUpdateListener
            }
            params.height = animator.animatedValue as Int
            view.layoutParams = params
        }
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (view.getTag(R.id.legacy_folder_row_height_animator) !== animation) {
                        return
                    }
                    params.height = targetHeight
                    view.layoutParams = params
                    view.setTag(R.id.legacy_folder_row_force_zero_height, targetCollapsed)
                    view.setTag(R.id.legacy_folder_row_height_animator, null)
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (view.getTag(R.id.legacy_folder_row_height_animator) !== animation) {
                        return
                    }
                    params.height = targetHeight
                    view.layoutParams = params
                    view.setTag(R.id.legacy_folder_row_force_zero_height, targetCollapsed)
                    view.setTag(R.id.legacy_folder_row_height_animator, null)
                }
            },
        )
    }
    view.setTag(R.id.legacy_folder_row_height_animator, heightAnimator)
    heightAnimator.start()
}

private fun playFolderEyeTransition(
    bright: View?,
    eye: ImageView,
    hide: Boolean,
) {
    eye.isEnabled = false
    eye.setImageResource(if (hide) R.drawable.eye_close_anim else R.drawable.eye_open_anim)
    (eye.drawable as? AnimationDrawable)?.let { drawable ->
        drawable.stop()
        drawable.start()
    }
    ObjectAnimator.ofFloat(
        bright,
        View.ALPHA,
        bright?.alpha ?: if (hide) 1f else FolderHiddenAlpha,
        if (hide) FolderHiddenAlpha else 1f,
    ).apply {
        duration = FolderVisibilityAnimationMillis
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bright?.alpha = if (hide) FolderHiddenAlpha else 1f
                    eye.setImageResource(if (hide) R.drawable.eye_icon_0016 else R.drawable.eye_icon_0001)
                    eye.isEnabled = true
                }

                override fun onAnimationCancel(animation: Animator) {
                    eye.isEnabled = true
                }
            },
        )
        start()
    }
}

@Composable
private fun LegacyFolderDetailPage(
    active: Boolean,
    tracks: List<MediaItem>,
    browser: Player?,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyFolderDetailRootView(context)
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            root.bind(
                tracks = tracks,
                currentMediaId = browser?.currentMediaItem?.mediaId,
                currentIsPlaying = browser?.isPlaying == true,
                onPlayAll = {
                    if (tracks.isNotEmpty()) {
                        browser.replaceQueueAndPlay(tracks)
                    }
                },
                onShuffle = {
                    if (tracks.isNotEmpty()) {
                        browser.replaceQueueAndPlayShuffled(tracks)
                    }
                },
                onTrackClick = { item, index ->
                    browser.replaceQueueAndPlay(tracks, index)
                },
                onTrackMoreClick = onTrackMoreClick,
            )
            root.bindPlayback(browser)
        },
    )
}

private class LegacyFolderDetailRootView(context: Context) : LinearLayout(context) {
    private val listView = ListView(context)
    private val blankView = LegacyFolderBlankView(
        context = context,
        iconRes = R.drawable.blank_song,
        primaryText = context.getString(R.string.no_song),
        secondaryText = context.getString(R.string.show_song),
    )
    private var boundTracks: List<MediaItem>? = null
    private var boundCurrentMediaId: String? = null
    private var boundCurrentIsPlaying: Boolean = false
    private var onPlayAll: () -> Unit = {}
    private var onShuffle: () -> Unit = {}
    private var onTrackClick: (MediaItem, Int) -> Unit = { _, _ -> }
    private var onTrackMoreClick: (MediaItem) -> Unit = {}

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.account_background)
        LayoutInflater.from(context).inflate(R.layout.layout_play_container, this, true)
        findViewById<View>(R.id.bt_play)?.setOnClickListener {
            onPlayAll()
        }
        findViewById<View>(R.id.bt_shuffle)?.setOnClickListener {
            onShuffle()
        }
        addView(
            View(context).apply {
                setBackgroundColor(context.getColor(R.color.listview_divider_color))
            },
            LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)),
        )
        val listFrame = FrameLayout(context)
        addView(listFrame, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        listView.apply {
            divider = ColorDrawable(context.getColor(R.color.listview_divider_color))
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundResource(R.drawable.account_background)
            isVerticalScrollBarEnabled = false
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.list_anim_layout)
            addLegacyPortListFooter()
            setOnItemClickListener { _, _, position, _ ->
                val adapter = legacyWrappedAdapter<LegacyFolderTrackAdapter>() ?: return@setOnItemClickListener
                val item = adapter.itemAt(position) ?: return@setOnItemClickListener
                onTrackClick(item, position)
            }
        }
        listFrame.addView(listView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        listFrame.addView(blankView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(
        tracks: List<MediaItem>,
        currentMediaId: String?,
        currentIsPlaying: Boolean,
        onPlayAll: () -> Unit,
        onShuffle: () -> Unit,
        onTrackClick: (MediaItem, Int) -> Unit,
        onTrackMoreClick: (MediaItem) -> Unit,
    ) {
        this.onPlayAll = onPlayAll
        this.onShuffle = onShuffle
        this.onTrackClick = onTrackClick
        this.onTrackMoreClick = onTrackMoreClick
        val sameTracks = boundTracks === tracks
        val samePlayback = boundCurrentMediaId == currentMediaId &&
            boundCurrentIsPlaying == currentIsPlaying
        if (sameTracks && samePlayback) {
            return
        }
        boundTracks = tracks
        boundCurrentMediaId = currentMediaId
        boundCurrentIsPlaying = currentIsPlaying

        findViewById<View>(R.id.bt_play)?.apply {
            isEnabled = tracks.isNotEmpty()
        }
        findViewById<View>(R.id.bt_shuffle)?.apply {
            isEnabled = tracks.isNotEmpty()
        }
        blankView.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (tracks.isEmpty()) View.INVISIBLE else View.VISIBLE
        listView.bindLegacyPortListFooter(
            pluralsRes = R.plurals.track_count,
            count = tracks.size,
        )
        val adapter = listView.legacyWrappedAdapter<LegacyFolderTrackAdapter>()
            ?: LegacyFolderTrackAdapter().also { adapter ->
                listView.adapter = adapter
            }
        adapter.onMoreClick = this.onTrackMoreClick
        val changed = adapter.updateItems(
            nextItems = tracks,
            nextCurrentMediaId = currentMediaId,
            nextCurrentIsPlaying = currentIsPlaying,
        )
        if (changed) {
            listView.scheduleLayoutAnimation()
        } else {
            adapter.updateVisiblePlaybackState(listView)
        }
    }

    fun bindPlayback(player: Player?) {
        val adapter = listView.legacyWrappedAdapter<LegacyFolderTrackAdapter>() ?: return
        if (listView.getTag(R.id.list) !== player) {
            (listView.getTag(R.id.text) as? Player.Listener)?.let { oldListener ->
                (listView.getTag(R.id.list) as? Player)?.removeListener(oldListener)
            }
            if (player != null) {
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        adapter.setPlaybackState(
                            nextCurrentMediaId = player.currentMediaItem?.mediaId,
                            nextCurrentIsPlaying = player.isPlaying,
                        )
                        adapter.updateVisiblePlaybackState(listView)
                    }
                }
                player.addListener(listener)
                listView.setTag(R.id.text, listener)
            } else {
                listView.setTag(R.id.text, null)
            }
            listView.setTag(R.id.list, player)
        }
    }

    override fun onDetachedFromWindow() {
        (listView.getTag(R.id.text) as? Player.Listener)?.let { oldListener ->
            (listView.getTag(R.id.list) as? Player)?.removeListener(oldListener)
        }
        listView.setTag(R.id.text, null)
        listView.setTag(R.id.list, null)
        super.onDetachedFromWindow()
    }
}

private class LegacyFolderTrackAdapter : BaseAdapter() {
    private var items: List<MediaItem> = emptyList()
    private var currentMediaId: String? = null
    private var currentIsPlaying: Boolean = false
    var onMoreClick: (MediaItem) -> Unit = {}

    fun updateItems(
        nextItems: List<MediaItem>,
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
    ): Boolean {
        val contentChanged = items != nextItems
        val playbackChanged = currentMediaId != nextCurrentMediaId ||
            currentIsPlaying != nextCurrentIsPlaying
        if (!contentChanged && !playbackChanged) {
            return false
        }
        items = nextItems
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun setPlaybackState(
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
    ) {
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
    }

    fun updateVisiblePlaybackState(listView: ListView) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex
            val item = itemAt(position)
            val child = listView.getChildAt(childIndex)
            val titleView = child?.findViewById<TextView>(R.id.listview_item_line_one) as? StretchTextView
            if (item != null && titleView != null) {
                bindPlaybackState(titleView, item)
            }
        }
    }

    fun itemAt(position: Int): MediaItem? = items.getOrNull(position)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track_list, parent, false)
        val item = items[position]
        val metadata = item.mediaMetadata
        val title = metadata.displayTitle?.toString()
            ?: metadata.title?.toString()
            ?: parent.context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()
            ?: metadata.subtitle?.toString()
            ?: parent.context.getString(R.string.unknown_artist)

        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = title
            isSelected = true
            setTextColor(FolderPrimaryTextColor)
            if (this is StretchTextView) {
                bindPlaybackState(this, item)
            }
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            text = artist
            setTextColor(FolderSecondaryTextColor)
        }
        view.findViewById<TextView>(R.id.tv_duration)?.apply {
            visibility = View.VISIBLE
            text = metadata.durationMs?.formatFolderDuration().orEmpty()
        }
        view.findViewById<ImageView>(R.id.mime_type)?.apply {
            val badge = item.folderQualityBadgeRes()
            if (badge != null) {
                visibility = View.VISIBLE
                setImageResource(badge)
            } else {
                visibility = View.GONE
            }
        }
        view.findViewById<CheckBox>(R.id.cb_del)?.visibility = View.GONE
        view.findViewById<ImageView>(R.id.iv_right)?.visibility = View.GONE
        view.findViewById<ImageView>(R.id.img_action_more)?.apply {
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = false
            setOnClickListener {
                onMoreClick(item)
            }
        }
        return view
    }

    private fun bindPlaybackState(titleView: StretchTextView, item: MediaItem) {
        if (item.mediaId == currentMediaId) {
            titleView.c(currentIsPlaying)
        } else {
            titleView.setShowingPlayImage(false)
        }
    }
}

@Composable
private fun LegacyFolderDeleteDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val latestOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val latestOnConfirm by androidx.compose.runtime.rememberUpdatedState(onConfirm)
    androidx.compose.runtime.DisposableEffect(visible) {
        if (!visible) {
            return@DisposableEffect onDispose { }
        }
        val dialog = MenuDialog(context).apply {
            setTitle(R.string.dialog_remove_song_by_dor)
            setPositiveButton(R.string.dialog_delete_conform) {
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

private class LegacyFolderBlankView(
    context: Context,
    iconRes: Int,
    primaryText: String,
    secondaryText: String,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.account_background)
        addView(
            ImageView(context).apply {
                setImageResource(iconRes)
                alpha = 0.42f
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            },
        )
        addView(
            TextView(context).apply {
                text = primaryText
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(0xc8, 0xc8, 0xc8))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
                includeFontPadding = false
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )
        if (secondaryText.isNotBlank()) {
            addView(
                TextView(context).apply {
                    text = secondaryText
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(0xc8, 0xc8, 0xc8))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    includeFontPadding = false
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(10)
                },
            )
        }
    }
}

private fun List<MediaItem>.sortedForFolder(): List<MediaItem> {
    return sortedWith(
        compareBy<MediaItem> { item ->
            item.folderSortBucket()
        }.thenBy { item ->
            item.folderSortKey()
        },
    )
}

private fun MediaItem.folderSortTitle(): String {
    return mediaMetadata.displayTitle?.toString()
        ?: mediaMetadata.title?.toString()
        ?: ""
}

private fun MediaItem.folderSortKey(): String {
    return FolderTitleNormalizer.normalize(folderSortTitle())
}

private fun MediaItem.folderSortBucket(): String {
    val letter = folderSectionLetter()
    return if (letter == "#") "ZZZ" else letter
}

private fun MediaItem.folderSectionLetter(): String {
    val firstLetter = folderSortKey().firstOrNull { char ->
        char.isLetterOrDigit()
    } ?: return "#"
    val upper = firstLetter.uppercaseChar()
    return if (upper in 'A'..'Z') upper.toString() else "#"
}

private object FolderTitleNormalizer {
    private val hanToLatin = runCatching {
        Transliterator.getInstance("Han-Latin; Latin-ASCII")
    }.getOrNull()
    private val combiningMarks = "\\p{Mn}+".toRegex()

    fun normalize(title: String): String {
        val trimmed = title.trim()
        val transliterated = hanToLatin?.transliterate(trimmed) ?: trimmed
        return Normalizer.normalize(transliterated, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}

private fun MediaItem.folderQualityBadgeRes(): Int? {
    return when (mediaMetadata.extras?.getString(LocalAudioLibrary.AudioQualityBadgeExtraKey)) {
        "flac" -> R.drawable.audio_quality_flac
        "ape" -> R.drawable.audio_quality_ape
        "wav" -> R.drawable.audio_quality_wav
        "aiff" -> R.drawable.audio_quality_aiff
        "alac" -> R.drawable.audio_quality_alac
        "cue" -> R.drawable.audio_quality_cue
        else -> null
    }
}

private fun Long.formatFolderDuration(): String {
    if (this <= 0L) {
        return ""
    }
    val totalSeconds = this / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (value in this) this - value else this + value
}

private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
