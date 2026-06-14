package com.smartisanos.music.ui.shell.cloud.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineTrack
import com.smartisanos.music.ui.shell.cloud.CloudHomeTrackPreviewRowHeight
import com.smartisanos.music.ui.shell.cloud.CloudSecondaryTextColor

@Composable
internal fun CloudHomeTrackPreviewRow(
    index: Int,
    track: OnlineTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(CloudHomeTrackPreviewRowHeight)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = TextStyle(
                fontSize = 13.sp,
                color = ComposeColor(0x66000000),
            ),
            modifier = Modifier.width(26.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = ComposeColor(0xCC000000),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.takeIf(String::isNotBlank)
                    ?: track.album
                    ?: stringResource(R.string.cloud_music_provider_netease),
                style = TextStyle(
                    fontSize = 11.sp,
                    color = CloudSecondaryTextColor,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
