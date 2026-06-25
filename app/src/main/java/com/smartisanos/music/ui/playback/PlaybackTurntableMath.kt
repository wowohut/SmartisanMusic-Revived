package com.smartisanos.music.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

internal data class OriginalNeedleMetrics(
    val widthDp: Float,
    val heightDp: Float,
    val topWidthDp: Float,
    val topMarginDp: Float,
    val rightMarginDp: Float,
    val shadowRightMarginDp: Float,
    val pivotXDp: Float,
    val pivotYDp: Float,
)

private val OriginalNeedleBaseMetrics = OriginalNeedleMetrics(
    widthDp = OriginalNeedleWidthBaseDp,
    heightDp = OriginalNeedleHeightBaseDp,
    topWidthDp = OriginalNeedleTopWidthBaseDp,
    topMarginDp = OriginalNeedleTopMarginBaseDp,
    rightMarginDp = OriginalNeedleRightMarginDp,
    shadowRightMarginDp = OriginalNeedleShadowRightMarginDp,
    pivotXDp = OriginalNeedlePivotXDp,
    pivotYDp = OriginalNeedlePivotYDp,
)

private val OriginalNeedleLargeMetrics = OriginalNeedleMetrics(
    widthDp = OriginalNeedleWidthBaseDp,
    heightDp = OriginalNeedleHeightLargeDp,
    topWidthDp = OriginalNeedleTopWidthBaseDp,
    topMarginDp = OriginalNeedleTopMarginLargeDp,
    rightMarginDp = OriginalNeedleRightMarginLargeDp,
    shadowRightMarginDp = OriginalNeedleShadowRightMarginLargeDp,
    pivotXDp = OriginalNeedlePivotXDp,
    pivotYDp = OriginalNeedlePivotYDp,
)

internal fun originalNeedleMetrics(turntableScale: Float): OriginalNeedleMetrics {
    val metrics = if (turntableScale >= OriginalLargeNeedleBreakpointScale) {
        OriginalNeedleLargeMetrics
    } else {
        OriginalNeedleBaseMetrics
    }
    val shrinkScale = turntableScale.coerceIn(0f, 1f)
    return if (shrinkScale < 1f) {
        metrics.scaled(shrinkScale)
    } else {
        metrics
    }
}

private fun OriginalNeedleMetrics.scaled(scale: Float): OriginalNeedleMetrics {
    // Shorten the tonearm for compact stages, but keep the asset's cross-axis
    // width so the 9-patch stylus is not squeezed into a distorted shape.
    return copy(
        heightDp = heightDp * scale,
        topMarginDp = topMarginDp * scale,
        pivotYDp = pivotYDp * scale,
    )
}

@Composable
internal fun rememberSmoothDiscRotation(
    running: Boolean,
    cycleDurationMs: Float,
    manualRotationOffsetDegrees: Float,
): State<Float> {
    val rotation = remember {
        mutableFloatStateOf(0f)
    }
    var lastManualRotationOffsetDegrees by remember {
        mutableFloatStateOf(manualRotationOffsetDegrees)
    }

    LaunchedEffect(running, cycleDurationMs) {
        if (!running) return@LaunchedEffect

        var anchorFrameTimeNanos = Long.MIN_VALUE
        val anchorRotation = rotation.floatValue
        val degreesPerMs = DiscRotationDegrees / cycleDurationMs
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (anchorFrameTimeNanos == Long.MIN_VALUE) {
                    anchorFrameTimeNanos = frameTimeNanos
                }
                val elapsedMs = (frameTimeNanos - anchorFrameTimeNanos) / 1_000_000f
                rotation.floatValue = anchorRotation + elapsedMs * degreesPerMs
            }
        }
    }

    LaunchedEffect(manualRotationOffsetDegrees) {
        val deltaDegrees = manualRotationOffsetDegrees - lastManualRotationOffsetDegrees
        lastManualRotationOffsetDegrees = manualRotationOffsetDegrees
        if (deltaDegrees != 0f) {
            rotation.floatValue += deltaDegrees
        }
    }

    return rotation
}

internal fun discCenter(size: IntSize): Offset = Offset(size.width / 2f, size.height / 2f)

internal fun discRadius(size: IntSize): Float = min(size.width, size.height) / 2f

private fun isWithinDisc(
    point: Offset,
    center: Offset,
    radius: Float,
): Boolean {
    if (radius <= 0f) {
        return false
    }
    val dx = point.x - center.x
    val dy = point.y - center.y
    return sqrt((dx * dx) + (dy * dy)) <= radius
}

