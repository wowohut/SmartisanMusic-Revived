package com.smartisanos.music.playback

import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.smartisanos.music.data.online.OnlineLyrics
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLyricsTest {

    @Test
    fun `extracts timed lyrics from custom lyrics frame`() {
        val lyrics = extractEmbeddedLyrics(
            entries = listOf(
                TextInformationFrame(
                    "TXXX",
                    "LYRICS",
                    listOf(
                        """
                        [offset:500]
                        [00:01.00]第一句
                        [00:02.00]
                        [00:02.50]第二句
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.isTimeSynced)
        assertEquals(3, lyrics.lines.size)
        assertEquals("第一句", lyrics.lines[0].text)
        assertEquals(1_500L, lyrics.lines[0].timestampMs)
        assertEquals("", lyrics.lines[1].text)
        assertEquals(2_500L, lyrics.lines[1].timestampMs)
        assertEquals("第二句", lyrics.lines[2].text)
        assertEquals(3_000L, lyrics.lines[2].timestampMs)
    }

    @Test
    fun `parses enhanced lrc without exposing inline timestamps`() {
        val lyrics = parseEmbeddedLyricsText(
            rawText = """
                [00:03.640]<00:03.640>Lyrics<00:03.832> <00:04.020>composed
            """.trimIndent(),
            hintedByKey = true,
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.isTimeSynced)
        assertTrue(lyrics.isWordSynced)
        assertEquals("Lyrics composed", lyrics.lines.single().text)
        assertEquals(3_640L, lyrics.lines.single().timestampMs)
        assertEquals(listOf("Lyrics", " ", "composed"), lyrics.lines.single().tokens.map { it.text })
        assertEquals(3_832L, lyrics.lines.single().tokens[1].timestampMs)
    }

    @Test
    fun `parses inline-only enhanced lrc as timed lyrics`() {
        val lyrics = parseEmbeddedLyricsText(
            rawText = """
                <00:00.000>After<00:00.280> <00:00.560>You
                <00:03.640>Lyrics
            """.trimIndent(),
            hintedByKey = true,
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.isTimeSynced)
        assertEquals(listOf("After You", "Lyrics"), lyrics.lines.map { it.text })
        assertEquals(0L, lyrics.lines[0].timestampMs)
        assertEquals(3_640L, lyrics.lines[1].timestampMs)
    }

    @Test
    fun `parses netease yrc word timing`() {
        val lyrics = parseEmbeddedLyricsText(
            rawText = """
                [3640,850](3640,192,0)Lyrics(3832,188,0) (4020,300,0)composed
            """.trimIndent(),
            hintedByKey = true,
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.isTimeSynced)
        assertTrue(lyrics.isWordSynced)
        assertEquals("Lyrics composed", lyrics.lines.single().text)
        assertEquals(3_640L, lyrics.lines.single().timestampMs)
        assertEquals(4_320L, lyrics.lines.single().tokens.last().endTimestampMs)
    }

    @Test
    fun `merges translated lyrics by timestamp`() {
        val lyrics = parseOnlineLyrics(
            OnlineLyrics(
                lyric = """
                    [00:03.64]Tell me
                    [00:07.29]Is this all we have
                """.trimIndent(),
                translatedLyric = """
                    [00:03.64]告诉我
                    [00:07.29]这就是我们所拥有的吗
                """.trimIndent(),
            ),
        )

        requireNotNull(lyrics)
        assertEquals("Tell me", lyrics.lines[0].text)
        assertEquals("告诉我", lyrics.lines[0].translation)
        assertEquals("这就是我们所拥有的吗", lyrics.lines[1].translation)
    }

    @Test
    fun `prefers word lyrics over plain online lyrics`() {
        val lyrics = parseOnlineLyrics(
            OnlineLyrics(
                lyric = "[00:03.64]Lyrics composed",
                translatedLyric = null,
                wordLyric = "[3640,850](3640,192,0)Lyrics(3832,188,0) (4020,300,0)composed",
            ),
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.isWordSynced)
        assertEquals("Lyrics composed", lyrics.lines.single().text)
    }

    @Test
    fun `extracts unsynced lyrics from uslt frame`() {
        val lyricsText = """
            春天该很好

            你若尚在场
            秋风即使带凉
        """.trimIndent()
        val lyrics = extractEmbeddedLyrics(
            entries = listOf(
                BinaryFrame(
                    "USLT",
                    buildUsltFrameData(lyricsText),
                ),
            ),
        )

        requireNotNull(lyrics)
        assertFalse(lyrics.isTimeSynced)
        assertEquals(
            listOf("春天该很好", "", "你若尚在场", "秋风即使带凉"),
            lyrics.lines.map { it.text },
        )
    }

    @Test
    fun `extracts synchronized lyrics from sylt frame`() {
        val lyrics = extractEmbeddedLyrics(
            entries = listOf(
                BinaryFrame(
                    "SYLT",
                    buildSyltFrameData(
                        "第一句" to 1_200L,
                        "第二句" to 2_400L,
                    ),
                ),
            ),
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.isTimeSynced)
        assertEquals(listOf("第一句", "第二句"), lyrics.lines.map { it.text })
        assertEquals(listOf(1_200L, 2_400L), lyrics.lines.map { it.timestampMs })
    }

    @Test
    fun `ignores lyricist metadata`() {
        val lyrics = extractEmbeddedLyrics(
            entries = listOf(
                VorbisComment("LYRICIST", "林夕"),
            ),
        )

        assertNull(lyrics)
    }

    private fun buildUsltFrameData(text: String): ByteArray {
        val description = byteArrayOf(0)
        return byteArrayOf(3) +
            "eng".toByteArray(StandardCharsets.US_ASCII) +
            description +
            text.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildSyltFrameData(
        vararg lines: Pair<String, Long>,
    ): ByteArray {
        val header = byteArrayOf(3) +
            "eng".toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(2, 1) +
            byteArrayOf(0)
        val body = lines.fold(ByteArray(0)) { data, line ->
            data +
                line.first.toByteArray(StandardCharsets.UTF_8) +
                byteArrayOf(0) +
                line.second.toUnsignedIntBytes()
        }
        return header + body
    }

    private fun Long.toUnsignedIntBytes(): ByteArray {
        return byteArrayOf(
            ((this shr 24) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            (this and 0xFF).toByte(),
        )
    }
}
