package com.smartisanos.music.ui.shell

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HeaderViewListAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.settings.ArtistSettings
import com.smartisanos.music.playback.LocalPlaybackBrowser
import com.smartisanos.music.playback.replaceQueueAndPlay
import com.smartisanos.music.playback.replaceQueueAndPlayShuffled
import com.smartisanos.music.ui.album.AlbumSummary
import com.smartisanos.music.ui.album.displayTrackNumber
import com.smartisanos.music.ui.artist.artistNormalizedKey
import com.smartisanos.music.ui.artist.toArtistDisplayNames
import com.smartisanos.music.ui.widgets.StretchTextView
import java.util.Locale

private val LegacyAlbumDetailPrimaryTextColor = Color.rgb(0x35, 0x35, 0x39)
private val LegacyAlbumDetailSecondaryTextColor = Color.rgb(0xa4, 0xa7, 0xac)

@Composable
internal fun LegacyPortAlbumDetailPage(
    album: AlbumSummary,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
    modifier: Modifier = Modifier,
) {
    val browser = LocalPlaybackBrowser.current
    var currentMediaId by remember(browser) {
        mutableStateOf(browser?.currentMediaItem?.mediaId)
    }
    var currentIsPlaying by remember(browser) {
        mutableStateOf(browser?.isPlaying == true)
    }
    var artworkBrowserState by remember {
        mutableStateOf<LegacyAlbumArtworkBrowserState?>(null)
    }

    BackHandler(enabled = artworkBrowserState != null) {
        artworkBrowserState = null
    }

    DisposableEffect(browser) {
        val playbackBrowser = browser ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                currentMediaId = player.currentMediaItem?.mediaId
                currentIsPlaying = player.isPlaying
            }
        }
        playbackBrowser.addListener(listener)
        onDispose {
            playbackBrowser.removeListener(listener)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LegacyAlbumDetailRoot(context)
            },
            update = { root ->
                root.bindHeader(
                    album = album,
                    enabled = album.songs.isNotEmpty(),
                    onCoverClick = { sourceView ->
                        artworkBrowserState = LegacyAlbumArtworkBrowserState(
                            album = album,
                            sourceView = sourceView,
                        )
                    },
                    onPlayAll = {
                        browser.replaceQueueAndPlay(album.songs)
                    },
                    onShuffle = {
                        browser.replaceQueueAndPlayShuffled(album.songs)
                    },
                    onAddToPlaylist = {
                        if (album.songs.isNotEmpty()) {
                            onRequestAddToPlaylist(album.songs)
                        }
                    },
                    onAddToQueue = {
                        if (album.songs.isNotEmpty()) {
                            onRequestAddToQueue(album.songs)
                        }
                    },
                )
                root.listView.bindLegacyPortListFooter(
                    pluralsRes = R.plurals.track_count,
                    count = album.songs.size,
                )

                val adapter = root.listView.legacyAlbumTrackAdapter()
                    ?: LegacyAlbumTrackAdapter().also { adapter ->
                        root.listView.adapter = adapter
                    }
                adapter.onMoreClick = onTrackMoreClick
                val contentChanged = adapter.updateItems(
                    nextItems = album.songs,
                    nextCurrentMediaId = currentMediaId,
                    nextCurrentIsPlaying = currentIsPlaying,
                    nextShowTrackArtists = album.songs.hasMultipleArtists(artistSettings),
                    nextArtistSettings = artistSettings,
                )
                if (!contentChanged) {
                    adapter.updateVisibleRows(root.listView)
                }
                root.listView.setOnItemClickListener { _, _, position, _ ->
                    val trackIndex = position - root.listView.headerViewsCount
                    if (trackIndex < 0) {
                        return@setOnItemClickListener
                    }
                    adapter.itemAt(trackIndex) ?: return@setOnItemClickListener
                    browser.replaceQueueAndPlay(album.songs, trackIndex)
                }
            },
        )
        LegacyAlbumArtworkBrowserOverlay(
            state = artworkBrowserState,
            onDismissRequest = {
                artworkBrowserState = null
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal fun ListView.legacyAlbumTrackAdapter(): LegacyAlbumTrackAdapter? {
    return when (val currentAdapter = adapter) {
        is LegacyAlbumTrackAdapter -> currentAdapter
        is HeaderViewListAdapter -> currentAdapter.wrappedAdapter as? LegacyAlbumTrackAdapter
        else -> null
    }
}

private class LegacyAlbumDetailRoot(context: Context) : FrameLayout(context) {
    val listView: ListView
    private val header = LegacyAlbumDetailHeader(context)
    private val artworkLoader = LegacyAlbumArtworkLoader(context)

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

    fun bindHeader(
        album: AlbumSummary,
        enabled: Boolean,
        onCoverClick: (View) -> Unit,
        onPlayAll: () -> Unit,
        onShuffle: () -> Unit,
        onAddToPlaylist: () -> Unit,
        onAddToQueue: () -> Unit,
    ) {
        header.bind(
            album = album,
            artworkLoader = artworkLoader,
            enabled = enabled,
            onCoverClick = onCoverClick,
            onPlayAll = onPlayAll,
            onShuffle = onShuffle,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
        )
    }

    override fun onDetachedFromWindow() {
        artworkLoader.clear()
        super.onDetachedFromWindow()
    }
}

private class LegacyAlbumDetailHeader(context: Context) : LinearLayout(context) {
    private val albumImage = ImageView(context)
    private val albumName = TextView(context)
    private val albumArtist = TextView(context)
    private val albumTag = TextView(context)
    private val addToPlaylist = ImageButton(context)
    private val addToQueue = ImageButton(context)
    private val playAll = TextView(context)
    private val shuffle = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.ablum_crosstexture_bg)

        val coverSize = resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height)
        val infoTop = resources.getDimensionPixelSize(R.dimen.album_detail_text_margin_top)
        val infoLeft = resources.getDimensionPixelSize(R.dimen.alum_tile_paddingleft)
        val operationBottom = resources.getDimensionPixelSize(R.dimen.album_opration_zone_padding_bottom)
        val operationRight = resources.getDimensionPixelSize(R.dimen.album_opration_zone_padding_right)
        val buttonMarginRight = resources.getDimensionPixelSize(R.dimen.btn_margin_right)

        addView(
            RelativeLayout(context).apply {
                val imageZone = FrameLayout(context).apply {
                    id = R.id.album_image_zone
                    albumImage.apply {
                        id = R.id.album_image
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        cropToPadding = true
                        setPadding(
                            resources.getDimensionPixelSize(R.dimen.gridview_padding),
                            resources.getDimensionPixelSize(R.dimen.gridview_padding),
                            resources.getDimensionPixelSize(R.dimen.gridview_padding),
                            resources.getDimensionPixelSize(R.dimen.gridview_padding),
                        )
                        setBackgroundResource(R.drawable.mask_albumcover)
                    }
                    addView(
                        albumImage,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        View(context).apply {
                            id = R.id.iv_mask_albumcover
                            setBackgroundResource(R.drawable.mask_albumcover)
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                addView(
                    imageZone,
                    RelativeLayout.LayoutParams(coverSize, coverSize).apply {
                        leftMargin = resources.getDimensionPixelSize(R.dimen.gridview_margin)
                        addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                        addRule(RelativeLayout.CENTER_VERTICAL)
                    },
                )

                addView(
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        setPadding(infoLeft, infoTop, 0, 0)
                        albumName.legacyAlbumHeaderText(
                            id = R.id.album_name,
                            textSizePx = resources.getDimension(R.dimen.album_detail_album_name_size),
                            maxLines = 1,
                        )
                        addView(
                            albumName,
                            LinearLayout.LayoutParams(
                                resources.getDimensionPixelSize(R.dimen.listview_items_header_width),
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                        albumArtist.legacyAlbumHeaderText(
                            id = R.id.album_artist_name,
                            textSizePx = resources.getDimension(R.dimen.album_detail_artist_size),
                            maxLines = 1,
                        )
                        addView(
                            albumArtist,
                            LinearLayout.LayoutParams(
                                resources.getDimensionPixelSize(R.dimen.listview_items_header_width),
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = resources.getDimensionPixelSize(R.dimen.album_detail_text_margin_top1)
                            },
                        )
                        albumTag.legacyAlbumHeaderText(
                            id = R.id.album_id_tag,
                            textSizePx = resources.getDimension(R.dimen.audio_normal_text_size),
                            maxLines = 1,
                        )
                        addView(
                            albumTag,
                            LinearLayout.LayoutParams(
                                resources.getDimensionPixelSize(R.dimen.listview_items_header_width),
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = resources.getDimensionPixelSize(R.dimen.album_detail_text_margin_top2)
                            },
                        )
                    },
                    RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        addRule(RelativeLayout.RIGHT_OF, R.id.album_image_zone)
                    },
                )

                addView(
                    LinearLayout(context).apply {
                        id = R.id.operation_zone
                        orientation = HORIZONTAL
                        setPadding(0, 0, operationRight, operationBottom)
                        addToPlaylist.apply {
                            id = R.id.btn_add_to_playlist
                            setBackgroundResource(R.drawable.album_btn_add_to_playlist_selector)
                            contentDescription = context.getString(R.string.add_to_playlist)
                        }
                        addView(
                            addToPlaylist,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                rightMargin = buttonMarginRight
                            },
                        )
                        addToQueue.apply {
                            id = R.id.btn_add_to_queue
                            setBackgroundResource(R.drawable.album_btn_add_to_queue_selector)
                            contentDescription = context.getString(R.string.add_to_queue)
                        }
                        addView(
                            addToQueue,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    },
                    RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        addRule(RelativeLayout.RIGHT_OF, R.id.album_image_zone)
                        addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    },
                )
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.album_header_height),
            ),
        )
        addDivider()
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                setBackgroundColor(Color.WHITE)
                addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER
                        playAll.legacyAlbumActionText(
                            id = R.id.header_play_all,
                            text = context.getString(R.string.play_all),
                            iconRes = R.drawable.btn_play_all_selector,
                        )
                        addView(
                            playAll,
                            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                        )
                    },
                    LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
                )
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.line_between)
                        scaleType = ImageView.ScaleType.FIT_XY
                    },
                    LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.album_play_type_divider_size),
                        LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER
                        shuffle.legacyAlbumActionText(
                            id = R.id.header_play_shuffle,
                            text = context.getString(R.string.play_shuffle),
                            iconRes = R.drawable.btn_shuffle3_selector,
                        )
                        addView(
                            shuffle,
                            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                        )
                    },
                    LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(45)),
        )
        addDivider()
    }

    fun bind(
        album: AlbumSummary,
        artworkLoader: LegacyAlbumArtworkLoader,
        enabled: Boolean,
        onPlayAll: () -> Unit,
        onShuffle: () -> Unit,
        onAddToPlaylist: () -> Unit,
        onAddToQueue: () -> Unit,
        onCoverClick: (View) -> Unit,
    ) {
        albumImage.bindLegacyAlbumArtwork(
            album = album,
            fallbackRes = R.drawable.noalbumcover_220,
            sizePx = resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height),
            artworkLoader = artworkLoader,
        )
        albumImage.setOnClickListener { view ->
            onCoverClick(view)
        }
        albumName.text = album.title
        albumName.isSelected = true
        albumArtist.text = album.artist
        albumTag.text = album.year?.toString().orEmpty()
        addToPlaylist.isEnabled = enabled
        addToQueue.isEnabled = enabled
        playAll.isEnabled = enabled
        shuffle.isEnabled = enabled
        playAll.alpha = if (enabled) 1f else 0.3f
        shuffle.alpha = if (enabled) 1f else 0.3f
        addToPlaylist.setOnClickListener {
            if (enabled) {
                onAddToPlaylist()
            }
        }
        addToQueue.setOnClickListener {
            if (enabled) {
                onAddToQueue()
            }
        }
        playAll.setOnClickListener {
            if (enabled) {
                onPlayAll()
            }
        }
        shuffle.setOnClickListener {
            if (enabled) {
                onShuffle()
            }
        }
    }

    private fun addDivider() {
        addView(
            View(context).apply {
                setBackgroundColor(context.getColor(R.color.listview_divider_color))
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.listview_dividerHeight),
            ),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal class LegacyAlbumTrackAdapter : BaseAdapter() {
    var onMoreClick: (MediaItem) -> Unit = {}
    private var items: List<MediaItem> = emptyList()
    private var currentMediaId: String? = null
    private var currentIsPlaying: Boolean = false
    private var showTrackArtists: Boolean = false
    private var artistSettings: ArtistSettings = ArtistSettings()
    private var forceSequentialTrackNumbers: Boolean = false

    fun updateItems(
        nextItems: List<MediaItem>,
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
        nextShowTrackArtists: Boolean,
        nextArtistSettings: ArtistSettings,
        nextForceSequentialTrackNumbers: Boolean = false,
    ): Boolean {
        val contentChanged = items != nextItems
        val stateChanged = currentMediaId != nextCurrentMediaId ||
            currentIsPlaying != nextCurrentIsPlaying ||
            showTrackArtists != nextShowTrackArtists ||
            artistSettings != nextArtistSettings ||
            forceSequentialTrackNumbers != nextForceSequentialTrackNumbers
        if (!contentChanged && !stateChanged) {
            return false
        }
        items = nextItems
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
        showTrackArtists = nextShowTrackArtists
        artistSettings = nextArtistSettings
        forceSequentialTrackNumbers = nextForceSequentialTrackNumbers
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun itemAt(position: Int): MediaItem? = items.getOrNull(position)

    fun setPlaybackState(
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
    ): Boolean {
        if (currentMediaId == nextCurrentMediaId && currentIsPlaying == nextCurrentIsPlaying) {
            return false
        }
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
        return true
    }

    fun updateVisibleRows(listView: ListView) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex - listView.headerViewsCount
            val item = itemAt(position) ?: continue
            val holder = listView.getChildAt(childIndex)?.tag as? LegacyAlbumTrackViewHolder ?: continue
            holder.bind(
                item = item,
                fallbackIndex = position + 1,
                selected = item.mediaId == currentMediaId,
                playing = currentIsPlaying,
                showArtist = showTrackArtists,
                artistSettings = artistSettings,
                forceSequentialTrackNumber = forceSequentialTrackNumbers,
            )
        }
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: createTrackRow(parent)
        val holder = view.tag as LegacyAlbumTrackViewHolder
        val item = items[position]
        holder.bind(
            item = item,
            fallbackIndex = position + 1,
            selected = item.mediaId == currentMediaId,
            playing = currentIsPlaying,
            showArtist = showTrackArtists,
            artistSettings = artistSettings,
            forceSequentialTrackNumber = forceSequentialTrackNumbers,
        )
        holder.more.setOnClickListener {
            onMoreClick(item)
        }
        return view
    }

    private fun createTrackRow(parent: ViewGroup): View {
        val context = parent.context
        val row = RelativeLayout(context).apply {
            background = context.getDrawable(R.drawable.listview_selector)
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.listview_item_height),
            )
        }

        val index = TextView(context).apply {
            id = R.id.album_list_item_track
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_large))
            setTextColor(context.colorStateListCompat(R.drawable.song_index_color_selector))
            isDuplicateParentStateEnabled = true
        }
        row.addView(
            index,
            RelativeLayout.LayoutParams(
                parent.resources.getDimensionPixelSize(R.dimen.album_song_index_width),
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = parent.resources.getDimensionPixelSize(R.dimen.album_song_index_margin_left)
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            },
        )

        val more = ImageView(context).apply {
            id = R.id.img_action_more
            setImageResource(R.drawable.btn_more_selector)
            scaleType = ImageView.ScaleType.CENTER
            isDuplicateParentStateEnabled = true
            isClickable = true
            isFocusable = false
            contentDescription = context.getString(R.string.tab_more)
        }
        row.addView(
            more,
            RelativeLayout.LayoutParams(
                parent.resources.getDimensionPixelSize(R.dimen.listview_item_height),
                parent.resources.getDimensionPixelSize(R.dimen.listview_item_height),
            ).apply {
                rightMargin = parent.resources.getDimensionPixelSize(R.dimen.listview_items_margin_right)
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            },
        )

        val duration = TextView(context).apply {
            id = R.id.album_list_item_duration
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_micro))
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(context.colorStateListCompat(R.drawable.text_color_white_and_gray1_selector))
        }
        row.addView(
            duration,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = parent.resources.getDimensionPixelSize(R.dimen.listview_items_margin_right)
                addRule(RelativeLayout.LEFT_OF, R.id.img_action_more)
                addRule(RelativeLayout.CENTER_VERTICAL)
            },
        )

        val textZone = RelativeLayout(context).apply {
            setPadding(resources.getDimensionPixelSize(R.dimen.album_text_padding_left), 0, 0, 0)
        }
        row.addView(
            textZone,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.RIGHT_OF, R.id.album_list_item_track)
                addRule(RelativeLayout.LEFT_OF, R.id.album_list_item_duration)
                addRule(RelativeLayout.CENTER_VERTICAL)
            },
        )

        val title = StretchTextView(context).apply {
            id = R.id.album_list_item_title
            ellipsize = TextUtils.TruncateAt.MARQUEE
            isSingleLine = true
            marqueeRepeatLimit = -1
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_medium))
            setTextColor(LegacyAlbumDetailPrimaryTextColor)
            isDuplicateParentStateEnabled = true
        }
        textZone.addView(
            title,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val artist = TextView(context).apply {
            id = R.id.album_list_item_artist
            ellipsize = TextUtils.TruncateAt.END
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_micro))
            setTextColor(LegacyAlbumDetailSecondaryTextColor)
        }
        textZone.addView(
            artist,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.BELOW, R.id.album_list_item_title)
                addRule(RelativeLayout.ALIGN_LEFT, R.id.album_list_item_title)
            },
        )

        row.tag = LegacyAlbumTrackViewHolder(
            index = index,
            title = title,
            artist = artist,
            duration = duration,
            more = more,
        )
        return row
    }
}

