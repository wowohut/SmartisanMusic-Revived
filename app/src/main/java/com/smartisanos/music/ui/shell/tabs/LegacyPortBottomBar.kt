package com.smartisanos.music.ui.shell.tabs

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisanos.music.ui.navigation.MusicDestination
import smartisanos.widget.tabswitcher.TabSwitcher

@Composable
internal fun LegacyPortBottomBar(
    currentDestination: MusicDestination,
    destinations: List<MusicDestination>,
    onDestinationSelected: (MusicDestination) -> Unit,
    modifier: Modifier = Modifier,
    topChromeVisible: Boolean = true,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationBarStartInsetPx = WindowInsets.navigationBars.getLeft(density, layoutDirection)
    val navigationBarEndInsetPx = WindowInsets.navigationBars.getRight(density, layoutDirection)
    val navigationBarInsetPx = WindowInsets.navigationBars.getBottom(density)
    val navigationBarInset = with(density) { navigationBarInsetPx.toDp() }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp + navigationBarInset),
        factory = { viewContext ->
            TabSwitcher(viewContext)
        },
        update = { tabSwitcher ->
            tabSwitcher.setNavigationBarInsets(
                start = navigationBarStartInsetPx,
                end = navigationBarEndInsetPx,
                bottom = navigationBarInsetPx,
            )
            tabSwitcher.setTopChromeVisible(topChromeVisible)
            // 先更新可见 tab 列表（重建时会校正选中态），再下发当前选中项。
            tabSwitcher.setDestinations(destinations)
            tabSwitcher.setOnDestinationSelectedListener(onDestinationSelected)
            tabSwitcher.setCurrentDestination(currentDestination)
        },
    )
}