internal fun isWithinScratchRegion(
    point: Offset,
    center: Offset,
    radius: Float,
): Boolean {
    if (radius <= 0f) {
        return false
    }
    val dx = point.x - center.x
    val dy = point.y - center.y
    val distance = sqrt((dx * dx) + (dy * dy))
    return distance in (radius * ScratchHubDeadZoneRatio)..radius
}

internal fun isDiscTapWithinSlop(
    initialPosition: Offset,
    finalPosition: Offset,
    maxMoveDistance: Float,
    center: Offset,
    radius: Float,
    tapTouchSlop: Float,
): Boolean {
    return maxMoveDistance <= tapTouchSlop &&
        isWithinDisc(initialPosition, center, radius) &&
        isWithinDisc(finalPosition, center, radius)
}

internal fun scratchStartPosition(
    positionMs: Long,
    durationMs: Long,
): Long = positionMs.coerceIn(0L, durationMs)

internal fun scratchFlingDurationMs(
    velocityDegreesPerSecond: Float,
    resumePlaybackAfterDrag: Boolean,
): Long {
    var duration = kotlin.math.abs(velocityDegreesPerSecond) * ScratchFlingDurationMultiplier
    if (velocityDegreesPerSecond < 0f && resumePlaybackAfterDrag) {
        duration *= ScratchFlingPlayingRewindDurationScale
    }
    return duration.roundToLong().coerceAtLeast(1L)
}

internal fun scratchFlingVelocityKeyframes(
    velocityDegreesPerSecond: Float,
    resumePlaybackAfterDrag: Boolean,
): FloatArray {
    return when {
        velocityDegreesPerSecond < 0f && resumePlaybackAfterDrag -> floatArrayOf(
            velocityDegreesPerSecond,
            (2f * velocityDegreesPerSecond) / 3f,
            velocityDegreesPerSecond / 3f,
            0f,
            -velocityDegreesPerSecond * 0.2f,
        )
        velocityDegreesPerSecond < 0f -> floatArrayOf(velocityDegreesPerSecond, 0f)
        else -> floatArrayOf(velocityDegreesPerSecond, ScratchPlaybackVelocityDegreesPerSecond)
    }
}

internal fun scratchFlingVelocityAt(
    keyframes: FloatArray,
    elapsedMs: Float,
    durationMs: Long,
): Float {
    if (keyframes.isEmpty()) {
        return 0f
    }
    if (keyframes.size == 1 || durationMs <= 0L) {
        return keyframes.last()
    }
    val scaledFraction = (elapsedMs / durationMs.toFloat())
        .coerceIn(0f, 1f) * (keyframes.size - 1)
    val startIndex = scaledFraction.toInt().coerceAtMost(keyframes.size - 2)
    val segmentFraction = scaledFraction - startIndex
    return keyframes[startIndex] +
        ((keyframes[startIndex + 1] - keyframes[startIndex]) * segmentFraction)
}

internal fun scratchPositionAfterAngle(
    positionMs: Long,
    deltaAngleDegrees: Float,
    durationMs: Long,
): Long {
    return (
        positionMs + (deltaAngleDegrees / DiscRotationDegrees) * ScratchCycleDurationMs
    ).roundToLong().coerceIn(0L, durationMs)
}

internal fun playbackNeedleGeometry(
    containerSize: IntSize,
    densityPxPerDp: Float,
    turntableScale: Float,
    rotationDegrees: Float,
): PlaybackNeedleGeometry {
    val metrics = originalNeedleMetrics(turntableScale)
    val needleWidthPx = metrics.widthDp * densityPxPerDp
    val needleHeightPx = metrics.heightDp * densityPxPerDp
    val needleLeftPx = containerSize.width -
        (metrics.rightMarginDp * densityPxPerDp) -
        needleWidthPx
    val needleTopPx = metrics.topMarginDp * densityPxPerDp
    val pivotLocal = Offset(
        x = metrics.pivotXDp * densityPxPerDp,
        y = metrics.pivotYDp * densityPxPerDp,
    )
    val pivot = Offset(
        x = needleLeftPx + pivotLocal.x,
        y = needleTopPx + pivotLocal.y,
    )
    return PlaybackNeedleGeometry(
        left = needleLeftPx,
        top = needleTopPx,
        width = needleWidthPx,
        height = needleHeightPx,
        pivotLocal = pivotLocal,
        pivot = pivot,
    )
}

