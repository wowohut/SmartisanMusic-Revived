package com.smartisanos.music.ui.playback

import android.widget.SeekBar
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartisanos.music.R
import com.smartisanos.music.ui.components.SmartisanTitleBarSurface
import com.smartisanos.music.ui.components.SmartisanTitleBarSurfaceStyle
import com.smartisanos.music.ui.widgets.ThumbOnlySeekBar
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun PlaybackTopBar(
    title: String,
    artist: String,
    topInset: Dp,
    onCollapse: () -> Unit,
) {
    SmartisanTitleBarSurface(
        style = SmartisanTitleBarSurfaceStyle.Playback,
        modifier = Modifier
            .fillMaxWidth()
            .height(topInset + 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressedDrawableButton(
                normalRes = R.drawable.btn_playing_back,
                pressedRes = R.drawable.btn_playing_back_down,
                contentDescription = stringResource(R.string.collapse_player),
                modifier = Modifier
                    .width(40.dp)
                    .height(30.dp),
                onClick = onCollapse,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = PlaybackTitleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = PlaybackArtistStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(30.dp),
            )
        }
    }
}

@Composable
internal fun PlaybackTimeSeekBar(
    durationMs: Long,
    currentPositionMs: Long,
    @DrawableRes thumbRes: Int,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit,
) {
    val duration = durationMs.coerceAtLeast(0L)
    val currentFraction = if (duration > 0L) {
        currentPositionMs.toFloat() / duration.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current
    val shownFraction = if (dragFraction.isNaN()) currentFraction else dragFraction.coerceIn(0f, 1f)
    val shownPosition = if (duration > 0L) {
        (shownFraction * duration.toFloat()).roundToLong()
    } else {
        0L
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlaybackSeekBarHeight)
                .padding(horizontal = PlaybackContentHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlaybackTime(shownPosition),
                style = PlaybackTimeStyle.copy(textAlign = TextAlign.Start),
                maxLines = 1,
                modifier = Modifier.width(PlaybackSeekTimeWidth),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { trackWidthPx = it.width }
                    .pointerInput(duration) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (trackWidthPx > 0) {
                                    dragFraction = fractionFromPosition(offset.x, trackWidthPx)
                                    if (duration > 0L) {
                                        onSeek((dragFraction * duration.toFloat()).roundToLong())
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (trackWidthPx > 0) {
                                    dragFraction = fractionFromPosition(change.position.x, trackWidthPx)
                                    if (duration > 0L) {
                                        onSeek((dragFraction * duration.toFloat()).roundToLong())
                                    }
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                val finalFraction = dragFraction.takeUnless { it.isNaN() } ?: currentFraction
                                if (duration > 0L) {
                                    onSeek((finalFraction * duration.toFloat()).roundToLong())
                                }
                                dragFraction = Float.NaN
                            },
                            onDragCancel = {
                                dragFraction = Float.NaN
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlaybackSeekTrackHeight)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(PlaybackTrackColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shownFraction)
                        .height(PlaybackSeekTrackHeight)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(PlaybackTrackFillColor),
                )
                Image(
                    painter = painterResource(thumbRes),
                    contentDescription = null,
                    modifier = Modifier
                        .width(PlaybackSeekThumbWidth)
                        .height(PlaybackSeekThumbHeight)
                        .align(Alignment.CenterStart)
                        .offset {
                            val thumbWidthPx = with(density) { PlaybackSeekThumbWidth.roundToPx() }
                            IntOffset(
                                x = ((trackWidthPx * shownFraction) - (thumbWidthPx / 2f)).roundToInt(),
                                y = 0,
                            )
                        },
                )
            }
            Text(
                text = "-${formatPlaybackTime((duration - shownPosition).coerceAtLeast(0L))}",
                style = PlaybackTimeStyle.copy(textAlign = TextAlign.End),
                maxLines = 1,
                modifier = Modifier.width(PlaybackSeekTimeWidth),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlaybackSeekBarDividerHeight)
                .background(PlaybackTopBarDivider.copy(alpha = 0.7f)),
        )
    }
}

