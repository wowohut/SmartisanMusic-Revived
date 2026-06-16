package com.smartisanos.music.data.online

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

private const val PageCacheDirectoryName = "online_page_cache"
private const val PageCacheSchemaVersion = 1
private const val DefaultMaxPageCacheFiles = 512
private const val DefaultMaxPageCacheEntryBytes = 8L * 1024L * 1024L
private const val DefaultMaxPageCacheTotalBytes = 64L * 1024L * 1024L

internal data class OnlinePageCacheCodec<T>(
    val encode: (T) -> JSONObject,
    val decode: (JSONObject) -> T?,
)

internal data class OnlinePageCacheEntry<T>(
    val value: T,
    val cachedAtMs: Long,
)

internal class OnlinePageDiskCache(
    private val directory: File,
    private val maxFiles: Int = DefaultMaxPageCacheFiles,
    private val maxEntryBytes: Long = DefaultMaxPageCacheEntryBytes,
    private val maxTotalBytes: Long = DefaultMaxPageCacheTotalBytes,
) {
    private val lock = Any()

    constructor(context: Context) : this(
        directory = File(context.applicationContext.cacheDir, PageCacheDirectoryName),
    )

    suspend fun <T> get(
        key: String,
        codec: OnlinePageCacheCodec<T>,
    ): OnlinePageCacheEntry<T>? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = key.cacheFile()
            if (!file.isFile) {
                return@synchronized null
            }
            val root = runCatching { JSONObject(file.readText()) }.getOrNull()
                ?: return@synchronized file.deleteAndReturnNull()
            if (
                root.optInt(SchemaVersionKey, 0) != PageCacheSchemaVersion ||
                root.optString(CacheKeyKey) != key
            ) {
                return@synchronized file.deleteAndReturnNull()
            }
            val cachedAtMs = root.optLong(CachedAtMsKey, 0L)
            val valueRoot = root.optJSONObject(ValueKey)
                ?: return@synchronized file.deleteAndReturnNull()
            val value = runCatching { codec.decode(valueRoot) }.getOrNull()
                ?: return@synchronized file.deleteAndReturnNull()
            file.setLastModified(System.currentTimeMillis())
            OnlinePageCacheEntry(
                value = value,
                cachedAtMs = cachedAtMs,
            )
        }
    }

    suspend fun <T> put(
        key: String,
        value: T,
        codec: OnlinePageCacheCodec<T>,
        cachedAtMs: Long = System.currentTimeMillis(),
    ) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (!directory.exists() && !directory.mkdirs()) {
                    return@synchronized
                }
                val root = JSONObject()
                    .put(SchemaVersionKey, PageCacheSchemaVersion)
                    .put(CacheKeyKey, key)
                    .put(CachedAtMsKey, cachedAtMs)
                    .put(ValueKey, codec.encode(value))
                val payload = root.toString()
                if (payload.toByteArray(Charsets.UTF_8).size > maxEntryBytes) {
                    key.cacheFile().delete()
                    return@synchronized
                }
                val file = key.cacheFile()
                val tempFile = File(directory, "${file.name}.tmp")
                runCatching {
                    tempFile.writeText(payload)
                    if (!tempFile.renameTo(file)) {
                        file.delete()
                        tempFile.renameTo(file)
                    }
                    file.setLastModified(cachedAtMs)
                    trimLocked()
                }
                tempFile.delete()
            }
        }
    }

    suspend fun removePrefix(prefix: String) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                directory
                    .listFiles { file -> file.isFile && file.extension == CacheFileExtension }
                    .orEmpty()
                    .forEach { file ->
                        val cachedKey = runCatching {
                            JSONObject(file.readText()).optString(CacheKeyKey)
                        }.getOrNull()
                        if (cachedKey == null || cachedKey.startsWith(prefix)) {
                            file.delete()
                        }
                    }
            }
        }
    }

    private fun String.cacheFile(): File {
        return File(directory, "${stablePageCacheKey()}.$CacheFileExtension")
    }

    private fun String.stablePageCacheKey(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun trimLocked() {
        val files = directory
            .listFiles { file -> file.isFile && file.extension == CacheFileExtension }
            .orEmpty()
        val byLastUsed = files.sortedBy(File::lastModified)
        val overflow = files.size - maxFiles
        if (overflow > 0) {
            byLastUsed.take(overflow).forEach { file -> file.delete() }
        }
        var totalBytes = directory
            .listFiles { file -> file.isFile && file.extension == CacheFileExtension }
            .orEmpty()
            .sumOf(File::length)
        if (totalBytes <= maxTotalBytes) {
            return
        }
        directory
            .listFiles { file -> file.isFile && file.extension == CacheFileExtension }
            .orEmpty()
            .sortedBy(File::lastModified)
            .forEach { file ->
                if (totalBytes > maxTotalBytes) {
                    totalBytes -= file.length()
                    file.delete()
                }
            }
    }

    private fun <T> File.deleteAndReturnNull(): T? {
        delete()
        return null
    }

    private companion object {
        const val CacheFileExtension = "json"
        const val SchemaVersionKey = "schemaVersion"
        const val CacheKeyKey = "key"
        const val CachedAtMsKey = "cachedAtMs"
        const val ValueKey = "value"
    }
}
