package smartisanos.widget.tabswitcher

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Checkable
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.smartisanos.music.R
import com.smartisanos.music.ui.navigation.MusicDestination

class TabSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
    private val tabContainer: LinearLayout
    private val topShadowView: ImageView
    private val topDividerView: View
    private val destinationViews = mutableMapOf<MusicDestination, BottomTabItemView>()
    private var selectedDestination = MusicDestination.Playlist
    private var currentDestinations: List<MusicDestination> = emptyList()
    private var startInset = 0
    private var endInset = 0
    private var bottomInset = 0
    private var suppressSelectionCallback = false
    private var onDestinationSelected: ((MusicDestination) -> Unit)? = null

    init {
        val barHeight = resources.getDimensionPixelSize(R.dimen.smartisan_tabswitch_tabbar_height)
        setBackgroundResource(R.drawable.sb_repeat_tabbar_bg)

        tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(
            tabContainer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                barHeight,
            ).apply {
                addRule(ALIGN_PARENT_BOTTOM)
            },
        )

        topShadowView = ImageView(context).apply {
            setBackgroundResource(R.drawable.tab_bar_shadow)
        }
        addView(
            topShadowView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(ALIGN_PARENT_TOP)
            },
        )

        topDividerView = View(context).apply {
            setBackgroundColor(context.getColor(R.color.nav_list_line))
        }
        addView(
            topDividerView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.nav_divider_height),
            ).apply {
                addRule(ALIGN_PARENT_TOP)
            },
        )

        setDestinations(MusicDestination.entries)
    }

    fun setTopChromeVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        topShadowView.visibility = visibility
        topDividerView.visibility = visibility
    }

    fun setNavigationBarInsets(start: Int, end: Int, bottom: Int) {
        if (startInset == start && endInset == end && bottomInset == bottom) {
            return
        }
        startInset = start
        endInset = end
        bottomInset = bottom
        (tabContainer.layoutParams as LayoutParams).let { layoutParams ->
            layoutParams.marginStart = start
            layoutParams.marginEnd = end
            layoutParams.bottomMargin = bottom
            tabContainer.layoutParams = layoutParams
        }
    }

    fun setOnDestinationSelectedListener(listener: ((MusicDestination) -> Unit)?) {
        onDestinationSelected = listener
    }

    fun setCurrentDestination(destination: MusicDestination) {
        selectDestination(destination, notify = false, animate = true)
    }

    fun setDestinations(destinations: List<MusicDestination>) {
        if (destinations == currentDestinations) {
            return
        }
        currentDestinations = destinations
        tabContainer.removeAllViews()
        destinationViews.clear()
        tabContainer.weightSum = destinations.size.toFloat().coerceAtLeast(1f)

        destinations.forEach { destination ->
            val label = context.getString(destination.labelRes)
            val itemView = BottomTabItemView(context).apply {
                id = View.generateViewId()
                setDrawableResource(destination.tabIconSelectorRes())
                text = label
                contentDescription = label
                setTextColor(context.getColorStateList(R.color.tab_bar_text_color))
                setOnCheckedChangeListener { view, checked ->
                    if (!checked || suppressSelectionCallback) {
                        return@setOnCheckedChangeListener
                    }
                    val selected = destinationViews.entries
                        .firstOrNull { it.value == view }
                        ?.key
                        ?: return@setOnCheckedChangeListener
                    selectDestination(selected, notify = true, animate = true)
                }
            }
            tabContainer.addView(
                itemView,
                LinearLayout.LayoutParams(
                    0,
                    LayoutParams.MATCH_PARENT,
                    1f,
                ),
            )
            destinationViews[destination] = itemView
        }

        // 重建后刷新选中态：若当前选中 tab 仍在可见列表中则保持高亮，
        // 否则清除高亮，等待上层通过 setCurrentDestination 下发新的选中项
        // （选中态的真相源在 Compose 层，View 层不擅自切换）。
        if (selectedDestination in destinationViews) {
            selectDestination(selectedDestination, notify = false, animate = false)
        } else {
            suppressSelectionCallback = true
            destinationViews.values.forEach { it.setChecked(false, false) }
            suppressSelectionCallback = false
        }
    }

    private fun selectDestination(
        destination: MusicDestination,
        notify: Boolean,
        animate: Boolean,
    ) {
        val selectedView = destinationViews[destination] ?: return
        val changed = selectedDestination != destination
        selectedDestination = destination
        suppressSelectionCallback = true
        destinationViews.forEach { (candidate, view) ->
            view.setChecked(candidate == destination, animate)
        }
        suppressSelectionCallback = false
        if (notify && changed && selectedView.isChecked) {
            onDestinationSelected?.invoke(destination)
        }
    }

    private fun MusicDestination.tabIconSelectorRes(): Int {
        return when (this) {
            MusicDestination.Playlist -> R.drawable.tabbar_playlist_selector
            MusicDestination.CloudMusic -> R.drawable.tabbar_cloud_music_selector
            MusicDestination.Artist -> R.drawable.tabbar_artist_selector
            MusicDestination.Album -> R.drawable.tabbar_album_selector
            MusicDestination.Songs -> R.drawable.tabbar_song_selector
            MusicDestination.More -> R.drawable.tabbar_more_selector
        }
    }
}

