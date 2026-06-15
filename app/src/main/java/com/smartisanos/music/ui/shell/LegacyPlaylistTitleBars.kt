package com.smartisanos.music.ui.shell

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisanos.music.R
import com.smartisanos.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBarTransition
import smartisanos.widget.TitleBar

@Composable
internal fun LegacyPlaylistTitleArea(
    target: LegacyPlaylistTarget?,
    detailTitle: String,
    rootEditMode: Boolean,
    rootSelectedCount: Int,
    detailEditMode: Boolean,
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    onRootEnterEdit: () -> Unit,
    onRootExitEdit: () -> Unit,
    onRootDeleteSelected: () -> Unit,
    onDetailBack: () -> Unit,
    onDetailEnterEdit: () -> Unit,
    onDetailExitEdit: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleTarget = target?.copy(title = detailTitle.ifBlank { target.title })
    val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        dimensionResource(R.dimen.title_bar_height)
    val titleBarContent: @Composable (
        LegacyPlaylistTarget?,
        String,
        Boolean,
        Int,
        Boolean,
    ) -> Unit = { barTarget, barTitle, rootEditing, rootSelectionCount, detailEditing ->
        LegacyPortSmartisanTitleBar(
            modifier = Modifier.fillMaxSize(),
        ) { titleBar ->
            titleBar.setupLegacyPlaylistTitleBar(
                target = barTarget,
                detailTitle = barTitle,
                rootEditMode = rootEditing,
                rootSelectedCount = rootSelectionCount,
                detailEditMode = detailEditing,
                onRootEnterEdit = onRootEnterEdit,
                onRootExitEdit = onRootExitEdit,
                onRootDeleteSelected = onRootDeleteSelected,
                onDetailBack = onDetailBack,
                onDetailEnterEdit = onDetailEnterEdit,
                onDetailExitEdit = onDetailExitEdit,
                onSearchClick = onSearchClick,
            )
        }
    }
    LegacyPortTitleBarTransition(
        secondaryKey = titleTarget,
        modifier = modifier
            .fillMaxWidth()
            .height(titleAreaHeight),
        label = "legacy playlist title transition",
        predictiveBackProgress = predictiveBackProgress,
        predictiveBackExitConsumed = predictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onPredictiveBackExitConsumedReset,
        primaryContent = {
            titleBarContent(null, "", rootEditMode, rootSelectedCount, false)
        },
        secondaryContent = { playlistTarget ->
            titleBarContent(playlistTarget, playlistTarget.title, false, 0, detailEditMode)
        },
    )
}

@Composable
internal fun LegacyPlaylistAddModeTitleArea(
    target: LegacyPlaylistTarget,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ComposeColor.White),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars),
        )
        val promptHeight = dimensionResource(R.dimen.status_bar_height)
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(promptHeight),
            factory = { context ->
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(context.getColor(R.color.title_color))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_act_title))
                }
            },
            update = { promptText ->
                val shortTitle = target.title.ellipsizeMiddle(8)
                promptText.text = promptText.context.getString(R.string.add_track_to) + " \"$shortTitle\""
            },
        )
        LegacyPortSmartisanTitleBar(
            includeStatusBar = false,
        ) { titleBar ->
            titleBar.setupLegacyPlaylistAddModeTitleBar(onConfirm)
        }
    }
}

private fun TitleBar.setupLegacyPlaylistTitleBar(
    target: LegacyPlaylistTarget?,
    detailTitle: String,
    rootEditMode: Boolean,
    rootSelectedCount: Int,
    detailEditMode: Boolean,
    onRootEnterEdit: () -> Unit,
    onRootExitEdit: () -> Unit,
    onRootDeleteSelected: () -> Unit,
    onDetailBack: () -> Unit,
    onDetailEnterEdit: () -> Unit,
    onDetailExitEdit: () -> Unit,
    onSearchClick: () -> Unit,
) {
    setShadowVisible(false)
    setCenterText(if (target == null) context.getString(R.string.tab_play_list) else detailTitle)

    when {
        target == null && rootEditMode -> {
            setPlaylistLeftImageView(R.drawable.standard_icon_cancel_selector) {
                onRootExitEdit()
            }
            setPlaylistRightImageView(R.drawable.titlebar_btn_delete_selector).apply {
                isEnabled = rootSelectedCount > 0
                setOnClickListener {
                    if (rootSelectedCount > 0) {
                        onRootDeleteSelected()
                    }
                }
            }
        }
        target == null -> {
            setPlaylistLeftImageView(R.drawable.standard_icon_multi_select_selector) {
                onRootEnterEdit()
            }
            setPlaylistRightImageView(R.drawable.search_btn_selector).apply {
                isEnabled = true
                setOnClickListener {
                    onSearchClick()
                }
            }
        }
        detailEditMode -> {
            setPlaylistLeftImageView(R.drawable.standard_icon_cancel_selector) {
                onDetailExitEdit()
            }
            setPlaylistRightImageView(R.drawable.standard_icon_hignlight_confirm_selector).apply {
                isEnabled = true
                setOnClickListener {
                    onDetailExitEdit()
                }
            }
        }
        else -> {
            setPlaylistLeftImageView(R.drawable.standard_icon_back_selector) {
                onDetailBack()
            }
            setPlaylistRightImageView(R.drawable.standard_icon_multi_select_selector).apply {
                isEnabled = true
                setOnClickListener {
                    onDetailEnterEdit()
                }
            }
        }
    }
}

private fun TitleBar.setPlaylistLeftImageView(
    resId: Int,
    onClick: () -> Unit,
): ImageView {
    return ((getLeftViewByIndex(0) as? ImageView) ?: addLeftImageView(resId)).apply {
        visibility = View.VISIBLE
        isEnabled = true
        setImageResource(resId)
        setOnClickListener {
            onClick()
        }
    }
}

private fun TitleBar.setPlaylistRightImageView(resId: Int): ImageView {
    return ((getRightViewByIndex(0) as? ImageView) ?: addRightImageView(resId)).apply {
        visibility = View.VISIBLE
        setImageResource(resId)
    }
}

private fun TitleBar.setupLegacyPlaylistAddModeTitleBar(onConfirm: () -> Unit) {
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    setCenterText(context.getString(R.string.name_tracks))
    addRightImageView(R.drawable.standard_icon_hignlight_confirm_selector).apply {
        setOnClickListener {
            onConfirm()
        }
    }
}
