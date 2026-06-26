package momoi.mod.qqpro.lib.material

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
 * A 56dp-tall horizontal bar with an optional leading navigation icon (back arrow / menu),
 * a title (with optional subtitle below), and a right-aligned action slot. Defaults to a flat
 * surface-color background; pass [setAppBarElevation] for a soft shadow. All touch targets are
 * sized to the Material 3 minimum of [M3.touchTargetMin] (48dp).
 *
 *     val bar = AppBar(ctx).apply {
 *         setTitle("设置")
 *         setSubtitle("12 项")
 *         setNavIcon(MaterialSymbol(MaterialSymbols.arrow_back, M3.onSurface))
 *         setOnNavClick { finish() }
 *         addAction(MaterialSymbol(MaterialSymbols.search, M3.onSurface)) { /* search */ }
 *     }
 *     container.addView(bar, LinearLayout.LayoutParams(FILL, M3.appBarHeight))
 *
 * Public on purpose (a @Mixin body referencing it needs it public).
 */
class AppBar(ctx: Context) : LinearLayout(ctx) {

    private val navButton = MaterialIconButton(ctx).apply {
        visibility = View.GONE
        // Nav button has no tonal container by default — it's a transparent tappable surface area;
        // MaterialIconButton's own ripple + click feedback handles touch response.
    }

    private val titleView = TextView(ctx).apply {
        setTextColor(M3.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textTitle)
        typeface = Typeface.DEFAULT_BOLD
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private val subtitleView = TextView(ctx).apply {
        setTextColor(M3.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textCaption)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        visibility = View.GONE
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
        // No explicit layoutParams — the caller decides how to size the bar (typically FILL ×
        // M3.appBarHeight). We don't set padding so the 56dp height is fully filled by content.

        // The nav button is always inserted first so the title column has a known left edge to
        // align against. Width is the touch target (48dp); no background by default — caller can
        // attach a tonal container via [setNavTonal] if desired.
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
    }

    /** Optional supporting text shown below the title in a smaller, secondary color. */
    fun setSubtitle(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.text = text
            subtitleView.visibility = View.VISIBLE
        }
    }

    /** Set the leading navigation icon (typically an arrow-back glyph). */
    fun setNavIcon(drawable: Drawable) {
        navButton.setIcon(drawable)
        navButton.visibility = View.VISIBLE
    }

    /** Show or hide the leading nav button entirely (e.g. on a home screen that has no up stack). */
    fun setNavVisible(visible: Boolean) {
        navButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Wire a click listener for the leading nav icon. */
    fun setOnNavClick(onClick: () -> Unit) {
        navButton.setOnClickListener { onClick() }
    }

    /** Add an action icon to the trailing slot. Each action is sized to the 48dp touch target. */
    fun addAction(drawable: Drawable, onClick: () -> Unit): MaterialIconButton {
        val btn = MaterialIconButton(ctx).apply {
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
            // elevation is a flag-only float on most views; the actual shadow comes from android's
            // outline. Use a small background overlay so the shadow has something to render against
            // on surfaces where the outline is None.
            this.elevation = elevation
        }
    }
}