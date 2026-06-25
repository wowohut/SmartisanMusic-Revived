package com.smartisanos.music.ui.playback

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.smartisanos.music.R
import com.smartisanos.music.playback.EmbeddedLyrics
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun PlaybackVisualStage(
    currentVisualPage: PlaybackVisualPage,
    coverPositionMs: Long,
    lyricsPositionMs: Long,
    durationMs: Long,
    scratchEnabled: Boolean,
    hidePlayerAxisEnabled: Boolean,
    albumArtwork: ImageBitmap?,
    keepLyricsScreenAwake: Boolean,
    embeddedLyrics: EmbeddedLyrics?,
    fallbackLyricsLines: List<String>,
    hasMediaItem: Boolean,
    isPlaying: Boolean,
    coverDragMode: CoverDragMode,
    previewPositionMs: Long?,
    needlePreviewRotationDegrees: Float?,
    needleParkedOutside: Boolean,
    discManualRotationOffsetDegrees: Float,
    mediaId: String?,
    onMoreClick: () -> Unit,
    onVisualPageToggle: () -> Unit,
    onKeepLyricsScreenAwakeToggle: () -> Unit,
    onDiscScratchStart: () -> Unit,
    onDiscScratchMotion: (Long, Float) -> Unit,
    onDiscScratchPositionChange: (Long, Float) -> Unit,
    onDiscScratchEnd: (Long, Float) -> Unit,
    onDiscScratchCancel: () -> Unit,
    onNeedleSeekStart: (Float, Long?) -> Unit,
    onNeedleSeekPositionChange: (Float, Long?) -> Unit,
    onNeedleSeekEnd: (Float, Long?) -> Unit,
    onNeedleSeekCancel: () -> Unit,
    onTurntableWidthChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val turntableWidth = playbackVisualStageWidth(maxWidth, maxHeight)
        if (turntableWidth <= 0.dp) {
            return@BoxWithConstraints
        }
        LaunchedEffect(turntableWidth) {
            onTurntableWidthChanged(turntableWidth)
        }
        val scale = turntableWidth.value / OriginalTurntableBaseWidthDp
        val turntableHeight = turntableWidth * PlaybackTurntableHeightToWidthRatio
        val moreButtonMargin = 12.dp * scale
        val moreButtonTopMargin = 38.dp * scale
        val actionButtonSize = PlaybackActionButtonSize * scale
        val isLyricsPage = currentVisualPage == PlaybackVisualPage.Lyrics

        Box(
            modifier = Modifier
                .width(turntableWidth)
                .height(turntableWidth * PlaybackVisualStageHeightToWidthRatio),
        ) {
            PressedDrawableButton(
                normalRes = R.drawable.more_btn,
                pressedRes = R.drawable.more_btn_down,
                contentDescription = stringResource(R.string.player_more_actions),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(2f)
                    .padding(start = moreButtonMargin, top = moreButtonTopMargin)
                    .size(actionButtonSize),
                onClick = onMoreClick,
            )
            AnimatedVisibility(
                visible = isLyricsPage,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = PlaybackVisualPageEnterDurationMillis,
                        delayMillis = PlaybackLyricsActionEnterDelayMillis,
                        easing = PlaybackLegacyDecelerateEasing,
                    ),
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = PlaybackVisualPageEnterDurationMillis,
                        delayMillis = PlaybackLyricsActionEnterDelayMillis,
                        easing = PlaybackLegacyDecelerateEasing,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = PlaybackVisualPageExitDurationMillis,
                        easing = PlaybackLegacyDecelerateEasing,
                    ),
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = PlaybackVisualPageExitDurationMillis,
                        easing = PlaybackLegacyDecelerateEasing,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(2f)
                    .padding(end = moreButtonMargin, top = moreButtonTopMargin),
            ) {
                val screenSwitchNormalRes = if (keepLyricsScreenAwake) {
                    R.drawable.sun_btn_on
                } else {
                    R.drawable.sun_btn_off
                }
                val screenSwitchPressedRes = if (keepLyricsScreenAwake) {
                    R.drawable.sun_btn_on_down
                } else {
                    R.drawable.sun_btn_off_down
                }
                PressedDrawableButton(
                    normalRes = screenSwitchNormalRes,
                    pressedRes = screenSwitchPressedRes,
                    contentDescription = stringResource(R.string.always_on),
                    modifier = Modifier.size(actionButtonSize),
                    onClick = onKeepLyricsScreenAwakeToggle,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(turntableWidth)
                    .height(turntableHeight),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentVisualPage,
                    transitionSpec = {
                        playbackVisualPageTransform(
                            enteringLyrics = targetState == PlaybackVisualPage.Lyrics,
                        )
                    },
                    label = "playbackVisualPage",
                    modifier = Modifier.matchParentSize(),
                ) { targetPage ->
                    when (targetPage) {
                        PlaybackVisualPage.Cover -> PlaybackCoverPage(
                            turntableWidth = turntableWidth,
                            scale = scale,
                            currentPositionMs = coverPositionMs,
                            durationMs = durationMs,
                            scratchEnabled = scratchEnabled,
                            hidePlayerAxisEnabled = hidePlayerAxisEnabled,
                            albumArtwork = albumArtwork,
                            hasMediaItem = hasMediaItem,
                            isPlaying = isPlaying,
                            coverDragMode = coverDragMode,
                            previewPositionMs = previewPositionMs,
                            needlePreviewRotationDegrees = needlePreviewRotationDegrees,
                            needleParkedOutside = needleParkedOutside,
                            discManualRotationOffsetDegrees = discManualRotationOffsetDegrees,
                            mediaId = mediaId,
                            onVisualPageToggle = onVisualPageToggle,
                            onDiscScratchStart = onDiscScratchStart,
                            onDiscScratchMotion = onDiscScratchMotion,
                            onDiscScratchPositionChange = onDiscScratchPositionChange,
                            onDiscScratchEnd = onDiscScratchEnd,
                            onDiscScratchCancel = onDiscScratchCancel,
                            onNeedleSeekStart = onNeedleSeekStart,
                            onNeedleSeekPositionChange = onNeedleSeekPositionChange,
                            onNeedleSeekEnd = onNeedleSeekEnd,
                            onNeedleSeekCancel = onNeedleSeekCancel,
                            modifier = Modifier.matchParentSize(),
                        )

                        PlaybackVisualPage.Lyrics -> PlaybackLyricsPage(
                            mediaId = mediaId,
                            lyrics = embeddedLyrics,
                            fallbackLyricsLines = fallbackLyricsLines,
                            currentPositionMs = lyricsPositionMs,
                            onVisualPageToggle = onVisualPageToggle,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
    }
}

private fun playbackVisualStageWidth(maxWidth: Dp, maxHeight: Dp): Dp {
    val heightBoundWidth = maxHeight.value / PlaybackVisualStageHeightToWidthRatio
    val width = minOf(maxWidth.value, heightBoundWidth)
        .takeIf { it.isFinite() }
        ?.coerceAtLeast(0f)
        ?: 0f
    return width.dp
}

private const val PlaybackTurntableHeightToWidthRatio = 356.5938f / OriginalTurntableBaseWidthDp
private const val PlaybackVisualStageHeightToWidthRatio =
    (356.5938f + 52f) / OriginalTurntableBaseWidthDp

private fun playbackVisualPageTransform(
    enteringLyrics: Boolean,
): ContentTransform {
    val enterOffsetDirection = if (enteringLyrics) 1 else -1
    val exitOffsetDirection = if (enteringLyrics) -1 else 1
    val enterScale = if (enteringLyrics) 0.985f else 1.015f
    val exitScale = if (enteringLyrics) 1.015f else 0.985f
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = PlaybackVisualPageEnterDurationMillis,
            delayMillis = PlaybackVisualPageEnterDelayMillis,
            easing = PlaybackLegacyDecelerateEasing,
        ),
    ) + scaleIn(
        initialScale = enterScale,
        animationSpec = tween(
            durationMillis = PlaybackVisualPageEnterDurationMillis,
            delayMillis = PlaybackVisualPageEnterDelayMillis,
            easing = PlaybackLegacyDecelerateEasing,
        ),
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = PlaybackVisualPageEnterDurationMillis,
            delayMillis = PlaybackVisualPageEnterDelayMillis,
            easing = PlaybackLegacyDecelerateEasing,
        ),
    ) { fullHeight -> enterOffsetDirection * (fullHeight / 36) }
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = PlaybackVisualPageExitDurationMillis,
            easing = PlaybackLegacyDecelerateEasing,
        ),
    ) + scaleOut(
        targetScale = exitScale,
        animationSpec = tween(
            durationMillis = PlaybackVisualPageExitDurationMillis,
            easing = PlaybackLegacyDecelerateEasing,
        ),
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = PlaybackVisualPageExitDurationMillis,
            easing = PlaybackLegacyDecelerateEasing,
        ),
    ) { fullHeight -> exitOffsetDirection * (fullHeight / 42) }

    return enter togetherWith exit
}

