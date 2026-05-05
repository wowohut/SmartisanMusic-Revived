package com.smartisanos.music.playback

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.icu.text.Transliterator
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisanos.music.R
import com.smartisanos.music.data.library.LibraryIndexDatabase
import com.smartisanos.music.data.library.LibraryIndexEntity
import com.smartisanos.music.data.library.LibraryIndexSnapshotEntity
import com.smartisanos.music.data.playback.PlaybackStatsRecord
import java.io.File
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class LocalAudioLibrary(
    private val context: Context,
    private val playbackStatsProvider: () -> Map<String, PlaybackStatsRecord> = { emptyMap() },
    private val playbackStatsByIdsProvider: (Set<String>) -> Map<String, PlaybackStatsRecord> = { mediaIds ->
        playbackStatsProvider().filterKeys(mediaIds::contains)
    },
    private val libraryIndexDatabase: LibraryIndexDatabase = LibraryIndexDatabase.getInstance(context),
) {

    private val audioCacheLock = Any()
    private val libraryIndexDao = libraryIndexDatabase.libraryIndexDao()
    @Volatile private var audioCache = AudioCache()

    fun getRootItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(context.getString(R.string.library_root))
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()

        return MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    fun getAudioItems(forceRefresh: Boolean = false): List<MediaItem> {
        val currentSnapshot = currentMediaStoreSnapshot()
        synchronized(audioCacheLock) {
            val cache = audioCache
            // generation 负责日常增量变化，version 负责更大范围的媒体库重建。
            if (!forceRefresh && cache.snapshot == currentSnapshot && cache.items.isNotEmpty()) {
                return cache.items
            }
        }

        if (!forceRefresh) {
            val indexedItems = getAudioItemsFromIndexIfFresh(currentSnapshot)
            if (indexedItems.isNotEmpty()) {
                synchronized(audioCacheLock) {
                    audioCache = AudioCache(
                        snapshot = currentSnapshot,
                        items = indexedItems,
                    )
                }
                return indexedItems
            }
        }

        return reconcileAudioIndexAndCache(currentSnapshot)
    }

    fun getAudioItemsByIds(mediaIds: List<String>): List<MediaItem> {
        val requestedIds = mediaIds
            .asSequence()
            .mapNotNull { mediaId -> mediaId.trim().toLongOrNull()?.takeIf { it > 0L } }
            .distinct()
            .toList()
        if (requestedIds.isEmpty()) {
            return emptyList()
        }

        val requestedIdStrings = requestedIds.map(Long::toString)
        val cachedItemsById = getValidCachedItemsById(requestedIdStrings.toSet())
        val missingIds = requestedIds.filter { id -> id.toString() !in cachedItemsById }
        if (missingIds.isEmpty()) {
            return requestedIdStrings.mapNotNull(cachedItemsById::get)
        }
        if (!canReadLibraryIndexOnCurrentThread()) {
            return requestedIdStrings.mapNotNull(cachedItemsById::get)
        }

        val missingIdStrings = missingIds.map(Long::toString)
        val indexFresh = isAudioIndexFresh(currentMediaStoreSnapshot())
        val indexedItemsById = if (indexFresh) {
            libraryIndexDao.getValidIndexesByMediaIds(missingIdStrings)
                .toMediaItems(playbackStatsForIds(missingIdStrings.toSet()))
                .associateBy(MediaItem::mediaId)
        } else {
            emptyMap()
        }
        val stillMissingIds = missingIds.filter { id -> id.toString() !in indexedItemsById }
        val playbackStats = playbackStatsForIds(stillMissingIds.map(Long::toString).toSet())
        val queriedItemsById = queryAudioIndexesByIds(stillMissingIds)
            .toMediaItems(playbackStats)
            .associateBy(MediaItem::mediaId)
        val itemsById = cachedItemsById + indexedItemsById + queriedItemsById

        return requestedIdStrings.mapNotNull(itemsById::get)
    }

    fun getAudioItemsByQueueKeys(queueKeys: List<PlaybackQueueSnapshotItem>): List<MediaItem> {
        if (queueKeys.isEmpty()) {
            return emptyList()
        }
        val byIds = getAudioItemsByIds(queueKeys.map(PlaybackQueueSnapshotItem::mediaId))
            .associateBy(MediaItem::mediaId)
            .toMutableMap()
        val missingStableKeys = queueKeys
            .asSequence()
            .filter { key -> key.mediaId !in byIds }
            .map(PlaybackQueueSnapshotItem::stableKey)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (missingStableKeys.isNotEmpty() && canReadLibraryIndexOnCurrentThread()) {
            val currentSnapshot = currentMediaStoreSnapshot()
            if (!isAudioIndexFresh(currentSnapshot)) {
                reconcileAudioIndexAndCache(currentSnapshot)
            }
            val stableItems = libraryIndexDao.getValidIndexesByStableKeys(missingStableKeys)
                .toMediaItems()
            stableItems.forEach { item ->
                byIds[item.mediaId] = item
            }
            val stableItemsByKey = stableItems.associateBy { item -> item.stableKey.orEmpty() }
            return queueKeys.mapNotNull { key ->
                byIds[key.mediaId] ?: stableItemsByKey[key.stableKey]
            }
        }
        return queueKeys.mapNotNull { key -> byIds[key.mediaId] }
    }

    fun invalidateAudioItems() {
        synchronized(audioCacheLock) {
            audioCache = AudioCache()
        }
    }

    fun refreshAudioItems(): RefreshResult {
        val indexedSnapshot = queryIndexedAudioSnapshot()
        val pendingAudioPaths = discoverUnindexedAudioPaths(indexedSnapshot)
        val scanResult = scanAudioFiles(pendingAudioPaths)
        val mediaStoreRefreshSucceeded = requestMediaStoreRefresh()
        val items = getAudioItems(forceRefresh = true)
        return RefreshResult(
            items = items,
            scannedFileCount = scanResult.scannedFileCount,
            failedScanCount = scanResult.failedScanCount,
            scanTimedOut = scanResult.timedOut,
            mediaStoreRefreshSucceeded = mediaStoreRefreshSucceeded,
        )
    }

    fun getItem(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) {
            return getRootItem()
        }
        return getAudioItemsByIds(listOf(mediaId)).firstOrNull()
    }

    private fun getAudioItemsFromIndexIfFresh(currentSnapshot: MediaStoreSnapshot): List<MediaItem> {
        if (!isAudioIndexFresh(currentSnapshot)) {
            return emptyList()
        }
        return libraryIndexDao.getValidIndexes().toMediaItems()
    }

    private fun isAudioIndexFresh(currentSnapshot: MediaStoreSnapshot): Boolean {
        val snapshotKey = libraryIndexDao.getSnapshotKey() ?: return false
        return snapshotKey == currentSnapshot.storageKey && libraryIndexDao.getValidIndexCount() > 0
    }

    private fun canReadLibraryIndexOnCurrentThread(): Boolean {
        return Looper.myLooper() != Looper.getMainLooper()
    }

    private fun reconcileAudioIndexAndCache(currentSnapshot: MediaStoreSnapshot): List<MediaItem> {
        val items = reconcileAudioIndex(currentSnapshot)
        synchronized(audioCacheLock) {
            audioCache = AudioCache(
                snapshot = currentSnapshot,
                items = items,
            )
        }
        return items
    }

    private fun reconcileAudioIndex(currentSnapshot: MediaStoreSnapshot): List<MediaItem> {
        val indexedAt = System.currentTimeMillis()
        val existingIndexes = libraryIndexDao.getAllIndexes()
        val existingByStableKey = existingIndexes.associateBy(LibraryIndexEntity::stableKey)
        val existingByMediaId = existingIndexes.associateBy(LibraryIndexEntity::mediaId)
        val currentRows = queryAudioCursorRows(
            selection = audioSelection(),
            selectionArgs = null,
            sortOrder = audioSortOrder(),
        )
        val currentStableKeys = currentRows.mapTo(linkedSetOf(), AudioCursorRow::stableKey)
        val nextIndexes = currentRows.map { row ->
            val previous = existingByStableKey[row.stableKey] ?: existingByMediaId[row.mediaId]
            if (previous != null && previous.matches(row)) {
                previous.copy(
                    mediaId = row.mediaId,
                    uri = row.uri,
                    valid = true,
                )
            } else {
                row.toLibraryIndexEntity(indexedAt)
            }
        }
        val invalidStableKeys = existingIndexes
            .asSequence()
            .filter(LibraryIndexEntity::valid)
            .map(LibraryIndexEntity::stableKey)
            .filter { stableKey -> stableKey !in currentStableKeys }
            .toList()

        libraryIndexDatabase.runInTransaction {
            if (existingIndexes.isEmpty()) {
                libraryIndexDao.deleteAllIndexes()
            }
            if (nextIndexes.isNotEmpty()) {
                libraryIndexDao.upsertIndexes(nextIndexes)
            }
            invalidStableKeys.chunked(SqlBindParameterChunkSize).forEach { chunk ->
                libraryIndexDao.markInvalid(chunk, indexedAt)
            }
            libraryIndexDao.upsertSnapshot(
                LibraryIndexSnapshotEntity(
                    snapshotKey = currentSnapshot.storageKey,
                    updatedAt = indexedAt,
                ),
            )
        }

        return libraryIndexDao.getValidIndexes().toMediaItems()
    }

    private fun queryAudioIndexesByIds(mediaIds: List<Long>): List<LibraryIndexEntity> {
        if (mediaIds.isEmpty()) {
            return emptyList()
        }
        val indexedAt = System.currentTimeMillis()
        return mediaIds
            .chunked(MediaStoreIdSelectionChunkSize)
            .flatMap { ids ->
                queryAudioCursorRows(
                    selection = buildString {
                        append(audioSelection())
                        append(" AND ${MediaStore.Audio.Media._ID} IN (")
                        append(ids.joinToString(separator = ",") { "?" })
                        append(")")
                    },
                    selectionArgs = ids.map(Long::toString).toTypedArray(),
                    sortOrder = null,
                )
            }
            .map { row -> row.toLibraryIndexEntity(indexedAt) }
    }

    private fun queryAudioCursorRows(
        selection: String,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): List<AudioCursorRow> {
        val rows = mutableListOf<AudioCursorRow>()
        try {
            context.contentResolver.query(
                audioCollection(),
                audioItemProjection(),
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.toAudioCursorRow()?.let(rows::add)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return rows
    }

    private fun Cursor.toAudioCursorRow(): AudioCursorRow? {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val volumeName = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME))
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: StableKeyVolumeFallback
        val relativePath = normalizeLibraryRelativePath(
            getString(getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)),
        )
        val displayName = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val stableKey = stableAudioLibraryKey(volumeName, relativePath, displayName) ?: return null
        val durationMs = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
        val mimeType = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
            ?.takeIf { it.isNotBlank() }

        return AudioCursorRow(
            mediaId = id.toString(),
            stableKey = stableKey,
            uri = ContentUris.withAppendedId(audioCollection(), id).toString(),
            title = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
            artist = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
            album = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
            albumArtist = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)),
            albumId = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)).takeIf { it > 0L },
            durationMs = durationMs,
            track = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)).takeIf { it > 0 },
            year = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)).takeIf { it > 0 },
            volumeName = volumeName,
            relativePath = relativePath,
            displayName = displayName,
            mimeType = mimeType,
            dateAdded = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)).takeIf { it > 0L },
            dateModified = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)).takeIf { it > 0L },
            generationAdded = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.GENERATION_ADDED)).takeIf { it > 0L },
            generationModified = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.GENERATION_MODIFIED)).takeIf { it > 0L },
        )
    }

    private fun AudioCursorRow.toLibraryIndexEntity(indexedAt: Long): LibraryIndexEntity {
        val normalizedTitle = title
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val normalizedArtist = artist
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
            ?: context.getString(R.string.unknown_artist)
        val normalizedAlbum = album
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() }
        val normalizedAlbumArtist = albumArtist
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
        val titleSortKey = LegacyLibraryTitleNormalizer.normalize(normalizedTitle)
        return LibraryIndexEntity(
            mediaId = mediaId,
            stableKey = stableKey,
            uri = uri,
            title = normalizedTitle,
            artist = normalizedArtist,
            album = normalizedAlbum,
            albumArtist = normalizedAlbumArtist,
            albumId = albumId,
            durationMs = durationMs,
            track = track,
            year = year,
            volumeName = volumeName,
            relativePath = relativePath,
            displayName = displayName,
            mimeType = mimeType,
            dateAdded = dateAdded,
            dateModified = dateModified,
            generationAdded = generationAdded,
            generationModified = generationModified,
            titleSortKey = titleSortKey,
            titleSection = titleSortKey.legacyLibraryTitleSection(),
            qualityBadge = resolveAudioQualityBadge(displayName, mimeType),
            indexedAt = indexedAt,
            valid = true,
        )
    }

    private fun LibraryIndexEntity.matches(row: AudioCursorRow): Boolean {
        return stableKey == row.stableKey &&
            dateModified == row.dateModified &&
            generationModified == row.generationModified &&
            durationMs == row.durationMs &&
            relativePath == row.relativePath &&
            displayName == row.displayName &&
            mimeType == row.mimeType
    }

    private fun List<LibraryIndexEntity>.toMediaItems(
        playbackStats: Map<String, PlaybackStatsRecord>? = null,
    ): List<MediaItem> {
        val resolvedPlaybackStats = playbackStats
            ?: runCatching(playbackStatsProvider).getOrDefault(emptyMap())
        return map { index -> index.toMediaItem(resolvedPlaybackStats[index.mediaId]) }
    }

    private fun LibraryIndexEntity.toMediaItem(stats: PlaybackStatsRecord?): MediaItem {
        val playCount = stats?.playCount?.takeIf { it > 0L }
        val score = stats?.score?.takeIf { it > 0 }
        val extras = Bundle().apply {
            putString(StableKeyExtraKey, stableKey)
            putString(TitleSortKeyExtraKey, titleSortKey)
            putString(TitleSectionExtraKey, titleSection)
            if (relativePath.isNotBlank()) {
                putString(RelativePathExtraKey, relativePath)
            }
            albumId?.let { putLong(AlbumIdExtraKey, it) }
            dateAdded?.let { putLong(DateAddedExtraKey, it) }
            generationAdded?.let { putLong(GenerationAddedExtraKey, it) }
            if (!qualityBadge.isNullOrBlank()) {
                putString(AudioQualityBadgeExtraKey, qualityBadge)
            }
            if (playCount != null) {
                putLong(PlayCountExtraKey, playCount)
            }
            if (score != null) {
                putLong(RatingExtraKey, score.toLong())
            }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setSubtitle(artist)
            .setDurationMs(durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)

        if (!album.isNullOrBlank()) {
            metadataBuilder.setAlbumTitle(album)
        }
        if (!albumArtist.isNullOrBlank()) {
            metadataBuilder.setAlbumArtist(albumArtist)
        }
        track?.let(metadataBuilder::setTrackNumber)
        year?.let(metadataBuilder::setReleaseYear)
        mediaId.toLongOrNull()?.let { id ->
            metadataBuilder.setArtworkUri(trackArtworkUri(id))
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun getValidCachedItemsById(mediaIds: Set<String>): Map<String, MediaItem> {
        if (mediaIds.isEmpty()) {
            return emptyMap()
        }
        return synchronized(audioCacheLock) {
            val cache = audioCache
            if (cache.items.isEmpty() || cache.snapshot != currentMediaStoreSnapshot()) {
                return@synchronized emptyMap()
            }
            cache.items
                .asSequence()
                .filter { item -> item.mediaId in mediaIds }
                .associateBy(MediaItem::mediaId)
        }
    }

    private fun playbackStatsForIds(mediaIds: Set<String>): Map<String, PlaybackStatsRecord> {
        if (mediaIds.isEmpty()) {
            return emptyMap()
        }
        return runCatching {
            playbackStatsByIdsProvider(mediaIds)
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val ROOT_ID = "root"
        const val StableKeyExtraKey = "com.smartisanos.music.extra.STABLE_KEY"
        const val AlbumIdExtraKey = "com.smartisanos.music.extra.ALBUM_ID"
        const val RelativePathExtraKey = "com.smartisanos.music.extra.RELATIVE_PATH"
        const val DateAddedExtraKey = "com.smartisanos.music.extra.DATE_ADDED"
        const val GenerationAddedExtraKey = "com.smartisanos.music.extra.GENERATION_ADDED"
        const val TitleSortKeyExtraKey = "com.smartisanos.music.extra.TITLE_SORT_KEY"
        const val TitleSectionExtraKey = "com.smartisanos.music.extra.TITLE_SECTION"
        const val AudioQualityBadgeExtraKey = "com.smartisanos.music.extra.AUDIO_QUALITY_BADGE"
        const val PlayCountExtraKey = "com.smartisanos.music.extra.PLAY_COUNT"
        const val RatingExtraKey = "com.smartisanos.music.extra.RATING"
        const val AudioQualityBadgeFlac = "flac"
        const val AudioQualityBadgeApe = "ape"
        const val AudioQualityBadgeWav = "wav"
        const val AudioQualityBadgeAiff = "aiff"
        const val AudioQualityBadgeAlac = "alac"
        const val AudioQualityBadgeCue = "cue"
        private const val MediaScannerWaitTimeoutSeconds = 30L
        private const val MediaStoreIdSelectionChunkSize = 500
        private const val SqlBindParameterChunkSize = 900
        private val AudioFileExtensions = setOf(
            "aac",
            "aif",
            "aiff",
            "alac",
            "amr",
            "ape",
            "flac",
            "m4a",
            "m4b",
            "mid",
            "midi",
            "mka",
            "mp3",
            "oga",
            "ogg",
            "opus",
            "wav",
            "wma",
        )

        fun albumArtworkUri(albumId: Long): Uri {
            return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId,
            )
        }

        fun trackArtworkUri(mediaId: Long): Uri {
            return Uri.parse("content://media/external/audio/media/$mediaId/albumart")
        }

        private fun resolveAudioQualityBadge(
            displayName: String?,
            mimeType: String?,
        ): String? {
            val extension = displayName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            val normalizedMimeType = mimeType?.lowercase(Locale.ROOT).orEmpty()

            return when {
                extension == AudioQualityBadgeFlac || normalizedMimeType.contains(AudioQualityBadgeFlac) -> {
                    AudioQualityBadgeFlac
                }
                extension == AudioQualityBadgeApe || normalizedMimeType.contains(AudioQualityBadgeApe) || normalizedMimeType.contains("monkeys-audio") -> {
                    AudioQualityBadgeApe
                }
                extension == AudioQualityBadgeWav || normalizedMimeType.contains(AudioQualityBadgeWav) || normalizedMimeType.contains("wave") -> {
                    AudioQualityBadgeWav
                }
                extension == AudioQualityBadgeAiff || extension == "aif" || normalizedMimeType.contains(AudioQualityBadgeAiff) -> {
                    AudioQualityBadgeAiff
                }
                extension == AudioQualityBadgeAlac || normalizedMimeType.contains(AudioQualityBadgeAlac) -> {
                    AudioQualityBadgeAlac
                }
                extension == AudioQualityBadgeCue || normalizedMimeType.contains(AudioQualityBadgeCue) -> {
                    AudioQualityBadgeCue
                }
                else -> null
            }
        }

    }

    data class RefreshResult(
        val items: List<MediaItem>,
        val scannedFileCount: Int,
        val failedScanCount: Int,
        val scanTimedOut: Boolean,
        val mediaStoreRefreshSucceeded: Boolean,
    ) {
        // ContentResolver.refresh() 是 provider best-effort 提示；MediaStore 不支持时会返回 false。
        // 手动重扫是否成功应以 MediaScanner 回调和重新查询结果为准，避免误报失败。
        val successful: Boolean
            get() = !scanTimedOut && failedScanCount == 0
    }

    private data class AudioCache(
        val snapshot: MediaStoreSnapshot? = null,
        val items: List<MediaItem> = emptyList(),
    )

    private fun currentMediaStoreSnapshot(): MediaStoreSnapshot {
        val volumes = runCatching {
            MediaStore.getExternalVolumeNames(context)
        }.getOrDefault(emptySet())

        return MediaStoreSnapshot(
            volumes
                .sorted()
                .associateWith { volume ->
                    VolumeSnapshot(
                        version = MediaStore.getVersion(context, volume),
                        generation = MediaStore.getGeneration(context, volume),
                    )
                },
        )
    }

    private data class MediaStoreSnapshot(
        val volumes: Map<String, VolumeSnapshot>,
    ) {
        val storageKey: String = volumes.entries.joinToString("\u001e") { (volume, snapshot) ->
            listOf(volume, snapshot.version, snapshot.generation.toString()).joinToString("\u001f")
        }
    }

    private data class VolumeSnapshot(
        val version: String,
        val generation: Long,
    )

    private data class IndexedAudioSnapshot(
        val fileKeys: Set<String>,
    )

    private data class ExternalVolumeRoot(
        val root: File,
        val mediaStoreVolumeName: String,
    )

    private data class ScanResult(
        val scannedFileCount: Int,
        val failedScanCount: Int,
        val timedOut: Boolean,
    )

    private data class AudioCursorRow(
        val mediaId: String,
        val stableKey: String,
        val uri: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val albumId: Long?,
        val durationMs: Long,
        val track: Int?,
        val year: Int?,
        val volumeName: String,
        val relativePath: String,
        val displayName: String,
        val mimeType: String?,
        val dateAdded: Long?,
        val dateModified: Long?,
        val generationAdded: Long?,
        val generationModified: Long?,
    )

    private fun audioCollection(): Uri {
        return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }

    private fun audioItemProjection(): Array<String> {
        return arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.GENERATION_ADDED,
            MediaStore.Audio.Media.GENERATION_MODIFIED,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
        )
    }

    private fun audioSelection(): String {
        return buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
        }
    }

    private fun audioSortOrder(): String {
        return "${MediaStore.Audio.Media.GENERATION_ADDED} DESC"
    }

    private fun queryIndexedAudioSnapshot(): IndexedAudioSnapshot {
        val fileKeys = linkedSetOf<String>()

        runCatching {
            context.contentResolver.query(
                audioCollection(),
                arrayOf(
                    MediaStore.MediaColumns.VOLUME_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                ),
                audioSelection(),
                null,
                null,
            )
        }.getOrNull()?.use { cursor ->
            val volumeNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)
            val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val volumeName = cursor.getString(volumeNameColumn)
                val relativePath = cursor.getString(relativePathColumn)
                val displayName = cursor.getString(displayNameColumn)
                stableAudioLibraryKey(volumeName, relativePath, displayName)?.let(fileKeys::add)
            }
        }

        return IndexedAudioSnapshot(
            fileKeys = fileKeys,
        )
    }

    private fun discoverUnindexedAudioPaths(indexedSnapshot: IndexedAudioSnapshot): List<String> {
        val pendingPaths = linkedSetOf<String>()
        externalVolumeRoots().forEach { volumeRoot ->
            volumeRoot.root.walkTopDown()
                .onEnter { directory -> shouldEnterDirectory(volumeRoot.root, directory) }
                .onFail { _, _ -> }
                .forEach { candidate ->
                    if (
                        candidate.name.startsWith('.') ||
                        !candidate.hasAudioCandidateExtension() ||
                        !candidate.isFile ||
                        !candidate.canRead()
                    ) {
                        return@forEach
                    }
                    val relativePathFromRoot = candidate.relativeToOrNull(volumeRoot.root)
                        ?.invariantSeparatorsPath
                        ?: return@forEach
                    if (shouldSkipMediaScannerPath(relativePathFromRoot)) {
                        return@forEach
                    }
                    val relativePath = candidate.relativePathFromVolumeRoot(volumeRoot.root)
                    val key = stableAudioLibraryKey(
                        volumeName = volumeRoot.mediaStoreVolumeName,
                        relativePath = relativePath,
                        displayName = candidate.name,
                    ) ?: return@forEach
                    if (key !in indexedSnapshot.fileKeys) {
                        pendingPaths += candidate.absolutePath
                    }
                }
        }
        return pendingPaths.toList()
    }

    private fun externalVolumeRoots(): Set<ExternalVolumeRoot> {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .mapNotNull { appDir ->
                val root = volumeRootFromAppDir(appDir)?.takeIf { it.isDirectory }
                    ?: return@mapNotNull null
                val mediaStoreVolumeName = storageManager
                    ?.getStorageVolume(root)
                    ?.mediaStoreVolumeName
                    ?: root.absolutePath
                ExternalVolumeRoot(
                    root = root,
                    mediaStoreVolumeName = mediaStoreVolumeName,
                )
            }
            .toSet()
    }

    private fun volumeRootFromAppDir(appDir: File): File? {
        val marker = "${File.separator}Android${File.separator}data${File.separator}"
        val path = appDir.absolutePath
        val markerIndex = path.indexOf(marker)
        if (markerIndex <= 0) {
            return null
        }
        return File(path.substring(0, markerIndex))
    }

    private fun shouldEnterDirectory(scanRoot: File, directory: File): Boolean {
        if (directory == scanRoot) {
            return true
        }
        if (!directory.canRead()) {
            return false
        }
        if (directory.name.startsWith('.')) {
            return false
        }
        val relativePath = directory.relativeToOrNull(scanRoot)
            ?.invariantSeparatorsPath
            .orEmpty()
        if (shouldSkipMediaScannerPath(relativePath)) {
            return false
        }
        return when {
            relativePath == "Android/data" || relativePath.startsWith("Android/data/") -> false
            relativePath == "Android/obb" || relativePath.startsWith("Android/obb/") -> false
            else -> true
        }
    }

    private fun File.relativePathFromVolumeRoot(volumeRoot: File): String? {
        val parent = parentFile ?: return null
        if (parent == volumeRoot) {
            return null
        }
        return parent.relativeToOrNull(volumeRoot)
            ?.invariantSeparatorsPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it/" }
    }

    private fun scanAudioFiles(paths: List<String>): ScanResult {
        if (paths.isEmpty()) {
            return ScanResult(
                scannedFileCount = 0,
                failedScanCount = 0,
                timedOut = false,
            )
        }
        val scannedFileCount = AtomicInteger(0)
        val failedScanCount = AtomicInteger(0)
        var timedOut = false

        paths.chunked(128).forEach { batch ->
            val latch = CountDownLatch(batch.size)
            val acceptingCallbacks = AtomicBoolean(true)
            val completedCallbackCount = AtomicInteger(0)
            val mimeTypes = batch.map(::resolveAudioMimeType).toTypedArray()
            MediaScannerConnection.scanFile(
                context,
                batch.toTypedArray(),
                mimeTypes,
            ) { _, uri ->
                if (acceptingCallbacks.get()) {
                    completedCallbackCount.incrementAndGet()
                    if (uri == null) {
                        failedScanCount.incrementAndGet()
                    } else {
                        scannedFileCount.incrementAndGet()
                    }
                }
                latch.countDown()
            }
            if (!latch.await(MediaScannerWaitTimeoutSeconds, TimeUnit.SECONDS)) {
                acceptingCallbacks.set(false)
                timedOut = true
                failedScanCount.addAndGet(batch.size - completedCallbackCount.get())
            }
        }

        return ScanResult(
            scannedFileCount = scannedFileCount.get(),
            failedScanCount = failedScanCount.get(),
            timedOut = timedOut,
        )
    }

    private fun requestMediaStoreRefresh(): Boolean {
        return runCatching {
            context.contentResolver.refresh(
                audioCollection(),
                null,
                null,
            )
        }.getOrDefault(false)
    }

    private fun File.hasAudioCandidateExtension(): Boolean {
        val extension = extension.lowercase(Locale.ROOT)
        return extension in AudioFileExtensions
    }

    private fun resolveAudioMimeType(path: String): String? {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        if (extension.isEmpty()) {
            return null
        }
        return when (extension) {
            AudioQualityBadgeApe -> "audio/ape"
            AudioQualityBadgeAlac -> "audio/alac"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}

private object LegacyLibraryTitleNormalizer {
    private val hanToLatin = runCatching {
        Transliterator.getInstance("Han-Latin; Latin-ASCII")
    }.getOrNull()
    private val combiningMarks = "\\p{Mn}+".toRegex()

    @Synchronized
    fun normalize(title: String): String {
        val trimmed = title.trim()
        val transliterated = hanToLatin?.transliterate(trimmed) ?: trimmed
        return Normalizer.normalize(transliterated, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}

internal fun String.fixLegacyMetadataEncoding(): String {
    if (isEmpty() || !looksLikeLegacyMojibake()) {
        return this
    }
    return LegacyMetadataRepairCharsets.firstNotNullOfOrNull { charset ->
        repairLegacyMojibake(charset)
    } ?: this
}

private fun String.repairLegacyMojibake(charset: Charset): String? {
    return toByteArrayOrNull(charset)
        ?.let { bytes -> String(bytes, StandardCharsets.UTF_8) }
        ?.takeIf { repaired ->
            repaired.isNotEmpty() &&
                repaired != this &&
                ReplacementCharacter !in repaired &&
                (repaired.containsCjkOrFullWidth() || !repaired.looksLikeLegacyMojibake())
        }
}

private fun String.toByteArrayOrNull(charset: Charset): ByteArray? {
    return runCatching {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer = encoder.encode(CharBuffer.wrap(this))
        ByteArray(buffer.remaining()).also(buffer::get)
    }.getOrNull()
}

private fun String.looksLikeLegacyMojibake(): Boolean {
    return LegacyMojibakeMarkers.any(::contains)
}

private fun String.containsCjkOrFullWidth(): Boolean {
    return any { char ->
        val code = char.code
        code in 0x3040..0x30FF ||
            code in 0x3400..0x9FFF ||
            code in 0x1100..0x11FF ||
            code in 0xAC00..0xD7AF ||
            code in 0xFF01..0xFFEF
    }
}

private val LegacyMetadataRepairCharsets = listOf(
    StandardCharsets.ISO_8859_1,
    Charset.forName("windows-1252"),
    Charset.forName("windows-1250"),
)

private val LegacyMojibakeMarkers = listOf("Ã", "Â", "ã", "ď", "ï", "æ", "å", "¤", "½", "ž")
private const val ReplacementCharacter = '\uFFFD'

private fun String.legacyLibraryTitleSection(): String {
    val firstLetter = firstOrNull { char ->
        char.isLetterOrDigit()
    } ?: return "#"
    val upper = firstLetter.uppercaseChar()
    return if (upper in 'A'..'Z') {
        upper.toString()
    } else {
        "#"
    }
}

internal fun stableAudioLibraryKey(
    volumeName: String?,
    relativePath: String?,
    displayName: String?,
): String? {
    val normalizedDisplayName = displayName?.trim().orEmpty()
    if (normalizedDisplayName.isEmpty()) {
        return null
    }
    val normalizedVolumeName = volumeName
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: StableKeyVolumeFallback
    return "$normalizedVolumeName:${normalizeLibraryRelativePath(relativePath)}$normalizedDisplayName"
        .lowercase(Locale.ROOT)
}

private fun normalizeLibraryRelativePath(relativePath: String?): String {
    return relativePath
        ?.replace('\\', '/')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { if (it.endsWith('/')) it else "$it/" }
        .orEmpty()
}

internal fun shouldSkipMediaScannerPath(relativePath: String): Boolean {
    return relativePath
        .replace('\\', '/')
        .split('/')
        .filter(String::isNotEmpty)
        .any { segment -> segment.startsWith('.') }
}

private const val StableKeyVolumeFallback = "unknown-volume"
