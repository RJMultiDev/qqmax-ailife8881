package momoi.mod.qqpro.lib.material

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.dp

/**
 * Phone-style Material 3 top app bar.
 *
 * A ~50dp-tall horizontal bar with a leading navigation icon (back arrow by default),
 * a title (with optional subtitle below, auto-shrinking if it overflows), and a right-aligned
 * action slot. Defaults to a flat surface-color background; pass [setAppBarElevation] for a
 * soft shadow. All touch targets are sized to the Material 3 minimum of [M3.touchTargetMin]
 * (48dp).
 *
 *     val bar = AppBar(ctx).apply {
 *         setTitle("设置")
 *         setSubtitle("12 项")
 *         setOnNavClick { finish() }
 *         addAction(MaterialSymbol(MaterialSymbols.search, M3.onSurface)) { /* search */ }
 *     }
 *     container.addView(bar, LinearLayout.LayoutParams(FILL, M3.appBarHeight))
 *
 * Public on purpose (a @Mixin body referencing it needs it public).
 */
class AppBar(ctx: Context) : LinearLayout(ctx) {

    // Back-button icon (arrow_back) shown by default. Call [setNavVisible(false)] to hide
    // on screens that don't navigate back (e.g. the root tab).
    private val navButton = MaterialIconButton(ctx).apply {
        setIcon(MaterialSymbol(MaterialSymbols.arrow_back, M3.onSurface))
        setOnClickListener {
            // ComponentActivity.onBackPressedDispatcher only available in AndroidX, but the
            // host activity is a plain Activity subclass. Use the legacy onBackPressed fallback.
            val act = (context as? Activity) ?: return@setOnClickListener
            @Suppress("DEPRECATION") act.onBackPressed()
        }
    }

    private val titleView = TextView(ctx).apply {
        setTextColor(M3.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textTitle)
        typeface = Typeface.DEFAULT_BOLD
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        // Phone QQ (AioTitleLayout) shrinks the title text to 75% when ellipsis starts at
        // position 0 (i.e. the whole string is clipped). We replicate that here: when the
        // measure pass finds an ellipsis at the very start, we drop the text size once. This
        // avoids the watch-style "..." truncation looking bad on wide phone screens.
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val layout = layout ?: return@addOnLayoutChangeListener
            if (layout.lineCount > 0) {
                val ellipsisStart = layout.getEllipsisStart(0)
                val ellipsisCount = layout.getEllipsisCount(0)
                if (ellipsisCount > 0 && ellipsisStart == 0 && layout.width > 0) {
                    // Text is fully ellipsized — shrink to 75% (matches QQ's AioTitleLayout).
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textTitle * 0.75f)
                }
            }
        }
    }

    private val subtitleView = TextView(ctx).apply {
        setTextColor(M3.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textCaption)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        visibility = GONE
    }

    private val actionsRow = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
    }

    private val titleColumn = LinearLayout(ctx).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(M3.surface)
        // The nav button is always inserted first so the title column has a known left edge to
        // align against. Width is the touch target (48dp).
        addView(navButton, LayoutParams(M3.touchTargetMin, M3.touchTargetMin))

        titleColumn.addView(titleView, LayoutParams(0, WRAP, 1f))
        titleColumn.addView(subtitleView, LayoutParams(0, WRAP, 1f))
        addView(titleColumn, LayoutParams(0, WRAP, 1f).apply {
            leftMargin = 4.dp
            rightMargin = 4.dp
        })

        // Trailing actions go on the right; each is added via [addAction].
        addView(actionsRow, LayoutParams(WRAP, M3.touchTargetMin))
    }

    /** Set the app-bar title text. */
    fun setTitle(text: CharSequence) {
        titleView.text = text
        // Reset text size to the default after setting new text — the auto-shrink layout
        // listener will re-apply the shrink if the new title also overflows.
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textTitle)
    }

    /** Optional supporting text shown below the title in a smaller, secondary color. */
    fun setSubtitle(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            subtitleView.visibility = GONE
        } else {
            subtitleView.text = text
            subtitleView.visibility = VISIBLE
        }
    }

    /** Set the leading navigation icon (typically an arrow-back glyph). */
    fun setNavIcon(drawable: Drawable) {
        navButton.setIcon(drawable)
        navButton.visibility = VISIBLE
    }

    /** Show or hide the leading nav button entirely (e.g. on a root tab screen). */
    fun setNavVisible(visible: Boolean) {
        navButton.visibility = if (visible) VISIBLE else GONE
    }

    /** Wire a click listener for the leading nav icon. Pass null to restore the default
     *  (onBackPressed on the hosting Activity). */
    fun setOnNavClick(onClick: (() -> Unit)?) {
        if (onClick == null) {
            navButton.setOnClickListener {
                val act = (context as? Activity) ?: return@setOnClickListener
                @Suppress("DEPRECATION") act.onBackPressed()
            }
        } else {
            navButton.setOnClickListener { onClick() }
        }
    }

    /** Add an action icon to the trailing slot. Each action is sized to the 48dp touch target. */
    fun addAction(drawable: Drawable, onClick: () -> Unit): MaterialIconButton {
        val btn = MaterialIconButton(context).apply {
            setIcon(drawable)
            setOnClickListener { onClick() }
        }
        actionsRow.addView(btn, LayoutParams(M3.touchTargetMin, M3.touchTargetMin))
        return btn
    }

    /** Drop all trailing actions. */
    fun clearActions() {
        actionsRow.removeAllViews()
    }

    /** Apply a soft elevation shadow (M3 flat top app bar by default has no shadow). */
    @Suppress("unused")
    fun setAppBarElevation(elevation: Float) {
        if (elevation > 0f) {
            this.elevation = elevation
        }
    }
}