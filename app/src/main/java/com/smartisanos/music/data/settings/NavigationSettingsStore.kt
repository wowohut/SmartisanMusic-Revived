package com.smartisanos.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartisanos.music.ui.navigation.MusicDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val NavigationSettingsStoreName = "navigation_settings"

private val Context.navigationSettingsDataStore by preferencesDataStore(
    name = NavigationSettingsStoreName,
)

data class NavigationSettings(
    val hiddenTabs: Set<String> = emptySet(),
)

class NavigationSettingsStore(
    private val context: Context,
) {

    val settings: Flow<NavigationSettings> = context.navigationSettingsDataStore.data
        .map { preferences ->
            NavigationSettings(
                hiddenTabs = preferences[HiddenTabsKey].orEmpty(),
            )
        }
        .distinctUntilChanged()

    /**
     * 增量更新单个 tab 的可见性。
     *
     * 在 DataStore 的 [edit] 块内基于存储层当前值应用变更，避免 UI 层闭包快照在连续快速
     * 切换时整体覆盖前一次操作。内置「至少保留一个可隐藏 tab」的拦截：当隐藏操作会导致
     * 除 [MusicDestination.More] 外所有 tab 都被隐藏时，拒绝写入（保持上一个状态）。
     */
    suspend fun setTabVisible(route: String, visible: Boolean) {
        context.navigationSettingsDataStore.edit { preferences ->
            val current = preferences[HiddenTabsKey].orEmpty()
            if (visible) {
                preferences[HiddenTabsKey] = current - route
            } else if (canHideAnyTab(current + route)) {
                preferences[HiddenTabsKey] = current + route
            }
            // canHide 为 false 时拒绝写入，保持存储不变。
        }
    }
}

/**
 * 判断「在 [hiddenTabs] 的基础上再隐藏一个 tab 后，是否仍至少有一个可隐藏 tab 可见」。
 *
 * store 的 [NavigationSettingsStore.setTabVisible] 与设置页 UI 共用此规则，确保两道拦截
 * 防线判断一致：UI 层基于快照即时弹回避免抖动，store 层基于存储值兜底防止竞态放过。
 */
internal fun canHideAnyTab(hiddenTabs: Set<String>): Boolean {
    val hideableRoutes = MusicDestination.entries
        .filter { it.route != MusicDestination.More.route }
        .map { it.route }
        .toSet()
    return hideableRoutes.any { it !in hiddenTabs }
}

/**
 * 计算底部导航栏实际需要渲染的 tab 列表。
 *
 * - [MusicDestination.More] 始终保留，它是设置页的入口来源，不可隐藏。
 * - 其余 tab 按 [NavigationSettings.hiddenTabs] 过滤。
 * - [forceVisible] 中的 tab 会被强制保留（用于「添加到播放列表」模式临时补回 Songs）。
 * - 保证至少返回一个可隐藏 tab，避免用户把除「更多」外的 tab 全部隐藏后
 *   无法切回任何内容页；全隐藏时回退保留 [MusicDestination.Playlist]。
 */
fun NavigationSettings.visibleDestinations(
    forceVisible: Set<MusicDestination> = emptySet(),
): List<MusicDestination> {
    val visible = MusicDestination.entries.filter { destination ->
        destination == MusicDestination.More ||
            destination.route !in hiddenTabs ||
            destination in forceVisible
    }
    val visibleHideable = visible.filter { it != MusicDestination.More }
    if (visibleHideable.isEmpty()) {
        // 全部可隐藏 tab 都被关闭，回退保留 Playlist。
        return MusicDestination.entries.filter { it == MusicDestination.More || it == MusicDestination.Playlist }
    }
    return visible
}

private val HiddenTabsKey = stringSetPreferencesKey("hidden_tabs")