@Composable
private fun PlaybackCoverPage(
    turntableWidth: Dp,
    scale: Float,
    currentPositionMs: Long,
    durationMs: Long,
    scratchEnabled: Boolean,
    hidePlayerAxisEnabled: Boolean,
    albumArtwork: ImageBitmap?,
    hasMediaItem: Boolean,
    isPlaying: Boolean,
    coverDragMode: CoverDragMode,
    previewPositionMs: Long?,
    needlePreviewRotationDegrees: Float?,
    needleParkedOutside: Boolean,
    discManualRotationOffsetDegrees: Float,
    mediaId: String?,
    onVisualPageToggle: () -> Unit,
    onDiscScratchStart: () -> Unit,
    onDiscScratchMotion: (Long, Float) -> Unit,
    onDiscScratchPositionChange: (Long, Float) -> Unit,
    onDiscScratchEnd: (Long, Float) -> Unit,
    onDiscScratchCancel: () -> Unit,
    onNeedleSeekStart: (Float, Long?) -> Unit,
    onNeedleSeekPositionChange: (Float, Long?) -> Unit,
    onNeedleSeekEnd: (Float, Long?) -> Unit,
    onNeedleSeekCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = durationMs
        .takeIf { it > 0L }
        ?.let { currentPositionMs.toFloat() / it.toFloat() }
        ?.coerceIn(0f, 1f)
        ?: 0f
    val targetNeedleRotation = needlePreviewRotationDegrees ?: run {
        if (hasMediaItem && !(needleParkedOutside && !isPlaying)) {
            NeedlePlaybackStartRotationDegrees + (progress * NeedlePlaybackSweepDegrees)
        } else {
            NeedleRestRotationDegrees
        }
    }

    val needleAnimatable = remember { Animatable(targetNeedleRotation) }
    var prevMediaId by remember { mutableStateOf(mediaId) }
    val needleSeekDragging = coverDragMode == CoverDragMode.NeedleSeek
    var needleLiftHeldAfterSeek by remember { mutableStateOf(false) }

    LaunchedEffect(needleSeekDragging, needleParkedOutside) {
        if (needleSeekDragging) {
            needleLiftHeldAfterSeek = true
            return@LaunchedEffect
        }
        if (needleLiftHeldAfterSeek && !needleParkedOutside) {
            delay(NeedleLiftHoldAfterReleaseMs)
        }
        needleLiftHeldAfterSeek = false
    }

    val needleLiftFraction by animateFloatAsState(
        targetValue = if (needleSeekDragging || needleLiftHeldAfterSeek) 1f else 0f,
        animationSpec = tween(if (needleSeekDragging || needleLiftHeldAfterSeek) 125 else 250),
        label = "needleLiftFraction",
    )

    LaunchedEffect(mediaId, targetNeedleRotation, needleSeekDragging) {
        if (prevMediaId != mediaId) {
            prevMediaId = mediaId
            return@LaunchedEffect
        }
        if (needleSeekDragging) {
            needleAnimatable.snapTo(targetNeedleRotation)
        } else {
            needleAnimatable.animateTo(targetNeedleRotation, animationSpec = tween(220))
        }
    }

    val needleRotation = needleAnimatable.value
    val density = LocalDensity.current
    val densityPxPerDp = density.density
    var discSize by remember { mutableStateOf(IntSize.Zero) }
    val latestDiscSize by rememberUpdatedState(discSize)
    val latestVisualPageToggle by rememberUpdatedState(onVisualPageToggle)
    val scratchAvailable by rememberUpdatedState(scratchEnabled && durationMs > 0L)
    val needleSeekAvailable by rememberUpdatedState(scratchEnabled && hasMediaItem && durationMs > 0L)
    val latestNeedleRotation by rememberUpdatedState(needleRotation)
    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestDiscScratchStart by rememberUpdatedState(onDiscScratchStart)
    val latestDiscScratchMotion by rememberUpdatedState(onDiscScratchMotion)
    val latestDiscScratchPositionChange by rememberUpdatedState(onDiscScratchPositionChange)
    val latestDiscScratchEnd by rememberUpdatedState(onDiscScratchEnd)
    val latestDiscScratchCancel by rememberUpdatedState(onDiscScratchCancel)
    val latestNeedleSeekStart by rememberUpdatedState(onNeedleSeekStart)
    val latestNeedleSeekPositionChange by rememberUpdatedState(onNeedleSeekPositionChange)
    val latestNeedleSeekEnd by rememberUpdatedState(onNeedleSeekEnd)
    val latestNeedleSeekCancel by rememberUpdatedState(onNeedleSeekCancel)
    val discRunning = isPlaying &&
        coverDragMode == CoverDragMode.None &&
        previewPositionMs == null
    val discRotationCycleDurationMs = if (coverDragMode == CoverDragMode.DiscScratch) {
        ScratchCycleDurationMs
    } else {
        PlaybackDiscCycleDurationMs
    }

    Box(modifier = modifier) {
        PlaybackTurntableDisc(
            albumArtwork = albumArtwork,
            running = discRunning,
            rotationCycleDurationMs = discRotationCycleDurationMs,
            manualRotationOffsetDegrees = discManualRotationOffsetDegrees,
            hidePlayerAxisEnabled = hidePlayerAxisEnabled,
            turntableWidth = turntableWidth,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { discSize = it }
                .zIndex(3f)
                .pointerInput(scratchAvailable, needleSeekAvailable, densityPxPerDp, scale) {
                    val tapTouchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val size = latestDiscSize
                        val center = discCenter(size)
                        val radius = discRadius(size)
                        val withinNeedleSeekRegion =
                            needleSeekAvailable && isWithinNeedleSeekRegion(
                                point = down.position,
                                containerSize = size,
                                densityPxPerDp = densityPxPerDp,
                                turntableScale = scale,
                                rotationDegrees = latestNeedleRotation,
                            )
                        val withinScratchRegion =
                            scratchAvailable && isWithinScratchRegion(down.position, center, radius)
                        val dragMode = when {
                            withinNeedleSeekRegion -> CoverDragMode.NeedleSeek
                            withinScratchRegion -> CoverDragMode.DiscScratch
                            else -> CoverDragMode.None
                        }

                        when (dragMode) {
                            CoverDragMode.None -> {
                                val initialPosition = down.position
                                var finalPosition = initialPosition
                                var maxMoveDistance = 0f
                                val pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: break
                                    finalPosition = change.position
                                    val moveDistance = distanceBetween(initialPosition, finalPosition)
                                    if (moveDistance > maxMoveDistance) {
                                        maxMoveDistance = moveDistance
                                    }
                                    if (!change.pressed) {
                                        if (
                                            maxMoveDistance <= tapTouchSlop &&
                                            isDiscTapWithinSlop(
                                                initialPosition = initialPosition,
                                                finalPosition = finalPosition,
                                                maxMoveDistance = maxMoveDistance,
                                                center = center,
                                                radius = radius,
                                                tapTouchSlop = tapTouchSlop,
                                            )
                                        ) {
                                            latestVisualPageToggle()
                                        }
                                        break
                                    }
                                }
                            }

                            CoverDragMode.DiscScratch -> {
                                val initialPosition = down.position
                                var finalPosition = initialPosition
                                var maxMoveDistance = 0f
                                var scratchPositionMs = 0L
                                var lastAngleDegrees = angleDegrees(initialPosition, center)
                                var lastMotionUptimeMs = down.uptimeMillis
                                var lastAngularVelocityDegreesPerSecond = 0f
                                var lastScratchDirection = 0
                                val scratchMotionSamples = ArrayDeque<ScratchMotionSample>()
                                val pointerId = down.id
                                var scratchStarted = false
                                var cancelled = true
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null) {
                                        break
                                    }
                                    finalPosition = change.position
                                    maxMoveDistance = max(
                                        maxMoveDistance,
                                        distanceBetween(initialPosition, finalPosition),
                                    )
                                    if (!change.pressed) {
                                        if (scratchStarted) {
                                            latestDiscScratchEnd(
                                                scratchPositionMs,
                                                scratchReleaseVelocityDegreesPerSecond(
                                                    angularVelocityDegreesPerSecond =
                                                        lastAngularVelocityDegreesPerSecond,
                                                    pixelFlingVelocityDegreesPerSecond =
                                                        scratchPixelFlingVelocityDegreesPerSecond(
                                                            samples = scratchMotionSamples,
                                                            releasePosition = change.position,
                                                            releaseUptimeMs = change.uptimeMillis,
                                                        ),
                                                    releaseDelayMs = change.uptimeMillis - lastMotionUptimeMs,
                                                    directionHint = lastScratchDirection,
                                                ),
                                            )
                                            change.consume()
                                        } else if (
                                            isDiscTapWithinSlop(
                                                initialPosition = initialPosition,
                                                finalPosition = finalPosition,
                                                maxMoveDistance = maxMoveDistance,
                                                center = center,
                                                radius = radius,
                                                tapTouchSlop = tapTouchSlop,
                                            )
                                        ) {
                                            latestVisualPageToggle()
                                        }
                                        cancelled = false
                                        break
                                    }
                                    if (!scratchStarted) {
                                        if (maxMoveDistance <= tapTouchSlop) {
                                            continue
                                        }
                                        scratchStarted = true
                                        scratchPositionMs = scratchStartPosition(
                                            positionMs = latestPositionMs,
                                            durationMs = latestDurationMs,
                                        )
                                        lastAngleDegrees = angleDegrees(change.position, center)
                                        lastMotionUptimeMs = change.uptimeMillis
                                        lastAngularVelocityDegreesPerSecond = 0f
                                        lastScratchDirection = 0
                                        scratchMotionSamples.clear()
                                        recordScratchMotionSample(
                                            samples = scratchMotionSamples,
                                            position = change.position,
                                            uptimeMs = change.uptimeMillis,
                                        )
                                        latestDiscScratchStart()
                                        latestDiscScratchPositionChange(scratchPositionMs, 0f)
                                        change.consume()
                                        continue
                                    }

                                    val currentAngle = angleDegrees(change.position, center)
                                    val deltaAngle = normalizeAngleDelta(currentAngle - lastAngleDegrees)
                                        .coerceIn(
                                            -ScratchMaxAngleStepDegrees,
                                            ScratchMaxAngleStepDegrees,
                                        )
                                    lastAngleDegrees = currentAngle
                                    val deltaTimeMs = (change.uptimeMillis - lastMotionUptimeMs)
                                        .coerceAtLeast(1L)
                                    lastAngularVelocityDegreesPerSecond = (
                                        deltaAngle * 1_000f / deltaTimeMs.toFloat()
                                    ).coerceIn(
                                        -ScratchVelocityMaxDegreesPerSecond,
                                        ScratchVelocityMaxDegreesPerSecond,
                                    )
                                    if (deltaAngle != 0f) {
                                        lastScratchDirection = if (deltaAngle < 0f) -1 else 1
                                    }
                                    lastMotionUptimeMs = change.uptimeMillis
                                    recordScratchMotionSample(
                                        samples = scratchMotionSamples,
                                        position = change.position,
                                        uptimeMs = change.uptimeMillis,
                                    )

                                    val targetPosition = scratchPositionAfterAngle(
                                        positionMs = scratchPositionMs,
                                        deltaAngleDegrees = deltaAngle,
                                        durationMs = latestDurationMs,
                                    )
                                    latestDiscScratchMotion(targetPosition, deltaAngle)
                                    if (targetPosition != scratchPositionMs) {
                                        scratchPositionMs = targetPosition
                                        latestDiscScratchPositionChange(targetPosition, deltaAngle)
                                    }
                                    change.consume()
                                }
                                if (cancelled && scratchStarted) {
                                    latestDiscScratchCancel()
                                }
                            }

                            CoverDragMode.NeedleSeek -> {
                                val initialPosition = down.position
                                var maxMoveDistance = 0f
                                var needleRotationDegrees = latestNeedleRotation
                                    .coerceIn(NeedleRestRotationDegrees, NeedlePlaybackEndRotationDegrees)
                                var needlePositionMs = needleSeekPositionFromRotation(
                                    rotationDegrees = needleRotationDegrees,
                                    durationMs = latestDurationMs,
                                )
                                var needleSeekHadPlayablePosition = needlePositionMs != null
                                var needlePivot = playbackNeedleGeometry(
                                    containerSize = size,
                                    densityPxPerDp = densityPxPerDp,
                                    turntableScale = scale,
                                    rotationDegrees = needleRotationDegrees,
                                ).pivot
                                var lastNeedleAngleDegrees = angleDegrees(initialPosition, needlePivot)
                                val pointerId = down.id
                                var needleSeekStarted = false
                                var cancelled = true
                                val outsideActivationDistancePx =
                                    NeedleSeekOutsideActivationDistanceDp * densityPxPerDp
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null) {
                                        break
                                    }
                                    maxMoveDistance = max(
                                        maxMoveDistance,
                                        distanceBetween(initialPosition, change.position),
                                    )
                                    if (!change.pressed) {
                                        if (needleSeekStarted) {
                                            if (needleSeekHadPlayablePosition || needlePositionMs != null) {
                                                latestNeedleSeekEnd(needleRotationDegrees, needlePositionMs)
                                            } else {
                                                latestNeedleSeekCancel()
                                            }
                                            change.consume()
                                        }
                                        cancelled = false
                                        break
                                    }
                                    if (!needleSeekStarted) {
                                        if (maxMoveDistance <= tapTouchSlop) {
                                            continue
                                        }
                                        val candidateNeedleAngleDegrees =
                                            angleDegrees(change.position, needlePivot)
                                        val candidateDeltaNeedleAngle = normalizeAngleDelta(
                                            candidateNeedleAngleDegrees - lastNeedleAngleDegrees,
                                        ).coerceIn(
                                            -ScratchMaxAngleStepDegrees,
                                            ScratchMaxAngleStepDegrees,
                                        )
                                        val candidateNeedleRotationDegrees =
                                            (needleRotationDegrees + candidateDeltaNeedleAngle)
                                                .coerceIn(
                                                    NeedleRestRotationDegrees,
                                                    NeedlePlaybackEndRotationDegrees,
                                                )
                                        val candidateNeedlePositionMs = needleSeekPositionFromRotation(
                                            rotationDegrees = candidateNeedleRotationDegrees,
                                            durationMs = latestDurationMs,
                                        )
                                        if (
                                            !shouldStartNeedleSeekDrag(
                                                initialPositionMs = needlePositionMs,
                                                candidatePositionMs = candidateNeedlePositionMs,
                                                maxMoveDistance = maxMoveDistance,
                                                outsideActivationDistancePx =
                                                    outsideActivationDistancePx,
                                            )
                                        ) {
                                            continue
                                        }
                                        needleSeekStarted = true
                                        needlePivot = playbackNeedleGeometry(
                                            containerSize = size,
                                            densityPxPerDp = densityPxPerDp,
                                            turntableScale = scale,
                                            rotationDegrees = candidateNeedleRotationDegrees,
                                        ).pivot
                                        needleRotationDegrees = candidateNeedleRotationDegrees
                                        needlePositionMs = candidateNeedlePositionMs
                                        if (needlePositionMs != null) {
                                            needleSeekHadPlayablePosition = true
                                        }
                                        lastNeedleAngleDegrees = angleDegrees(change.position, needlePivot)
                                        latestNeedleSeekStart(needleRotationDegrees, needlePositionMs)
                                        change.consume()
                                        continue
                                    }

                                    val currentNeedleAngleDegrees = angleDegrees(change.position, needlePivot)
                                    val deltaNeedleAngle = normalizeAngleDelta(
                                        currentNeedleAngleDegrees - lastNeedleAngleDegrees,
                                    ).coerceIn(
                                        -ScratchMaxAngleStepDegrees,
                                        ScratchMaxAngleStepDegrees,
                                    )
                                    lastNeedleAngleDegrees = currentNeedleAngleDegrees
                                    needleRotationDegrees = (needleRotationDegrees + deltaNeedleAngle)
                                        .coerceIn(
                                            NeedleRestRotationDegrees,
                                            NeedlePlaybackEndRotationDegrees,
                                        )
                                    needlePositionMs = needleSeekPositionFromRotation(
                                        rotationDegrees = needleRotationDegrees,
                                        durationMs = latestDurationMs,
                                    )
                                    if (needlePositionMs != null) {
                                        needleSeekHadPlayablePosition = true
                                    }
                                    latestNeedleSeekPositionChange(needleRotationDegrees, needlePositionMs)
                                    change.consume()
                                }
                                if (cancelled && needleSeekStarted) {
                                    latestNeedleSeekCancel()
                                }
                            }
                        }
                    }
                },
        )
        OriginalNeedleStack(
            needleRotation = needleRotation,
            needleLiftFraction = needleLiftFraction,
            scale = scale,
            modifier = Modifier
                .matchParentSize()
                .zIndex(2f),
        )
    }
}