private data class LegacyAlbumTrackViewHolder(
    val index: TextView,
    val title: StretchTextView,
    val artist: TextView,
    val duration: TextView,
    val more: ImageView,
) {
    fun bind(
        item: MediaItem,
        fallbackIndex: Int,
        selected: Boolean,
        playing: Boolean,
        showArtist: Boolean,
        artistSettings: ArtistSettings,
        forceSequentialTrackNumber: Boolean,
    ) {
        val metadata = item.mediaMetadata
        index.text = if (forceSequentialTrackNumber) {
            fallbackIndex.toString()
        } else {
            item.displayTrackNumber()
        }
        title.text = metadata.displayTitle
            ?: metadata.title
            ?: ""
        title.isSelected = true
        title.setTextColor(LegacyAlbumDetailPrimaryTextColor)
        if (selected) {
            title.c(playing)
        } else {
            title.setShowingPlayImage(false)
        }
        artist.text = item.albumDetailArtistText(artistSettings)
        artist.visibility = if (showArtist) View.VISIBLE else View.GONE
        duration.text = metadata.durationMs?.formatLegacyDuration().orEmpty()
    }
}

private fun List<MediaItem>.hasMultipleArtists(artistSettings: ArtistSettings): Boolean {
    return flatMap { item ->
        item.mediaMetadata.artist.toArtistDisplayNames(
            artistSettings = artistSettings,
            unknownArtistTitle = "",
        )
    }
        .filter { it.isNotBlank() }
        .distinctBy { it.artistNormalizedKey() }
        .size > 1
}

