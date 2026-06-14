package com.smartisanos.music.ui.shell.cloud

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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineAccountPlaylist
import com.smartisanos.music.data.online.OnlineAlbum
import com.smartisanos.music.data.online.OnlineRadio
import com.smartisanos.music.ui.shell.addLegacyPortListFooter
import com.smartisanos.music.ui.shell.bindLegacyPortListFooter
import com.smartisanos.music.ui.shell.legacyWrappedAdapter
import androidx.compose.ui.viewinterop.AndroidView

internal sealed interface CloudAccountLibraryItem {
    data class Playlist(val playlist: OnlineAccountPlaylist) : CloudAccountLibraryItem
    data class Album(val album: OnlineAlbum) : CloudAccountLibraryItem
    data class Radio(val radio: OnlineRadio) : CloudAccountLibraryItem
}

@Composable
internal fun CloudMusicAccountLibraryList(
    playlists: List<OnlineAccountPlaylist>,
    albums: List<OnlineAlbum>,
    radios: List<OnlineRadio>,
    selectedPlaylistId: String?,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onPlaylistClick: (OnlineAccountPlaylist) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onRadioClick: (OnlineRadio) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackBarOverlayHeightPx = with(LocalDensity.current) {
        playbackBarOverlayHeight.roundToPx()
    }
    val items = remember(playlists, albums, radios) {
        playlists.map { playlist -> CloudAccountLibraryItem.Playlist(playlist) } +
            albums.map { album -> CloudAccountLibraryItem.Album(album) } +
            radios.map { radio -> CloudAccountLibraryItem.Radio(radio) }
    }
    AndroidView(
        modifier = modifier,
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
                pluralsRes = R.plurals.cloud_music_account_library_count,
                count = items.size,
            )
            val adapter = listView.legacyWrappedAdapter<CloudMusicAccountLibraryAdapter>()
                ?: CloudMusicAccountLibraryAdapter().also { nextAdapter ->
                    listView.adapter = nextAdapter
                }
            val changed = adapter.updateItems(
                nextItems = items,
                nextSelectedPlaylistId = selectedPlaylistId,
            )
            if (changed) {
                listView.scheduleLayoutAnimation()
            } else {
                adapter.updateVisibleRows(listView)
            }
            listView.setOnItemClickListener { _, _, position, _ ->
                when (val item = adapter.itemAt(position)) {
                    is CloudAccountLibraryItem.Playlist -> onPlaylistClick(item.playlist)
                    is CloudAccountLibraryItem.Album -> onAlbumClick(item.album)
                    is CloudAccountLibraryItem.Radio -> onRadioClick(item.radio)
                    null -> Unit
                }
            }
        },
    )
}

internal class CloudMusicAccountLibraryAdapter : BaseAdapter() {
    private var items: List<CloudAccountLibraryItem> = emptyList()
    private var selectedPlaylistId: String? = null

    fun updateItems(
        nextItems: List<CloudAccountLibraryItem>,
        nextSelectedPlaylistId: String?,
    ): Boolean {
        val changed = items != nextItems || selectedPlaylistId != nextSelectedPlaylistId
        if (changed) {
            items = nextItems
            selectedPlaylistId = nextSelectedPlaylistId
            notifyDataSetChanged()
        }
        return changed
    }

    fun updateVisibleRows(listView: ListView) {
        for (index in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + index
            val item = itemAt(position) ?: continue
            bindAccountLibraryRow(listView.getChildAt(index), item)
        }
    }

    fun itemAt(position: Int): CloudAccountLibraryItem? = items.getOrNull(position)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any? = itemAt(position)

    override fun getItemId(position: Int): Long {
        return when (val item = itemAt(position)) {
            is CloudAccountLibraryItem.Playlist ->
                item.playlist.playlistId.toLongOrNull() ?: position.toLong()
            is CloudAccountLibraryItem.Album ->
                item.album.albumId.toLongOrNull()?.let { albumId -> -albumId } ?: -(position.toLong() + 1L)
            is CloudAccountLibraryItem.Radio ->
                item.radio.radioId.toLongOrNull()?.let { radioId -> Long.MIN_VALUE + radioId } ?: Long.MIN_VALUE + position.toLong()
            null -> position.toLong()
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: createAccountLibraryRow(parent)
        itemAt(position)?.let { item -> bindAccountLibraryRow(view, item) }
        return view
    }

    private fun createAccountLibraryRow(parent: ViewGroup): View {
        val context = parent.context
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.listview_selector)
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setPadding(context.dpPx(15), 0, context.dpPx(15), 0)
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.listview_item_height),
            )
            addView(
                TextView(context).apply {
                    id = R.id.listview_item_line_one
                    gravity = Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_large))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                TextView(context).apply {
                    id = R.id.listview_item_line_two
                    gravity = Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(context.getColor(R.color.list_item_second_line))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_micro))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = context.dpPx(5)
                },
            )
        }
    }

    private fun bindAccountLibraryRow(view: View, item: CloudAccountLibraryItem) {
        val context = view.context
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = item.displayTitle(context)
            setTextColor(
                if (item is CloudAccountLibraryItem.Playlist && item.playlist.playlistId == selectedPlaylistId) {
                    Color.rgb(177, 36, 32)
                } else {
                    context.getColor(R.color.list_item_first_line)
                },
            )
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.text =
            item.displaySubtitle(context)
    }
}

internal fun CloudAccountLibraryItem.displayTitle(context: Context): String {
    return when (this) {
        is CloudAccountLibraryItem.Playlist -> playlist.displayTitle(context)
        is CloudAccountLibraryItem.Album -> album.title
        is CloudAccountLibraryItem.Radio -> radio.title
    }
}

internal fun CloudAccountLibraryItem.displaySubtitle(context: Context): String {
    return when (this) {
        is CloudAccountLibraryItem.Playlist -> listOf(
            context.getString(R.string.cloud_music_account_playlist_label),
            context.getString(R.string.cloud_music_playlist_track_count, playlist.trackCount),
        )
        is CloudAccountLibraryItem.Album -> listOfNotNull(
            context.getString(R.string.cloud_music_account_album_label),
            album.albumSubtitle(context),
        )
        is CloudAccountLibraryItem.Radio -> listOfNotNull(
            context.getString(R.string.cloud_music_account_radio_label),
            radio.subtitleText(context),
        )
    }.joinToString(" · ")
}
