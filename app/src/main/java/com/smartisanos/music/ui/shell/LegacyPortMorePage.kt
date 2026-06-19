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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.favorite.FavoriteSongRecord
import com.smartisanos.music.data.settings.ArtistSettings
import com.smartisanos.music.data.settings.AudioFxPreset
import com.smartisanos.music.data.settings.NeteaseAudioQuality
import com.smartisanos.music.data.settings.NavigationSettings
import com.smartisanos.music.data.settings.OnlineMusicSettings
import com.smartisanos.music.data.settings.PlaybackSettings
import com.smartisanos.music.data.library.LibraryExclusions
import com.smartisanos.music.data.library.LibraryExclusionsStore
import com.smartisanos.music.playback.LocalAudioLibrary
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.ui.components.hasAudioPermission
import com.smartisanos.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisanos.music.ui.folder.DirectoryEntry
import com.smartisanos.music.ui.folder.buildDirectoryEntries
import com.smartisanos.music.ui.folder.filterDirectoryEntriesForDisplay
import com.smartisanos.music.ui.folder.filterMediaItemsForDirectory
import com.smartisanos.music.ui.folder.mediaIdsInDirectory
import com.smartisanos.music.ui.shell.loved.LegacyPortLovedSongsPage
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

private enum class LegacyMoreSecondaryTarget {
    LovedSongs,
    Folder,
    Settings,
}

private enum class LegacyMoreRootEntry(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    LovedSongs(
        labelRes = R.string.collect_music,
        iconRes = R.drawable.tabbar_like,
    ),
    Folder(
        labelRes = R.string.tab_directory,
        iconRes = R.drawable.tabbar_folder,
    ),
}

