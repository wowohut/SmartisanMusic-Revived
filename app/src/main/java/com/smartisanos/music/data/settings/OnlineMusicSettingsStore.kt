package com.smartisanos.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val OnlineMusicSettingsStoreName = "online_music_settings"

private val Context.onlineMusicSettingsDataStore by preferencesDataStore(
    name = OnlineMusicSettingsStoreName,
)

data class OnlineMusicSettings(
    val neteasePlaybackQuality: NeteaseAudioQuality = NeteaseAudioQuality.ExHigh,
    val neteaseDownloadQuality: NeteaseAudioQuality = NeteaseAudioQuality.ExHigh,
)

enum class NeteaseAudioQuality(
    val preferenceValue: String,
    val level: String,
    val encodeType: String,
) {
    Standard("standard", "standard", "mp3"),
    Higher("higher", "higher", "mp3"),
    ExHigh("exhigh", "exhigh", "mp3"),
    Lossless("lossless", "lossless", "flac"),
    HiRes("hires", "hires", "flac"),
    HdSurround("jyeffect", "jyeffect", "flac"),
    Surround("sky", "sky", "flac"),
    Master("jymaster", "jymaster", "flac");

    companion object {
        fun fromPreference(value: String?): NeteaseAudioQuality {
            return entries.firstOrNull { quality -> quality.preferenceValue == value } ?: ExHigh
        }
    }
}

val NeteaseAudioQualityFallbackOrder = listOf(
    NeteaseAudioQuality.Master,
    NeteaseAudioQuality.Surround,
    NeteaseAudioQuality.HdSurround,
    NeteaseAudioQuality.HiRes,
    NeteaseAudioQuality.Lossless,
    NeteaseAudioQuality.ExHigh,
    NeteaseAudioQuality.Higher,
    NeteaseAudioQuality.Standard,
)

fun NeteaseAudioQuality.fallbackCandidates(): List<NeteaseAudioQuality> {
    val index = NeteaseAudioQualityFallbackOrder.indexOf(this)
    return if (index >= 0) {
        NeteaseAudioQualityFallbackOrder.drop(index)
    } else {
        listOf(this, NeteaseAudioQuality.ExHigh, NeteaseAudioQuality.Standard).distinct()
    }
}

class OnlineMusicSettingsStore(
    private val context: Context,
) {

    val settings: Flow<OnlineMusicSettings> = context.onlineMusicSettingsDataStore.data
        .map(Preferences::toOnlineMusicSettings)
        .distinctUntilChanged()

    suspend fun readSettings(): OnlineMusicSettings {
        return context.onlineMusicSettingsDataStore.data.first().toOnlineMusicSettings()
    }

    suspend fun setNeteasePlaybackQuality(quality: NeteaseAudioQuality) {
        context.onlineMusicSettingsDataStore.edit { preferences ->
            preferences[NeteasePlaybackQualityKey] = quality.preferenceValue
        }
    }

    suspend fun setNeteaseDownloadQuality(quality: NeteaseAudioQuality) {
        context.onlineMusicSettingsDataStore.edit { preferences ->
            preferences[NeteaseDownloadQualityKey] = quality.preferenceValue
        }
    }
}

private fun Preferences.toOnlineMusicSettings(): OnlineMusicSettings {
    return OnlineMusicSettings(
        neteasePlaybackQuality = NeteaseAudioQuality.fromPreference(this[NeteasePlaybackQualityKey]),
        neteaseDownloadQuality = NeteaseAudioQuality.fromPreference(this[NeteaseDownloadQualityKey]),
    )
}

private val NeteasePlaybackQualityKey = stringPreferencesKey("netease_playback_quality")
private val NeteaseDownloadQualityKey = stringPreferencesKey("netease_download_quality")
