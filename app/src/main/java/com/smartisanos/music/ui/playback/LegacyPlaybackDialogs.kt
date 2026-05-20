package com.smartisanos.music.ui.playback

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartisanos.music.R
import com.smartisanos.music.playback.PlaybackSleepTimerState
import com.smartisanos.ui_widget.SmartisanNumberPicker
import smartisanos.widget.MenuDialogTitleBar

internal data class LegacyPlaybackMoreActionCallbacks(
    val onAddToPlaylistClick: () -> Unit,
    val onAddToQueueClick: () -> Unit,
    val onFavoriteToggle: () -> Unit,
    val onLyricsToggle: () -> Unit,
    val onSleepTimerClick: () -> Unit,
    val onSetRingtoneClick: () -> Unit,
    val onScratchToggle: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onDismissRequest: () -> Unit,
)

@Composable
internal fun LegacyPlaybackMoreActionsOverlay(
    visible: Boolean,
    favoriteEnabled: Boolean,
    visualPage: PlaybackVisualPage,
    scratchEnabled: Boolean,
    sleepTimerActive: Boolean,
    addToPlaylistEnabled: Boolean,
    callbacks: LegacyPlaybackMoreActionCallbacks,
    modifier: Modifier = Modifier,
) {
    var renderOverlay by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            renderOverlay = true
        }
    }
    if (renderOverlay) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                LegacyPlaybackMoreActionsView(context).apply {
                    onHidden = { renderOverlay = false }
                }
            },
            update = { view ->
                view.onHidden = { renderOverlay = false }
                view.bind(
                    favoriteEnabled = favoriteEnabled,
                    visualPage = visualPage,
                    scratchEnabled = scratchEnabled,
                    sleepTimerActive = sleepTimerActive,
                    addToPlaylistEnabled = addToPlaylistEnabled,
                    callbacks = callbacks,
                )
                if (visible) {
                    view.showPanel()
                } else {
                    view.dismissPanel()
                }
            },
        )
    }
}

@Composable
internal fun LegacyPlaybackSleepTimerDialog(
    visible: Boolean,
    state: PlaybackSleepTimerState,
    bottomInsetPx: Int,
    onDismissRequest: () -> Unit,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renderOverlay by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            renderOverlay = true
        }
    }
    if (renderOverlay) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                LegacySleepTimerView(context).apply {
                    onHidden = { renderOverlay = false }
                }
            },
            update = { view ->
                view.onHidden = { renderOverlay = false }
                view.bind(
                    state = state,
                    bottomInsetPx = bottomInsetPx,
                    onDismissRequest = onDismissRequest,
                    onDurationSelected = onDurationSelected,
                )
                if (visible) {
                    view.showPanel()
                } else {
                    view.dismissPanel()
                }
            },
        )
    }
}