private fun MediaItem.albumDetailArtistText(artistSettings: ArtistSettings): String {
    return mediaMetadata.artist.toArtistDisplayNames(
        artistSettings = artistSettings,
        unknownArtistTitle = "",
    )
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .takeIf { it.isNotBlank() }
        ?: mediaMetadata.albumArtist?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
}

private fun TextView.legacyAlbumHeaderText(
    id: Int,
    textSizePx: Float,
    maxLines: Int,
): TextView {
    this.id = id
    gravity = Gravity.CENTER_VERTICAL
    ellipsize = TextUtils.TruncateAt.MARQUEE
    isSingleLine = maxLines == 1
    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
    setTextColor(context.getColor(R.color.title_text_color))
    return this
}

private fun TextView.legacyAlbumActionText(
    id: Int,
    text: String,
    iconRes: Int,
): TextView {
    this.id = id
    this.text = text
    gravity = Gravity.CENTER
    isClickable = true
    isFocusable = true
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.button_text_size))
    setTextColor(context.colorStateListCompat(R.drawable.text_color_album_action_selector))
    setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
    compoundDrawablePadding = dp(7)
    return this
}

private fun TextView.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun Context.colorStateListCompat(resId: Int): ColorStateList {
    return resources.getColorStateList(resId, theme)
}

private fun Long.formatLegacyDuration(): String {
    if (this <= 0L) {
        return ""
    }
    val totalSeconds = this / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
