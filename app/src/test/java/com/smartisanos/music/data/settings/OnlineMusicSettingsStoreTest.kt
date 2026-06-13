package com.smartisanos.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineMusicSettingsStoreTest {

    @Test
    fun neteaseQualityFallbackStartsAtPreferredQuality() {
        assertEquals(
            listOf(
                NeteaseAudioQuality.Lossless,
                NeteaseAudioQuality.ExHigh,
                NeteaseAudioQuality.Higher,
                NeteaseAudioQuality.Standard,
            ),
            NeteaseAudioQuality.Lossless.fallbackCandidates(),
        )
    }

    @Test
    fun neteaseQualityFallbackKeepsDefaultConservative() {
        assertEquals(
            listOf(
                NeteaseAudioQuality.ExHigh,
                NeteaseAudioQuality.Higher,
                NeteaseAudioQuality.Standard,
            ),
            NeteaseAudioQuality.ExHigh.fallbackCandidates(),
        )
    }
}
