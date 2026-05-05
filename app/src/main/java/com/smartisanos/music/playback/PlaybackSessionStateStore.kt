package com.smartisanos.music.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val PlaybackSessionStateStoreName = "playback_session_state"
private const val MediaIdSeparator = "\n"
private const val QueueItemSeparator = "\n"
private const val QueueItemFieldSeparator = "\t"

private val Context.playbackSessionStateDataStore by preferencesDataStore(
    name = PlaybackSessionStateStoreName,
)

internal data class PlaybackSessionSnapshot(
    val mediaIds: List<String> = emptyList(),
    val queueItems: List<PlaybackQueueSnapshotItem> = mediaIds.map { mediaId ->
        PlaybackQueueSnapshotItem(mediaId = mediaId)
    },
    val currentMediaId: String? = null,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
)

internal data class PlaybackQueueSnapshotItem(
    val mediaId: String,
    val stableKey: String = "",
)

internal class PlaybackSessionStateStore(
    private val context: Context,
) {

    val snapshot: Flow<PlaybackSessionSnapshot> = context.playbackSessionStateDataStore.data
        .map { preferences ->
            val mediaIds = preferences[MediaIdsKey].orEmpty().decodeMediaIds()
            PlaybackSessionSnapshot(
                mediaIds = mediaIds,
                queueItems = preferences[QueueItemsKey]
                    ?.decodeQueueItems()
                    ?.takeIf(List<PlaybackQueueSnapshotItem>::isNotEmpty)
                    ?: mediaIds.map { mediaId -> PlaybackQueueSnapshotItem(mediaId = mediaId) },
                currentMediaId = preferences[CurrentMediaIdKey]?.takeIf(String::isNotBlank),
                currentIndex = preferences[CurrentIndexKey] ?: 0,
                positionMs = preferences[PositionMsKey] ?: 0L,
                repeatMode = preferences[RepeatModeKey] ?: Player.REPEAT_MODE_OFF,
                shuffleModeEnabled = preferences[ShuffleModeEnabledKey] ?: false,
            )
        }

    suspend fun load(): PlaybackSessionSnapshot = snapshot.first()

    suspend fun save(snapshot: PlaybackSessionSnapshot) {
        context.playbackSessionStateDataStore.edit { preferences ->
            preferences[MediaIdsKey] = snapshot.mediaIds.encodeMediaIds()
            preferences[QueueItemsKey] = snapshot.queueItems.encodeQueueItems()
            snapshot.currentMediaId?.let { currentMediaId ->
                preferences[CurrentMediaIdKey] = currentMediaId
            } ?: preferences.remove(CurrentMediaIdKey)
            preferences[CurrentIndexKey] = snapshot.currentIndex
            preferences[PositionMsKey] = snapshot.positionMs.coerceAtLeast(0L)
            preferences[RepeatModeKey] = snapshot.repeatMode
            preferences[ShuffleModeEnabledKey] = snapshot.shuffleModeEnabled
        }
    }
}

private fun List<String>.encodeMediaIds(): String {
    return asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(MediaIdSeparator)
}

private fun String.decodeMediaIds(): List<String> {
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
}

private fun List<PlaybackQueueSnapshotItem>.encodeQueueItems(): String {
    return asSequence()
        .filter { item -> item.mediaId.isNotBlank() }
        .joinToString(QueueItemSeparator) { item ->
            listOf(item.mediaId.trim(), item.stableKey.trim()).joinToString(QueueItemFieldSeparator)
        }
}

private fun String.decodeQueueItems(): List<PlaybackQueueSnapshotItem> {
    return lineSequence()
        .mapNotNull { line ->
            val parts = line.split(QueueItemFieldSeparator, limit = 2)
            val mediaId = parts.getOrNull(0)?.trim().orEmpty()
            if (mediaId.isBlank()) {
                null
            } else {
                PlaybackQueueSnapshotItem(
                    mediaId = mediaId,
                    stableKey = parts.getOrNull(1)?.trim().orEmpty(),
                )
            }
        }
        .toList()
}

private val MediaIdsKey = stringPreferencesKey("media_ids")
private val QueueItemsKey = stringPreferencesKey("queue_items")
private val CurrentMediaIdKey = stringPreferencesKey("current_media_id")
private val CurrentIndexKey = intPreferencesKey("current_index")
private val PositionMsKey = longPreferencesKey("position_ms")
private val RepeatModeKey = intPreferencesKey("repeat_mode")
private val ShuffleModeEnabledKey = booleanPreferencesKey("shuffle_mode_enabled")