private class LegacyPlaybackMoreActionsView(
    context: Context,
) : FrameLayout(context) {
    var onHidden: () -> Unit = {}

    private val interpolator = DecelerateInterpolator()
    private val itemAdapter = MoreActionAdapter(context)
    private val parentPanel = LinearLayout(context).apply {
        id = R.id.parentPanel
        orientation = LinearLayout.VERTICAL
        isClickable = true
    }
    private val titleBar = MenuDialogTitleBar(context).apply {
        id = R.id.menu_dialog_title_bar
        forceRequestAccessibilityFocusWhenAttached(false)
        setTitle(R.string.select_action)
        setLeftButtonVisibility(View.INVISIBLE)
        setRightButtonVisibility(View.VISIBLE)
    }
    private val gridView = GridView(context).apply {
        id = R.id.gridview
        numColumns = MoreActionColumnCount
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        selector = ColorDrawable(Color.TRANSPARENT)
        cacheColorHint = Color.TRANSPARENT
        isVerticalScrollBarEnabled = false
        horizontalSpacing = 0
        verticalSpacing = 0
        adapter = itemAdapter
    }
    private val bottomLine = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.bottom_line))
    }
    private var shown = false
    private var dismissing = false

    init {
        id = R.id.container
        setBackgroundResource(R.color.transparent_black)
        alpha = 0f
        visibility = GONE
        isClickable = true
        setOnClickListener {
            itemAdapter.callbacks?.onDismissRequest?.invoke()
        }
        titleBar.setOnRightButtonClickListener {
            itemAdapter.callbacks?.onDismissRequest?.invoke()
        }
        gridView.setOnItemClickListener { _, _, position, _ ->
            val item = itemAdapter.getItem(position)
            if (item.enabled) {
                item.onClick()
            }
        }
        val panelParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        addView(parentPanel, panelParams)
        parentPanel.addView(
            titleBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.titlebar_height),
            ),
        )
        parentPanel.addView(
            gridView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dpInt(MoreActionItemHeightDp * 2f),
            ),
        )
        parentPanel.addView(
            bottomLine,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1,
            ),
        )
    }

    fun bind(
        favoriteEnabled: Boolean,
        visualPage: PlaybackVisualPage,
        scratchEnabled: Boolean,
        sleepTimerActive: Boolean,
        addToPlaylistEnabled: Boolean,
        callbacks: LegacyPlaybackMoreActionCallbacks,
    ) {
        itemAdapter.callbacks = callbacks
        val items = buildMoreActions(
            favoriteEnabled = favoriteEnabled,
            visualPage = visualPage,
            scratchEnabled = scratchEnabled,
            sleepTimerActive = sleepTimerActive,
            addToPlaylistEnabled = addToPlaylistEnabled,
            callbacks = callbacks,
        )
        itemAdapter.setItems(items)
        val rows = (items.size + MoreActionColumnCount - 1) / MoreActionColumnCount
        gridView.layoutParams = gridView.layoutParams.apply {
            height = context.dpInt(MoreActionItemHeightDp * rows)
        }
    }

    fun showPanel() {
        cancelAnimations()
        if (shown && !dismissing) {
            return
        }
        shown = true
        dismissing = false
        visibility = VISIBLE
        isClickable = true
        post {
            val panelHeight = parentPanel.measuredPanelHeight()
            parentPanel.translationY = panelHeight.toFloat()
            parentPanel.alpha = PanelHiddenAlpha
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
            parentPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
        }
    }

    fun dismissPanel() {
        if (!shown && !dismissing) {
            visibility = GONE
            onHidden()
            return
        }
        if (dismissing) {
            return
        }
        dismissing = true
        cancelAnimations()
        post {
            val panelHeight = parentPanel.measuredPanelHeight()
            animate()
                .alpha(0f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
            parentPanel.animate()
                .translationY(panelHeight.toFloat())
                .alpha(PanelHiddenAlpha)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .withEndAction {
                    shown = false
                    dismissing = false
                    visibility = GONE
                    onHidden()
                }
                .start()
        }
    }

    private fun cancelAnimations() {
        animate().withEndAction(null).cancel()
        parentPanel.animate().withEndAction(null).cancel()
    }
}

