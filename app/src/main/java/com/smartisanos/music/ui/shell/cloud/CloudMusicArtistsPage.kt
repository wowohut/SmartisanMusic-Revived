package com.smartisanos.music.ui.shell.cloud

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineArtist
import com.smartisanos.music.ui.shell.addLegacyPortListFooter
import com.smartisanos.music.ui.shell.bindLegacyPortListFooter
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicBlankState
import com.smartisanos.music.ui.shell.cloud.components.CloudMusicDivider
import com.smartisanos.music.ui.shell.legacyWrappedAdapter

internal sealed interface CloudArtistState {
    object Idle : CloudArtistState
    object Loading : CloudArtistState
    object Empty : CloudArtistState
    object Error : CloudArtistState
    data class Success(val artists: List<OnlineArtist>) : CloudArtistState
}

@Composable
internal fun CloudMusicArtistStateContent(
    state: CloudArtistState,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onRetryClick: () -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CloudArtistState.Idle,
        CloudArtistState.Loading -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_artists_loading),
            subtitle = null,
            modifier = modifier,
        )
        CloudArtistState.Empty -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_artists_empty),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            modifier = modifier,
        )
        CloudArtistState.Error -> CloudMusicBlankState(
            title = stringResource(R.string.cloud_music_artists_error),
            subtitle = stringResource(R.string.cloud_music_empty_subtitle),
            actionText = stringResource(R.string.cloud_music_retry),
            onActionClick = onRetryClick,
            modifier = modifier,
        )
        is CloudArtistState.Success -> CloudMusicArtistList(
            artists = state.artists,
            selectedArtistId = null,
            active = active,
            playbackBarOverlayHeight = playbackBarOverlayHeight,
            onArtistClick = onArtistClick,
            modifier = modifier,
        )
    }
}

@Composable
internal fun CloudMusicArtistList(
    artists: List<OnlineArtist>,
    selectedArtistId: String?,
    active: Boolean,
    playbackBarOverlayHeight: Dp,
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackBarOverlayHeightPx = with(LocalDensity.current) {
        playbackBarOverlayHeight.roundToPx()
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
                pluralsRes = R.plurals.legacy_artist_count,
                count = artists.size,
            )
            val adapter = listView.legacyWrappedAdapter<CloudMusicArtistAdapter>()
                ?: CloudMusicArtistAdapter().also { nextAdapter ->
                    listView.adapter = nextAdapter
                }
            val changed = adapter.updateItems(
                nextItems = artists,
                nextSelectedArtistId = selectedArtistId,
            )
            if (changed) {
                listView.scheduleLayoutAnimation()
            } else {
                adapter.updateVisibleRows(listView)
            }
            listView.setOnItemClickListener { _, _, position, _ ->
                adapter.itemAt(position)?.let(onArtistClick)
            }
        },
    )
}

internal class CloudMusicArtistAdapter : BaseAdapter() {
    private var artists: List<OnlineArtist> = emptyList()
    private var selectedArtistId: String? = null

    fun updateItems(
        nextItems: List<OnlineArtist>,
        nextSelectedArtistId: String?,
    ): Boolean {
        val changed = artists != nextItems || selectedArtistId != nextSelectedArtistId
        if (changed) {
            artists = nextItems
            selectedArtistId = nextSelectedArtistId
            notifyDataSetChanged()
        }
        return changed
    }

    fun updateVisibleRows(listView: ListView) {
        for (index in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + index
            val artist = itemAt(position) ?: continue
            bindArtistRow(listView.getChildAt(index), artist)
        }
    }

    fun itemAt(position: Int): OnlineArtist? = artists.getOrNull(position)

    override fun getCount(): Int = artists.size

    override fun getItem(position: Int): Any? = itemAt(position)

    override fun getItemId(position: Int): Long = artists.getOrNull(position)?.artistId?.toLongOrNull() ?: position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: createArtistRow(parent)
        itemAt(position)?.let { artist -> bindArtistRow(view, artist) }
        return view
    }

    private fun createArtistRow(parent: ViewGroup): View {
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

    private fun bindArtistRow(view: View, artist: OnlineArtist) {
        val context = view.context
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = artist.name
            setTextColor(
                if (artist.artistId == selectedArtistId) {
                    Color.rgb(177, 36, 32)
                } else {
                    context.getColor(R.color.list_item_first_line)
                },
            )
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.text =
            artist.subtitleText(context)
    }
}
