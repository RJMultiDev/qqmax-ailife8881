package momoi.mod.qqpro.lib.material

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.dp

/**
 * A phone-style Material 3 bottom navigation bar.
 *
 * 72dp tall (M3 spec for phones), surface-container background, holds an even-spaced row of items
 * (icon + label, with an optional unread badge in the top-end corner). Each item has a 48dp touch
 * target. The selected item gets:
 *  - a 3dp primary pill above the icon (M3's "active indicator")
 *  - icon + label tinted in [M3.primary]
 *
 * Unselected items use [M3.onSurfaceVariant] for both icon and label.
 *
 *     val nav = BottomNavigationView(ctx, listOf(
 *         BottomNavItem(MaterialSymbol(MaterialSymbols.chat_bubble, M3.primary), "消息", badgeCount = 3),
 *         BottomNavItem(MaterialSymbol(MaterialSymbols.person, M3.onSurfaceVariant), "联系人"),
 *         BottomNavItem(MaterialSymbol(MaterialSymbols.settings, M3.onSurfaceVariant), "设置"),
 *     ), selectedIndex = 0) { index -> /* navigate to page */ }
 *     container.addView(nav, LinearLayout.LayoutParams(FILL, M3.bottomNavHeight))
 *
 * Public on purpose (a @Mixin body referencing it needs it public).
 */
class BottomNavigationView(
    ctx: Context,
    items: List<BottomNavItem>,
    initialSelected: Int = 0,
    private val onSelect: (Int) -> Unit = {},
) : LinearLayout(ctx) {

    private val itemViews = mutableListOf<ItemView>()

    private var selectedIndex: Int = initialSelected.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(M3.surfaceContainer)
        // A soft top shadow (M3 nav bar lifts off the content when used over scrolling content).
        elevation = 3f
        layoutParams = LayoutParams(FILL, M3.bottomNavHeight)
        // Padding 8dp horizontal so each item's 48dp touch target has a small breathing space.
        setPaddingHorizontal(8.dp)

        items.forEachIndexed { index, item ->
            val v = ItemView(ctx, item, index == selectedIndex, index) { tappedIndex ->
                setSelected(tappedIndex)
                onSelect(tappedIndex)
            }
            itemViews.add(v)
            addView(v, LayoutParams(0, FILL, 1f))
        }
    }

    /** Programmatic select (e.g. when the underlying page changes externally). */
    fun setSelected(index: Int) {
        val safe = index.coerceIn(0, (itemViews.size - 1).coerceAtLeast(0))
        if (safe == selectedIndex && itemViews.isNotEmpty()) return
        itemViews.forEachIndexed { i, v -> v.setSelectedInternal(i == safe) }
        selectedIndex = safe
    }

    /** Update the badge count on the item at [index] (0 hides the badge). */
    fun setBadge(index: Int, count: Int) {
        itemViews.getOrNull(index)?.setBadge(count)
    }

    // Helper for the horizontal padding shorthand (we don't want a full LinearLayout import alias).
    private fun setPaddingHorizontal(value: Int) {
        setPadding(value, paddingTop, value, paddingBottom)
    }

    /**
     * One item cell in the bottom navigation row: indicator + icon (with optional badge) + label.
     */
    private inner class ItemView(
        ctx: Context,
        val item: BottomNavItem,
        selected: Boolean,
        val itemIndex: Int,
        private val onTap: (Int) -> Unit,
    ) : LinearLayout(ctx) {

        private val indicator = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3.dp.toFloat()
                setColor(0x00000000)
            }
            // Width fills the cell less 16dp padding; 3dp tall = the M3 active-indicator height.
            layoutParams = LayoutParams(0, M3.bottomNavIndicatorHeight).apply {
                leftMargin = 16.dp; rightMargin = 16.dp
            }
        }

        private val iconWrap = FrameLayout(ctx).apply {
            val sz = M3.touchTargetMin
            layoutParams = LayoutParams(sz, sz)
        }

        private val iconView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(item.icon)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                M3.bottomNavIconSize,
                M3.bottomNavIconSize,
                Gravity.CENTER,
            )
        }

        private val badgeView = TextView(ctx).apply {
            setTextColor(0xFF_FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textCaption - 2f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 9999f
                setColor(M3.badge)
            }
            setPadding(4.dp, 0, 4.dp, 0)
            minWidth = 14.dp
            visibility = View.GONE
            layoutParams = android.widget.FrameLayout.LayoutParams(
                WRAP, 14.dp,
                Gravity.TOP or Gravity.END,
            )
        }

        private val labelView = TextView(ctx).apply {
            text = item.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textNavLabel)
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(WRAP, WRAP).apply {
                topMargin = 2.dp
            }
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = M3.ripple(null)

            iconWrap.addView(iconView)
            iconWrap.addView(badgeView)

            addView(indicator)
            addView(iconWrap)
            addView(labelView)

            setBadge(item.badgeCount)
            setSelectedInternal(selected)

            setOnClickListener { onTap(itemIndex) }
        }

        fun setSelectedInternal(selected: Boolean) {
            // Indicator color: primary when selected, fully transparent when not.
            (indicator.background as? GradientDrawable)?.setColor(
                if (selected) M3.primary else 0x00000000,
            )
            val color = if (selected) M3.primary else M3.onSurfaceVariant
            labelView.setTextColor(color)
            iconView.setColorFilter(color)
            // Make the icon state reflect selection so Material tinted drawables stay coherent.
            iconView.isSelected = selected
        }

        fun setBadge(count: Int) {
            if (count > 0) {
                badgeView.text = if (count > 99) "99+" else count.toString()
                badgeView.visibility = View.VISIBLE
            } else {
                badgeView.visibility = View.GONE
            }
        }
    }
}

/**
 * Description of one bottom-nav cell.
 *
 * @param icon drawable shown in the cell (typically a [MaterialSymbol]). Color filter is applied by the
 *             nav bar based on selection, so a neutral / outline icon is recommended.
 * @param label short label (1–2 characters of Chinese fits comfortably; English labels up to 8 chars).
 * @param badgeCount unread / notification count; 0 hides the badge. Capped at "99+".
 */
class BottomNavItem(
    val icon: Drawable,
    val label: CharSequence,
    val badgeCount: Int = 0,
)