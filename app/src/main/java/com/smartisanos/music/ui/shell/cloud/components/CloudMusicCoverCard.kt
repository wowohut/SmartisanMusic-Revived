package com.smartisanos.music.ui.shell.cloud.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisanos.music.ui.shell.cloud.CloudHomeCoverCardWidth
import com.smartisanos.music.ui.shell.cloud.CloudSecondaryTextColor

@Composable
internal fun CloudHomeCoverCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CloudHomeCoverCardWidth)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
    ) {
        CloudMusicCoverImage(
            imageUrl = imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = 0.67.dp,
                    color = ComposeColor(0x1F000000),
                    shape = RoundedCornerShape(4.dp),
                ),
        )
        Text(
            text = title,
            style = TextStyle(
                fontSize = 12.sp,
                color = ComposeColor(0xCC000000),
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        subtitle?.takeIf(String::isNotBlank)?.let { subtitleText ->
            Text(
                text = subtitleText,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = CloudSecondaryTextColor,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
internal fun CloudHomeCoverSection(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier.background(ComposeColor.White),
    ) {
        CloudHomeSectionHeader(
            title = title,
            actionText = actionText,
            onClick = onActionClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
        ) {
            content()
        }
        CloudMusicDivider()
    }
}
