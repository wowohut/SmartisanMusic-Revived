package com.smartisanos.music.data.online

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

private const val LyricsCacheDirectoryName = "lyrics_cache"
private const val LyricsCacheTtlMs = 7L * 24L * 60L * 60L * 1000L
private const val MaxLyricsCacheFiles = 1_000

internal class OnlineLyricsDiskCache(
    private val directory: File,
    private val ttlMs: Long = LyricsCacheTtlMs,
) {
    private val lock = Any()

    constructor(
        context: Context,
        ttlMs: Long = LyricsCacheTtlMs,
    ) : this(
        directory = File(context.applicationContext.cacheDir, LyricsCacheDirectoryName),
        ttlMs = ttlMs,
    )

    suspend fun get(identity: OnlineTrackIdentity): OnlineLyrics? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = identity.cacheFile()
            if (!file.isFile) {
                return@synchronized null
            }
            val root = runCatching { JSONObject(file.readText()) }.getOrNull()
                ?: return@synchronized null
            val cachedAtMs = root.optLong(CachedAtMsKey, 0L)
            if (cachedAtMs <= 0L || System.currentTimeMillis() - cachedAtMs > ttlMs) {
                file.delete()
                return@synchronized null
            }
            file.setLastModified(System.currentTimeMillis())
            OnlineLyrics(
                lyric = root.optNullableString(LyricKey),
                translatedLyric = root.optNullableString(TranslatedLyricKey),
            )
        }
    }

    suspend fun put(
        identity: OnlineTrackIdentity,
        lyrics: OnlineLyrics,
    ) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (!directory.exists() && !directory.mkdirs()) {
                    return@synchronized
                }
                val file = identity.cacheFile()
                val tempFile = File(directory, "${file.name}.tmp")
                val root = JSONObject()
                    .put(CachedAtMsKey, System.currentTimeMillis())
                    .putNullable(LyricKey, lyrics.lyric)
                    .putNullable(TranslatedLyricKey, lyrics.translatedLyric)
                runCatching {
                    tempFile.writeText(root.toString())
                    if (!tempFile.renameTo(file)) {
                        file.delete()
                        tempFile.renameTo(file)
                    }
                    trimLocked()
                }
                tempFile.delete()
            }
        }
    }

    private fun OnlineTrackIdentity.cacheFile(): File {
        return File(directory, "${stableLyricsCacheKey()}.json")
    }

    private fun OnlineTrackIdentity.stableLyricsCacheKey(): String {
        val rawKey = "$source:$trackId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun trimLocked() {
        val files = directory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
        val overflow = files.size - MaxLyricsCacheFiles
        if (overflow <= 0) {
            return
        }
        files
            .sortedBy(File::lastModified)
            .take(overflow)
            .forEach { file -> file.delete() }
    }

    private companion object {
        const val CachedAtMsKey = "cachedAtMs"
        const val LyricKey = "lyric"
        const val TranslatedLyricKey = "translatedLyric"
    }
}

private fun JSONObject.putNullable(
    name: String,
    value: String?,
): JSONObject {
    return if (value.isNullOrBlank()) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name).takeIf(String::isNotBlank)
}