@Composable
internal fun PlaybackControlButtons(
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    controlWidth: Dp,
    entranceTimeMillis: Float,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
) {
    val buttonMetrics = playbackControlButtonMetrics(controlWidth)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PlaybackControlButtonsTopPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val repeatIconRes = playbackRepeatButtonRes(repeatMode)
        PressedDrawableButton(
            normalRes = repeatIconRes,
            pressedRes = repeatIconRes,
            contentDescription = stringResource(repeatContentDescriptionRes(repeatMode)),
            modifier = Modifier
                .width(buttonMetrics.outerWidth)
                .height(buttonMetrics.height)
                .then(
                    playbackControlEntranceModifier(
                        timeMillis = entranceTimeMillis,
                        delayMillis = PlaybackOuterButtonAlphaDelayMillis,
                        durationMillis = PlaybackOuterButtonAlphaDurationMillis,
                        offsetY = PlaybackControlEntranceOffset,
                        animateY = false,
                    ),
                ),
            onClick = onRepeatClick,
        )
        PressedDrawableButton(
            normalRes = R.drawable.btn_playing_prev,
            pressedRes = R.drawable.btn_playing_prev_down,
            contentDescription = stringResource(R.string.previous_song),
            modifier = Modifier
                .width(buttonMetrics.sideWidth)
                .height(buttonMetrics.height)
                .then(
                    playbackControlEntranceModifier(
                        timeMillis = entranceTimeMillis,
                        delayMillis = PlaybackSideButtonEntranceDelayMillis,
                        durationMillis = PlaybackControlEntranceDurationMillis,
                        offsetY = PlaybackControlEntranceOffset,
                    ),
                ),
            onClick = onPreviousClick,
        )
        PressedDrawableButton(
            normalRes = if (isPlaying) {
                R.drawable.btn_playing_pause
            } else {
                R.drawable.btn_playing_play
            },
            pressedRes = if (isPlaying) {
                R.drawable.btn_playing_pause_down
            } else {
                R.drawable.btn_playing_play_down
            },
            contentDescription = if (isPlaying) {
                stringResource(R.string.pause)
            } else {
                stringResource(R.string.play)
            },
            modifier = Modifier
                .width(buttonMetrics.playWidth)
                .height(buttonMetrics.height)
                .then(
                    playbackControlEntranceModifier(
                        timeMillis = entranceTimeMillis,
                        delayMillis = PlaybackPlayButtonEntranceDelayMillis,
                        durationMillis = PlaybackControlEntranceDurationMillis,
                        offsetY = PlaybackControlEntranceOffset,
                    ),
                ),
            onClick = onPlayPauseClick,
        )
        PressedDrawableButton(
            normalRes = R.drawable.btn_playing_next,
            pressedRes = R.drawable.btn_playing_next_down,
            contentDescription = stringResource(R.string.next_song),
            modifier = Modifier
                .width(buttonMetrics.sideWidth)
                .height(buttonMetrics.height)
                .then(
                    playbackControlEntranceModifier(
                        timeMillis = entranceTimeMillis,
                        delayMillis = PlaybackSideButtonEntranceDelayMillis,
                        durationMillis = PlaybackControlEntranceDurationMillis,
                        offsetY = PlaybackControlEntranceOffset,
                    ),
                ),
            onClick = onNextClick,
        )
        PressedDrawableButton(
            normalRes = if (shuffleEnabled) {
                R.drawable.btn_playing_shuffle_on
            } else {
                R.drawable.btn_playing_shuffle_off
            },
            pressedRes = if (shuffleEnabled) {
                R.drawable.btn_playing_shuffle_on
            } else {
                R.drawable.btn_playing_shuffle_off
            },
            contentDescription = stringResource(R.string.shuffle),
            modifier = Modifier
                .width(buttonMetrics.outerWidth)
                .height(buttonMetrics.height)
                .then(
                    playbackControlEntranceModifier(
                        timeMillis = entranceTimeMillis,
                        delayMillis = PlaybackOuterButtonAlphaDelayMillis,
                        durationMillis = PlaybackOuterButtonAlphaDurationMillis,
                        offsetY = PlaybackControlEntranceOffset,
                        animateY = false,
                    ),
                ),
            onClick = onShuffleClick,
        )
    }
}

