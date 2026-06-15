package com.smartisanos.music.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt

internal const val LegacyPageStackSlideMillis = 300
private val LegacyPageStackPushEasing = Easing { fraction ->
    ((cos((fraction + 1f) * Math.PI) / 2.0) + 0.5).toFloat()
}
private val LegacyPageStackPopEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - (inverse * inverse)
}
private val LegacyPageStackVerticalEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - (inverse * inverse * inverse)
}

internal enum class LegacyPortPageStackAxis {
    Horizontal,
    VerticalPush,
}

internal class LegacyPortPredictiveBackState internal constructor() {
    var progress: Float? by mutableStateOf(null)
        private set
    var exitConsumed: Boolean by mutableStateOf(false)
        private set

    internal fun update(progress: Float?) {
        exitConsumed = false
        this.progress = progress?.coerceIn(0f, 1f)
    }

    internal fun consumeExit() {
        progress = null
        exitConsumed = true
    }

    internal fun reset() {
        progress = null
        exitConsumed = false
    }
}

@Composable
internal fun rememberLegacyPortPredictiveBackState(): LegacyPortPredictiveBackState {
    return remember { LegacyPortPredictiveBackState() }
}

@Composable
internal fun LegacyPortPredictiveBackHandler(
    enabled: Boolean,
    state: LegacyPortPredictiveBackState,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled) {
        state.reset()
        onBack()
    }
}

