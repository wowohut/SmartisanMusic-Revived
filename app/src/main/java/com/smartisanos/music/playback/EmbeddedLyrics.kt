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
import com.smartisanos.music.data.online.onlineIdentityOrNull
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.max

internal data class EmbeddedLyricsLine(
    val text: String,
    val timestampMs: Long? = null,
)

internal data class EmbeddedLyrics(
    val lines: List<EmbeddedLyricsLine>,
    val isTimeSynced: Boolean,
)

private val LrcTimestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val LrcOffsetRegex = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)
private val LrcMetadataRegex = Regex(
    """^\[(ar|al|ti|by|offset|re|ve|la|length):.*]$""",
    RegexOption.IGNORE_CASE,
)

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
    mediaItem.onlineLyricsText()?.let { lyricsText ->
        parseEmbeddedLyricsText(
            rawText = lyricsText,
            hintedByKey = true,
        )?.let { lyrics -> return lyrics }
    }
    val onlineIdentity = mediaItem.onlineIdentityOrNull()
    onlineIdentity?.let { identity ->
        runCatching {
            OnlineMusicRepositoryRouter(context.applicationContext).lyrics(identity)
        }.getOrNull()?.preferredLyricsText()?.let { lyricsText ->
            parseEmbeddedLyricsText(
                rawText = lyricsText,
                hintedByKey = true,
            )?.let { lyrics -> return lyrics }
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

private fun MediaItem.onlineLyricsText(): String? {
    val extras = mediaMetadata.extras ?: return null
    return extras.getString(OnlineLyricsExtraKey)?.takeIf(String::isNotBlank)
        ?: extras.getString(OnlineTranslatedLyricsExtraKey)?.takeIf(String::isNotBlank)
}

private fun OnlineLyrics.preferredLyricsText(): String? {
    return lyric?.takeIf(String::isNotBlank)
        ?: translatedLyric?.takeIf(String::isNotBlank)
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

internal fun parseEmbeddedLyricsText(
    rawText: String?,
    hintedByKey: Boolean = false,
): EmbeddedLyrics? {
    val normalizedText = normalizeLyricsText(rawText)
    if (normalizedText.isEmpty()) {
        return null
    }

    var offsetMs = 0L
    val timedLines = mutableListOf<EmbeddedLyricsLine>()
    val plainLines = mutableListOf<String>()

    normalizedText.lineSequence().forEach { rawLine ->
        val trimmedLine = rawLine.trim()
        if (trimmedLine.isEmpty()) {
            plainLines += ""
            return@forEach
        }

        val offsetMatch = LrcOffsetRegex.matchEntire(trimmedLine)
        if (offsetMatch != null) {
            offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: offsetMs
            return@forEach
        }

        if (LrcMetadataRegex.matches(trimmedLine)) {
            return@forEach
        }

        val timestamps = LrcTimestampRegex.findAll(trimmedLine).toList()
        if (timestamps.isNotEmpty()) {
            val lyricText = LrcTimestampRegex.replace(trimmedLine, "").trim()
            timestamps.forEach { match ->
                timedLines += EmbeddedLyricsLine(
                    text = lyricText,
                    timestampMs = max(0L, parseLrcTimestamp(match) + offsetMs),
                )
            }
            return@forEach
        }

        plainLines += trimmedLine
    }

    if (timedLines.isNotEmpty()) {
        val normalizedTimedLines = normalizeEmbeddedLyricsLines(
            timedLines.sortedBy { it.timestampMs ?: Long.MAX_VALUE },
        )
        if (normalizedTimedLines.isEmpty()) {
            return null
        }
        return EmbeddedLyrics(
            lines = normalizedTimedLines,
            isTimeSynced = true,
        )
    }

    val normalizedPlainLines = normalizeEmbeddedLyricsLines(
        plainLines.map { EmbeddedLyricsLine(text = it) },
    )
    if (normalizedPlainLines.isEmpty()) {
        return null
    }

    if (!hintedByKey && !looksLikeLyricsText(normalizedPlainLines.map { it.text })) {
        return null
    }

    return EmbeddedLyrics(
        lines = normalizedPlainLines,
        isTimeSynced = false,
    )
}

private fun normalizeEmbeddedLyricsLines(
    lines: List<EmbeddedLyricsLine>,
): List<EmbeddedLyricsLine> {
    if (lines.isEmpty()) {
        return emptyList()
    }

    val normalizedLines = mutableListOf<EmbeddedLyricsLine>()
    lines.forEach { line ->
        val normalizedText = line.text.trim()
        if (normalizedText.isEmpty()) {
            if (normalizedLines.isNotEmpty() && normalizedLines.last().text.isNotBlank()) {
                normalizedLines += line.copy(text = "")
            }
        } else {
            normalizedLines += line.copy(text = normalizedText)
        }
    }

    while (normalizedLines.firstOrNull()?.text?.isBlank() == true) {
        normalizedLines.removeAt(0)
    }
    while (normalizedLines.lastOrNull()?.text?.isBlank() == true) {
        normalizedLines.removeAt(normalizedLines.lastIndex)
    }

    return normalizedLines
}

private fun chooseBetterLyrics(
    current: EmbeddedLyrics?,
    candidate: EmbeddedLyrics?,
): EmbeddedLyrics? {
    candidate ?: return current
    current ?: return candidate

    if (candidate.isTimeSynced != current.isTimeSynced) {
        return if (candidate.isTimeSynced) candidate else current
    }

    return if (candidate.lines.size > current.lines.size) candidate else current
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

private fun normalizeLyricsText(rawText: String?): String {
    return rawText
        ?.replace("\uFEFF", "")
        ?.replace("\u0000", "")
        ?.replace(Regex("(?i)<br\\s*/?>"), "\n")
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.trim()
        .orEmpty()
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

private fun looksLikeLyricsText(lines: List<String>): Boolean {
    val nonBlankLines = lines.filter { it.isNotBlank() }
    if (nonBlankLines.isEmpty()) {
        return false
    }
    if (nonBlankLines.size >= 3) {
        return true
    }
    return nonBlankLines.size >= 2 && nonBlankLines.all { it.length <= 48 }
}

private fun parseLrcTimestamp(match: MatchResult): Long {
    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
    val fraction = match.groupValues.getOrNull(3).orEmpty()
    val fractionMs = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLong() * 100L
        2 -> fraction.toLong() * 10L
        else -> fraction.take(3).toLong()
    }
    return (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
}

private fun readUnsignedInt(data: ByteArray, startIndex: Int): Long {
    return ((data[startIndex].toLong() and 0xFF) shl 24) or
        ((data[startIndex + 1].toLong() and 0xFF) shl 16) or
        ((data[startIndex + 2].toLong() and 0xFF) shl 8) or
        (data[startIndex + 3].toLong() and 0xFF)
}
