package com.smartisanos.music.ui.shell.cloud.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisanos.music.R
import com.smartisanos.music.ui.components.SmartisanDrawableBackground
import com.smartisanos.music.ui.shell.cloud.CloudAccentColor
import com.smartisanos.music.ui.shell.cloud.CloudSectionTitleHeight

@Composable
internal fun CloudMusicSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(CloudSectionTitleHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        SmartisanDrawableBackground(
            drawableRes = R.drawable.home_recommend_title_bg,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = title,
            style = TextStyle(
                fontSize = 15.sp,
                color = ComposeColor(0xCC000000),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 11.dp, end = 32.dp),
        )
    }
}

@Composable
internal fun CloudMusicDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.67.dp)
            .background(ComposeColor(0xFFEBEBEB)),
    )
}

@Composable
internal fun CloudHomeSectionHeader(
    title: String,
    actionText: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(CloudSectionTitleHeight)
            .clickable(
                enabled = onClick != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onClick?.invoke() },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        SmartisanDrawableBackground(
            drawableRes = R.drawable.home_recommend_title_bg,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = title,
            style = TextStyle(
                fontSize = 15.sp,
                color = ComposeColor(0xCC000000),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 11.dp, end = if (actionText == null) 12.dp else 72.dp),
        )
        if (actionText != null) {
            Text(
                text = actionText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = CloudAccentColor,
                ),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            )
        }
    }
}
