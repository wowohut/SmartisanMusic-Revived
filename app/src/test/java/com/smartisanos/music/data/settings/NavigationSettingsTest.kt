package com.smartisanos.music.data.settings

import com.smartisanos.music.ui.navigation.MusicDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSettingsTest {

    @Test
    fun visibleDestinationsReturnsAllWhenNothingHidden() {
        val visible = NavigationSettings().visibleDestinations()

        assertEquals(MusicDestination.entries.toList(), visible)
    }

    @Test
    fun visibleDestinationsAlwaysKeepsMoreTab() {
        val visible = NavigationSettings(
            hiddenTabs = setOf(MusicDestination.More.route),
        ).visibleDestinations()

        assertTrue(MusicDestination.More in visible)
    }

    @Test
    fun visibleDestinationsFiltersHiddenTabs() {
        val visible = NavigationSettings(
            hiddenTabs = setOf(MusicDestination.Album.route, MusicDestination.Artist.route),
        ).visibleDestinations()

        assertTrue(MusicDestination.Album !in visible)
        assertTrue(MusicDestination.Artist !in visible)
        assertTrue(MusicDestination.Playlist in visible)
        assertTrue(MusicDestination.More in visible)
    }

    @Test
    fun visibleDestinationsFallsBackToPlaylistWhenAllHideableHidden() {
        val allHideableRoutes = MusicDestination.entries
            .filter { it != MusicDestination.More }
            .map { it.route }
            .toSet()
        val visible = NavigationSettings(hiddenTabs = allHideableRoutes).visibleDestinations()

        // 全部可隐藏 tab 被关闭时，回退保留 Playlist + More，避免无可切回的内容页。
        assertEquals(
            listOf(MusicDestination.Playlist, MusicDestination.More),
            visible,
        )
    }

    @Test
    fun visibleDestinationsForceVisibleOverridesHidden() {
        val visible = NavigationSettings(
            hiddenTabs = setOf(MusicDestination.Songs.route),
        ).visibleDestinations(forceVisible = setOf(MusicDestination.Songs))

        // forceVisible 临时补回被隐藏的 Songs（用于添加到播放列表模式）。
        assertTrue(MusicDestination.Songs in visible)
    }

    @Test
    fun visibleDestinationsPreservesEnumDeclarationOrder() {
        val visible = NavigationSettings(
            hiddenTabs = setOf(MusicDestination.CloudMusic.route),
        ).visibleDestinations()

        val expected = MusicDestination.entries.filter { it != MusicDestination.CloudMusic }
        assertEquals(expected, visible)
    }

    @Test
    fun canHideAnyTabReturnsTrueWhenHideableTabRemainsVisible() {
        // 仅隐藏 1 个，仍有多个可隐藏 tab 可见，允许继续隐藏。
        val hidden = setOf(MusicDestination.Album.route)
        assertTrue(canHideAnyTab(hidden + MusicDestination.Artist.route))
    }

    @Test
    fun canHideAnyTabReturnsFalseWhenHidingLastVisibleHideableTab() {
        // 除 More 外只剩 Playlist 可见，再隐藏 Playlist 会导致无可切换的内容页。
        val allButPlaylist = MusicDestination.entries
            .filter { it != MusicDestination.More && it != MusicDestination.Playlist }
            .map { it.route }
            .toSet()
        assertFalse(canHideAnyTab(allButPlaylist + MusicDestination.Playlist.route))
    }

    @Test
    fun canHideAnyTabIgnoresMoreTab() {
        // hiddenTabs 误含 More.route 时，More 不计入可隐藏判断，仍按其余 tab 判定。
        val onlyMoreHidden = setOf(MusicDestination.More.route)
        assertTrue(canHideAnyTab(onlyMoreHidden + MusicDestination.Playlist.route))
    }
}