private class LegacySleepTimerView(
    context: Context,
) : FrameLayout(context) {
    var onHidden: () -> Unit = {}

    private val interpolator = DecelerateInterpolator()
    private val titleBar = MenuDialogTitleBar(context).apply {
        id = R.id.titlebar
        forceRequestAccessibilityFocusWhenAttached(false)
        setLeftButtonVisibility(View.VISIBLE)
        setRightButtonVisibility(View.VISIBLE)
        setLeftImageViewRes(R.drawable.standard_icon_cancel_selector)
        setRightImageRes(R.drawable.standard_icon_complete_selector)
    }
    private val numberPicker = SmartisanNumberPicker(context).apply {
        id = R.id.number_picker
        isFocusable = true
        isFocusableInTouchMode = true
        c(
            ContextCompat.getColor(context, R.color.menu_text_color),
            ContextCompat.getColor(context, R.color.btn_text_color_blue),
        )
        d(
            resources.getDimensionPixelSize(R.dimen.time_picker_text_size),
            resources.getDimensionPixelSize(R.dimen.time_picker_text_size_hight),
        )
        setWrapSelectorWheel(false)
    }
    private val parentPanel = LinearLayout(context).apply {
        id = R.id.parentPanel
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        isClickable = true
    }
    private var shown = false
    private var dismissing = false
    private var activeMode: Boolean? = null
    private var selectedValue = 1
    private var dismissRequest: () -> Unit = {}
    private var durationSelected: (Long) -> Unit = {}

    init {
        id = R.id.container
        setBackgroundResource(R.color.transparent_black)
        alpha = 0f
        visibility = GONE
        isClickable = true
        setOnClickListener {
            dismissRequest()
        }
        addView(
            parentPanel,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        parentPanel.addView(
            titleBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.titlebar_height),
            ),
        )
        val pickerContainer = RelativeLayout(context).apply {
            setBackgroundResource(R.drawable.time_picker_widget_bg)
            setPadding(context.dpInt(15f), 0, context.dpInt(15f), 0)
            addView(
                numberPicker,
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                ).apply {
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                },
            )
        }
        parentPanel.addView(
            pickerContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dpInt(208f),
            ),
        )
        val bottomDrawable = ContextCompat.getDrawable(context, R.drawable.time_picker_widget_bottom)
        parentPanel.addView(
            TextView(context).apply {
                background = bottomDrawable
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                bottomDrawable?.intrinsicHeight?.takeIf { it > 0 } ?: context.dpInt(9f),
            ),
        )
        titleBar.setOnLeftButtonClickListener {
            dismissRequest()
        }
        titleBar.setOnRightButtonClickListener {
            if (titleBar.getRightImageView().isEnabled) {
                durationSelected(mapSleepTimerDuration(activeMode == true, selectedValue))
            }
        }
        numberPicker.setOnValueChangedListener { _, _, newVal ->
            selectedValue = newVal
            updateCompleteButton()
            LegacyPlaybackHaptics.vibrateEffect(context)
        }
    }

    fun bind(
        state: PlaybackSleepTimerState,
        bottomInsetPx: Int,
        onDismissRequest: () -> Unit,
        onDurationSelected: (Long) -> Unit,
    ) {
        dismissRequest = onDismissRequest
        durationSelected = onDurationSelected
        if (parentPanel.paddingBottom != bottomInsetPx) {
            parentPanel.setPadding(0, 0, 0, bottomInsetPx)
        }
        val isActive = state.isActive
        if (activeMode != isActive) {
            activeMode = isActive
            configurePicker(isActive)
        }
        titleBar.setTitle(
            if (isActive) {
                "${context.getString(R.string.remain_time)} ${formatSleepTimerRemaining(state.remainingMs)}"
            } else {
                context.getString(R.string.setting_stop_time)
            },
        )
        updateCompleteButton()
    }

    fun showPanel() {
        cancelAnimations()
        if (shown && !dismissing) {
            return
        }
        shown = true
        dismissing = false
        visibility = VISIBLE
        post {
            val panelHeight = parentPanel.measuredPanelHeight()
            parentPanel.translationY = panelHeight.toFloat()
            parentPanel.alpha = PanelHiddenAlpha
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
            parentPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
        }
    }

    fun dismissPanel() {
        if (!shown && !dismissing) {
            visibility = GONE
            onHidden()
            return
        }
        if (dismissing) {
            return
        }
        dismissing = true
        cancelAnimations()
        post {
            val panelHeight = parentPanel.measuredPanelHeight()
            animate()
                .alpha(0f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
            parentPanel.animate()
                .translationY(panelHeight.toFloat())
                .alpha(PanelHiddenAlpha)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .withEndAction {
                    shown = false
                    dismissing = false
                    visibility = GONE
                    onHidden()
                }
                .start()
        }
    }

    private fun configurePicker(active: Boolean) {
        val labels = if (active) {
            arrayOf(
                context.getString(R.string.time_countdown),
                context.getString(R.string.time_no),
                context.getString(R.string.time_15m),
                context.getString(R.string.time_30m),
                context.getString(R.string.time_1h),
                context.getString(R.string.time_1_5h),
                context.getString(R.string.time_2h),
            )
        } else {
            arrayOf(
                context.getString(R.string.time_no),
                context.getString(R.string.time_15m),
                context.getString(R.string.time_30m),
                context.getString(R.string.time_1h),
                context.getString(R.string.time_1_5h),
                context.getString(R.string.time_2h),
            )
        }
        selectedValue = 1
        numberPicker.setDisplayedValues(labels)
        numberPicker.setMinValue(1)
        numberPicker.setMaxValue(labels.size)
        numberPicker.setValue(selectedValue)
        updateCompleteButton()
    }

    private fun updateCompleteButton() {
        val completeButton = titleBar.getRightImageView()
        val enabled = !(activeMode == true && selectedValue == 1)
        completeButton.isEnabled = enabled
        completeButton.alpha = if (enabled) 1f else DisabledAlpha
    }

    private fun cancelAnimations() {
        animate().withEndAction(null).cancel()
        parentPanel.animate().withEndAction(null).cancel()
    }
}

