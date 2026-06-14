package com.smartisanos.music.ui.shell.cloud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisanos.music.ui.shell.LegacyPortPageStackAxis
import com.smartisanos.music.ui.shell.LegacyPortPageStackTransition
import com.smartisanos.music.ui.shell.rememberLegacyPortPredictiveBackState

/**
 * 云音乐内部页面栈转场容器。
 *
 * 将 [CloudMusicRoute] 映射到主/二级页面，并接入项目已有的
 * [LegacyPortPageStackTransition]，实现：
 * - 主 Tab 页之间无转场切换
 * - 详情页从右侧横向切入
 * - 搜索页从底部纵向 push
 */
@Composable
internal fun CloudMusicTransitionHost(
    currentRoute: CloudMusicRoute,
    onNavigate: (CloudMusicRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    primaryContent: @Composable (CloudMusicRoute) -> Unit,
    secondaryContent: @Composable (CloudMusicRoute) -> Unit,
) {
    val isDetail = CloudMusicRoute.isDetail(currentRoute)
    val isSearch = currentRoute == CloudMusicRoute.Search
    val predictiveBackState = rememberLegacyPortPredictiveBackState()

    BackHandler(enabled = isDetail || isSearch) {
        onBack()
    }

    LegacyPortPageStackTransition(
        secondaryKey = currentRoute.takeIf { isDetail || isSearch },
        modifier = modifier.fillMaxSize(),
        label = "cloud music page stack",
        axis = LegacyPortPageStackAxis.Horizontal,
        axisForKey = { route ->
            if (route == CloudMusicRoute.Search) {
                LegacyPortPageStackAxis.VerticalPush
            } else {
                LegacyPortPageStackAxis.Horizontal
            }
        },
        predictiveBackProgress = predictiveBackState.progress,
        predictiveBackExitConsumed = predictiveBackState.exitConsumed,
        onPredictiveBackExitConsumedReset = { predictiveBackState.reset() },
        primaryContent = {
            primaryContent(currentRoute.primaryRoute())
        },
        secondaryContent = { route ->
            secondaryContent(route)
        },
    )
}
