package com.smartisanos.music.ui.shell.playback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.isExternalAudioLaunchItem
import com.smartisanos.music.playback.NowPlayingArtworkRepository
import kotlin.math.roundToInt

@Composable
internal fun LegacyPortPlaybackBar(
    snapshot: LegacyPlaybackBarSnapshot,
    shown: Boolean,
    favoriteIds: Set<String>,
    artworkBitmap: Bitmap?,
    onHidden: () -> Unit,
    onOpenPlayback: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    bottomDividerVisible: Boolean = true,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            LegacyPlaybackBarHostView(viewContext)
        },
        update = { host ->
            host.bind(
                snapshot = snapshot,
                favoriteIds = favoriteIds,
                artworkBitmap = artworkBitmap,
                onOpenPlayback = onOpenPlayback,
                onToggleFavorite = onToggleFavorite,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                bottomDividerVisible = bottomDividerVisible,
            )
            host.setPlaybackBarShown(
                shown = shown,
                onHidden = onHidden,
            )
        },
    )
}

internal data class LegacyPlaybackBarSnapshot(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
) {
    val isPlaybackActive: Boolean
        get() = isPlaying || (playWhenReady && isBuffering)

    val isPlaybackBuffering: Boolean
        get() = playWhenReady && isBuffering
}

internal fun Player?.legacyPlaybackBarSnapshot(): LegacyPlaybackBarSnapshot {
    val player = this ?: return LegacyPlaybackBarSnapshot()
    return LegacyPlaybackBarSnapshot(
        mediaItem = player.currentMediaItem,
        isPlaying = player.isPlaying,
        playWhenReady = player.playWhenReady,
        isBuffering = player.playbackState == Player.STATE_BUFFERING,
    )
}

internal suspend fun loadLegacyArtworkBitmap(
    context: android.content.Context,
    mediaItem: MediaItem,
): Bitmap? = NowPlayingArtworkRepository.load(context, mediaItem, LegacyPlaybackBarArtworkDecodeSize)

internal fun peekLegacyArtworkBitmap(
    mediaItem: MediaItem,
): Bitmap? = NowPlayingArtworkRepository.peek(mediaItem, LegacyPlaybackBarArtworkDecodeSize)

private class LegacyPlaybackBarHostView(context: Context) : FrameLayout(context) {
    private val playbackBar: View =
        LayoutInflater.from(context).inflate(R.layout.playback_bar, this, false)

    private var animator: ValueAnimator? = null
    private var targetShown = false
    private var hiddenCallback: () -> Unit = {}