private class BottomTabItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), Checkable {
    private var imageView: ImageView? = null
    private var textView: TextView? = null
    private var checked = false
    private var broadcasting = false
    private var onCheckedChangeListener: ((BottomTabItemView, Boolean) -> Unit)? = null

    var text: CharSequence = ""
        set(value) {
            field = value
            getTextViewOrCreate().text = value
        }

    init {
        gravity = Gravity.CENTER
        orientation = VERTICAL
        isClickable = true
    }

    override fun isChecked(): Boolean = checked

    override fun setChecked(checked: Boolean) {
        setChecked(checked, animate = true)
    }

    fun setChecked(checked: Boolean, animate: Boolean) {
        val changed = this.checked != checked
        this.checked = checked
        isSelected = checked
        isActivated = checked
        refreshDrawableState()
        imageView?.refreshDrawableState()
        textView?.refreshDrawableState()
        if (changed || !animate) {
            updateContentScale(animate)
        }
        if (!changed || broadcasting) {
            return
        }
        broadcasting = true
        onCheckedChangeListener?.invoke(this, this.checked)
        broadcasting = false
    }

    override fun toggle() {
        if (!checked) {
            isChecked = true
        }
    }

    override fun performClick(): Boolean {
        toggle()
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animateContentScale(PressedScale, TouchScaleDurationMs)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> updateContentScale(animate = true)
        }
        return handled
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val state = super.onCreateDrawableState(extraSpace + 1)
        if (checked) {
            mergeDrawableStates(state, CheckedStateSet)
        }
        return state
    }

    fun setDrawableResource(drawableRes: Int) {
        getImageViewOrCreate().setImageResource(drawableRes)
    }

    fun setTextColor(colorStateList: android.content.res.ColorStateList) {
        getTextViewOrCreate().setTextColor(colorStateList)
    }

    fun setOnCheckedChangeListener(listener: ((BottomTabItemView, Boolean) -> Unit)?) {
        onCheckedChangeListener = listener
    }

    private fun getImageViewOrCreate(): ImageView {
        imageView?.let { return it }
        return ImageView(context).also { view ->
            imageView = view
            view.setDuplicateParentStateEnabled(true)
            view.scaleX = NormalScale
            view.scaleY = NormalScale
            addView(view, generateDefaultLayoutParams())
        }
    }

    private fun getTextViewOrCreate(): TextView {
        textView?.let { return it }
        getImageViewOrCreate()
        return TextView(context).also { view ->
            textView = view
            view.setDuplicateParentStateEnabled(true)
            view.ellipsize = TextUtils.TruncateAt.END
            view.gravity = Gravity.CENTER_HORIZONTAL
            view.setSingleLine()
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            view.typeface = Typeface.DEFAULT_BOLD
            view.scaleX = NormalScale
            view.scaleY = NormalScale
            val topMargin = resources.getDimensionPixelSize(
                R.dimen.smartisan_switch_bar_drawablePadding,
            )
            addView(
                view,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    this.topMargin = topMargin
                },
            )
        }
    }

    private fun updateContentScale(animate: Boolean) {
        val target = if (checked) SelectedScale else NormalScale
        if (animate) {
            animateContentScale(target, SelectScaleDurationMs)
        } else {
            imageView?.animate()?.cancel()
            textView?.animate()?.cancel()
            imageView?.scaleX = target
            imageView?.scaleY = target
            textView?.scaleX = target
            textView?.scaleY = target
        }
    }

    private fun animateContentScale(target: Float, durationMs: Long) {
        imageView?.animate()?.cancel()
        textView?.animate()?.cancel()
        imageView?.animate()
            ?.scaleX(target)
            ?.scaleY(target)
            ?.setDuration(durationMs)
            ?.start()
        textView?.animate()
            ?.scaleX(target)
            ?.scaleY(target)
            ?.setDuration(durationMs)
            ?.start()
    }

    companion object {
        private val CheckedStateSet = intArrayOf(android.R.attr.state_checked)
        private const val NormalScale = 0.9f
        private const val SelectedScale = 1.0f
        private const val PressedScale = 1.1f
        private const val TouchScaleDurationMs = 120L
        private const val SelectScaleDurationMs = 200L
    }
}
