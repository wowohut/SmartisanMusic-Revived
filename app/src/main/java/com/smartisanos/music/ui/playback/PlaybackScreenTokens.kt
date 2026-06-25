package com.smartisanos.music.ui.playback

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PlaybackPageBackground = Color.White
internal val PlaybackTopBarDivider = Color(0xFFE6E6E6)
private val PlaybackTitleColor = Color(0xFF6B6B6F)
private val PlaybackSubtitleColor = Color(0x88333333)
private val PlaybackTimeColor = Color(0x88333333)
internal val PlaybackTrackColor = Color(0x1F000000)
internal val PlaybackTrackFillColor = Color(0xFFBBBBBB)

internal const val PlaybackDiscCycleDurationMs = 15_500f
internal const val ScratchCycleDurationMs = 1_800f
internal const val DiscRotationDegrees = 360f
internal const val ScratchHubDeadZoneRatio = 0.06f
internal const val PlaybackScratchMinMotionDegrees = 0.18f
internal const val PlaybackScratchMaxDeltaTimeMs = 72L
internal const val ScratchMaxAngleStepDegrees = 54f
internal const val ScratchVelocityMaxDegreesPerSecond = 1_000f
internal const val ScratchFlingMinVelocityDegreesPerSecond = 160f
internal const val ScratchFlingReleaseTimeoutMs = 120L
internal const val ScratchFlingVelocitySampleWindowMs = 120L
internal const val ScratchFlingMinVelocitySampleMs = 16L
internal const val ScratchFlingDurationMultiplier = 1.5f
internal const val ScratchFlingPlayingRewindDurationScale = 1.2f
internal const val ScratchFlingFrameDivisor = 1_700f
internal const val ScratchPixelFlingDivisor = 40f
internal const val ScratchWarmupRefreshMs = 3_000L
internal const val ScratchPlaybackVelocityDegreesPerSecond =
    DiscRotationDegrees * 1_000f / PlaybackDiscCycleDurationMs
internal const val CoverPreviewTimeoutMs = 260L
internal const val CoverPreviewSettleToleranceMs = 24L
internal const val NeedleSeekSettleHoldTimeoutMs = 900L
internal const val NeedleLiftHoldAfterReleaseMs = 250L
internal const val OriginalTurntableBaseWidthDp = 360f
internal const val OriginalLargeNeedleBreakpointScale = 411f / OriginalTurntableBaseWidthDp
internal const val OriginalNeedleTouchStartRatio = 0.8f
internal const val OriginalNeedleWidthBaseDp = 73.3f
internal const val OriginalNeedleHeightBaseDp = 310f
internal const val OriginalNeedleTopWidthBaseDp = 41f
internal const val OriginalNeedleTopMarginBaseDp = 25.5f
internal const val OriginalNeedleRightMarginDp = 2.5f
internal const val OriginalNeedleShadowRightMarginDp = 2f
internal const val OriginalNeedlePivotXDp = 48f
internal const val OriginalNeedlePivotYDp = 28f
internal const val OriginalNeedleHeightLargeDp = 354.6953f
internal const val OriginalNeedleTopMarginLargeDp = 29.199982f
internal const val OriginalNeedleRightMarginLargeDp = 2.7999878f
internal const val OriginalNeedleShadowRightMarginLargeDp = 2.2999878f
internal const val NeedleSeekOutsideActivationDistanceDp = 36f
internal const val NeedleSeekStartPositionGuardMs = 1_000L
internal const val NeedleRestRotationDegrees = 0f
internal const val NeedlePlaybackStartRotationDegrees = 12f
internal const val NeedlePlaybackSweepDegrees = 22.3f
internal const val NeedlePlaybackEndRotationDegrees =
    NeedlePlaybackStartRotationDegrees + NeedlePlaybackSweepDegrees
internal const val NeedleLiftScaleY = 0.98f
internal const val NeedleLiftShadowRotationOffsetDegrees = 4f
internal const val PlaybackAlbumArtDiameterRatio = 405f / 1080f
internal const val PlaybackTurntableAxisDiameterRatio = 62f / 1080f
internal const val PlaybackTurntableAxisSourceDiameterPx = 60

internal val PlaybackContentHorizontalPadding = 16.dp
internal val PlaybackVisualStageTopPadding = 16.dp
internal val PlaybackSeekBarHeight = 48.dp
internal val PlaybackSeekTimeWidth = 44.dp
internal val PlaybackSeekTrackHeight = 8.dp
internal val PlaybackSeekThumbWidth = 22.3.dp
internal val PlaybackSeekThumbHeight = 41.dp
internal val PlaybackSeekBarDividerHeight = 0.7.dp
internal val PlaybackVolumeBarHeight = 60.dp
internal val PlaybackVolumeHorizontalPadding = 26.5.dp
internal val PlaybackVolumeThumbOffset = 5.dp
internal val PlaybackActionButtonSize = 31.dp
internal val PlaybackMinimumTouchTargetSize = 48.dp
internal val PlaybackControlEntranceOffset = 186.dp
internal val PlaybackControlButtonsTopPadding = 6.dp
internal val PlaybackBottomControlsVolumeTopPadding = 18.dp
internal val PlaybackBottomControlsContentBottomPadding = 19.dp
internal val PlaybackBottomControlsBottomSpacing = 14.dp
internal val PlaybackBottomControlsMinimumBottomSpacing = 16.dp

internal const val PlaybackEntranceTotalDurationMillis = 980
internal const val PlaybackTurntableEntranceDurationMillis = 300
internal const val PlaybackControlEntranceDurationMillis = 240
internal const val PlaybackPlayButtonEntranceDelayMillis = 100
internal const val PlaybackSideButtonEntranceDelayMillis = 150
internal const val PlaybackVolumeEntranceDelayMillis = 155
internal const val PlaybackOuterButtonAlphaDelayMillis = 500
internal const val PlaybackOuterButtonAlphaDurationMillis = 480
internal const val PlaybackVisualPageEnterDurationMillis = 240
internal const val PlaybackVisualPageExitDurationMillis = 180
internal const val PlaybackVisualPageEnterDelayMillis = 40
internal const val PlaybackLyricsActionEnterDelayMillis = 70

internal val PlaybackLegacyDecelerateEasing = Easing { fraction ->
    val inverse = 1f - fraction.coerceIn(0f, 1f)
    1f - inverse * inverse * inverse
}

internal fun playbackEntranceProgress(
    timeMillis: Float,
    delayMillis: Int,
    durationMillis: Int,
): Float {
    if (durationMillis <= 0) {
        return 1f
    }
    val linear = ((timeMillis - delayMillis.toFloat()) / durationMillis.toFloat())
        .coerceIn(0f, 1f)
    val inverse = 1f - linear
    return 1f - inverse * inverse * inverse
}

internal val PlaybackTitleStyle = TextStyle(
    fontSize = 18.sp,
    fontWeight = FontWeight.SemiBold,
    color = PlaybackTitleColor,
)
internal val PlaybackArtistStyle = TextStyle(
    fontSize = 11.sp,
    color = PlaybackSubtitleColor,
)
internal val PlaybackTimeStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    color = PlaybackTimeColor,
)