@Composable
private fun PlaybackLyricsPage(
    mediaId: String?,
    lyrics: EmbeddedLyrics?,
    fallbackLyricsLines: List<String>,
    currentPositionMs: Long,
    onVisualPageToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestVisualPageToggle by rememberUpdatedState(onVisualPageToggle)
    PlaybackLyricsOverlay(
        mediaId = mediaId,
        lyrics = lyrics,
        fallbackLines = fallbackLyricsLines,
        currentPositionMs = currentPositionMs,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        latestVisualPageToggle()
                    },
                )
            },
    )
}

@Composable
private fun PlaybackTurntableDisc(
    albumArtwork: ImageBitmap?,
    running: Boolean,
    rotationCycleDurationMs: Float,
    manualRotationOffsetDegrees: Float,
    hidePlayerAxisEnabled: Boolean,
    turntableWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val discRotation = rememberSmoothDiscRotation(
        running = running,
        cycleDurationMs = rotationCycleDurationMs,
        manualRotationOffsetDegrees = manualRotationOffsetDegrees,
    )

    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.playing_lp),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
        Image(
            painter = painterResource(R.drawable.playing_cover_lp),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = discRotation.value
                },
        )
        if (albumArtwork != null) {
            PlaybackTurntableAlbumArt(
                artwork = albumArtwork,
                turntableWidth = turntableWidth,
                discRotation = discRotation,
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(1f),
            )
            if (!hidePlayerAxisEnabled) {
                PlaybackTurntableAxisOverlay(
                    turntableWidth = turntableWidth,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(1.1f),
                )
            }
        }
    }
}