@Composable
internal fun <T : Any> LegacyPortPageStackTransition(
    secondaryKey: T?,
    modifier: Modifier = Modifier,
    label: String = "legacy page stack transition",
    axis: LegacyPortPageStackAxis = LegacyPortPageStackAxis.Horizontal,
    axisForKey: (T) -> LegacyPortPageStackAxis = { axis },
    secondaryDepthForKey: (T) -> Int = { 1 },
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    primaryContent: @Composable () -> Unit,
    popPrimaryContent: (@Composable (T) -> Unit)? = null,
    secondaryContent: @Composable (T) -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(false)
    }
    val hasSecondary = secondaryKey != null
    visibleState.targetState = hasSecondary
    val horizontalProgress = remember {
        Animatable(if (hasSecondary) 1f else 0f)
    }

    var retainedSecondaryKey by remember {
        mutableStateOf<T?>(secondaryKey)
    }
    var retainedAxis by remember {
        mutableStateOf(axis)
    }
    var retainedSecondaryDepth by remember {
        mutableStateOf(secondaryKey?.let(secondaryDepthForKey) ?: 0)
    }
    LaunchedEffect(secondaryKey, predictiveBackExitConsumed) {
        val effectAxis = secondaryKey?.let(axisForKey) ?: retainedAxis
        if (secondaryKey != null) {
            val nextAxis = axisForKey(secondaryKey)
            val nextDepth = secondaryDepthForKey(secondaryKey)
            val previousSecondaryKey = retainedSecondaryKey
            val isReplacingWithPop = previousSecondaryKey != null &&
                previousSecondaryKey != secondaryKey &&
                retainedAxis == LegacyPortPageStackAxis.Horizontal &&
                nextDepth < retainedSecondaryDepth
            onPredictiveBackExitConsumedReset?.invoke()
            if (isReplacingWithPop && predictiveBackExitConsumed) {
                retainedSecondaryKey = secondaryKey
                retainedSecondaryDepth = nextDepth
                retainedAxis = nextAxis
                horizontalProgress.snapTo(1f)
            } else if (isReplacingWithPop) {
                horizontalProgress.snapTo(1f)
                horizontalProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = LegacyPageStackSlideMillis,
                        easing = LegacyPageStackPopEasing,
                    ),
                )
                retainedSecondaryKey = secondaryKey
                retainedSecondaryDepth = nextDepth
                retainedAxis = nextAxis
                horizontalProgress.snapTo(1f)
            } else {
                retainedSecondaryKey = secondaryKey
                retainedSecondaryDepth = nextDepth
                retainedAxis = nextAxis
            }
            if (!isReplacingWithPop && retainedAxis == LegacyPortPageStackAxis.Horizontal) {
                horizontalProgress.snapTo(0f)
                horizontalProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = LegacyPageStackSlideMillis,
                        easing = LegacyPageStackPushEasing,
                    ),
                )
            }
        } else if (retainedSecondaryKey != null && effectAxis == LegacyPortPageStackAxis.Horizontal) {
            if (predictiveBackExitConsumed) {
                horizontalProgress.snapTo(0f)
            } else {
                horizontalProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = LegacyPageStackSlideMillis,
                        easing = LegacyPageStackPopEasing,
                    ),
                )
            }
            retainedSecondaryKey = null
            retainedSecondaryDepth = 0
        } else if (retainedSecondaryKey != null) {
            delay(LegacyPageStackSlideMillis.toLong())
            retainedSecondaryKey = null
            retainedSecondaryDepth = 0
        }
    }
    LaunchedEffect(predictiveBackExitConsumed, hasSecondary) {
        if (!hasSecondary && predictiveBackExitConsumed) {
            retainedSecondaryKey = null
            retainedSecondaryDepth = 0
        }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val nextKey = secondaryKey
        val nextAxis = nextKey?.let(axisForKey)
        val retainedKey = retainedSecondaryKey
        val replacingWithPop = nextKey != null &&
            retainedKey != null &&
            nextKey != retainedKey &&
            retainedAxis == LegacyPortPageStackAxis.Horizontal &&
            secondaryDepthForKey(nextKey) < retainedSecondaryDepth
        val activeAxis = if (replacingWithPop) {
            retainedAxis
        } else {
            nextAxis ?: retainedAxis
        }
        val predictiveReplacementExitConsumed = replacingWithPop && predictiveBackExitConsumed
        val activeBackProgress = predictiveBackProgress
            ?.takeIf { activeAxis == LegacyPortPageStackAxis.Horizontal }
            ?.coerceIn(0f, 1f)
        val predictiveExitConsumed = !hasSecondary && predictiveBackExitConsumed
        val enteringNewSecondary = hasSecondary &&
            secondaryKey != retainedSecondaryKey &&
            !replacingWithPop &&
            activeAxis == LegacyPortPageStackAxis.Horizontal
        val visibleProgress = when {
            predictiveExitConsumed -> 0f
            predictiveReplacementExitConsumed -> 1f
            activeBackProgress != null -> 1f - activeBackProgress
            replacingWithPop -> horizontalProgress.value.coerceIn(0f, 1f)
            enteringNewSecondary -> 0f
            activeAxis == LegacyPortPageStackAxis.Horizontal -> horizontalProgress.value.coerceIn(0f, 1f)
            else -> 0f
        }
        val primaryOffsetX = when {
            predictiveExitConsumed -> 0
            activeAxis == LegacyPortPageStackAxis.Horizontal -> (-widthPx * visibleProgress).roundToInt()
            else -> 0
        }
        val secondaryOffsetX = when {
            predictiveExitConsumed && activeAxis == LegacyPortPageStackAxis.Horizontal -> widthPx
            activeAxis == LegacyPortPageStackAxis.Horizontal -> (widthPx * (1f - visibleProgress)).roundToInt()
            else -> 0
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = primaryOffsetX.toFloat()
                },
        ) {
            val popTargetKey = nextKey.takeIf {
                replacingWithPop && !predictiveReplacementExitConsumed
            }
            val popContent = popPrimaryContent
            if (popTargetKey != null && popContent != null) {
                popContent(popTargetKey)
            } else {
                primaryContent()
            }
        }

        val contentKey = when {
            predictiveExitConsumed && activeAxis == LegacyPortPageStackAxis.Horizontal -> null
            predictiveReplacementExitConsumed -> secondaryKey
            replacingWithPop -> retainedKey
            else -> secondaryKey ?: retainedSecondaryKey
        }
        if (contentKey != null) {
            if (activeAxis == LegacyPortPageStackAxis.Horizontal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = secondaryOffsetX.toFloat()
                        }
                        .zIndex(1f),
                ) {
                    secondaryContent(contentKey)
                }
            } else {
                AnimatedVisibility(
                    visibleState = visibleState,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f),
                    enter = slideInVertically(
                        animationSpec = tween(
                            durationMillis = LegacyPageStackSlideMillis,
                            easing = LegacyPageStackVerticalEasing,
                        ),
                        initialOffsetY = { it },
                    ),
                    exit = if (predictiveExitConsumed) {
                        ExitTransition.None
                    } else {
                        slideOutVertically(
                            animationSpec = tween(
                                durationMillis = LegacyPageStackSlideMillis,
                                easing = LegacyPageStackVerticalEasing,
                            ),
                            targetOffsetY = { it },
                        )
                    },
                ) {
                    secondaryContent(contentKey)
                }
            }
        }
    }
}
