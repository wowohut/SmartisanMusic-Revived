@file:Suppress("DEPRECATION")
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisanos.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.smartisanos.music.data.online.OnlineLyrics
import com.smartisanos.music.data.online.OnlineLyricsExtraKey
import com.smartisanos.music.data.online.OnlineMusicRepositoryRouter
import com.smartisanos.music.data.online.OnlineTranslatedLyricsExtraKey
import com.smartisanos.music.data.online.OnlineTranslatedWordLyricsExtraKey
import com.smartisanos.music.data.online.OnlineWordLyricsExtraKey
import com.smartisanos.music.data.online.hasContent
import com.smartisanos.music.data.online.onlineIdentityOrNull
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal data class EmbeddedLyricsLine(
    val text: String,
    val timestampMs: Long? = null,
    val translation: String? = null,
    val tokens: List<EmbeddedLyricsToken> = emptyList(),
)

internal data class EmbeddedLyricsToken(
    val text: String,
    val timestampMs: Long,
    val endTimestampMs: Long? = null,
)

internal data class EmbeddedLyrics(
    val lines: List<EmbeddedLyricsLine>,
    val isTimeSynced: Boolean,
) {
    val isWordSynced: Boolean
        get() = lines.any { line -> line.tokens.isNotEmpty() }
}

internal fun extractEmbeddedLyrics(tracks: Tracks): EmbeddedLyrics? {
    var bestLyrics: EmbeddedLyrics? = null
    for (group in tracks.groups) {
        for (trackIndex in 0 until group.length) {
            bestLyrics = chooseBetterLyrics(
                current = bestLyrics,
                candidate = extractEmbeddedLyrics(group.getTrackFormat(trackIndex).metadata),
            )
        }
    }
    return bestLyrics
}

internal suspend fun loadEmbeddedLyrics(
    context: Context,
    mediaItem: MediaItem,
): EmbeddedLyrics? {
    mediaItem.onlineLyrics()?.let { onlineLyrics ->
        parseOnlineLyrics(onlineLyrics)?.let { lyrics -> return lyrics }
    }
    val onlineIdentity = mediaItem.onlineIdentityOrNull()
    onlineIdentity?.let { identity ->
        runCatching {
            OnlineMusicRepositoryRouter(context.applicationContext).lyrics(identity)
        }.getOrNull()?.let { onlineLyrics ->
            parseOnlineLyrics(onlineLyrics)?.let { lyrics -> return lyrics }
        }
    }
    if (onlineIdentity != null) {
        return null
    }
    mediaItem.localConfiguration?.uri ?: return null

    return runCatching {
        MetadataRetriever.Builder(context, mediaItem).build().use { retriever ->
            val trackGroups = retriever.retrieveTrackGroups().await(context)
            var bestLyrics: EmbeddedLyrics? = null
            for (groupIndex in 0 until trackGroups.length) {
                val group = trackGroups.get(groupIndex)
                for (trackIndex in 0 until group.length) {
                    bestLyrics = chooseBetterLyrics(
                        current = bestLyrics,
                        candidate = extractEmbeddedLyrics(group.getFormat(trackIndex).metadata),
                    )
                }
            }
            bestLyrics
        }
    }.getOrNull()
}

private fun MediaItem.onlineLyrics(): OnlineLyrics? {
    val extras = mediaMetadata.extras ?: return null
    val lyrics = OnlineLyrics(
        lyric = extras.getString(OnlineLyricsExtraKey)?.takeIf(String::isNotBlank),
        translatedLyric = extras.getString(OnlineTranslatedLyricsExtraKey)?.takeIf(String::isNotBlank),
        wordLyric = extras.getString(OnlineWordLyricsExtraKey)?.takeIf(String::isNotBlank),
        translatedWordLyric = extras.getString(OnlineTranslatedWordLyricsExtraKey)?.takeIf(String::isNotBlank),
    )
    return lyrics.takeIf(OnlineLyrics::hasContent)
}

internal fun extractEmbeddedLyrics(
    entries: Iterable<Metadata.Entry>,
): EmbeddedLyrics? {
    var bestLyrics: EmbeddedLyrics? = null
    for (entry in entries) {
        bestLyrics = chooseBetterLyrics(bestLyrics, extractEmbeddedLyrics(entry))
    }
    return bestLyrics
}

private fun extractEmbeddedLyrics(metadata: Metadata?): EmbeddedLyrics? {
    metadata ?: return null
    var bestLyrics: EmbeddedLyrics? = null
    for (index in 0 until metadata.length()) {
        bestLyrics = chooseBetterLyrics(bestLyrics, extractEmbeddedLyrics(metadata.get(index)))
    }
    return bestLyrics
}