    init {
        clipChildren = true
        clipToPadding = true
        visibility = View.INVISIBLE
        isClickable = false
        setBackgroundColor(Color.TRANSPARENT)
        addView(
            playbackBar,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        playbackBar.translationY = hiddenOffset()
        playbackBar.findViewById<ImageView>(R.id.album_art)?.setImageResource(R.drawable.noalbumcover_220)
        playbackBar.findViewById<View>(R.id.playback_bar_shadow)
            ?.setBackgroundResource(R.drawable.now_playing_bar_shadow)
    }

    fun bind(
        snapshot: LegacyPlaybackBarSnapshot,
        favoriteIds: Set<String>,
        artworkBitmap: Bitmap?,
        onOpenPlayback: () -> Unit,
        onToggleFavorite: (MediaItem) -> Unit,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        bottomDividerVisible: Boolean,
    ) {
        playbackBar.findViewById<View>(R.id.playback_bar_bottom_divider)?.visibility =
            if (bottomDividerVisible) View.VISIBLE else View.GONE

        val mediaItem = snapshot.mediaItem ?: return
        val mediaId = mediaItem.mediaId
        val isExternalAudio = mediaItem.isExternalAudioLaunchItem()
        val isFavorite = !isExternalAudio && mediaId in favoriteIds
        val title = mediaItem.mediaMetadata.displayTitle?.toString()
            ?: mediaItem.mediaMetadata.title?.toString()
            ?: context.getString(R.string.unknown_song_title)
        val artist = mediaItem.mediaMetadata.subtitle?.toString()
            ?: mediaItem.mediaMetadata.artist?.toString()
            ?: context.getString(R.string.unknown_artist)
        val subtitle = if (snapshot.isPlaybackBuffering) {
            context.getString(R.string.playback_buffering)
        } else {
            artist
        }

        playbackBar.findViewById<TextView>(R.id.track_name)?.text = title
        playbackBar.findViewById<TextView>(R.id.artist_name)?.text = subtitle
        playbackBar.findViewById<ImageButton>(R.id.left_btn)?.apply {
            setImageResource(
                if (isFavorite) {
                    R.drawable.float_favor_cancel_selector
                } else {
                    R.drawable.float_favor_add_selector
                },
            )
            isEnabled = !isExternalAudio
            setOnClickListener {
                onToggleFavorite(mediaItem)
            }
        }
        playbackBar.findViewById<ImageButton>(R.id.prev_btn)?.apply {
            setImageResource(R.drawable.float_btn_prev_selector)
            setOnClickListener { onPrevious() }
        }
        playbackBar.findViewById<ImageButton>(R.id.play_btn)?.apply {
            setImageResource(
                if (snapshot.isPlaybackActive) {
                    R.drawable.float_btn_pause_selector
                } else {
                    R.drawable.float_btn_play_selector
                },
            )
            setOnClickListener { onPlayPause() }
        }
        playbackBar.findViewById<ImageButton>(R.id.next_btn)?.apply {
            setImageResource(R.drawable.float_btn_next_selector)
            setOnClickListener { onNext() }
        }
        playbackBar.findViewById<View>(R.id.song_info_zone)?.setOnClickListener {
            onOpenPlayback()
        }
        playbackBar.findViewById<ImageView>(R.id.album_art)?.apply {
            if (artworkBitmap != null) {
                setImageBitmap(artworkBitmap)
            } else {
                setImageResource(R.drawable.noalbumcover_220)
            }
            setOnClickListener {
                onOpenPlayback()
            }
        }
    }

    fun setPlaybackBarShown(
        shown: Boolean,
        onHidden: () -> Unit,
    ) {
        hiddenCallback = onHidden
        if (shown == targetShown) {
            if (!shown && animator == null) {
                post { hiddenCallback() }
            }
            return
        }
        targetShown = shown
        animator?.cancel()

        if (shown) {
            visibility = View.VISIBLE
            isClickable = true
            if (playbackBar.translationY == 0f) {
                playbackBar.translationY = hiddenOffset()
            }
        }

        val nextAnimator = ValueAnimator.ofFloat(
            playbackBar.translationY,
            if (shown) 0f else hiddenOffset(),
        ).apply {
            duration = PlaybackBarAnimationDurationMs
            interpolator = LegacyCubicOutInterpolator
            addUpdateListener { valueAnimator ->
                playbackBar.translationY = valueAnimator.animatedValue as Float
            }
        }
        animator = nextAnimator
        var cancelled = false
        nextAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (shown) {
                        visibility = View.VISIBLE
                        isClickable = true
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) {
                        animator = null
                    }
                    if (!cancelled && !targetShown) {
                        playbackBar.translationY = hiddenOffset()
                        visibility = View.INVISIBLE
                        isClickable = false
                        hiddenCallback()
                    }
                }
            },
        )
        nextAnimator.start()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (!targetShown && animator == null) {
            playbackBar.translationY = height.toFloat()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun hiddenOffset(): Float {
        val measuredHeight = height.takeIf { it > 0 }
        if (measuredHeight != null) {
            return measuredHeight.toFloat()
        }
        val shadowHeight = (PlaybackBarShadowHeightDp * resources.displayMetrics.density).roundToInt()
        return (resources.getDimensionPixelSize(R.dimen.play_back_content_height) + shadowHeight).toFloat()
    }
}

private object LegacyCubicOutInterpolator : TimeInterpolator {
    override fun getInterpolation(input: Float): Float {
        val shifted = input - 1f
        return shifted * shifted * shifted + 1f
    }
}

private const val PlaybackBarAnimationDurationMs = 300L
private const val PlaybackBarShadowHeightDp = 6f
private val LegacyPlaybackBarArtworkDecodeSize = Size(128, 128)