@Composable
internal fun LegacyPortMorePage(
    active: Boolean,
    mediaItems: List<MediaItem>,
    favoriteRecords: List<FavoriteSongRecord>,
    hiddenMediaIds: Set<String>,
    playbackSettings: PlaybackSettings,
    onlineMusicSettings: OnlineMusicSettings,
    artistSettings: ArtistSettings,
    libraryLoaded: Boolean,
    libraryRefreshVersion: Int,
    libraryRefreshing: Boolean,
    onRefreshLibrary: () -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onNeteasePlaybackQualityChange: (NeteaseAudioQuality) -> Unit,
    onNeteaseAuthChanged: () -> Unit,
    navigationSettings: NavigationSettings,
    onTabVisibilityChange: (String, Boolean) -> Unit,
    onMediaIdsHidden: (Set<String>) -> Unit,
    onRequestDeleteMediaIds: (Set<String>) -> Unit,
    onLovedSongsTrackMoreClick: (MediaItem) -> Unit,
    onFolderTrackMoreClick: (MediaItem) -> Unit,
    onRemoveFavoriteMediaIds: (Set<String>) -> Unit,
    onSettingsPageActiveChanged: (Boolean) -> Unit,
    onLibraryNeeded: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var secondaryTarget by remember { mutableStateOf<LegacyMoreSecondaryTarget?>(null) }
    val secondaryPredictiveBackState = rememberLegacyPortPredictiveBackState()

    LaunchedEffect(active, secondaryTarget) {
        when {
            active && secondaryTarget == LegacyMoreSecondaryTarget.LovedSongs -> {
                onLibraryNeeded()
                onSettingsPageActiveChanged(false)
            }
            active && secondaryTarget == LegacyMoreSecondaryTarget.Settings -> onSettingsPageActiveChanged(true)
            secondaryTarget == null -> {
                delay(LegacyPageStackSlideMillis.toLong())
                onSettingsPageActiveChanged(false)
            }
            else -> onSettingsPageActiveChanged(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onSettingsPageActiveChanged(false)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LegacyPortPageStackTransition(
            secondaryKey = secondaryTarget,
            modifier = Modifier.fillMaxSize(),
            label = "legacy more page stack",
            axisForKey = { target ->
                when (target) {
                    LegacyMoreSecondaryTarget.LovedSongs -> LegacyPortPageStackAxis.Horizontal
                    LegacyMoreSecondaryTarget.Folder -> LegacyPortPageStackAxis.Horizontal
                    LegacyMoreSecondaryTarget.Settings -> LegacyPortPageStackAxis.VerticalPush
                }
            },
            predictiveBackProgress = secondaryPredictiveBackState.progress,
            predictiveBackExitConsumed = secondaryPredictiveBackState.exitConsumed,
            onPredictiveBackExitConsumedReset = secondaryPredictiveBackState::reset,
            primaryContent = {
                LegacyMoreRootPage(
                    active = active,
                    onSettingsClick = {
                        onSettingsPageActiveChanged(true)
                        secondaryTarget = LegacyMoreSecondaryTarget.Settings
                    },
                    onFolderClick = {
                        secondaryTarget = LegacyMoreSecondaryTarget.Folder
                    },
                    onLovedSongsClick = {
                        onLibraryNeeded()
                        secondaryTarget = LegacyMoreSecondaryTarget.LovedSongs
                    },
                    onSearchClick = onSearchClick,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            secondaryContent = { target ->
                when (target) {
                    LegacyMoreSecondaryTarget.LovedSongs -> LegacyPortLovedSongsPage(
                        active = active,
                        mediaItems = mediaItems,
                        favoriteRecords = favoriteRecords,
                        hiddenMediaIds = hiddenMediaIds,
                        libraryLoaded = libraryLoaded,
                        onClose = {
                            secondaryTarget = null
                        },
                        closePredictiveBackState = secondaryPredictiveBackState,
                        onTrackMoreClick = onLovedSongsTrackMoreClick,
                        onRemoveFavoriteMediaIds = onRemoveFavoriteMediaIds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LegacyMoreSecondaryTarget.Folder -> LegacyPortFolderPage(
                        active = active,
                        libraryRefreshVersion = libraryRefreshVersion,
                        libraryRefreshing = libraryRefreshing,
                        onClose = {
                            secondaryTarget = null
                        },
                        closePredictiveBackState = secondaryPredictiveBackState,
                        onRefreshLibrary = onRefreshLibrary,
                        onMediaIdsHidden = onMediaIdsHidden,
                        onRequestDeleteMediaIds = onRequestDeleteMediaIds,
                        onTrackMoreClick = onFolderTrackMoreClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LegacyMoreSecondaryTarget.Settings -> LegacyPortSettingsPage(
                        active = active,
                        playbackSettings = playbackSettings,
                        artistSettings = artistSettings,
                        onlineMusicSettings = onlineMusicSettings,
                        navigationSettings = navigationSettings,
                        onClose = {
                            secondaryTarget = null
                        },
                        onScratchEnabledChange = onScratchEnabledChange,
                        onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                        onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                        onAudioFxEnabledChange = onAudioFxEnabledChange,
                        onAudioFxPresetChange = onAudioFxPresetChange,
                        onAudioFxCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
                        onArtistSeparatorsChange = onArtistSeparatorsChange,
                        onNeteasePlaybackQualityChange = onNeteasePlaybackQualityChange,
                        onNeteaseAuthChanged = onNeteaseAuthChanged,
                        onTabVisibilityChange = onTabVisibilityChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }
}

@Composable
private fun LegacyMoreRootPage(
    active: Boolean,
    onSettingsClick: () -> Unit,
    onFolderClick: () -> Unit,
    onLovedSongsClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        LegacyPortSmartisanTitleBar(
            modifier = Modifier.fillMaxWidth(),
            showShadow = true,
        ) { titleBar ->
            titleBar.setupLegacyMoreRootTitleBar(
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick,
            )
        }
        LegacyMoreRootList(
            active = active,
            onFolderClick = onFolderClick,
            onLovedSongsClick = onLovedSongsClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

private fun TitleBar.setupLegacyMoreRootTitleBar(
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    setCenterText(R.string.tab_more)
    addLeftImageView(R.drawable.standard_icon_settings_selector).apply {
        setOnClickListener {
            onSettingsClick()
        }
    }
    addRightImageView(R.drawable.search_btn_selector).apply {
        setOnClickListener {
            onSearchClick()
        }
    }
}

@Composable
private fun LegacyMoreRootList(
    active: Boolean,
    onFolderClick: () -> Unit,
    onLovedSongsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            LayoutInflater.from(viewContext).inflate(R.layout.more_fragment_layout, null, false).apply {
                findViewById<ListView>(R.id.list)?.apply {
                    divider = null
                    cacheColorHint = Color.TRANSPARENT
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutAnimation = AnimationUtils.loadLayoutAnimation(viewContext, R.anim.list_anim_layout)
                }
            }
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            val listView = root.findViewById<ListView>(R.id.list) ?: return@AndroidView
            val adapter = listView.adapter as? LegacyMoreRootAdapter ?: LegacyMoreRootAdapter().also { adapter ->
                listView.adapter = adapter
                listView.scheduleLayoutAnimation()
            }
            listView.setOnItemClickListener { _, _, position, _ ->
                when (adapter.itemAt(position)) {
                    LegacyMoreRootEntry.LovedSongs -> onLovedSongsClick()
                    LegacyMoreRootEntry.Folder -> onFolderClick()
                    null -> Unit
                }
            }
        },
    )
}

private class LegacyMoreRootAdapter : BaseAdapter() {
    private val entries = LegacyMoreRootEntry.entries

    fun itemAt(position: Int): LegacyMoreRootEntry? = entries.getOrNull(position)

    override fun getCount(): Int = entries.size

    override fun getItem(position: Int): Any = entries[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.more_item, parent, false)
        val itemView: ListContentItemText = (view as? ListContentItemText)
            ?: view.findViewById<ListContentItemText>(R.id.list_content_item)
            ?: return view
        val entry = entries[position]
        itemView.setIcon(entry.iconRes)
        itemView.setTitle(parent.context.getString(entry.labelRes))
        itemView.setSummary(null)
        itemView.setSubtitle(null)
        itemView.setArrowVisible(true)
        return view
    }
}