private data class PlaybackControlButtonMetrics(
    val outerWidth: Dp,
    val sideWidth: Dp,
    val playWidth: Dp,
    val height: Dp,
)

internal val PlaybackBottomControlsMinimumWidth =
    (OriginalTurntableBaseWidthDp * PlaybackMinimumTouchTargetSize.value /
        PlaybackControlOuterButtonBaseWidthDp).dp

private fun playbackControlButtonMetrics(controlWidth: Dp): PlaybackControlButtonMetrics {
    val width = controlWidth.value
    return when {
        width >= 432f -> PlaybackControlButtonMetrics(
            outerWidth = 77.dp,
            sideWidth = 84.dp,
            playWidth = 102.3.dp,
            height = 104.5.dp,
        )
        width >= 411f -> PlaybackControlButtonMetrics(
            outerWidth = 77.dp,
            sideWidth = 80.1.dp,
            playWidth = 98.dp,
            height = 99.5.dp,
        )
        else -> {
            val scale = width / OriginalTurntableBaseWidthDp
            PlaybackControlButtonMetrics(
                outerWidth = PlaybackControlOuterButtonBaseWidthDp.dp * scale,
                sideWidth = PlaybackControlSideButtonBaseWidthDp.dp * scale,
                playWidth = PlaybackControlPlayButtonBaseWidthDp.dp * scale,
                height = PlaybackControlButtonBaseHeightDp.dp * scale,
            )
        }
    }
}

private const val PlaybackControlOuterButtonBaseWidthDp = 67.3f
private const val PlaybackControlSideButtonBaseWidthDp = 70f
private const val PlaybackControlPlayButtonBaseWidthDp = 85.3f
private const val PlaybackControlButtonBaseHeightDp = 87f

@Composable
private fun playbackControlEntranceModifier(
    timeMillis: Float,
    delayMillis: Int,
    durationMillis: Int,
    offsetY: Dp,
    animateY: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val progress = playbackEntranceProgress(
        timeMillis = timeMillis,
        delayMillis = delayMillis,
        durationMillis = durationMillis,
    )
    val offsetYPx = with(density) {
        offsetY.roundToPx().toFloat()
    }
    return Modifier.graphicsLayer {
        if (animateY) {
            translationY = (1f - progress) * offsetYPx
        }
        alpha = if (animateY) 1f else progress
    }
}

@Composable
internal fun PlaybackVolumeBar(
    value: Float,
    width: Dp,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) {
        PlaybackVolumeHorizontalPadding.roundToPx()
    }
    val thumbOffsetPx = with(density) { PlaybackVolumeThumbOffset.roundToPx() }
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    AndroidView(
        factory = { context ->
            ThumbOnlySeekBar(context).apply {
                max = 100
                splitTrack = false
                contentDescription = context.getString(R.string.volume)
                progressDrawable = ContextCompat.getDrawable(context, R.drawable.volume_seekbar_progress)
                thumb = ContextCompat.getDrawable(context, R.drawable.playing_control_volume)
                thumbOffset = thumbOffsetPx
                setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            if (fromUser) {
                                latestOnValueChange.value(progress / 100f)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            }
        },
        update = { seekBar ->
            val targetProgress = (value.coerceIn(0f, 1f) * 100f).roundToInt()
            if (seekBar.progress != targetProgress) {
                seekBar.progress = targetProgress
            }
            if (seekBar.thumbOffset != thumbOffsetPx) {
                seekBar.thumbOffset = thumbOffsetPx
            }
            if (
                seekBar.paddingLeft != horizontalPaddingPx ||
                seekBar.paddingRight != horizontalPaddingPx
            ) {
                seekBar.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0)
            }
        },
        modifier = modifier
            .width(width)
            .height(PlaybackVolumeBarHeight),
    )
}