private fun extractEmbeddedLyrics(entry: Metadata.Entry): EmbeddedLyrics? {
    return when (entry) {
        is BinaryFrame -> when (entry.id) {
            "USLT" -> {
                val rawText = decodeUnsynchronizedLyricsFrame(entry.data) ?: return null
                parseEmbeddedLyricsText(
                    rawText = rawText,
                    hintedByKey = true,
                )
            }

            "SYLT" -> decodeSynchronizedLyricsFrame(entry.data)
            else -> null
        }

        is TextInformationFrame -> {
            val hintedByKey = looksLikeLyricsKey(entry.id) || looksLikeLyricsKey(entry.description)
            parseEmbeddedLyricsText(
                rawText = entry.values.joinToString(separator = "\n"),
                hintedByKey = hintedByKey,
            )
        }

        is CommentFrame -> {
            val hintedByKey = looksLikeLyricsKey(entry.description)
            parseEmbeddedLyricsText(
                rawText = entry.text,
                hintedByKey = hintedByKey,
            )
        }

        is InternalFrame -> {
            val hintedByKey = looksLikeLyricsKey(entry.description) || looksLikeLyricsKey(entry.domain)
            parseEmbeddedLyricsText(
                rawText = entry.text,
                hintedByKey = hintedByKey,
            )
        }

        is VorbisComment -> {
            val hintedByKey = looksLikeLyricsKey(entry.key)
            parseEmbeddedLyricsText(
                rawText = entry.value,
                hintedByKey = hintedByKey,
            )
        }

        is MdtaMetadataEntry -> {
            val hintedByKey = looksLikeLyricsKey(entry.key)
            val rawText = if (entry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_STRING) {
                entry.value.toString(StandardCharsets.UTF_8)
            } else {
                null
            }
            parseEmbeddedLyricsText(rawText = rawText, hintedByKey = hintedByKey)
        }

        else -> null
    }
}

private fun decodeUnsynchronizedLyricsFrame(data: ByteArray): String? {
    if (data.size <= 4) {
        return null
    }

    val encoding = data[0].toInt() and 0xFF
    val charset = charsetForId3Encoding(encoding) ?: return null
    val descriptionStart = 4
    val descriptionEnd = findId3StringTerminator(data, descriptionStart, encoding)
    val textStart = (descriptionEnd + delimiterLength(encoding)).coerceAtMost(data.size)

    return decodeId3String(data, textStart, data.size, charset)
}

private fun decodeSynchronizedLyricsFrame(data: ByteArray): EmbeddedLyrics? {
    if (data.size <= 6) {
        return null
    }

    val encoding = data[0].toInt() and 0xFF
    val charset = charsetForId3Encoding(encoding) ?: return null
    val timestampFormat = data[4].toInt() and 0xFF
    if (timestampFormat != 2) {
        return null
    }

    val descriptionStart = 6
    val descriptionEnd = findId3StringTerminator(data, descriptionStart, encoding)
    var cursor = (descriptionEnd + delimiterLength(encoding)).coerceAtMost(data.size)
    val lines = mutableListOf<EmbeddedLyricsLine>()

    while (cursor < data.size) {
        val textEnd = findId3StringTerminator(data, cursor, encoding)
        val nextCursor = textEnd + delimiterLength(encoding)
        if (nextCursor + 4 > data.size) {
            break
        }

        val text = decodeId3String(data, cursor, textEnd, charset)
        val timestampMs = readUnsignedInt(data, nextCursor)
        if (!text.isNullOrBlank()) {
            lines += EmbeddedLyricsLine(
                text = text.trim(),
                timestampMs = timestampMs,
            )
        }
        cursor = nextCursor + 4
    }

    if (lines.isEmpty()) {
        return null
    }

    return EmbeddedLyrics(lines = lines, isTimeSynced = true)
}

private fun decodeId3String(
    data: ByteArray,
    startIndex: Int,
    endIndex: Int,
    charset: Charset,
): String? {
    if (startIndex >= endIndex || startIndex >= data.size) {
        return null
    }

    return String(
        bytes = data,
        offset = startIndex,
        length = endIndex - startIndex,
        charset = charset,
    ).replace("\u0000", "").trim().takeIf { it.isNotEmpty() }
}

private fun findId3StringTerminator(
    data: ByteArray,
    startIndex: Int,
    encoding: Int,
): Int {
    val delimiterLength = delimiterLength(encoding)
    var index = startIndex
    while (index < data.size) {
        if (data[index] == 0.toByte()) {
            if (delimiterLength == 1) {
                return index
            }
            if (index + 1 < data.size && data[index + 1] == 0.toByte()) {
                return index
            }
        }
        index += delimiterLength
    }
    return data.size
}

private fun delimiterLength(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

private fun charsetForId3Encoding(encoding: Int): Charset? =
    when (encoding) {
        0 -> StandardCharsets.ISO_8859_1
        1 -> Charset.forName("UTF-16")
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> null
    }

private fun looksLikeLyricsKey(rawValue: String?): Boolean {
    val normalized = rawValue
        ?.trim()
        ?.uppercase()
        ?.replace(Regex("[^A-Z0-9]"), "")
        .orEmpty()

    if (normalized.isEmpty()) {
        return false
    }

    if (
        normalized.contains("LYRICIST") ||
        normalized.contains("COMPOSER") ||
        normalized.contains("WRITER")
    ) {
        return false
    }

    return normalized == "LRC" ||
        normalized == "USLT" ||
        normalized == "SYLT" ||
        normalized == "LYRIC" ||
        normalized.contains("LYRICS")
}

private fun readUnsignedInt(data: ByteArray, startIndex: Int): Long {
    return ((data[startIndex].toLong() and 0xFF) shl 24) or
        ((data[startIndex + 1].toLong() and 0xFF) shl 16) or
        ((data[startIndex + 2].toLong() and 0xFF) shl 8) or
        (data[startIndex + 3].toLong() and 0xFF)
}