internal fun isWithinNeedleSeekRegion(
    point: Offset,
    containerSize: IntSize,
    densityPxPerDp: Float,
    turntableScale: Float,
    rotationDegrees: Float,
): Boolean {
    if (containerSize.width <= 0 || containerSize.height <= 0 || densityPxPerDp <= 0f) {
        return false
    }
    val geometry = playbackNeedleGeometry(
        containerSize = containerSize,
        densityPxPerDp = densityPxPerDp,
        turntableScale = turntableScale,
        rotationDegrees = rotationDegrees,
    )
    val localPoint = needleLocalPoint(
        point = point,
        geometry = geometry,
        rotationDegrees = rotationDegrees,
    )
    return localPoint.x >= 0f &&
        localPoint.x <= geometry.width &&
        localPoint.y >= geometry.height * OriginalNeedleTouchStartRatio &&
        localPoint.y <= geometry.height
}

internal fun needleSeekRotationFromPoint(
    point: Offset,
    containerSize: IntSize,
    densityPxPerDp: Float,
    turntableScale: Float,
): Float {
    if (containerSize.width <= 0 || containerSize.height <= 0 || densityPxPerDp <= 0f) {
        return NeedleRestRotationDegrees
    }
    val neutralGeometry = playbackNeedleGeometry(
        containerSize = containerSize,
        densityPxPerDp = densityPxPerDp,
        turntableScale = turntableScale,
        rotationDegrees = 0f,
    )
    val neutralAngle = angleDegrees(
        point = Offset(
            x = neutralGeometry.left + (neutralGeometry.width / 2f),
            y = neutralGeometry.top + (neutralGeometry.height * OriginalNeedleTouchStartRatio),
        ),
        center = neutralGeometry.pivot,
    )
    val pointAngle = angleDegrees(point, neutralGeometry.pivot)
    return normalizeAngleDelta(pointAngle - neutralAngle)
        .coerceIn(NeedleRestRotationDegrees, NeedlePlaybackEndRotationDegrees)
}

internal fun needleLocalPoint(
    point: Offset,
    geometry: PlaybackNeedleGeometry,
    rotationDegrees: Float,
): Offset {
    val unrotatedOffset = rotateOffset(
        offset = Offset(
            x = point.x - geometry.pivot.x,
            y = point.y - geometry.pivot.y,
        ),
        rotationDegrees = -rotationDegrees,
    )
    return Offset(
        x = geometry.pivotLocal.x + unrotatedOffset.x,
        y = geometry.pivotLocal.y + unrotatedOffset.y,
    )
}

internal fun needleSeekPositionFromRotation(
    rotationDegrees: Float,
    durationMs: Long,
): Long? {
    if (durationMs <= 0L || rotationDegrees < NeedlePlaybackStartRotationDegrees) {
        return null
    }
    val fraction = (
        (rotationDegrees - NeedlePlaybackStartRotationDegrees) /
            NeedlePlaybackSweepDegrees
    ).coerceIn(0f, 1f)
    return (durationMs.toFloat() * fraction)
        .roundToLong()
        .coerceIn(0L, durationMs)
}

internal fun shouldStartNeedleSeekDrag(
    initialPositionMs: Long?,
    candidatePositionMs: Long?,
    maxMoveDistance: Float,
    outsideActivationDistancePx: Float,
): Boolean {
    if (initialPositionMs == null) {
        return true
    }
    val movingToOutsideOrStart = candidatePositionMs == null ||
        candidatePositionMs <= NeedleSeekStartPositionGuardMs
    return !movingToOutsideOrStart || maxMoveDistance >= outsideActivationDistancePx
}

internal fun angleDegrees(
    point: Offset,
    center: Offset,
): Float = Math.toDegrees(
    atan2(
        y = (point.y - center.y).toDouble(),
        x = (point.x - center.x).toDouble(),
    ),
).toFloat()

internal fun normalizeAngleDelta(deltaDegrees: Float): Float {
    var normalized = deltaDegrees
    while (normalized > 180f) {
        normalized -= 360f
    }
    while (normalized < -180f) {
        normalized += 360f
    }
    return normalized
}

private fun rotateOffset(
    offset: Offset,
    rotationDegrees: Float,
): Offset {
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    return Offset(
        x = (offset.x * cosValue) - (offset.y * sinValue),
        y = (offset.x * sinValue) + (offset.y * cosValue),
    )
}

internal fun distanceBetween(
    first: Offset,
    second: Offset,
): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return sqrt((dx * dx) + (dy * dy))
}
