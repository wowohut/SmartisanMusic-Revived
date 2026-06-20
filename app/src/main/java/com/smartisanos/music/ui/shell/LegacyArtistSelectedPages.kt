package com.smartisanos.music.ui.shell

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.settings.ArtistSettings
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.isPlaybackActiveForUi
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.playback.replaceQueueAndPlayShuffled
import com.smartisanos.music.ui.album.AlbumSummary
import com.smartisanos.music.ui.album.AlbumViewMode
import com.smartisanos.music.ui.album.buildAlbumSummaries
import com.smartisanos.music.ui.artist.ArtistSummary
import java.text.Collator
import java.util.Locale

@Composable
internal fun LegacyPortSelectedArtistPage(
    artist: ArtistSummary,
    albums: List<AlbumSummary>,
    target: LegacyArtistTarget?,
    browser: Player?,
    albumViewMode: AlbumViewMode,
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    onTargetChanged: (LegacyArtistTarget?) -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
    switchAnimator: LegacyArtistAlbumViewSwitchAnimator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val directAlbumTarget = target as? LegacyArtistTarget.Album
    if (directAlbumTarget != null && !directAlbumTarget.fromArtistAlbums) {
        albums.firstOrNull { album -> album.id == directAlbumTarget.albumId }?.let { album ->
            LegacyPortAlbumDetailPage(
                album = album,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onTrackMoreClick = onTrackMoreClick,
                artistSettings = artistSettings,
                modifier = modifier,
            )
            return
        }
    }

    val nestedTarget = target?.takeIf { currentTarget ->
        currentTarget.artistId == artist.id && currentTarget !is LegacyArtistTarget.Albums
    }
    val allSongsTitle = stringResource(R.string.artist_all_songs)
    val entries = remember(artist, albums, allSongsTitle) {
        buildArtistAlbumEntries(
            artist = artist,
            albums = albums,
            allSongsTitle = allSongsTitle,
        )
    }
    LegacyPortPageStackTransition(
        secondaryKey = nestedTarget,
        modifier = modifier,
        label = "legacy selected artist transition",
        predictiveBackProgress = predictiveBackProgress,
        predictiveBackExitConsumed = predictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onPredictiveBackExitConsumedReset,
        primaryContent = {
            LegacyPortArtistAlbumsPage(
                artist = artist,
                entries = entries,
                browser = browser,
                viewMode = albumViewMode,
                onTargetChanged = onTargetChanged,
                switchAnimator = switchAnimator,
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { detailTarget ->
            when (detailTarget) {
                is LegacyArtistTarget.AllSongs -> {
                    LegacyPortArtistAllSongsPage(
                        artistName = artist.name,
                        songs = artist.songs,
                        onTrackMoreClick = onTrackMoreClick,
                        artistSettings = artistSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is LegacyArtistTarget.Album -> {
                    albums.firstOrNull { album -> album.id == detailTarget.albumId }?.let { album ->
                        LegacyPortAlbumDetailPage(
                            album = album,
                            onRequestAddToPlaylist = onRequestAddToPlaylist,
                            onRequestAddToQueue = onRequestAddToQueue,
                            onTrackMoreClick = onTrackMoreClick,
                            artistSettings = artistSettings,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                is LegacyArtistTarget.Albums -> Unit
            }
        },
    )
}

@Composable
private fun LegacyPortArtistAlbumsPage(
    artist: ArtistSummary,
    entries: List<LegacyArtistAlbumEntry>,
    browser: Player?,
    viewMode: AlbumViewMode,
    onTargetChanged: (LegacyArtistTarget?) -> Unit,
    switchAnimator: LegacyArtistAlbumViewSwitchAnimator,
    modifier: Modifier = Modifier,
) {
    var currentMediaId by remember(browser) {
        mutableStateOf(browser?.currentMediaItem?.mediaId)
    }

    DisposableEffect(browser) {
        val playbackBrowser = browser ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                currentMediaId = player.currentMediaItem?.mediaId
            }
        }
        playbackBrowser.addListener(listener)
        onDispose {
            playbackBrowser.removeListener(listener)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            LegacyArtistAlbumsRoot(viewContext)
        },
        update = { root ->
            val listAdapter = root.listView.adapter as? LegacyArtistAlbumListAdapter
                ?: LegacyArtistAlbumListAdapter(root.artworkLoader).also { adapter ->
                    root.listView.adapter = adapter
                }
            val gridAdapter = root.gridView.adapter as? LegacyArtistAlbumGridAdapter
                ?: LegacyArtistAlbumGridAdapter(root.artworkLoader).also { adapter ->
                    root.gridView.adapter = adapter
                }
            val listContentChanged = listAdapter.updateItems(
                nextItems = entries,
                nextCurrentMediaId = currentMediaId,
            )
            val gridContentChanged = gridAdapter.updateItems(
                nextItems = entries,
                nextCurrentMediaId = currentMediaId,
            )
            if (!listContentChanged) {
                listAdapter.updateVisibleRows(root.listView)
            }
            if (!gridContentChanged) {
                gridAdapter.updateVisibleRows(root.gridView)
            }

            root.listView.setOnItemClickListener { _, _, position, _ ->
                listAdapter.itemAt(position)?.toTarget(
                    artist = artist,
                    allSongsTitle = root.context.getString(R.string.artist_all_songs),
                )?.let(onTargetChanged)
            }
            root.gridView.setOnItemClickListener { _, _, position, _ ->
                gridAdapter.itemAt(position)?.toTarget(
                    artist = artist,
                    allSongsTitle = root.context.getString(R.string.artist_all_songs),
                )?.let(onTargetChanged)
            }

            val previousMode = root.viewMode
            root.viewMode = viewMode
            if (previousMode == null) {
                root.showModeImmediately(viewMode)
            } else if (previousMode != viewMode) {
                switchAnimator.animate(
                    root = root,
                    from = previousMode,
                    to = viewMode,
                )
            }
        },
    )
}

internal class LegacyArtistAlbumsRoot(context: Context) : FrameLayout(context) {
    val listHost: FrameLayout
    val listView: ListView
    val gridView: GridView
    val artworkLoader = LegacyAlbumArtworkLoader(context)
    var viewMode: AlbumViewMode? = null

    init {
        setBackgroundColor(Color.WHITE)
        listHost = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            visibility = View.VISIBLE
        }
        addView(
            listHost,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )

        listView = ListView(context).apply {
            id = R.id.list
            divider = ColorDrawable(context.getColor(R.color.listview_divider_color))
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.WHITE)
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.list_anim_layout)
        }
        listHost.addView(
            listView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        gridView = GridView(context).apply {
            id = R.id.listview_grid
            numColumns = resources.getInteger(R.integer.gridview_columns)
            gravity = Gravity.CENTER_HORIZONTAL
            selector = ColorDrawable(Color.TRANSPARENT)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.WHITE)
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            verticalSpacing = resources.getDimensionPixelSize(R.dimen.gridview_verticalSpacing)
            horizontalSpacing = resources.getDimensionPixelSize(R.dimen.gridview_horizontalSpacing)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.gridview_margin),
                0,
                resources.getDimensionPixelSize(R.dimen.gridview_margin),
                0,
            )
            visibility = View.GONE
        }
        addView(
            gridView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun showModeImmediately(mode: AlbumViewMode) {
        listHost.visibility = if (mode == AlbumViewMode.List) View.VISIBLE else View.GONE
        listHost.alpha = 1f
        listView.visibility = View.VISIBLE
        listView.alpha = 1f
        gridView.visibility = if (mode == AlbumViewMode.Tile) View.VISIBLE else View.GONE
        gridView.alpha = 1f
    }

    override fun onDetachedFromWindow() {
        artworkLoader.clear()
        super.onDetachedFromWindow()
    }
}

private class LegacyArtistAlbumListAdapter(
    private val artworkLoader: LegacyAlbumArtworkLoader,
) : BaseAdapter() {
    private var items: List<LegacyArtistAlbumEntry> = emptyList()
    private var currentMediaId: String? = null

    fun updateItems(
        nextItems: List<LegacyArtistAlbumEntry>,
        nextCurrentMediaId: String?,
    ): Boolean {
        val contentChanged = items != nextItems
        val stateChanged = currentMediaId != nextCurrentMediaId
        if (!contentChanged && !stateChanged) {
            return false
        }
        items = nextItems
        currentMediaId = nextCurrentMediaId
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun itemAt(position: Int): LegacyArtistAlbumEntry? = items.getOrNull(position)

    fun updateVisibleRows(listView: ListView) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex
            val item = itemAt(position) ?: continue
            val child = listView.getChildAt(childIndex) ?: continue
            bindState(child, item)
        }
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.artist_listview_items, parent, false)
        val item = items[position]
        view.setBackgroundResource(R.drawable.listview_selector)
        view.tag = item.stableId
        view.findViewById<ImageView>(R.id.listview_item_image)?.apply {
            setTag(R.string.add_track, position)
            when (item) {
                is LegacyArtistAlbumEntry.AllSongs -> {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setTag(R.id.legacy_album_artwork_request, null)
                    setImageResource(R.drawable.noalbumcover_all_songs2)
                }
                is LegacyArtistAlbumEntry.Album -> bindLegacyAlbumArtwork(
                    album = item.album,
                    fallbackRes = R.drawable.noalbumcover_120,
                    sizePx = parent.resources.getDimensionPixelSize(R.dimen.album_list_item_image_width),
                    artworkLoader = artworkLoader,
                )
            }
        }
        view.findViewById<View>(R.id.iv_mask_albumcover)?.visibility =
            if (item is LegacyArtistAlbumEntry.AllSongs) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.iv_mask_albumcover)?.setBackgroundResource(R.drawable.mask_albumcover_list)
        bindState(view, item)
        return view
    }

    private fun bindState(view: View, item: LegacyArtistAlbumEntry) {
        val selected = item.songs.any { song -> song.mediaId == currentMediaId }
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = item.title
            setTextColor(if (selected) Color.rgb(0xe6, 0x40, 0x40) else LegacyArtistPrimaryTextColor)
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            text = context.resources.getQuantityString(
                R.plurals.album_track_count,
                item.trackCount,
                item.trackCount,
            )
            setTextColor(LegacyArtistSecondaryTextColor)
        }
    }
}