@Composable
private fun PlaybackTurntableAlbumArt(
    artwork: ImageBitmap,
    turntableWidth: Dp,
    discRotation: State<Float>,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = artwork,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(turntableWidth * PlaybackAlbumArtDiameterRatio)
            .graphicsLayer {
                rotationZ = discRotation.value
            }
            .clip(CircleShape),
    )
}

@Composable
private fun PlaybackTurntableAxisOverlay(
    turntableWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val axisBitmap = ImageBitmap.imageResource(id = R.drawable.playing_lp)
    val srcLeft = (axisBitmap.width - PlaybackTurntableAxisSourceDiameterPx) / 2
    val srcTop = (axisBitmap.height - PlaybackTurntableAxisSourceDiameterPx) / 2

    Canvas(
        modifier = modifier
            .size(turntableWidth * PlaybackTurntableAxisDiameterRatio)
            .clip(CircleShape),
    ) {
        drawImage(
            image = axisBitmap,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(
                PlaybackTurntableAxisSourceDiameterPx,
                PlaybackTurntableAxisSourceDiameterPx,
            ),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

private data class OriginalNeedleViews(
    val base: ImageView,
    val shadow: ImageView,
    val needle: ImageView,
    val top: ImageView,
)

private data class OriginalNeedleLayoutPx(
    val needleWidthPx: Int,
    val needleHeightPx: Int,
    val needleTopWidthPx: Int,
    val needleTopMarginPx: Int,
    val needleRightMarginPx: Int,
    val needleShadowRightMarginPx: Int,
    val needlePivotXPx: Float,
    val needlePivotYPx: Float,
)

@Composable
private fun OriginalNeedleStack(
    needleRotation: Float,
    needleLiftFraction: Float,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layout = remember(scale, density) {
        val metrics = originalNeedleMetrics(scale)
        OriginalNeedleLayoutPx(
            needleWidthPx = with(density) { metrics.widthDp.dp.roundToPx() },
            needleHeightPx = with(density) { metrics.heightDp.dp.roundToPx() },
            needleTopWidthPx = with(density) { metrics.topWidthDp.dp.roundToPx() },
            needleTopMarginPx = with(density) { metrics.topMarginDp.dp.roundToPx() },
            needleRightMarginPx = with(density) { metrics.rightMarginDp.dp.roundToPx() },
            needleShadowRightMarginPx = with(density) {
                metrics.shadowRightMarginDp.dp.roundToPx()
            },
            needlePivotXPx = with(density) { metrics.pivotXDp.dp.toPx() },
            needlePivotYPx = with(density) { metrics.pivotYDp.dp.toPx() },
        )
    }
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false

                val base = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setBackgroundResource(R.drawable.playing_stylus_lp_bg_original)
                }
                val shadow = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_END
                    setImageResource(R.drawable.needle_shadow2)
                }
                val needle = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageResource(R.drawable.playing_stylus_lp_original)
                }
                val top = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_START
                    setImageResource(R.drawable.playing_stylus_lp_top_original)
                }

                tag = OriginalNeedleViews(base, shadow, needle, top)
                addView(base)
                addView(shadow)
                addView(needle)
                addView(top)
            }
        },
        modifier = modifier,
        update = { frame ->
            val views = frame.tag as OriginalNeedleViews

            updateOriginalNeedleLayout(
                view = views.base,
                widthPx = layout.needleWidthPx,
                heightPx = layout.needleHeightPx,
                topMarginPx = layout.needleTopMarginPx,
                endMarginPx = layout.needleRightMarginPx,
            )
            updateOriginalNeedleLayout(
                view = views.shadow,
                widthPx = layout.needleWidthPx,
                heightPx = layout.needleHeightPx,
                topMarginPx = layout.needleTopMarginPx,
                endMarginPx = layout.needleShadowRightMarginPx,
            )
            updateOriginalNeedleLayout(
                view = views.needle,
                widthPx = layout.needleWidthPx,
                heightPx = layout.needleHeightPx,
                topMarginPx = layout.needleTopMarginPx,
                endMarginPx = layout.needleRightMarginPx,
            )
            updateOriginalNeedleLayout(
                view = views.top,
                widthPx = layout.needleTopWidthPx,
                heightPx = layout.needleHeightPx,
                topMarginPx = layout.needleTopMarginPx,
                endMarginPx = layout.needleRightMarginPx,
            )

            val liftedScaleY = 1f - ((1f - NeedleLiftScaleY) * needleLiftFraction)
            val shadowRotation = needleRotation -
                (NeedleLiftShadowRotationOffsetDegrees * needleLiftFraction)
            listOf(views.shadow, views.needle).forEach { view ->
                view.pivotX = layout.needlePivotXPx
                view.pivotY = layout.needlePivotYPx
                view.scaleY = liftedScaleY
            }
            views.shadow.rotation = shadowRotation
            views.needle.rotation = needleRotation
            views.base.rotation = 0f
            views.base.scaleY = 1f
            views.top.rotation = 0f
            views.top.scaleY = 1f
        },
    )
}

