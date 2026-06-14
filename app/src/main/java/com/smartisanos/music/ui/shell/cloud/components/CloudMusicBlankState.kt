package com.smartisanos.music.ui.shell.cloud.components

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisanos.music.R
import com.smartisanos.music.ui.shell.LegacyPlaylistBlankView
import com.smartisanos.music.ui.shell.cloud.dpPx

@Composable
internal fun CloudMusicBlankState(
    title: String,
    subtitle: String?,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                setBackgroundResource(R.drawable.account_background)
            }
        },
        update = { root ->
            val content = CloudMusicBlankContent(
                title = title,
                subtitle = subtitle.orEmpty(),
                actionText = actionText.orEmpty(),
            )
            if (root.tag == content) {
                root.findViewById<Button>(R.id.btn_ok)?.setOnClickListener {
                    onActionClick?.invoke()
                }
                return@AndroidView
            }
            root.tag = content
            root.removeAllViews()
            val contentColumn = LinearLayout(root.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            contentColumn.addView(
                LegacyPlaylistBlankView(
                    context = root.context,
                    iconRes = R.drawable.blank_search,
                    primaryText = content.title,
                    secondaryText = content.subtitle,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (content.actionText.isNotBlank() && onActionClick != null) {
                contentColumn.addView(
                    Button(root.context).apply {
                        id = R.id.btn_ok
                        text = content.actionText
                        isAllCaps = false
                        gravity = Gravity.CENTER
                        includeFontPadding = true
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_PX,
                            resources.getDimension(R.dimen.semi_large_text_size),
                        )
                        setBackgroundResource(R.drawable.shrink_long_btn_red_selector)
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = 0
                        minimumHeight = 0
                        setOnClickListener { onActionClick.invoke() }
                    },
                    LinearLayout.LayoutParams(
                        root.context.dpPx(160),
                        root.context.dpPx(48),
                    ).apply {
                        topMargin = root.context.dpPx(24)
                    },
                )
            }
            root.addView(
                contentColumn,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        },
    )
}

private data class CloudMusicBlankContent(
    val title: String,
    val subtitle: String,
    val actionText: String,
)
