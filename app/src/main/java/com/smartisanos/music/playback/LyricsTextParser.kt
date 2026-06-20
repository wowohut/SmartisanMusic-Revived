package com.smartisanos.music.playback

import com.smartisanos.music.data.online.OnlineLyrics
import kotlin.math.abs
import kotlin.math.max

private val LrcTimestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val EnhancedLrcTimestampRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
private val LrcOffsetRegex = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)
private val LrcMetadataRegex = Regex(
    """^\[(ar|al|ti|by|offset|re|ve|la|length):.*]$""",
    RegexOption.IGNORE_CASE,
)
private val YrcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
private val YrcTokenRegex = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^()]*)""")

internal fun parseOnlineLyrics(lyrics: OnlineLyrics): EmbeddedLyrics? {
    val primary = chooseBestLyricsCandidate(
        rawTexts = listOf(lyrics.wordLyric, lyrics.lyric),
        hintedByKey = true,
    )
    val translation = chooseBestLyricsCandidate(
        rawTexts = listOf(lyrics.translatedWordLyric, lyrics.translatedLyric),
        hintedByKey = true,
    )
    return mergeTranslatedLyrics(primary, translation) ?: primary ?: translation
}

internal fun parseEmbeddedLyricsText(
    rawText: String?,
    hintedByKey: Boolean = false,
): EmbeddedLyrics? {
    return parseLyricsDocument(rawText = rawText, hintedByKey = hintedByKey)
}

private fun chooseBestLyricsCandidate(
    rawTexts: List<String?>,
    hintedByKey: Boolean,
): EmbeddedLyrics? {
    var bestLyrics: EmbeddedLyrics? = null
    rawTexts.forEach { rawText ->
        bestLyrics = chooseBetterLyrics(
            current = bestLyrics,
            candidate = parseLyricsDocument(rawText = rawText, hintedByKey = hintedByKey),
        )
    }
    return bestLyrics
}

private fun parseLyricsDocument(
    rawText: String?,
    hintedByKey: Boolean,
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

        parseYrcLine(trimmedLine, offsetMs)?.let { line ->
            timedLines += line
            return@forEach
        }

        val timestamps = LrcTimestampRegex.findAll(trimmedLine).toList()
        val lineTextWithoutLrcTimestamps = LrcTimestampRegex.replace(trimmedLine, "")
        val enhancedLine = parseEnhancedLrcLine(
            rawLine = lineTextWithoutLrcTimestamps,
            offsetMs = offsetMs,
        )
        if (timestamps.isNotEmpty() || enhancedLine != null) {
            val lyricText = enhancedLine?.text
                ?: stripInlineLyricTimestamps(lineTextWithoutLrcTimestamps).trim()
            timestamps.forEach { match ->
                timedLines += EmbeddedLyricsLine(
                    text = lyricText,
                    timestampMs = max(0L, parseTimestampMatch(match) + offsetMs),
                    tokens = enhancedLine?.tokens.orEmpty(),
                )
            }
            if (timestamps.isEmpty() && enhancedLine != null) {
                timedLines += enhancedLine
            }
            return@forEach
        }

        plainLines += stripInlineLyricTimestamps(trimmedLine).trim()
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

private fun parseEnhancedLrcLine(
    rawLine: String,
    offsetMs: Long,
): EmbeddedLyricsLine? {
    val timestampMatches = EnhancedLrcTimestampRegex.findAll(rawLine).toList()
    if (timestampMatches.isEmpty()) {
        return null
    }

    val tokens = timestampMatches.mapIndexedNotNull { index, match ->
        val textStart = match.range.last + 1
        val textEnd = timestampMatches.getOrNull(index + 1)?.range?.first ?: rawLine.length
        val tokenText = rawLine.substring(textStart, textEnd)
        if (tokenText.isEmpty()) {
            return@mapIndexedNotNull null
        }
        val timestampMs = max(0L, parseTimestampMatch(match) + offsetMs)
        val endTimestampMs = timestampMatches.getOrNull(index + 1)?.let { nextMatch ->
            max(0L, parseTimestampMatch(nextMatch) + offsetMs)
        }
        EmbeddedLyricsToken(
            text = tokenText,
            timestampMs = timestampMs,
            endTimestampMs = endTimestampMs,
        )
    }.trimDisplayTokens()
    val text = tokens.joinToString(separator = "") { token -> token.text }.trim()
    if (text.isBlank()) {
        return null
    }

    return EmbeddedLyricsLine(
        text = text,
        timestampMs = tokens.firstOrNull()?.timestampMs,
        tokens = tokens,
    )
}

private fun parseYrcLine(
    rawLine: String,
    offsetMs: Long,
): EmbeddedLyricsLine? {
    val lineMatch = YrcLineRegex.matchEntire(rawLine) ?: return null
    val lineStartMs = lineMatch.groupValues[1].toLongOrNull() ?: return null
    val lineDurationMs = lineMatch.groupValues[2].toLongOrNull() ?: 0L
    val lineBody = lineMatch.groupValues[3]
    val tokens = YrcTokenRegex.findAll(lineBody).mapNotNull { match ->
        val rawTokenStartMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val tokenDurationMs = match.groupValues[2].toLongOrNull() ?: 0L
        val tokenText = match.groupValues[3]
        if (tokenText.isEmpty()) {
            return@mapNotNull null
        }
        val tokenStartMs = normalizeYrcTokenStartMs(
            lineStartMs = lineStartMs,
            tokenStartMs = rawTokenStartMs,
        )
        EmbeddedLyricsToken(
            text = tokenText,
            timestampMs = max(0L, tokenStartMs + offsetMs),
            endTimestampMs = max(0L, tokenStartMs + tokenDurationMs + offsetMs),
        )
    }.toList().trimDisplayTokens()
    val text = if (tokens.isNotEmpty()) {
        tokens.joinToString(separator = "") { token -> token.text }.trim()
    } else {
        YrcTokenRegex.replace(lineBody, "").trim()
    }
    if (text.isBlank()) {
        return null
    }

    val lineTimestampMs = max(0L, lineStartMs + offsetMs)
    return EmbeddedLyricsLine(
        text = text,
        timestampMs = lineTimestampMs,
        tokens = tokens.ifEmpty {
            listOf(
                EmbeddedLyricsToken(
                    text = text,
                    timestampMs = lineTimestampMs,
                    endTimestampMs = max(0L, lineStartMs + lineDurationMs + offsetMs),
                ),
            )
        },
    )
}

private fun normalizeYrcTokenStartMs(
    lineStartMs: Long,
    tokenStartMs: Long,
): Long {
    return if (tokenStartMs >= lineStartMs) {
        tokenStartMs
    } else {
        lineStartMs + tokenStartMs
    }
}

private fun List<EmbeddedLyricsToken>.trimDisplayTokens(): List<EmbeddedLyricsToken> {
    return dropWhile { token -> token.text.isBlank() }
        .dropLastWhile { token -> token.text.isBlank() }
}

private fun stripInlineLyricTimestamps(rawLine: String): String {
    return EnhancedLrcTimestampRegex.replace(
        YrcTokenRegex.replace(rawLine, ""),
        "",
    )
}

private fun mergeTranslatedLyrics(
    primary: EmbeddedLyrics?,
    translation: EmbeddedLyrics?,
): EmbeddedLyrics? {
    primary ?: return translation
    translation ?: return primary
    if (translation.lines.isEmpty()) {
        return primary
    }

    val translatedLines = if (primary.isTimeSynced && translation.isTimeSynced) {
        primary.lines.map { line ->
            line.copy(translation = translation.findTimedTranslation(line)?.text)
        }
    } else if (primary.lines.size == translation.lines.size) {
        primary.lines.mapIndexed { index, line ->
            line.copy(translation = translation.lines[index].text.takeIf(String::isNotBlank))
        }
    } else {
        primary.lines
    }
    return primary.copy(lines = normalizeEmbeddedLyricsLines(translatedLines))
}

private fun EmbeddedLyrics.findTimedTranslation(
    primaryLine: EmbeddedLyricsLine,
): EmbeddedLyricsLine? {
    val timestampMs = primaryLine.timestampMs ?: return null
    return lines
        .filter { line -> line.timestampMs != null }
        .minByOrNull { line -> abs((line.timestampMs ?: 0L) - timestampMs) }
        ?.takeIf { line -> abs((line.timestampMs ?: 0L) - timestampMs) <= TranslationTimestampToleranceMs }
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
        val normalizedTranslation = line.translation?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedText.isEmpty()) {
            if (normalizedLines.isNotEmpty() && normalizedLines.last().text.isNotBlank()) {
                normalizedLines += line.copy(text = "", translation = null, tokens = emptyList())
            }
        } else {
            normalizedLines += line.copy(text = normalizedText, translation = normalizedTranslation)
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

internal fun chooseBetterLyrics(
    current: EmbeddedLyrics?,
    candidate: EmbeddedLyrics?,
): EmbeddedLyrics? {
    candidate ?: return current
    current ?: return candidate

    if (candidate.isTimeSynced != current.isTimeSynced) {
        return if (candidate.isTimeSynced) candidate else current
    }

    if (candidate.isWordSynced != current.isWordSynced) {
        return if (candidate.isWordSynced) candidate else current
    }

    val candidateTranslationCount = candidate.lines.count { line -> !line.translation.isNullOrBlank() }
    val currentTranslationCount = current.lines.count { line -> !line.translation.isNullOrBlank() }
    if (candidateTranslationCount != currentTranslationCount) {
        return if (candidateTranslationCount > currentTranslationCount) candidate else current
    }

    return if (candidate.lines.size > current.lines.size) candidate else current
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

private fun parseTimestampMatch(match: MatchResult): Long {
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

private const val TranslationTimestampToleranceMs = 1_200L
