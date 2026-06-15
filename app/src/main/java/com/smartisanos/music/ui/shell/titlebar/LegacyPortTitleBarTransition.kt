package com.smartisanos.music.ui.shell.titlebar

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.cos

private const val LegacyTitleBarTransitionMillis = 300
private val LegacyTitleBarLeftSlotWidth = 74.dp
private val LegacyTitleBarSlotSlideDistance = 38.dp
private const val LegacyTitleBarLeftExitFraction = 0.5f
private const val LegacyTitleBarLeftEnterStartFraction = 1f / 3f
private const val LegacyTitleBarLeftEnterDurationFraction = 0.5f
private const val LegacyTitleBarLeftPushSlideDurationFraction = 2f / 3f

private fun legacyTitleBarAccelerateDecelerate(fraction: Float): Float {
    return ((cos((fraction + 1f) * Math.PI) / 2.0) + 0.5).toFloat()
}

private fun legacyTitleBarDecelerate(fraction: Float): Float {
    val inverse = 1f - fraction
    return 1f - (inverse * inverse)
}

internal enum class LegacyTitleBarReplacementDirection {
    Push,
    Pop,
}

internal data class LegacyTitleBarLeftSlotMotion(
    val primaryAlpha: Float,
    val secondaryAlpha: Float,
    val primaryTranslationX: Float,
    val secondaryTranslationX: Float,
)

internal data class LegacyTitleBarLayerAlphas(
    val primaryAlpha: Float,
    val secondaryAlpha: Float,
)

internal fun legacyTitleBarLayerAlphas(
    progress: Float,
    direction: LegacyTitleBarReplacementDirection,
): LegacyTitleBarLayerAlphas {
    val titleProgress = progress.coerceIn(0f, 1f)
    val transitionFraction = when (direction) {
        LegacyTitleBarReplacementDirection.Push -> titleProgress
        LegacyTitleBarReplacementDirection.Pop -> 1f - titleProgress
    }
    return when (direction) {
        LegacyTitleBarReplacementDirection.Push -> LegacyTitleBarLayerAlphas(
            primaryAlpha = 1f - legacyTitleBarAccelerateDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0f,
                    duration = LegacyTitleBarLeftExitFraction,
                ),
            ),
            secondaryAlpha = legacyTitleBarAccelerateDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0.5f,
                    duration = 0.5f,
                ),
            ),
        )
        LegacyTitleBarReplacementDirection.Pop -> LegacyTitleBarLayerAlphas(
            primaryAlpha = legacyTitleBarAccelerateDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0.5f,
                    duration = 0.5f,
                ),
            ),
            secondaryAlpha = 1f - legacyTitleBarDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0f,
                    duration = LegacyTitleBarLeftExitFraction,
                ),
            ),
        )
    }
}

internal fun legacyTitleBarLeftSlotMotion(
    progress: Float,
    direction: LegacyTitleBarReplacementDirection,
    slidePx: Float,
): LegacyTitleBarLeftSlotMotion {
    val titleProgress = progress.coerceIn(0f, 1f)
    val transitionFraction = when (direction) {
        LegacyTitleBarReplacementDirection.Push -> titleProgress
        LegacyTitleBarReplacementDirection.Pop -> 1f - titleProgress
    }
    return when (direction) {
        LegacyTitleBarReplacementDirection.Push -> LegacyTitleBarLeftSlotMotion(
            primaryAlpha = 1f - legacyTitleBarAccelerateDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0f,
                    duration = LegacyTitleBarLeftExitFraction,
                ),
            ),
            secondaryAlpha = legacyTitleBarAccelerateDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = LegacyTitleBarLeftEnterStartFraction,
                    duration = LegacyTitleBarLeftEnterDurationFraction,
                ),
            ),
            primaryTranslationX = 0f,
            secondaryTranslationX = slidePx * (
                1f - legacyTitleBarAccelerateDecelerate(
                    legacyTitleBarWindowFraction(
                        fraction = transitionFraction,
                        start = LegacyTitleBarLeftEnterStartFraction,
                        duration = LegacyTitleBarLeftPushSlideDurationFraction,
                    ),
                )
                ),
        )
        LegacyTitleBarReplacementDirection.Pop -> LegacyTitleBarLeftSlotMotion(
            primaryAlpha = legacyTitleBarAccelerateDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0.5f,
                    duration = 0.5f,
                ),
            ),
            secondaryAlpha = 1f - legacyTitleBarDecelerate(
                legacyTitleBarWindowFraction(
                    fraction = transitionFraction,
                    start = 0f,
                    duration = LegacyTitleBarLeftExitFraction,
                ),
            ),
            primaryTranslationX = 0f,
            secondaryTranslationX = slidePx * legacyTitleBarDecelerate(transitionFraction),
        )
    }
}

private fun legacyTitleBarWindowFraction(
    fraction: Float,
    start: Float,
    duration: Float,
): Float {
    return ((fraction - start) / duration)
        .coerceIn(0f, 1f)
}

