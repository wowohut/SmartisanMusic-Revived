package com.smartisanos.music.ui.playback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisanos.music.R
import com.smartisanos.music.data.favorite.FavoriteSongsRepository
import com.smartisanos.music.data.playlist.PlaylistRepository
import com.smartisanos.music.data.settings.PlaybackSettings
import com.smartisanos.music.isExternalAudioLaunchItem
import com.smartisanos.music.playback.EmbeddedLyrics
import com.smartisanos.music.playback.LocalPlaybackController
import com.smartisanos.music.playback.PlaybackSleepTimer
import com.smartisanos.music.playback.artworkRequestKey
import com.smartisanos.music.playback.await
import com.smartisanos.music.playback.cancelSleepTimer
import com.smartisanos.music.playback.extractEmbeddedLyrics
import com.smartisanos.music.playback.invalidateLibrary
import com.smartisanos.music.playback.loadEmbeddedLyrics
import com.smartisanos.music.playback.removeMediaItemsByMediaIds
import com.smartisanos.music.playback.setScratchSeekModeEnabled
import com.smartisanos.music.playback.startSleepTimer
import com.smartisanos.music.ui.components.loadEmbeddedArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private data class PlaybackCoverPageState(
    val dragMode: CoverDragMode = CoverDragMode.None,
    val previewPositionMs: Long? = null,
    val resumePlaybackAfterDrag: Boolean = false,
    val needlePreviewRotationDegrees: Float? = null,
    val needleSettlingPositionMs: Long? = null,
    val needleParkedOutside: Boolean = false,
)