private fun updateOriginalNeedleLayout(
    view: ImageView,
    widthPx: Int,
    heightPx: Int,
    topMarginPx: Int,
    endMarginPx: Int,
) {
    val params = (view.layoutParams as? FrameLayout.LayoutParams)
    val gravity = Gravity.TOP or Gravity.END
    if (params == null) {
        view.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx, gravity).apply {
            topMargin = topMarginPx
            marginEnd = endMarginPx
            rightMargin = endMarginPx
        }
        return
    }
    if (
        params.width == widthPx &&
        params.height == heightPx &&
        params.gravity == gravity &&
        params.topMargin == topMarginPx &&
        params.marginEnd == endMarginPx &&
        params.rightMargin == endMarginPx
    ) {
        return
    }
    params.width = widthPx
    params.height = heightPx
    params.gravity = gravity
    params.topMargin = topMarginPx
    params.marginEnd = endMarginPx
    params.rightMargin = endMarginPx
    view.layoutParams = params
}

private data class ScratchMotionSample(
    val position: Offset,
    val uptimeMs: Long,
)

private fun scratchReleaseVelocityDegreesPerSecond(
    angularVelocityDegreesPerSecond: Float,
    pixelFlingVelocityDegreesPerSecond: Float,
    releaseDelayMs: Long,
    directionHint: Int,
): Float {
    val recentAngularVelocity = if (releaseDelayMs <= ScratchFlingReleaseTimeoutMs) {
        angularVelocityDegreesPerSecond
    } else {
        0f
    }
    val direction = when {
        recentAngularVelocity < 0f -> -1
        recentAngularVelocity > 0f -> 1
        directionHint < 0 -> -1
        directionHint > 0 -> 1
        else -> 0
    }
    if (direction == 0) {
        return 0f
    }
    val blendedVelocity = (
        abs(recentAngularVelocity) +
            pixelFlingVelocityDegreesPerSecond.coerceAtLeast(0f)
        ) / 2f
    if (blendedVelocity < ScratchFlingMinVelocityDegreesPerSecond) {
        return 0f
    }
    val signedVelocity = blendedVelocity * direction
    return signedVelocity.coerceIn(
        -ScratchVelocityMaxDegreesPerSecond,
        ScratchVelocityMaxDegreesPerSecond,
    )
}

