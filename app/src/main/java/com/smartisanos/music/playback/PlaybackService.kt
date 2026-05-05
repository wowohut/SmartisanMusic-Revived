@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisanos.music.playback

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.core.content.ContextCompat
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.smartisanos.music.MainActivity
import com.smartisanos.music.data.library.LibraryExclusions
import com.smartisanos.music.data.library.LibraryExclusionsStore
import com.smartisanos.music.data.playback.PlaybackStatsRepository
import com.smartisanos.music.isExternalAudioLaunchItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class PlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var localAudioLibrary: LocalAudioLibrary
    private lateinit var libraryExecutor: ListeningExecutorService
    private lateinit var libraryRefreshExecutor: ListeningExecutorService
    private lateinit var libraryExclusionsStore: LibraryExclusionsStore
    private lateinit var playbackStatsRepository: PlaybackStatsRepository
    private lateinit var playbackSessionStateStore: PlaybackSessionStateStore
    private var playbackSessionStateCoordinator: PlaybackSessionStateCoordinator? = null
    private var playbackPlayCountTracker: PlaybackPlayCountTracker? = null
    private var pendingStatsLibraryRefreshJob: Job? = null
    private var pendingRatingLibraryRefreshJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var exclusionsSnapshot: LibraryExclusions = LibraryExclusions()
    private val exclusionsReady = CompletableDeferred<LibraryExclusions>()
    private var scratchSuppressedPlayerVolume: Float? = null

    override fun onCreate() {
        super.onCreate()
        playbackStatsRepository = PlaybackStatsRepository.getInstance(this)
        localAudioLibrary = LocalAudioLibrary(
            context = this,
            playbackStatsProvider = playbackStatsRepository::getStats,
            playbackStatsByIdsProvider = playbackStatsRepository::getStats,
        )
        libraryExclusionsStore = LibraryExclusionsStore(this)
        playbackSessionStateStore = PlaybackSessionStateStore(this)
        libraryExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
        libraryRefreshExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            PlaybackLibrarySessionCallback(),
        )
            .setSessionActivity(createSessionActivityPendingIntent())
            .setPeriodicPositionUpdateEnabled(false)
            .build()

        playbackPlayCountTracker = PlaybackPlayCountTracker(
            player = exoPlayer,
            repository = playbackStatsRepository,
            scope = serviceScope,
            onPlayCountChanged = {
                scheduleStatsLibraryRefresh()
            },
        ).also { tracker ->
            tracker.start()
        }

        playbackSessionStateCoordinator = PlaybackSessionStateCoordinator(
            player = exoPlayer,
            stateStore = playbackSessionStateStore,
            scope = serviceScope,
            canLoadLibraryItems = { hasAudioPermission() },
            loadLibraryItemsByQueueKeys = { queueKeys -> getAudioItemsByQueueKeys(queueKeys) },
        ).also { coordinator ->
            coordinator.start()
        }

        serviceScope.launch(Dispatchers.IO) {
            libraryExclusionsStore.exclusions.collect { exclusions ->
                exclusionsSnapshot = exclusions
                if (!exclusionsReady.isCompleted) {
                    exclusionsReady.complete(exclusions)
                }
                withContext(Dispatchers.Main.immediate) {
                    removeHiddenQueuedItems(exclusions)
                    mediaLibrarySession?.notifyChildrenChanged(
                        LocalAudioLibrary.ROOT_ID,
                        Int.MAX_VALUE,
                        null,
                    )
                }
            }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        if (!exclusionsReady.isCompleted) {
            exclusionsReady.complete(exclusionsSnapshot)
        }
        playbackSessionStateCoordinator?.let { coordinator ->
            runBlocking {
                coordinator.saveNow()
            }
            coordinator.stop()
        }
        playbackSessionStateCoordinator = null
        playbackPlayCountTracker?.let { tracker ->
            runBlocking {
                tracker.stopAndFlush()
            }
        }
        playbackPlayCountTracker = null
        pendingStatsLibraryRefreshJob?.cancel()
        pendingStatsLibraryRefreshJob = null
        pendingRatingLibraryRefreshJob?.cancel()
        pendingRatingLibraryRefreshJob = null
        serviceScope.cancel()
        PlaybackSleepTimer.cancel()
        mediaLibrarySession?.release()
        mediaLibrarySession = null

        player?.release()
        player = null
        libraryExecutor.shutdown()
        libraryRefreshExecutor.shutdown()

        super.onDestroy()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAudioItems(forceRefresh: Boolean = false): List<MediaItem> {
        if (!hasAudioPermission()) {
            return emptyList()
        }
        val exclusions = if (exclusionsReady.isCompleted) {
            exclusionsSnapshot
        } else {
            runBlocking { exclusionsReady.await() }
        }
        return localAudioLibrary.getAudioItems(forceRefresh = forceRefresh)
            .asSequence()
            .filter { item ->
                val relativePath = item.mediaMetadata.extras
                    ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                !exclusions.isMediaHidden(item.mediaId, relativePath)
            }
            .toList()
    }

    private fun getAudioItemsByIds(mediaIds: List<String>): List<MediaItem> {
        if (!hasAudioPermission() || mediaIds.isEmpty()) {
            return emptyList()
        }
        val exclusions = if (exclusionsReady.isCompleted) {
            exclusionsSnapshot
        } else {
            runBlocking { exclusionsReady.await() }
        }
        return localAudioLibrary.getAudioItemsByIds(mediaIds)
            .asSequence()
            .filter { item ->
                val relativePath = item.mediaMetadata.extras
                    ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                !exclusions.isMediaHidden(item.mediaId, relativePath)
            }
            .toList()
    }

    private fun getAudioItemsByQueueKeys(queueKeys: List<PlaybackQueueSnapshotItem>): List<MediaItem> {
        if (!hasAudioPermission() || queueKeys.isEmpty()) {
            return emptyList()
        }
        val exclusions = if (exclusionsReady.isCompleted) {
            exclusionsSnapshot
        } else {
            runBlocking { exclusionsReady.await() }
        }
        return localAudioLibrary.getAudioItemsByQueueKeys(queueKeys)
            .asSequence()
            .filter { item ->
                val relativePath = item.mediaMetadata.extras
                    ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                !exclusions.isMediaHidden(item.mediaId, relativePath)
            }
            .toList()
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            PlaybackSessionActivityRequestCode,
            MainActivity.createOpenPlaybackIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setScratchAudioSuppressionEnabled(enabled: Boolean) {
        val activePlayer = player ?: return
        if (enabled) {
            if (scratchSuppressedPlayerVolume == null) {
                scratchSuppressedPlayerVolume = activePlayer.volume
                activePlayer.volume = 0f
            }
            return
        }
        scratchSuppressedPlayerVolume?.let { volume ->
            activePlayer.volume = volume
        }
        scratchSuppressedPlayerVolume = null
    }

    private fun removeHiddenQueuedItems(exclusions: LibraryExclusions) {
        player.removeMediaItemsMatching { item ->
            val relativePath = item.mediaMetadata.extras
                ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            exclusions.isMediaHidden(item.mediaId, relativePath)
        }
    }

    private inner class PlaybackLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(ScratchSeekModeCommand)
                .add(ScratchAudioSuppressionCommand)
                .add(StartSleepTimerCommand)
                .add(CancelSleepTimerCommand)
                .add(RefreshLibraryCommand)
                .add(InvalidateLibraryCommand)
                .add(SetTrackRatingCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(localAudioLibrary.getRootItem(), params),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return libraryExecutor.submit<LibraryResult<ImmutableList<MediaItem>>> {
                if (parentId != LocalAudioLibrary.ROOT_ID) {
                    return@submit LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
                }

                val items = getAudioItems()
                if (items.isEmpty() && !hasAudioPermission()) {
                    return@submit LibraryResult.ofError(
                        SessionError.ERROR_PERMISSION_DENIED,
                        params,
                    )
                }

                val fromIndex = (page * pageSize).coerceAtMost(items.size)
                val toIndex = (fromIndex + pageSize).coerceAtMost(items.size)
                LibraryResult.ofItemList(items.subList(fromIndex, toIndex), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return libraryExecutor.submit<LibraryResult<MediaItem>> {
                if (!hasAudioPermission() && mediaId != LocalAudioLibrary.ROOT_ID) {
                    return@submit LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                }

                val item = if (mediaId == LocalAudioLibrary.ROOT_ID) {
                    localAudioLibrary.getRootItem()
                } else {
                    getAudioItemsByIds(listOf(mediaId)).firstOrNull()
                }
                if (item == null) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                } else {
                    LibraryResult.ofItem(item, null)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            return libraryExecutor.submit<MutableList<MediaItem>> {
                val itemsById = getAudioItemsByIds(mediaItems.map { item -> item.mediaId })
                    .associateBy { it.mediaId }
                mediaItems.mapNotNullTo(mutableListOf()) { item ->
                    itemsById[item.mediaId] ?: item.takeIf { it.isExternalAudioLaunchItem() }
                }
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ScratchSeekModeAction) {
                val enabled = args.getBoolean(ScratchSeekModeEnabledKey, false)
                player?.setSeekParameters(
                    if (enabled) SeekParameters.EXACT else SeekParameters.DEFAULT,
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ScratchAudioSuppressionAction) {
                setScratchAudioSuppressionEnabled(
                    args.getBoolean(ScratchAudioSuppressionEnabledKey, false),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == StartSleepTimerAction) {
                val durationMs = args.getLong(SleepTimerDurationMsKey, 0L)
                if (durationMs <= 0L) {
                    return Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_BAD_VALUE),
                    )
                }
                PlaybackSleepTimer.start(durationMs) {
                    player?.pause()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == CancelSleepTimerAction) {
                PlaybackSleepTimer.cancel()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == RefreshLibraryAction) {
                return libraryRefreshExecutor.submit<SessionResult> {
                    if (!hasAudioPermission()) {
                        return@submit SessionResult(SessionError.ERROR_PERMISSION_DENIED)
                    }

                    val result = localAudioLibrary.refreshAudioItems()
                    mediaLibrarySession?.notifyChildrenChanged(
                        LocalAudioLibrary.ROOT_ID,
                        result.items.size,
                        null,
                    )
                    serviceScope.launch {
                        playbackSessionStateCoordinator?.restoreIfQueueEmpty()
                    }
                    SessionResult(
                        if (result.successful) {
                            SessionResult.RESULT_SUCCESS
                        } else {
                            SessionError.ERROR_UNKNOWN
                        },
                    )
                }
            }
            if (customCommand.customAction == InvalidateLibraryAction) {
                return libraryRefreshExecutor.submit<SessionResult> {
                    if (!hasAudioPermission()) {
                        return@submit SessionResult(SessionError.ERROR_PERMISSION_DENIED)
                    }

                    val items = getAudioItems(forceRefresh = true)
                    mediaLibrarySession?.notifyChildrenChanged(
                        LocalAudioLibrary.ROOT_ID,
                        items.size,
                        null,
                    )
                    serviceScope.launch {
                        playbackSessionStateCoordinator?.restoreIfQueueEmpty()
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            if (customCommand.customAction == SetTrackRatingAction) {
                val mediaId = args.getString(TrackRatingMediaIdKey)?.trim().orEmpty()
                val score = args.getInt(TrackRatingScoreKey, -1)
                if (mediaId.isBlank() || score !in TrackRatingMinScore..TrackRatingMaxScore) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }
                return libraryRefreshExecutor.submit<SessionResult> {
                    runBlocking {
                        playbackStatsRepository.setScore(mediaId, score)
                    } ?: return@submit SessionResult(SessionError.ERROR_UNKNOWN)
                    serviceScope.launch(Dispatchers.Main.immediate) {
                        scheduleRatingLibraryRefresh()
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun scheduleStatsLibraryRefresh() {
        serviceScope.launch(Dispatchers.Main.immediate) {
            pendingStatsLibraryRefreshJob?.cancel()
            pendingStatsLibraryRefreshJob = serviceScope.launch(Dispatchers.Main.immediate) {
                delay(StatsLibraryRefreshDebounceMs)
                refreshStatsLibrary()
            }
        }
    }

    private fun scheduleRatingLibraryRefresh() {
        pendingRatingLibraryRefreshJob?.cancel()
        pendingRatingLibraryRefreshJob = serviceScope.launch(Dispatchers.Main.immediate) {
            delay(RatingLibraryRefreshDebounceMs)
            pendingStatsLibraryRefreshJob?.cancel()
            pendingStatsLibraryRefreshJob = null
            refreshStatsLibrary()
        }
    }

    private fun refreshStatsLibrary() {
        localAudioLibrary.invalidateAudioItems()
        mediaLibrarySession?.notifyChildrenChanged(
            LocalAudioLibrary.ROOT_ID,
            Int.MAX_VALUE,
            null,
        )
    }

    private companion object {
        private const val PlaybackSessionActivityRequestCode = 1001
        private const val StatsLibraryRefreshDebounceMs = 600L
        private const val RatingLibraryRefreshDebounceMs = 250L
    }
}