@Composable
fun PlaybackScreen(
    playbackSettings: PlaybackSettings,
    onScratchEnabledChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit = {},
    onRequestAddToQueue: (List<MediaItem>) -> Unit = {},
    onLibraryChanged: () -> Unit = {},
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current
    val context = LocalContext.current
    val favoriteRepository = remember(context.applicationContext) {
        FavoriteSongsRepository.getInstance(context.applicationContext)
    }
    val playlistRepository = remember(context.applicationContext) {
        PlaylistRepository.getInstance(context.applicationContext)
    }
    val entranceTimeMillis = remember { Animatable(0f) }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLibraryChanged by rememberUpdatedState(onLibraryChanged)
    val scratchSoundController = remember(context) {
        ScratchSoundController(context)
    }
    val popcornSoundController = remember(context) {
        VinylPopcornSoundController(context)
    }
    var volume by remember(context) {
        mutableFloatStateOf(context.musicStreamVolumeFraction())
    }
    var state by remember(controller) {
        mutableStateOf(
            controller.snapshot(
                volume = volume,
            ),
        )
    }
    val latestVolume by rememberUpdatedState(volume)
    var livePositionMs by remember(controller) {
        mutableLongStateOf(state.currentPositionMs)
    }
    var showMorePanel by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    var showSetRingtoneDialog by rememberSaveable { mutableStateOf(false) }
    var showWriteSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showQueueOverlay by rememberSaveable { mutableStateOf(false) }
    var currentVisualPage by rememberSaveable { mutableStateOf(PlaybackVisualPage.Cover) }
    var keepLyricsScreenAwake by rememberSaveable { mutableStateOf(false) }
    var pendingRingtoneUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var sleepTimerWasActive by remember { mutableStateOf(false) }
    var coverPageState by remember(state.mediaItem?.mediaId) {
        mutableStateOf(PlaybackCoverPageState())
    }
    var scratchFlingJob by remember { mutableStateOf<Job?>(null) }
    var discManualRotationOffsetDegrees by remember { mutableFloatStateOf(0f) }
    val sleepTimerState by PlaybackSleepTimer.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        entranceTimeMillis.snapTo(0f)
        entranceTimeMillis.animateTo(
            targetValue = PlaybackEntranceTotalDurationMillis.toFloat(),
            animationSpec = tween(
                durationMillis = PlaybackEntranceTotalDurationMillis,
                easing = LinearEasing,
            ),
        )
    }

    fun clearPendingDeleteTarget() {
        pendingDeleteMediaId = null
        pendingDeleteUriString = null
    }

    val applyPendingRingtone by rememberUpdatedState(
        newValue = {
            val ringtoneUriString = pendingRingtoneUriString
            pendingRingtoneUriString = null
            val ringtoneUri = ringtoneUriString
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::parse)
                ?: run {
                    context.toast(R.string.can_not_set_ringtone)
                    return@rememberUpdatedState
                }
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    context.applicationContext.trySetDefaultRingtone(ringtoneUri)
                }
                context.toast(if (success) R.string.ring_success else R.string.ring_fault)
            }
        },
    )
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.System.canWrite(context)) {
            applyPendingRingtone()
        } else {
            pendingRingtoneUriString = null
            context.toast(R.string.ringtone_permission_missing)
        }
    }
    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val mediaId = pendingDeleteMediaId
        clearPendingDeleteTarget()
        if (result.resultCode == Activity.RESULT_OK && !mediaId.isNullOrBlank()) {
            controller.removeMediaItemsByMediaIds(setOf(mediaId))
            scope.launch {
                runCatching {
                    favoriteRepository.remove(mediaId)
                }
                runCatching {
                    playlistRepository.removeMediaIdsFromAll(setOf(mediaId))
                }
                runCatching {
                    controller?.invalidateLibrary()?.await(context)
                }
                currentOnLibraryChanged()
            }
            context.toast(R.string.playback_delete_success)
        }
    }

    fun launchDeleteRequest(target: PlaybackDeleteTarget) {
        pendingDeleteMediaId = target.mediaId
        pendingDeleteUriString = target.uri.toString()
        val request = runCatching {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(target.uri),
            )
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        }.getOrElse {
            clearPendingDeleteTarget()
            context.toast(R.string.playback_delete_failed)
            return
        }

        runCatching {
            deleteMediaLauncher.launch(request)
        }.onFailure {
            clearPendingDeleteTarget()
            context.toast(R.string.playback_delete_failed)
        }
    }

    BackHandler {
        if (showSleepTimerDialog) {
            showSleepTimerDialog = false
        } else if (showQueueOverlay) {
            showQueueOverlay = false
        } else if (showMorePanel) {
            showMorePanel = false
        } else {
            onCollapse()
        }
    }

    DisposableEffect(controller) {
        val playbackController = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val nextState = playbackController.snapshot(volume = latestVolume)
                state = nextState
                livePositionMs = nextState.currentPositionMs
            }
        }
        playbackController.addListener(listener)
        onDispose {
            playbackController.removeListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                PlaybackSleepTimer.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showSleepTimerDialog) {
        if (showSleepTimerDialog) {
            PlaybackSleepTimer.refresh()
        }
    }

    LaunchedEffect(sleepTimerState.isActive) {
        if (sleepTimerWasActive && !sleepTimerState.isActive) {
            showSleepTimerDialog = false
        }
        sleepTimerWasActive = sleepTimerState.isActive
    }

    fun resetCoverPageInteraction(resumePlayback: Boolean) {
        scratchFlingJob?.cancel()
        scratchFlingJob = null
        val shouldResumePlayback = resumePlayback && coverPageState.resumePlaybackAfterDrag
        coverPageState = PlaybackCoverPageState()
        if (shouldResumePlayback) {
            controller?.play()
        }
        controller?.setScratchSeekModeEnabled(false)
        scratchSoundController.stop()
    }

    fun finishDiscScratch(
        positionMs: Long,
        resumePlaybackAfterDrag: Boolean,
    ) {
        coverPageState = coverPageState.copy(
            dragMode = CoverDragMode.None,
            previewPositionMs = positionMs,
            resumePlaybackAfterDrag = false,
            needlePreviewRotationDegrees = null,
            needleSettlingPositionMs = null,
            needleParkedOutside = false,
        )
        controller?.seekTo(positionMs)
        if (resumePlaybackAfterDrag) {
            controller?.play()
        }
        controller?.setScratchSeekModeEnabled(false)
        scratchSoundController.stop()
    }

    fun launchDiscScratchFling(
        startPositionMs: Long,
        initialVelocityDegreesPerSecond: Float,
        resumePlaybackAfterDrag: Boolean,
        durationMs: Long,
    ) {
        scratchFlingJob?.cancel()
        scratchFlingJob = null
        val clampedVelocity = initialVelocityDegreesPerSecond
            .coerceIn(-ScratchVelocityMaxDegreesPerSecond, ScratchVelocityMaxDegreesPerSecond)
        if (abs(clampedVelocity) < ScratchFlingMinVelocityDegreesPerSecond || durationMs <= 0L) {
            finishDiscScratch(startPositionMs, resumePlaybackAfterDrag)
            return
        }

        val flingDurationMs = scratchFlingDurationMs(
            velocityDegreesPerSecond = clampedVelocity,
            resumePlaybackAfterDrag = resumePlaybackAfterDrag,
        )
        val velocityKeyframes = scratchFlingVelocityKeyframes(
            velocityDegreesPerSecond = clampedVelocity,
            resumePlaybackAfterDrag = resumePlaybackAfterDrag,
        )
        coverPageState = coverPageState.copy(
            dragMode = CoverDragMode.DiscScratch,
            previewPositionMs = startPositionMs,
            resumePlaybackAfterDrag = resumePlaybackAfterDrag,
            needlePreviewRotationDegrees = null,
            needleSettlingPositionMs = null,
            needleParkedOutside = false,
        )
        scratchFlingJob = scope.launch {
            var positionMs = startPositionMs.coerceIn(0L, durationMs)
            var previousFrameNanos = Long.MIN_VALUE
            var previousVelocity = velocityKeyframes.first()
            var elapsedMs = 0f
            while (isActive && elapsedMs < flingDurationMs) {
                val frameNanos = withFrameNanos { it }
                if (previousFrameNanos == Long.MIN_VALUE) {
                    previousFrameNanos = frameNanos
                    continue
                }
                val frameDeltaMs = ((frameNanos - previousFrameNanos) / 1_000_000f)
                    .coerceIn(1f, PlaybackScratchMaxDeltaTimeMs.toFloat())
                previousFrameNanos = frameNanos
                elapsedMs = (elapsedMs + frameDeltaMs).coerceAtMost(flingDurationMs.toFloat())

                val currentVelocity = scratchFlingVelocityAt(
                    keyframes = velocityKeyframes,
                    elapsedMs = elapsedMs,
                    durationMs = flingDurationMs,
                )
                val deltaAngle = ((previousVelocity + currentVelocity) * frameDeltaMs) /
                    ScratchFlingFrameDivisor
                previousVelocity = currentVelocity

                if (abs(deltaAngle) >= PlaybackScratchMinMotionDegrees) {
                    discManualRotationOffsetDegrees += deltaAngle
                    val targetPosition = scratchPositionAfterAngle(
                        positionMs = positionMs,
                        deltaAngleDegrees = deltaAngle,
                        durationMs = durationMs,
                    )
                    scratchSoundController.onScratchMotion(targetPosition, deltaAngle)
                    if (targetPosition != positionMs) {
                        positionMs = targetPosition
                        coverPageState = coverPageState.copy(previewPositionMs = positionMs)
                    }
                }

            }
            finishDiscScratch(positionMs, resumePlaybackAfterDrag)
            scratchFlingJob = null
        }
    }

    fun setVisualPage(targetPage: PlaybackVisualPage) {
        if (currentVisualPage == targetPage) {
            return
        }
        currentVisualPage = targetPage
        if (targetPage != PlaybackVisualPage.Cover) {
            resetCoverPageInteraction(resumePlayback = true)
        }
    }

    fun toggleVisualPage() {
        setVisualPage(
            if (currentVisualPage == PlaybackVisualPage.Cover) {
                PlaybackVisualPage.Lyrics
            } else {
                PlaybackVisualPage.Cover
            },
        )
    }

    DisposableEffect(controller) {
        val playbackController = controller
        onDispose {
            playbackController?.setScratchSeekModeEnabled(false)
        }
    }

    DisposableEffect(scratchSoundController) {
        onDispose {
            scratchFlingJob?.cancel()
            scratchSoundController.release()
        }
    }

    DisposableEffect(popcornSoundController) {
        onDispose {
            popcornSoundController.release()
        }
    }

    val keepLyricsScreenOn = currentVisualPage == PlaybackVisualPage.Lyrics &&
        keepLyricsScreenAwake
    DisposableEffect(context, keepLyricsScreenOn) {
        val window = context.findActivity()?.window
        if (keepLyricsScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(controller, state.mediaItem?.mediaId) {
        val playbackController = controller ?: return@LaunchedEffect
        while (isActive) {
            livePositionMs = playbackController.currentPosition.coerceAtLeast(0L)
            delay(
                if (playbackController.isPlaying) {
                    PlaybackPositionPlayingRefreshMs
                } else {
                    PlaybackPositionIdleRefreshMs
                },
            )
        }
    }

    LaunchedEffect(context) {
        while (isActive) {
            delay(PlaybackVolumeRefreshMs)
            val nextVolume = context.musicStreamVolumeFraction()
            if (abs(nextVolume - latestVolume) >= PlaybackVolumeChangeEpsilon) {
                volume = nextVolume
                state = state.copy(volume = nextVolume)
            }
        }
    }

    LaunchedEffect(controller, playbackSettings.scratchEnabled, currentVisualPage) {
        if (!playbackSettings.scratchEnabled || currentVisualPage != PlaybackVisualPage.Cover) {
            resetCoverPageInteraction(resumePlayback = true)
        }
    }

    LaunchedEffect(
        playbackSettings.popcornSoundEnabled,
        state.isPlaying,
        coverPageState.dragMode,
    ) {
        if (
            !playbackSettings.popcornSoundEnabled ||
            !state.isPlaying ||
            coverPageState.dragMode != CoverDragMode.None
        ) {
            popcornSoundController.stop()
            return@LaunchedEffect
        }
        try {
            while (isActive) {
                popcornSoundController.playRandomPop()
                delay(Random.nextLong(from = 860L, until = 1_640L))
            }
        } finally {
            popcornSoundController.stop()
        }
    }

    val mediaMetadata = state.mediaItem?.mediaMetadata
    val scratchSourceUri = state.mediaItem?.localConfiguration?.uri
    val title = mediaMetadata?.displayTitle?.toString()
        ?: mediaMetadata?.title?.toString()
        ?: stringResource(R.string.unknown_song_title)
    val artist = mediaMetadata?.subtitle?.toString()
        ?: mediaMetadata?.artist?.toString()
        ?: stringResource(R.string.unknown_artist)
    val durationMs = state.durationMs.takeIf { it > 0L }
        ?: mediaMetadata?.durationMs
        ?: 0L
    val currentMediaItem = state.mediaItem
    val currentMediaId = currentMediaItem?.mediaId
    val currentIsExternalAudio = currentMediaItem?.isExternalAudioLaunchItem() == true
    val favoriteEnabled = !currentIsExternalAudio &&
        !currentMediaId.isNullOrBlank() &&
        currentMediaId in favoriteIds
    val coverPreviewPositionMs = coverPageState.previewPositionMs
    val boundedLivePositionMs = livePositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val displayPositionMs = if (currentVisualPage == PlaybackVisualPage.Cover) {
        coverPreviewPositionMs
            ?.coerceIn(0L, durationMs.coerceAtLeast(0L))
            ?: boundedLivePositionMs
    } else {
        boundedLivePositionMs
    }
    val primaryLyricLine = stringResource(R.string.playback_more_primary_line)
    val secondaryLyricLine = stringResource(R.string.playback_more_secondary_line)
    val tertiaryLyricLine = stringResource(R.string.playback_more_tertiary_line)
    val fallbackLyricsLines = remember(
        title,
        artist,
        primaryLyricLine,
        secondaryLyricLine,
        tertiaryLyricLine,
    ) {
        listOf(
            title,
            artist,
            primaryLyricLine,
            secondaryLyricLine,
            tertiaryLyricLine,
        )
    }
    val controllerTracks = controller?.currentTracks
    val trackLyrics = remember(state.mediaItem?.mediaId, controllerTracks) {
        controllerTracks?.let(::extractEmbeddedLyrics)
    }
    val embeddedLyrics by produceState<EmbeddedLyrics?>(
        initialValue = trackLyrics,
        key1 = state.mediaItem?.mediaId,
        key2 = state.mediaItem?.localConfiguration?.uri,
        key3 = trackLyrics,
    ) {
        value = trackLyrics ?: state.mediaItem?.let { mediaItem ->
            loadEmbeddedLyrics(context, mediaItem)
        }
    }
    val artworkRequestKey = state.mediaItem?.artworkRequestKey()
    val albumArtwork by produceState<ImageBitmap?>(
        initialValue = null,
        artworkRequestKey,
    ) {
        value = state.mediaItem?.let { mediaItem ->
            loadEmbeddedArtwork(context, mediaItem)
        }
    }

    LaunchedEffect(
        currentVisualPage,
        coverPageState.dragMode,
        coverPageState.previewPositionMs,
        boundedLivePositionMs,
    ) {
        if (currentVisualPage != PlaybackVisualPage.Cover) {
            return@LaunchedEffect
        }
        val previewPosition = coverPageState.previewPositionMs ?: return@LaunchedEffect
        if (
            coverPageState.dragMode == CoverDragMode.None &&
            abs(boundedLivePositionMs - previewPosition) <= CoverPreviewSettleToleranceMs
        ) {
            coverPageState = coverPageState.copy(previewPositionMs = null)
        }
    }

    LaunchedEffect(
        currentVisualPage,
        coverPageState.dragMode,
        coverPageState.previewPositionMs,
    ) {
        if (currentVisualPage != PlaybackVisualPage.Cover) {
            return@LaunchedEffect
        }
        val previewPosition = coverPageState.previewPositionMs ?: return@LaunchedEffect
        if (coverPageState.dragMode != CoverDragMode.None) {
            return@LaunchedEffect
        }
        delay(CoverPreviewTimeoutMs)
        if (
            coverPageState.dragMode == CoverDragMode.None &&
            coverPageState.previewPositionMs == previewPosition
        ) {
            coverPageState = coverPageState.copy(previewPositionMs = null)
        }
    }

    LaunchedEffect(
        currentVisualPage,
        coverPageState.dragMode,
        coverPageState.needleSettlingPositionMs,
        boundedLivePositionMs,
    ) {
        if (currentVisualPage != PlaybackVisualPage.Cover) {
            return@LaunchedEffect
        }
        val settlingPosition = coverPageState.needleSettlingPositionMs ?: return@LaunchedEffect
        if (
            coverPageState.dragMode == CoverDragMode.None &&
            abs(boundedLivePositionMs - settlingPosition) <= CoverPreviewSettleToleranceMs
        ) {
            coverPageState = coverPageState.copy(
                needlePreviewRotationDegrees = null,
                needleSettlingPositionMs = null,
            )
        }
    }

    LaunchedEffect(
        currentVisualPage,
        coverPageState.dragMode,
        coverPageState.needleSettlingPositionMs,
    ) {
        if (currentVisualPage != PlaybackVisualPage.Cover) {
            return@LaunchedEffect
        }
        val settlingPosition = coverPageState.needleSettlingPositionMs ?: return@LaunchedEffect
        if (coverPageState.dragMode != CoverDragMode.None) {
            return@LaunchedEffect
        }
        delay(NeedleSeekSettleHoldTimeoutMs)
        if (
            coverPageState.dragMode == CoverDragMode.None &&
            coverPageState.needleSettlingPositionMs == settlingPosition
        ) {
            coverPageState = coverPageState.copy(
                needlePreviewRotationDegrees = null,
                needleSettlingPositionMs = null,
            )
        }
    }

    val latestScratchWarmupPositionMs by rememberUpdatedState(boundedLivePositionMs)
    LaunchedEffect(scratchSourceUri, playbackSettings.scratchEnabled) {
        scratchFlingJob?.cancel()
        scratchFlingJob = null
        scratchSoundController.stop()
        if (
            scratchSourceUri == null ||
            !playbackSettings.scratchEnabled
        ) {
            scratchSoundController.prepareSource(null, 0L)
            return@LaunchedEffect
        }
        while (isActive) {
            scratchSoundController.prepareSource(
                sourceUri = scratchSourceUri,
                positionMs = latestScratchWarmupPositionMs,
            )
            delay(ScratchWarmupRefreshMs)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PlaybackPageBackground),
    ) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) {
            maxHeight.roundToPx()
        }
        val topInset = with(density) {
            WindowInsets.safeDrawing.getTop(this).toDp()
        }
        val bottomInset = with(density) {
            WindowInsets.safeDrawing.getBottom(this).toDp()
        }
        val turntableWidth = maxWidth
        val bottomControlsWidth = turntableWidth
        val scale = turntableWidth.value / OriginalTurntableBaseWidthDp
        val turntableEntranceProgress = playbackEntranceProgress(
            timeMillis = entranceTimeMillis.value,
            delayMillis = 0,
            durationMillis = PlaybackTurntableEntranceDurationMillis,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumePlaybackTouchFallthrough(),
        )
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showTopBar) {
                PlaybackTopBar(
                    title = title,
                    artist = artist,
                    topInset = topInset,
                    onQueueClick = { showQueueOverlay = true },
                    onCollapse = onCollapse,
                )
            }
            PlaybackTimeSeekBar(
                durationMs = durationMs,
                currentPositionMs = displayPositionMs,
                thumbRes = R.drawable.playing_control_time,
                modifier = Modifier.fillMaxWidth(),
                onSeek = { positionMs ->
                    controller?.seekTo(positionMs)
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .graphicsLayer {
                        translationY = (1f - turntableEntranceProgress) * screenHeightPx.toFloat()
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                PlaybackVisualStage(
                    modifier = Modifier.width(turntableWidth),
                    turntableWidth = turntableWidth,
                    scale = scale,
                    currentVisualPage = currentVisualPage,
                    coverPositionMs = displayPositionMs,
                    lyricsPositionMs = boundedLivePositionMs,
                    durationMs = durationMs,
                    scratchEnabled = playbackSettings.scratchEnabled,
                    hidePlayerAxisEnabled = playbackSettings.hidePlayerAxisEnabled,
                    albumArtwork = albumArtwork,
                    keepLyricsScreenAwake = keepLyricsScreenAwake,
                    embeddedLyrics = embeddedLyrics,
                    fallbackLyricsLines = fallbackLyricsLines,
                    hasMediaItem = state.mediaItem != null,
                    isPlaying = state.isPlaying,
                    coverDragMode = coverPageState.dragMode,
                    previewPositionMs = coverPageState.previewPositionMs,
                    needlePreviewRotationDegrees = coverPageState.needlePreviewRotationDegrees,
                    needleParkedOutside = coverPageState.needleParkedOutside,
                    discManualRotationOffsetDegrees = discManualRotationOffsetDegrees,
                    mediaId = state.mediaItem?.mediaId,
                    onMoreClick = {
                        showMorePanel = true
                    },
                    onVisualPageToggle = ::toggleVisualPage,
                    onKeepLyricsScreenAwakeToggle = {
                        val enabled = !keepLyricsScreenAwake
                        keepLyricsScreenAwake = enabled
                        context.toast(
                            if (enabled) {
                                R.string.screen_light_on
                            } else {
                                R.string.screen_light_off
                            },
                        )
                    },
                    onDiscScratchStart = {
                        scratchFlingJob?.cancel()
                        scratchFlingJob = null
                        val resumePlaybackAfterDrag = state.isPlaying ||
                            coverPageState.resumePlaybackAfterDrag
                        coverPageState = coverPageState.copy(
                            dragMode = CoverDragMode.DiscScratch,
                            previewPositionMs = boundedLivePositionMs,
                            resumePlaybackAfterDrag = resumePlaybackAfterDrag,
                            needlePreviewRotationDegrees = null,
                            needleSettlingPositionMs = null,
                            needleParkedOutside = false,
                        )
                        if (resumePlaybackAfterDrag) {
                            controller?.pause()
                        }
                        controller?.setScratchSeekModeEnabled(true)
                        scratchSoundController.onScratchStart(
                            sourceUri = scratchSourceUri,
                            positionMs = boundedLivePositionMs,
                        )
                    },
                    onDiscScratchMotion = { positionMs, deltaAngle ->
                        discManualRotationOffsetDegrees += deltaAngle
                        scratchSoundController.onScratchMotion(positionMs, deltaAngle)
                    },
                    onDiscScratchPositionChange = { positionMs, _ ->
                        coverPageState = coverPageState.copy(
                            previewPositionMs = positionMs,
                        )
                    },
                    onDiscScratchEnd = { positionMs, flingVelocityDegreesPerSecond ->
                        val resumePlaybackAfterDrag = coverPageState.resumePlaybackAfterDrag
                        launchDiscScratchFling(
                            startPositionMs = positionMs,
                            initialVelocityDegreesPerSecond = flingVelocityDegreesPerSecond,
                            resumePlaybackAfterDrag = resumePlaybackAfterDrag,
                            durationMs = durationMs,
                        )
                    },
                    onDiscScratchCancel = {
                        resetCoverPageInteraction(resumePlayback = true)
                    },
                    onNeedleSeekStart = { rotationDegrees, positionMs ->
                        LegacyPlaybackHaptics.vibrateEffect(context)
                        val resumePlaybackAfterDrag = state.isPlaying
                        coverPageState = coverPageState.copy(
                            dragMode = CoverDragMode.NeedleSeek,
                            previewPositionMs = positionMs ?: 0L,
                            resumePlaybackAfterDrag = resumePlaybackAfterDrag,
                            needlePreviewRotationDegrees = rotationDegrees,
                            needleSettlingPositionMs = null,
                            needleParkedOutside = false,
                        )
                        if (resumePlaybackAfterDrag) {
                            controller?.pause()
                            state = state.copy(isPlaying = false)
                        }
                        controller?.setScratchSeekModeEnabled(true)
                    },
                    onNeedleSeekPositionChange = { rotationDegrees, positionMs ->
                        coverPageState = coverPageState.copy(
                            previewPositionMs = positionMs ?: 0L,
                            needlePreviewRotationDegrees = rotationDegrees,
                            needleSettlingPositionMs = null,
                        )
                    },
                    onNeedleSeekEnd = { rotationDegrees, positionMs ->
                        LegacyPlaybackHaptics.vibrateEffect(context)
                        val resumePlaybackAfterDrag = coverPageState.resumePlaybackAfterDrag
                        if (positionMs == null) {
                            coverPageState = coverPageState.copy(
                                dragMode = CoverDragMode.None,
                                previewPositionMs = 0L,
                                resumePlaybackAfterDrag = false,
                                needlePreviewRotationDegrees = null,
                                needleSettlingPositionMs = null,
                                needleParkedOutside = true,
                            )
                            controller?.seekTo(0L)
                            controller?.pause()
                            livePositionMs = 0L
                            state = state.copy(isPlaying = false, currentPositionMs = 0L)
                        } else {
                            coverPageState = coverPageState.copy(
                                dragMode = CoverDragMode.None,
                                previewPositionMs = positionMs,
                                resumePlaybackAfterDrag = false,
                                needlePreviewRotationDegrees = rotationDegrees,
                                needleSettlingPositionMs = positionMs,
                                needleParkedOutside = false,
                            )
                            controller?.seekTo(positionMs)
                            livePositionMs = positionMs
                            state = state.copy(
                                isPlaying = resumePlaybackAfterDrag,
                                currentPositionMs = positionMs,
                            )
                            if (resumePlaybackAfterDrag) {
                                controller?.play()
                            }
                        }
                        controller?.setScratchSeekModeEnabled(false)
                        scratchSoundController.stop()
                    },
                    onNeedleSeekCancel = {
                        LegacyPlaybackHaptics.vibrateEffect(context)
                        resetCoverPageInteraction(resumePlayback = true)
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            PlaybackBottomControls(
                width = bottomControlsWidth,
                bottomInset = bottomInset,
                state = state.copy(volume = volume),
                entranceTimeMillis = entranceTimeMillis.value,
                onRepeatClick = {
                    val nextRepeatMode = nextPlaybackRepeatMode(state.repeatMode)
                    controller?.repeatMode = nextRepeatMode
                    state = state.copy(repeatMode = nextRepeatMode)
                    context.toast(repeatToastRes(nextRepeatMode))
                },
                onPreviousClick = {
                    controller?.seekToPrevious()
                },
                onPlayPauseClick = {
                    if (state.isPlaying) {
                        controller?.pause()
                    } else {
                        controller?.play()
                    }
                },
                onNextClick = {
                    controller?.seekToNext()
                },
                onShuffleClick = {
                    val shuffleEnabled = !state.shuffleEnabled
                    controller?.shuffleModeEnabled = shuffleEnabled
                    state = state.copy(shuffleEnabled = shuffleEnabled)
                    context.toast(shuffleToastRes(shuffleEnabled))
                },
                onVolumeChange = { targetVolume ->
                    context.setMusicStreamVolumeFraction(targetVolume)
                    val actualVolume = context.musicStreamVolumeFraction()
                    volume = actualVolume
                    state = state.copy(volume = actualVolume)
                },
            )
        }

        PlaybackMoreActionOverlays(
            showMorePanel = showMorePanel,
            favoriteEnabled = favoriteEnabled,
            currentVisualPage = currentVisualPage,
            scratchEnabled = playbackSettings.scratchEnabled,
            sleepTimerActive = sleepTimerState.isActive,
            addToPlaylistEnabled = !currentIsExternalAudio,
            showSleepTimerDialog = showSleepTimerDialog,
            sleepTimerState = sleepTimerState,
            bottomInsetPx = (bottomInset.value * density.density).roundToInt(),
            showSetRingtoneDialog = showSetRingtoneDialog,
            showWriteSettingsDialog = showWriteSettingsDialog,
            onAddToPlaylistClick = {
                state.mediaItem?.let { onRequestAddToPlaylist(listOf(it)) }
                showMorePanel = false
            },
            onAddToQueueClick = {
                state.mediaItem?.let { onRequestAddToQueue(listOf(it)) }
                showMorePanel = false
            },
            onFavoriteToggle = {
                val mediaId = currentMediaId
                if (!mediaId.isNullOrBlank() && !currentIsExternalAudio) {
                    scope.launch {
                        favoriteRepository.toggle(mediaId)
                    }
                }
                showMorePanel = false
            },
            onSetRingtoneClick = {
                showMorePanel = false
                showSetRingtoneDialog = true
            },
            onSleepTimerClick = {
                showMorePanel = false
                showSleepTimerDialog = true
            },
            onLyricsToggle = {
                toggleVisualPage()
                showMorePanel = false
            },
            onScratchToggle = {
                onScratchEnabledChange(!playbackSettings.scratchEnabled)
                showMorePanel = false
            },
            onDeleteClick = {
                when (val result = state.mediaItem?.resolveDeleteTarget()
                    ?: PlaybackDeleteTargetResult.Unavailable) {
                    is PlaybackDeleteTargetResult.Available -> {
                        launchDeleteRequest(result.target)
                    }
                    PlaybackDeleteTargetResult.CueFile -> {
                        context.toast(R.string.can_not_delete_cue_file)
                    }
                    PlaybackDeleteTargetResult.Unavailable -> {
                        context.toast(R.string.can_not_delete_song)
                    }
                }
                showMorePanel = false
            },
            onDismissMorePanel = {
                showMorePanel = false
            },
            onSleepTimerDismiss = {
                showSleepTimerDialog = false
            },
            onSleepTimerDurationSelected = { selectedDurationMs ->
                showSleepTimerDialog = false
                if (selectedDurationMs > 0L) {
                    controller?.startSleepTimer(selectedDurationMs)
                } else {
                    controller?.cancelSleepTimer()
                    if (sleepTimerState.isActive) {
                        context.toast(R.string.sleep_timer_stopped)
                    }
                }
            },
            onSetRingtoneConfirm = {
                showSetRingtoneDialog = false
                val ringtoneUri = state.mediaItem?.localConfiguration?.uri
                if (ringtoneUri == null) {
                    context.toast(R.string.can_not_set_ringtone)
                } else {
                    pendingRingtoneUriString = ringtoneUri.toString()
                    if (Settings.System.canWrite(context)) {
                        applyPendingRingtone()
                    } else {
                        showWriteSettingsDialog = true
                    }
                }
            },
            onSetRingtoneDismiss = {
                showSetRingtoneDialog = false
            },
            onWriteSettingsConfirm = {
                showWriteSettingsDialog = false
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                if (intent.resolveActivity(context.packageManager) == null) {
                    pendingRingtoneUriString = null
                    context.toast(R.string.ring_fault)
                } else {
                    writeSettingsLauncher.launch(intent)
                }
            },
            onWriteSettingsDismiss = {
                showWriteSettingsDialog = false
                pendingRingtoneUriString = null
            },
        )

        PlaybackQueueOverlayHost(
            showQueueOverlay = showQueueOverlay,
            currentTrackProvider = {
                state.mediaItem?.toPlaybackQueueTrack(
                    context = context,
                    queueIndex = controller?.currentMediaItemIndex ?: -1,
                )
            },
            upcomingItemsProvider = {
                controller?.upcomingQueueTracks(context).orEmpty()
            },
            isCurrentFavorite = favoriteEnabled,
            reorderEnabled = state.canReorderUpcomingQueue,
            onExitFullScreenClick = {
                showQueueOverlay = false
                onCollapse()
            },
            onReturnToPlaybackClick = {
                showQueueOverlay = false
            },
            onItemClick = { queueIndex ->
                val playbackController = controller ?: return@PlaybackQueueOverlayHost
                if (queueIndex in 0 until playbackController.mediaItemCount) {
                    playbackController.seekToDefaultPosition(queueIndex)
                }
                showQueueOverlay = false
            },
            onFavoriteCurrentClick = {
                val mediaId = currentMediaId ?: return@PlaybackQueueOverlayHost
                if (currentIsExternalAudio) {
                    return@PlaybackQueueOverlayHost
                }
                scope.launch {
                    favoriteRepository.toggle(mediaId)
                }
            },
            onClearUpcomingClick = {
                val playbackController = controller ?: return@PlaybackQueueOverlayHost
                val currentIndex = playbackController.currentMediaItemIndex
                val upcomingIndexes = playbackController.upcomingQueueTracks(context)
                    .map { track -> track.queueIndex }
                    .filter { index -> index >= 0 && index != currentIndex }
                    .distinct()
                    .sortedDescending()
                upcomingIndexes.forEach { index ->
                    if (index in 0 until playbackController.mediaItemCount) {
                        playbackController.removeMediaItem(index)
                    }
                }
            },
            onMoveRequest = { fromIndex, toIndex ->
                if (!state.canReorderUpcomingQueue) {
                    return@PlaybackQueueOverlayHost
                }
                if (fromIndex == toIndex) {
                    return@PlaybackQueueOverlayHost
                }
                val playbackController = controller ?: return@PlaybackQueueOverlayHost
                val itemCount = playbackController.mediaItemCount
                if (fromIndex in 0 until itemCount && toIndex in 0 until itemCount) {
                    playbackController.moveMediaItem(fromIndex, toIndex)
                }
            },
        )
    }
}

private val PlaybackScreenState.canReorderUpcomingQueue: Boolean
    get() = !shuffleEnabled && repeatMode != Player.REPEAT_MODE_ALL

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private const val PlaybackPositionPlayingRefreshMs = 250L
private const val PlaybackPositionIdleRefreshMs = 500L
private const val PlaybackVolumeRefreshMs = 200L
private const val PlaybackVolumeChangeEpsilon = 0.001f