private fun recordScratchMotionSample(
    samples: ArrayDeque<ScratchMotionSample>,
    position: Offset,
    uptimeMs: Long,
) {
    samples.addLast(ScratchMotionSample(position, uptimeMs))
    while (
        samples.size > 2 &&
        uptimeMs - samples.first().uptimeMs > ScratchFlingVelocitySampleWindowMs
    ) {
        samples.removeFirst()
    }
}

private fun scratchPixelFlingVelocityDegreesPerSecond(
    samples: ArrayDeque<ScratchMotionSample>,
    releasePosition: Offset,
    releaseUptimeMs: Long,
): Float {
    val sample = samples.firstOrNull {
        val ageMs = releaseUptimeMs - it.uptimeMs
        ageMs in ScratchFlingMinVelocitySampleMs..ScratchFlingVelocitySampleWindowMs
    } ?: samples.lastOrNull {
        releaseUptimeMs - it.uptimeMs >= ScratchFlingMinVelocitySampleMs
    } ?: return 0f

    val deltaTimeMs = (releaseUptimeMs - sample.uptimeMs).coerceAtLeast(1L).toFloat()
    val velocityX = (releasePosition.x - sample.position.x) * 1_000f / deltaTimeMs
    val velocityY = (releasePosition.y - sample.position.y) * 1_000f / deltaTimeMs
    return ((abs(velocityX) + abs(velocityY)) / ScratchPixelFlingDivisor)
        .coerceIn(0f, ScratchVelocityMaxDegreesPerSecond)
}
