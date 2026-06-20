@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisanos.music.playback

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import com.smartisanos.music.data.online.OnlineMusicRepositoryRouter
import com.smartisanos.music.data.online.OnlineTrackIdentity
import com.smartisanos.music.data.online.isOnlineMediaItem
import com.smartisanos.music.data.online.isNeteasePreviewDuration
import com.smartisanos.music.data.online.onlinePlaybackUriIdentityOrNull
import com.smartisanos.music.data.online.onlineTrackIdentityOrNull
import com.smartisanos.music.data.online.shouldRefreshOnlinePlaybackUrl
import com.smartisanos.music.data.online.toOnlinePlaybackPlaceholderMediaItem
import com.smartisanos.music.data.online.withOnlinePlaybackPlaceholderUri
import com.smartisanos.music.data.playback.PlaybackStatsRepository
import com.smartisanos.music.data.settings.PlaybackSettingsStore
import com.smartisanos.music.isExternalAudioLaunchItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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
    private lateinit var playbackSettingsStore: PlaybackSettingsStore
    private lateinit var playbackStatsRepository: PlaybackStatsRepository
    private lateinit var playbackSessionStateStore: PlaybackSessionStateStore
    private lateinit var onlineMusicRepository: OnlineMusicRepositoryRouter
    private var playbackSessionStateCoordinator: PlaybackSessionStateCoordinator? = null
    private var playbackPlayCountTracker: PlaybackPlayCountTracker? = null
    private var playbackAudioFxController: PlaybackAudioFxController? = null
    private var playbackMetadataPreloader: PlaybackMetadataPreloader? = null
    private var mediaSessionArtworkBitmapLoader: MediaSessionArtworkBitmapLoader? = null
    private var pendingStatsLibraryRefreshJob: Job? = null
    private var pendingRatingLibraryRefreshJob: Job? = null
    private var onlineMediaRefreshJob: Job? = null
    private var onlineMediaRefreshJobForceRefresh = false
    private var lastOnlineMediaRefreshKey: String? = null
    private var lastOnlineMediaRefreshAtMs: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var exclusionsSnapshot: LibraryExclusions = LibraryExclusions()
    private val exclusionsReady = CompletableDeferred<LibraryExclusions>()
    private val audioFxPlayerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            playbackAudioFxController?.setAudioSessionId(audioSessionId)
        }
    }
    private val onlineMediaRefreshListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            resolveAdjacentOnlineMediaItem()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                refreshCurrentOnlineMediaUrlAfterPreviewEnd()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            refreshCurrentOnlineMediaUrlAfterError()
        }
    }

    override fun onCreate() {
        super.onCreate()
        playbackStatsRepository = PlaybackStatsRepository.getInstance(this)
        localAudioLibrary = LocalAudioLibrary(
            context = this,
            playbackStatsProvider = playbackStatsRepository::getStats,
            playbackStatsByIdsProvider = playbackStatsRepository::getStats,
        )
        libraryExclusionsStore = LibraryExclusionsStore(this)
        playbackSettingsStore = PlaybackSettingsStore(this)
        playbackSessionStateStore = PlaybackSessionStateStore(this)
        onlineMusicRepository = OnlineMusicRepositoryRouter(applicationContext)
        libraryExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
        libraryRefreshExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this),
            OnlinePlaybackDataSpecResolver(onlineMusicRepository),
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(
            PlaybackStreamingCache.createDataSourceFactory(
                context = this,
                upstreamFactory = dataSourceFactory,
            ),
        )
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(PlaylistPreloadDurationUs))
            }
        val artworkBitmapLoader = MediaSessionArtworkBitmapLoader(this)

        player = exoPlayer
        playbackAudioFxController = PlaybackAudioFxController().also { controller ->
            controller.setAudioSessionId(exoPlayer.audioSessionId)
        }
        exoPlayer.addListener(audioFxPlayerListener)
        exoPlayer.addListener(onlineMediaRefreshListener)
        playbackMetadataPreloader = PlaybackMetadataPreloader(
            context = this,
            player = exoPlayer,
            scope = serviceScope,
        ).also { preloader ->
            preloader.start()
        }
        mediaSessionArtworkBitmapLoader = artworkBitmapLoader
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            PlaybackLibrarySessionCallback(),
        )
            .setSessionActivity(createSessionActivityPendingIntent())
            .setBitmapLoader(artworkBitmapLoader)
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
        serviceScope.launch(Dispatchers.Main.immediate) {
            playbackSettingsStore.settings.collect { settings ->
                playbackAudioFxController?.setSettings(settings)
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
        playbackMetadataPreloader?.stop()
        playbackMetadataPreloader = null
        serviceScope.cancel()
        PlaybackSleepTimer.cancel()
        player?.removeListener(audioFxPlayerListener)
        player?.removeListener(onlineMediaRefreshListener)
        onlineMediaRefreshJob?.cancel()
        onlineMediaRefreshJob = null
        playbackAudioFxController?.release()
        playbackAudioFxController = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null

        player?.release()
        player = null
        mediaSessionArtworkBitmapLoader?.shutdown()
        mediaSessionArtworkBitmapLoader = null
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
        if (queueKeys.isEmpty()) {
            return emptyList()
        }
        val localQueueKeys = queueKeys.filterNot { key ->
            key.mediaId.onlineTrackIdentityOrNull() != null
        }
        val onlineItems = restoreOnlineItemsByQueueKeys(queueKeys)
        val localItems = if (hasAudioPermission() && localQueueKeys.isNotEmpty()) {
            val exclusions = if (exclusionsReady.isCompleted) {
                exclusionsSnapshot
            } else {
                runBlocking { exclusionsReady.await() }
            }
            localAudioLibrary.getAudioItemsByQueueKeys(localQueueKeys)
                .asSequence()
                .filter { item ->
                    val relativePath = item.mediaMetadata.extras
                        ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                    !exclusions.isMediaHidden(item.mediaId, relativePath)
                }
                .toList()
        } else {
            emptyList()
        }
        val itemsById = (localItems + onlineItems).associateBy(MediaItem::mediaId)
        val itemsByStableKey = (localItems + onlineItems).associateBy { item -> item.stableKey.orEmpty() }
        return queueKeys.mapNotNull { key ->
            itemsById[key.mediaId] ?: itemsByStableKey[key.stableKey]
        }
    }

    private fun restoreOnlineItemsByQueueKeys(
        queueKeys: List<PlaybackQueueSnapshotItem>,
    ): List<MediaItem> {
        val identities = queueKeys
            .asSequence()
            .mapNotNull { key -> key.mediaId.onlineTrackIdentityOrNull() }
            .distinct()
            .toList()
        if (identities.isEmpty()) {
            return emptyList()
        }
        return identities.map(OnlineTrackIdentity::toOnlinePlaybackPlaceholderMediaItem)
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            PlaybackSessionActivityRequestCode,
            MainActivity.createOpenPlaybackIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun removeHiddenQueuedItems(exclusions: LibraryExclusions) {
        player.removeMediaItemsMatching { item ->
            val relativePath = item.mediaMetadata.extras
                ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            exclusions.isMediaHidden(item.mediaId, relativePath)
        }
    }

    private fun refreshCurrentOnlineMediaUrlAfterError() {
        val playbackPlayer = player ?: return
        val currentItem = playbackPlayer.currentMediaItem ?: return
        if (!currentItem.isOnlineMediaItem()) {
            return
        }

        val refreshKey = currentItem.mediaId.takeIf(String::isNotBlank) ?: return
        if (!recordOnlineMediaRefreshAttempt(refreshKey)) {
            return
        }

        resolveOnlineMediaItemAt(
            item = currentItem,
            itemIndex = playbackPlayer.currentMediaItemIndex,
            resumePositionMs = playbackPlayer.currentPosition.coerceAtLeast(0L),
            resumePlayback = playbackPlayer.playWhenReady,
            prepareAfterReplace = true,
            forceRefresh = true,
            skipOnFailure = true,
        )
    }

    private fun refreshCurrentOnlineMediaUrlAfterPreviewEnd() {
        val playbackPlayer = player ?: return
        val currentItem = playbackPlayer.currentMediaItem ?: return
        if (!currentItem.isOnlineMediaItem()) {
            return
        }
        val originalDurationMs = currentItem.mediaMetadata.durationMs ?: return
        val playedDurationMs = playbackPlayer.duration
            .takeIf { duration -> duration > 0L && duration != C.TIME_UNSET }
            ?: playbackPlayer.currentPosition.coerceAtLeast(0L)
        if (!isNeteasePreviewDuration(playedDurationMs, originalDurationMs)) {
            return
        }
        val refreshKey = currentItem.mediaId.takeIf(String::isNotBlank) ?: return
        if (!recordOnlineMediaRefreshAttempt(refreshKey)) {
            return
        }
        resolveOnlineMediaItemAt(
            item = currentItem,
            itemIndex = playbackPlayer.currentMediaItemIndex,
            resumePositionMs = 0L,
            resumePlayback = true,
            prepareAfterReplace = true,
            forceRefresh = true,
        )
    }

    private fun resolveAdjacentOnlineMediaItem() {
        val playbackPlayer = player ?: return
        val currentIndex = playbackPlayer.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) {
            return
        }
        val currentItem = playbackPlayer.currentMediaItem
        if (
            currentItem?.isOnlineMediaItem() == true &&
            currentItem.shouldRefreshOnlinePlaybackUrl()
        ) {
            if (currentItem.localConfiguration?.uri != null) {
                val refreshKey = currentItem.mediaId.takeIf(String::isNotBlank) ?: return
                if (!recordOnlineMediaRefreshAttempt(refreshKey)) {
                    return
                }
            }
            resolveOnlineMediaItemAt(
                item = currentItem,
                itemIndex = currentIndex,
                resumePositionMs = playbackPlayer.currentPosition.coerceAtLeast(0L),
                resumePlayback = playbackPlayer.playWhenReady,
                prepareAfterReplace = true,
                forceRefresh = false,
            )
            return
        }

        val nextIndex = currentIndex + 1
        if (nextIndex !in 0 until playbackPlayer.mediaItemCount) {
            return
        }
        val nextItem = playbackPlayer.getMediaItemAt(nextIndex)
        if (!nextItem.isOnlineMediaItem() || !nextItem.shouldRefreshOnlinePlaybackUrl()) {
            return
        }
        resolveOnlineMediaItemAt(
            item = nextItem,
            itemIndex = nextIndex,
            resumePositionMs = 0L,
            resumePlayback = false,
            prepareAfterReplace = false,
            forceRefresh = false,
        )
    }

    private fun resolveOnlineMediaItemAt(
        item: MediaItem,
        itemIndex: Int,
        resumePositionMs: Long,
        resumePlayback: Boolean,
        prepareAfterReplace: Boolean,
        forceRefresh: Boolean,
        skipOnFailure: Boolean = false,
    ) {
        val activeRefreshJob = onlineMediaRefreshJob
        if (activeRefreshJob?.isActive == true) {
            if (!forceRefresh || onlineMediaRefreshJobForceRefresh) {
                return
            }
            activeRefreshJob.cancel()
        }
        if (!item.isOnlineMediaItem()) {
            return
        }
        if (!forceRefresh && !item.shouldRefreshOnlinePlaybackUrl()) {
            return
        }
        var resolveAdjacentAfterCompletion = false
        val refreshJob = serviceScope.launch {
            val refreshedItem = try {
                onlineMusicRepository.resolvePlayableMediaItem(
                    mediaItem = item,
                    includeLyrics = false,
                    forceRefresh = forceRefresh,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (refreshedItem == null) {
                if (skipOnFailure) {
                    skipCurrentOnlineMediaItemAfterError(item.mediaId)
                }
                return@launch
            }
            val activePlayer = player ?: return@launch
            val targetIndex = itemIndex.takeIf { it in 0 until activePlayer.mediaItemCount }
                ?: return@launch
            if (activePlayer.getMediaItemAt(targetIndex).mediaId != item.mediaId) {
                return@launch
            }
            activePlayer.replaceMediaItem(targetIndex, refreshedItem)
            if (activePlayer.currentMediaItemIndex == targetIndex) {
                val targetPositionMs = if (prepareAfterReplace) {
                    resumePositionMs
                } else {
                    activePlayer.currentPosition.coerceAtLeast(0L)
                }
                val targetPlayWhenReady = if (prepareAfterReplace) {
                    resumePlayback
                } else {
                    activePlayer.playWhenReady
                }
                activePlayer.seekTo(targetIndex, targetPositionMs)
                activePlayer.prepare()
                activePlayer.playWhenReady = targetPlayWhenReady
                if (targetPlayWhenReady) {
                    activePlayer.play()
                }
                resolveAdjacentAfterCompletion = true
            }
        }
        onlineMediaRefreshJob = refreshJob
        onlineMediaRefreshJobForceRefresh = forceRefresh
        refreshJob.invokeOnCompletion {
            if (onlineMediaRefreshJob === refreshJob) {
                onlineMediaRefreshJob = null
                onlineMediaRefreshJobForceRefresh = false
            }
            if (resolveAdjacentAfterCompletion) {
                serviceScope.launch {
                    resolveAdjacentOnlineMediaItem()
                }
            }
        }
    }

    private fun skipCurrentOnlineMediaItemAfterError(mediaId: String) {
        val playbackPlayer = player ?: return
        val currentItem = playbackPlayer.currentMediaItem ?: return
        if (currentItem.mediaId != mediaId) {
            return
        }
        if (
            !shouldSkipOnlinePlaybackError(
                isCurrentOnline = currentItem.isOnlineMediaItem(),
                hasNextMediaItem = playbackPlayer.hasNextMediaItem(),
                repeatMode = playbackPlayer.repeatMode,
            )
        ) {
            return
        }
        val resumePlayback = playbackPlayer.playWhenReady
        playbackPlayer.seekToNextMediaItem()
        playbackPlayer.prepare()
        if (resumePlayback) {
            playbackPlayer.play()
        }
    }

    private fun recordOnlineMediaRefreshAttempt(refreshKey: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (
            lastOnlineMediaRefreshKey == refreshKey &&
            now - lastOnlineMediaRefreshAtMs < OnlineMediaRefreshCooldownMs
        ) {
            return false
        }
        lastOnlineMediaRefreshKey = refreshKey
        lastOnlineMediaRefreshAtMs = now
        return true
    }

    private inner class PlaybackLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(ScratchSeekModeCommand)
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
                if (
                    !hasAudioPermission() &&
                    mediaId != LocalAudioLibrary.ROOT_ID &&
                    mediaId.onlineTrackIdentityOrNull() == null
                ) {
                    return@submit LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                }

                val item = if (mediaId == LocalAudioLibrary.ROOT_ID) {
                    localAudioLibrary.getRootItem()
                } else if (mediaId.onlineTrackIdentityOrNull() != null) {
                    val identity = mediaId.onlineTrackIdentityOrNull()
                    runBlocking {
                        identity?.let { onlineMusicRepository.getMediaItem(it) }
                    }
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
                val itemsById = getAudioItemsByIds(
                    mediaItems
                        .filterNot(MediaItem::isOnlineMediaItem)
                        .map { item -> item.mediaId },
                )
                    .associateBy { it.mediaId }
                mediaItems.mapNotNullTo(mutableListOf()) { item ->
                    when {
                        item.isOnlineMediaItem() -> item.withOnlinePlaybackPlaceholderUri()
                        item.isExternalAudioLaunchItem() -> item
                        else -> itemsById[item.mediaId]
                    }
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
                    val savedScore = runBlocking {
                        playbackStatsRepository.setScore(mediaId, score)
                    } ?: return@submit SessionResult(SessionError.ERROR_UNKNOWN)
                    runBlocking(Dispatchers.Main.immediate) {
                        updateQueuedTrackRating(mediaId, savedScore)
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

    private fun updateQueuedTrackRating(mediaId: String, score: Int) {
        val playbackPlayer = player ?: return
        for (index in 0 until playbackPlayer.mediaItemCount) {
            val item = playbackPlayer.getMediaItemAt(index)
            if (item.mediaId == mediaId) {
                playbackPlayer.replaceMediaItem(index, item.withPlaybackRating(score))
            }
        }
    }

    private companion object {
        private const val PlaybackSessionActivityRequestCode = 1001
        private const val StatsLibraryRefreshDebounceMs = 600L
        private const val RatingLibraryRefreshDebounceMs = 250L
        private const val OnlineMediaRefreshCooldownMs = 30_000L
        private const val PlaylistPreloadDurationUs = 12_000_000L
    }
}

private class OnlinePlaybackDataSpecResolver(
    private val onlineMusicRepository: OnlineMusicRepositoryRouter,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val identity = dataSpec.uri.onlinePlaybackUriIdentityOrNull() ?: return dataSpec
        return dataSpec.withUri(resolvePlaybackUri(identity))
    }

    override fun resolveReportedUri(uri: Uri): Uri {
        return uri
    }

    private fun resolvePlaybackUri(identity: OnlineTrackIdentity): Uri {
        return runBlocking(Dispatchers.IO) {
            onlineMusicRepository.resolvePlaybackUri(identity)
        }
    }
}