private class MoreActionAdapter(
    private val context: Context,
) : BaseAdapter() {
    var callbacks: LegacyPlaybackMoreActionCallbacks? = null
    private var items: List<MoreActionItem> = emptyList()

    fun setItems(items: List<MoreActionItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): MoreActionItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun isEnabled(position: Int): Boolean = items[position].enabled

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val holder: MoreActionViewHolder
        val root = if (convertView == null) {
            val createdRoot = RelativeLayout(context).apply {
                id = R.id.poprelative
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.menu_item_selector)
                layoutParams = AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    context.dpInt(MoreActionItemHeightDp),
                )
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val icon = ImageView(context).apply {
                id = R.id.menu_icon
                isDuplicateParentStateEnabled = true
                contentDescription = null
            }
            val text = TextView(context).apply {
                id = R.id.menu_text
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine(true)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.add_nav_text_color))
                setPadding(context.dpInt(1f), 0, context.dpInt(1f), 0)
            }
            content.addView(
                icon,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            content.addView(
                text,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = context.dpInt(1f)
                    leftMargin = context.dpInt(6f)
                    rightMargin = context.dpInt(6f)
                },
            )
            createdRoot.addView(
                content,
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                },
            )
            holder = MoreActionViewHolder(icon, text)
            createdRoot.tag = holder
            createdRoot
        } else {
            holder = convertView.tag as MoreActionViewHolder
            convertView as RelativeLayout
        }
        val item = items[position]
        root.isEnabled = item.enabled
        root.alpha = if (item.enabled) 1f else DisabledAlpha
        holder.icon.isEnabled = item.enabled
        holder.text.isEnabled = item.enabled
        holder.icon.setImageDrawable(menuIconDrawable(context, item.iconRes, item.pressedIconRes))
        holder.text.setText(item.labelRes)
        holder.text.setTextColor(
            ContextCompat.getColor(
                context,
                if (item.selected) R.color.btn_text_color_blue else R.color.add_nav_text_color,
            ),
        )
        root.contentDescription = context.getString(item.labelRes)
        root.setOnClickListener {
            if (item.enabled) {
                item.onClick()
            }
        }
        return root
    }
}

private data class MoreActionViewHolder(
    val icon: ImageView,
    val text: TextView,
)