private class LegacyArtistAlbumGridAdapter(
    private val artworkLoader: LegacyAlbumArtworkLoader,
) : BaseAdapter() {
    private var items: List<LegacyArtistAlbumEntry> = emptyList()
    private var currentMediaId: String? = null

    fun updateItems(
        nextItems: List<LegacyArtistAlbumEntry>,
        nextCurrentMediaId: String?,
    ): Boolean {
        val contentChanged = items != nextItems
        val stateChanged = currentMediaId != nextCurrentMediaId
        if (!contentChanged && !stateChanged) {
            return false
        }
        items = nextItems
        currentMediaId = nextCurrentMediaId
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun itemAt(position: Int): LegacyArtistAlbumEntry? = items.getOrNull(position)

    fun updateVisibleRows(gridView: GridView) {
        for (childIndex in 0 until gridView.childCount) {
            val position = gridView.firstVisiblePosition + childIndex
            val item = itemAt(position) ?: continue
            val child = gridView.getChildAt(childIndex) ?: continue
            bindState(child, item)
        }
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: createGridItem(parent)
        val item = items[position]
        view.tag = item.stableId
        view.findViewById<LegacyAlbumTileImageView>(R.id.gridview_image)?.apply {
            setTag(R.string.add_track, position)
            when (item) {
                is LegacyArtistAlbumEntry.AllSongs -> {
                    setMaskEnabled(false)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setTag(R.id.legacy_album_artwork_request, null)
                    setImageResource(R.drawable.noalbumcover_all_songs2)
                }
                is LegacyArtistAlbumEntry.Album -> bindLegacyAlbumArtwork(
                    album = item.album,
                    fallbackRes = R.drawable.noalbumcover_220,
                    sizePx = parent.resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height),
                    artworkLoader = artworkLoader,
                ).also {
                    setMaskEnabled(true)
                }
            }
        }
        bindState(view, item)
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        return view
    }

    private fun bindState(view: View, item: LegacyArtistAlbumEntry) {
        val selected = item.songs.any { song -> song.mediaId == currentMediaId }
        view.findViewById<TextView>(R.id.tv_album_name)?.apply {
            text = item.title
            setTextColor(if (selected) Color.rgb(0xe6, 0x40, 0x40) else Color.BLACK)
            visibility = View.VISIBLE
        }
    }

    private fun createGridItem(parent: ViewGroup): View {
        val context = parent.context
        val coverSize = parent.resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setDuplicateParentStateEnabled(true)
            setPadding(0, parent.resources.getDimensionPixelSize(R.dimen.gridview_padding_top2), 0, 0)
            layoutParams = AbsListView.LayoutParams(
                coverSize,
                AbsListView.LayoutParams.WRAP_CONTENT,
            )
            addView(
                FrameLayout(context).apply {
                    id = R.id.edit_zone
                    addView(
                        LegacyAlbumTileImageView(context).apply {
                            id = R.id.gridview_image
                        },
                        FrameLayout.LayoutParams(coverSize, coverSize),
                    )
                },
                LinearLayout.LayoutParams(coverSize, coverSize),
            )
            addView(
                TextView(context).apply {
                    id = R.id.tv_album_name
                    gravity = Gravity.CENTER
                    ellipsize = TextUtils.TruncateAt.END
                    setSingleLine(true)
                    textSize = 13f
                    setTextColor(Color.BLACK)
                },
                LinearLayout.LayoutParams(coverSize, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
    }
}

@Composable
private fun LegacyPortArtistAllSongsPage(
    artistName: String,
    songs: List<MediaItem>,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings,
    modifier: Modifier = Modifier,
) {
    val browser = LocalPlaybackBrowser.current
    val sortedSongs = remember(songs) {
        songs.sortedWith(artistAllSongsComparator())
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyArtistAllSongsRoot(context)
        },
        update = { root ->
            root.bindHeader(
                title = root.context.getString(R.string.artist_all_songs),
                artistName = artistName,
                enabled = sortedSongs.isNotEmpty(),
                onShuffle = {
                    if (sortedSongs.isNotEmpty()) {
                        browser.replaceQueueAndPlayShuffled(sortedSongs)
                    }
                },
            )
            root.listView.bindLegacyPortListFooter(
                pluralsRes = R.plurals.track_count,
                count = sortedSongs.size,
            )
            val adapter = root.listView.legacyAlbumTrackAdapter()
                ?: LegacyAlbumTrackAdapter().also { adapter ->
                    root.listView.adapter = adapter
                }
            adapter.onMoreClick = onTrackMoreClick
            val contentChanged = adapter.updateItems(
                nextItems = sortedSongs,
                nextCurrentMediaId = browser?.currentMediaItem?.mediaId,
                nextCurrentIsPlaying = browser.isPlaybackActiveForUi(),
                nextShowTrackArtists = true,
                nextArtistSettings = artistSettings,
                nextForceSequentialTrackNumbers = true,
            )
            root.bindPlayback(browser, adapter)
            if (!contentChanged) {
                adapter.updateVisibleRows(root.listView)
            }
            root.listView.setOnItemClickListener { _, _, position, _ ->
                val trackIndex = position - root.listView.headerViewsCount
                if (trackIndex < 0 || trackIndex >= sortedSongs.size) {
                    return@setOnItemClickListener
                }
                browser.replaceQueueAndPlay(sortedSongs, trackIndex)
            }
        },
    )
}

private class LegacyArtistAllSongsRoot(context: Context) : FrameLayout(context) {
    val listView: ListView
    private val header = LegacyArtistAllSongsHeader(context)
    private var playbackPlayer: Player? = null
    private var playbackListener: Player.Listener? = null

    init {
        setBackgroundResource(R.drawable.account_background)
        listView = ListView(context).apply {
            id = R.id.list
            divider = ColorDrawable(context.getColor(R.color.listview_divider_color))
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundResource(R.drawable.account_background)
            isVerticalScrollBarEnabled = false
            setHeaderDividersEnabled(false)
            addHeaderView(header, null, false)
            addLegacyPortListFooter()
        }
        addView(
            listView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun bindPlayback(
        player: Player?,
        adapter: LegacyAlbumTrackAdapter,
    ) {
        if (playbackPlayer === player) {
            return
        }
        playbackListener?.let { listener ->
            playbackPlayer?.removeListener(listener)
        }
        playbackPlayer = player
        playbackListener = null
        if (player == null) {
            return
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    adapter.setPlaybackState(
                        player.currentMediaItem?.mediaId,
                        player.isPlaybackActiveForUi(),
                    )
                ) {
                    adapter.updateVisibleRows(listView)
                }
            }
        }
        playbackListener = listener
        player.addListener(listener)
    }

    fun bindHeader(
        title: String,
        artistName: String,
        enabled: Boolean,
        onShuffle: () -> Unit,
    ) {
        header.bind(
            title = title,
            artistName = artistName,
            enabled = enabled,
            onShuffle = onShuffle,
        )
    }

    override fun onDetachedFromWindow() {
        playbackListener?.let { listener ->
            playbackPlayer?.removeListener(listener)
        }
        playbackListener = null
        playbackPlayer = null
        super.onDetachedFromWindow()
    }
}

private class LegacyArtistAllSongsHeader(context: Context) : RelativeLayout(context) {
    private val albumImage = ImageView(context)
    private val albumName = TextView(context)
    private val albumArtist = TextView(context)
    private val shuffle = ImageButton(context)

    init {
        setBackgroundResource(R.drawable.ablum_crosstexture_bg)
        layoutParams = AbsListView.LayoutParams(
            AbsListView.LayoutParams.MATCH_PARENT,
            dp(150),
        )

        val coverSize = resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height)
        val coverContainer = FrameLayout(context).apply {
            id = R.id.fl_album_image
            albumImage.apply {
                id = R.id.album_image
                scaleType = ImageView.ScaleType.CENTER_CROP
                cropToPadding = true
                setImageResource(R.drawable.noalbumcover_all_songs2)
            }
            addView(
                albumImage,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        addView(
            coverContainer,
            LayoutParams(coverSize, coverSize).apply {
                leftMargin = resources.getDimensionPixelSize(R.dimen.gridview_margin)
                addRule(ALIGN_PARENT_LEFT)
                addRule(CENTER_VERTICAL)
            },
        )

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(resources.getDimensionPixelSize(R.dimen.alum_tile_paddingleft), 0, 0, 0)
                albumName.legacyArtistHeaderText(
                    id = R.id.album_name,
                    textSizePx = resources.getDimension(R.dimen.text_size_medium),
                    color = LegacyArtistPrimaryTextColor,
                )
                addView(
                    albumName,
                    LinearLayout.LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.listview_items_header_width),
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                albumArtist.legacyArtistHeaderText(
                    id = R.id.album_artist_name,
                    textSizePx = resources.getDimension(R.dimen.text_size_small),
                    color = LegacyArtistPrimaryTextColor,
                )
                addView(
                    albumArtist,
                    LinearLayout.LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.listview_items_header_width),
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RIGHT_OF, R.id.fl_album_image)
                addRule(CENTER_VERTICAL)
            },
        )

        shuffle.apply {
            setBackgroundResource(R.drawable.btn_album_shuffle3_selector)
            contentDescription = context.getString(R.string.play_shuffle)
        }
        addView(
            shuffle,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = dp(12)
                bottomMargin = dp(6)
                addRule(ALIGN_PARENT_RIGHT)
                addRule(ALIGN_PARENT_BOTTOM)
            },
        )

        addView(
            View(context).apply {
                setBackgroundResource(R.drawable.ablum_crosstexture_bg_shadow)
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(6),
            ).apply {
                addRule(ALIGN_PARENT_BOTTOM)
            },
        )
    }

    fun bind(
        title: String,
        artistName: String,
        enabled: Boolean,
        onShuffle: () -> Unit,
    ) {
        albumName.text = title
        albumArtist.text = artistName
        shuffle.isEnabled = enabled
        shuffle.alpha = if (enabled) 1f else 0.3f
        shuffle.setOnClickListener {
            if (enabled) {
                onShuffle()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private fun TextView.legacyArtistHeaderText(
    id: Int,
    textSizePx: Float,
    color: Int,
) {
    this.id = id
    gravity = Gravity.CENTER_VERTICAL
    ellipsize = TextUtils.TruncateAt.END
    isSingleLine = true
    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
    setTextColor(color)
}

private sealed class LegacyArtistAlbumEntry {
    abstract val stableId: String
    abstract val title: String
    abstract val songs: List<MediaItem>

    val trackCount: Int
        get() = songs.size

    data class AllSongs(
        override val stableId: String,
        override val title: String,
        override val songs: List<MediaItem>,
    ) : LegacyArtistAlbumEntry()

    data class Album(
        val album: AlbumSummary,
    ) : LegacyArtistAlbumEntry() {
        override val stableId: String = album.id
        override val title: String = album.title
        override val songs: List<MediaItem> = album.songs
    }
}

private fun buildArtistAlbumEntries(
    artist: ArtistSummary,
    albums: List<AlbumSummary>,
    allSongsTitle: String,
): List<LegacyArtistAlbumEntry> {
    return listOf(
        LegacyArtistAlbumEntry.AllSongs(
            stableId = "${artist.id}:all",
            title = allSongsTitle,
            songs = artist.songs,
        ),
    ) + albums.map { album -> LegacyArtistAlbumEntry.Album(album) }
}

private fun LegacyArtistAlbumEntry.toTarget(
    artist: ArtistSummary,
    allSongsTitle: String,
): LegacyArtistTarget {
    return when (this) {
        is LegacyArtistAlbumEntry.AllSongs -> LegacyArtistTarget.AllSongs(
            artistId = artist.id,
            artistName = artist.name,
            title = allSongsTitle,
        )
        is LegacyArtistAlbumEntry.Album -> LegacyArtistTarget.Album(
            artistId = artist.id,
            artistName = artist.name,
            albumId = album.id,
            title = album.title,
            fromArtistAlbums = true,
        )
    }
}

internal fun ArtistSummary.albumSummaries(
    context: Context,
    artistSettings: ArtistSettings = ArtistSettings(),
): List<AlbumSummary> {
    return buildAlbumSummaries(
        mediaItems = songs,
        unknownAlbumTitle = context.getString(R.string.unknown_album),
        multipleArtistsTitle = context.getString(R.string.many_artist),
        artistSettings = artistSettings,
    )
}

private fun artistAllSongsComparator(): Comparator<MediaItem> {
    val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }
    return Comparator { left, right ->
        val leftTitle = left.artistAllSongsSortTitle()
        val rightTitle = right.artistAllSongsSortTitle()
        val localized = collator.compare(leftTitle, rightTitle)
        if (localized != 0) {
            localized
        } else {
            leftTitle.lowercase(Locale.ROOT).compareTo(rightTitle.lowercase(Locale.ROOT))
        }
    }
}

private fun MediaItem.artistAllSongsSortTitle(): String {
    return (mediaMetadata.displayTitle ?: mediaMetadata.title)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: mediaId
}