@Composable
internal fun <T : Any> LegacyPortTitleBarTransition(
    secondaryKey: T?,
    modifier: Modifier = Modifier,
    label: String = "legacy title bar transition",
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    primaryLeftContent: (@Composable () -> Unit)? = null,
    secondaryLeftContent: (@Composable (T) -> Unit)? = null,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable (T) -> Unit,
) {
    val progress = remember { Animatable(if (secondaryKey != null) 1f else 0f) }
    var retainedSecondaryKey by remember { mutableStateOf<T?>(secondaryKey) }
    var direction by remember {
        mutableStateOf(
            if (secondaryKey != null) {
                LegacyTitleBarReplacementDirection.Push
            } else {
                LegacyTitleBarReplacementDirection.Pop
            },
        )
    }

    LaunchedEffect(secondaryKey) {
        if (secondaryKey != null) {
            onPredictiveBackExitConsumedReset?.invoke()
            retainedSecondaryKey = secondaryKey
            direction = LegacyTitleBarReplacementDirection.Push
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = LegacyTitleBarTransitionMillis,
                    easing = LinearEasing,
                ),
            )
        } else if (retainedSecondaryKey != null) {
            direction = LegacyTitleBarReplacementDirection.Pop
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = LegacyTitleBarTransitionMillis,
                    easing = LinearEasing,
                ),
            )
            retainedSecondaryKey = null
        }
    }

    val titleProgress = when {
        predictiveBackExitConsumed -> 0f
        predictiveBackProgress != null -> 1f - predictiveBackProgress.coerceIn(0f, 1f)
        else -> progress.value.coerceIn(0f, 1f)
    }
    val activeDirection = if (predictiveBackProgress != null || predictiveBackExitConsumed) {
        LegacyTitleBarReplacementDirection.Pop
    } else {
        direction
    }
    val layerAlphas = legacyTitleBarLayerAlphas(
        progress = titleProgress,
        direction = activeDirection,
    )
    val contentKey = secondaryKey ?: retainedSecondaryKey
    val slidePx = with(LocalDensity.current) {
        LegacyTitleBarSlotSlideDistance.toPx()
    }
    val leftMotion = legacyTitleBarLeftSlotMotion(
        progress = titleProgress,
        direction = activeDirection,
        slidePx = slidePx,
    )

    LegacyPortTitleBarReplacementLayers(
        modifier = modifier
            .clipToBounds(),
        primaryAlpha = layerAlphas.primaryAlpha,
        secondaryAlpha = layerAlphas.secondaryAlpha,
        primaryLeftAlpha = leftMotion.primaryAlpha,
        secondaryLeftAlpha = leftMotion.secondaryAlpha,
        primaryLeftTranslationX = leftMotion.primaryTranslationX,
        secondaryLeftTranslationX = leftMotion.secondaryTranslationX,
        primaryLeftContent = primaryLeftContent,
        secondaryLeftContent = contentKey?.let { key ->
            secondaryLeftContent?.let { content ->
                {
                    content(key)
                }
            }
        },
        primaryContent = primaryContent,
        secondaryContent = contentKey?.let { key ->
            {
                secondaryContent(key)
            }
        },
    )
}

@Composable
internal fun LegacyPortTitleBarReplacementLayers(
    primaryAlpha: Float,
    secondaryAlpha: Float,
    primaryLeftAlpha: Float = primaryAlpha,
    secondaryLeftAlpha: Float = secondaryAlpha,
    primaryLeftTranslationX: Float,
    secondaryLeftTranslationX: Float,
    modifier: Modifier = Modifier,
    leftSlotWidth: Dp = LegacyTitleBarLeftSlotWidth,
    primaryLeftContent: (@Composable () -> Unit)? = null,
    secondaryLeftContent: (@Composable () -> Unit)? = null,
    primaryContent: @Composable () -> Unit,
    secondaryContent: (@Composable () -> Unit)?,
) {
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color.White),
    ) {
        fun Modifier.titleLayer(alpha: Float): Modifier {
            return fillMaxSize().graphicsLayer {
                this.alpha = alpha
            }
        }

        Box(modifier = Modifier.titleLayer(primaryAlpha)) {
            primaryContent()
        }
        if (secondaryContent != null) {
            Box(
                modifier = Modifier.titleLayer(secondaryAlpha),
            ) {
                secondaryContent()
            }
        }

        if (primaryLeftContent != null || secondaryLeftContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(leftSlotWidth)
                    .fillMaxHeight()
                    .background(Color.White),
            )

            fun Modifier.leftSlotLayer(alpha: Float, translationX: Float): Modifier {
                return width(leftSlotWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        this.alpha = alpha
                        this.translationX = translationX
                    }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(leftSlotWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                if (primaryLeftContent != null) {
                    Box(
                        modifier = Modifier.leftSlotLayer(primaryLeftAlpha, primaryLeftTranslationX),
                    ) {
                        primaryLeftContent()
                    }
                }
                if (secondaryLeftContent != null) {
                    Box(
                        modifier = Modifier.leftSlotLayer(secondaryLeftAlpha, secondaryLeftTranslationX),
                    ) {
                        secondaryLeftContent()
                    }
                }
            }
        }
    }
}

@Composable
internal fun LegacyTitleBarLeftIcon(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier
            .width(LegacyTitleBarLeftSlotWidth)
            .fillMaxHeight(),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER
            }
        },
        update = { imageView ->
            imageView.setImageResource(iconRes)
            imageView.isEnabled = true
            imageView.setOnClickListener {
                onClick()
            }
        },
    )
}