private data class MoreActionItem(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val pressedIconRes: Int = iconRes,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private fun buildMoreActions(
    favoriteEnabled: Boolean,
    visualPage: PlaybackVisualPage,
    scratchEnabled: Boolean,
    sleepTimerActive: Boolean,
    addToPlaylistEnabled: Boolean,
    callbacks: LegacyPlaybackMoreActionCallbacks,
): List<MoreActionItem> {
    return listOf(
        MoreActionItem(
            labelRes = R.string.add_to_playlist,
            iconRes = R.drawable.more_select_icon_addlist,
            pressedIconRes = R.drawable.more_select_icon_addlist_down,
            enabled = addToPlaylistEnabled,
            onClick = callbacks.onAddToPlaylistClick,
        ),
        MoreActionItem(
            labelRes = R.string.add_to_queue,
            iconRes = R.drawable.more_select_icon_addplay,
            pressedIconRes = R.drawable.more_select_icon_addplay_down,
            onClick = callbacks.onAddToQueueClick,
        ),
        MoreActionItem(
            labelRes = if (favoriteEnabled) R.string.cancel_love else R.string.love,
            iconRes = if (favoriteEnabled) {
                R.drawable.more_select_icon_favorite_cancel
            } else {
                R.drawable.more_select_icon_favorite_add
            },
            pressedIconRes = if (favoriteEnabled) {
                R.drawable.more_select_icon_favorite_cancel_down
            } else {
                R.drawable.more_select_icon_favorite_add_down
            },
            enabled = addToPlaylistEnabled,
            onClick = callbacks.onFavoriteToggle,
        ),
        MoreActionItem(
            labelRes = R.string.lyrics,
            iconRes = R.drawable.more_select_icon_lyric,
            selected = visualPage == PlaybackVisualPage.Lyrics,
            onClick = callbacks.onLyricsToggle,
        ),
        MoreActionItem(
            labelRes = R.string.sleep_timer,
            iconRes = R.drawable.more_select_icon_timer,
            selected = sleepTimerActive,
            onClick = callbacks.onSleepTimerClick,
        ),
        MoreActionItem(
            labelRes = R.string.set_ringtone,
            iconRes = R.drawable.more_select_icon_ringtone,
            onClick = callbacks.onSetRingtoneClick,
        ),
        MoreActionItem(
            labelRes = R.string.djing,
            iconRes = if (scratchEnabled) {
                R.drawable.more_select_icon_djing_on
            } else {
                R.drawable.more_select_icon_djing
            },
            selected = scratchEnabled,
            onClick = callbacks.onScratchToggle,
        ),
        MoreActionItem(
            labelRes = R.string.delete,
            iconRes = R.drawable.more_select_icon_delete,
            onClick = callbacks.onDeleteClick,
        ),
    )
}

private fun mapSleepTimerDuration(
    active: Boolean,
    value: Int,
): Long {
    val originalValue = if (active) value else value + 1
    return when (originalValue) {
        3 -> 15L * MinuteMs
        4 -> 30L * MinuteMs
        5 -> 60L * MinuteMs
        6 -> 90L * MinuteMs
        7 -> 120L * MinuteMs
        else -> 0L
    }
}

private fun menuIconDrawable(
    context: Context,
    @DrawableRes normalRes: Int,
    @DrawableRes pressedRes: Int,
): Drawable? {
    val normal = ContextCompat.getDrawable(context, normalRes) ?: return null
    if (normalRes == pressedRes) {
        return normal
    }
    return StateListDrawable().apply {
        ContextCompat.getDrawable(context, pressedRes)?.let { pressed ->
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_selected), pressed)
        }
        addState(intArrayOf(), normal)
    }
}

private fun View.measuredPanelHeight(): Int {
    return height.takeIf { it > 0 } ?: measuredHeight.takeIf { it > 0 } ?: 0
}

private fun Context.dpInt(value: Float): Int {
    return (value * resources.displayMetrics.density + 0.5f).toInt()
}

private const val MoreActionColumnCount = 4
private const val MoreActionItemHeightDp = 74f
private const val PopupAnimationDurationMs = 300L
private const val PanelHiddenAlpha = 0.92f
private const val DisabledAlpha = 0.35f
private const val MinuteMs = 60_000L
