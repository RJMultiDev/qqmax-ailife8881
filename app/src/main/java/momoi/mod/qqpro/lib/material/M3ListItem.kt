package momoi.mod.qqpro.lib.material

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import momoi.mod.qqpro.lib.dp

/**
 * A Material 3 list row, sized for phone-class screens:
 *  - minimum height 56dp (one-line), 72dp (two-line), 88dp (three-line)
 *  - horizontal padding 16dp, vertical padding 8dp (compact but with breathing space)
 *  - title 16sp (bodyLarge in MD3 type scale), subtitle 14sp (bodyMedium)
 *  - leading slot defaults to a 40dp avatar (square)
 *  - trailing slot defaults to 16dp left margin so it never crowds the title
 *
 * Optional 3rd line "overline" (12sp, onSurfaceVariant) — used by rich list rows.
 *
 *     M3ListItem(ctx).leading(avatar).title("Alice").subtitle("online").trailing(switch)
 *
 * Public on purpose (a @Mixin body referencing it needs it public).
 */
class M3ListItem(ctx: Context) : LinearLayout(ctx) {

    private val textCol = LinearLayout(ctx).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
    private val overlineView = TextView(ctx).apply {
        setTextColor(M3.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textCaption)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    private val titleView = TextView(ctx).apply {
        setTextColor(M3.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textBody)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private val subtitleView = TextView(ctx).apply {
        setTextColor(M3.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textSubtitle)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    private var leadingView: View? = null
    private var trailingView: View? = null
    private var minHeightOverride: Int = M3.listItemMinHeight

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Phone-class padding: 16dp horizontal, 8dp vertical. Material 3 list items have generous
        // horizontal gutters so titles never feel cramped against the screen edge.
        setPadding(16.dp, 8.dp, 16.dp, 8.dp)
        // Minimum 56dp height so a one-line row still meets the M3 list-item spec.
        minHeight = M3.listItemMinHeight
        background = M3.ripple(null)
        textCol.addView(overlineView)
        textCol.addView(titleView)
        textCol.addView(subtitleView)
        addView(textCol, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun title(text: CharSequence): M3ListItem = apply { titleView.text = text }

    fun subtitle(text: CharSequence?): M3ListItem = apply {
        if (text.isNullOrEmpty()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.text = text
            subtitleView.visibility = View.VISIBLE
            // Two-line row → 72dp; with overline it'd be 88dp (set via [overline]).
            if (overlineView.visibility != View.VISIBLE) minHeight = M3.listItemTwoLine
        }
    }

    /** Optional small line above the title — typically a timestamp, category tag, or unread count. */
    fun overline(text: CharSequence?): M3ListItem = apply {
        if (text.isNullOrEmpty()) {
            overlineView.visibility = View.GONE
        } else {
            overlineView.text = text
            overlineView.visibility = View.VISIBLE
            // Three-line row → 88dp (overline + title + subtitle). With a single-line title the
            // 72dp default still reads fine; we bump to 88 only when both overline and subtitle are
            // present.
            if (subtitleView.visibility == View.VISIBLE) minHeight = M3.listItemThreeLine
        }
    }

    /** Place a leading view (e.g. avatar) at the start; [sizeDp] is its square size (0 = wrap). */
    fun leading(view: View, sizeDp: Int = 40): M3ListItem = apply {
        leadingView?.let { removeView(it) }
        leadingView = view
        val lp = if (sizeDp > 0) LayoutParams(sizeDp.dp, sizeDp.dp) else LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.rightMargin = 16.dp
        addView(view, 0, lp)
    }

    /** Place a trailing view (e.g. switch / chevron / badge) at the end. */
    fun trailing(view: View): M3ListItem = apply {
        trailingView?.let { removeView(it) }
        trailingView = view
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        // M3 spec: 16dp gap between trailing control and the title column (vs. the 8dp of older
        // compact lists). Keeps tappable rows from looking crowded.
        lp.leftMargin = 16.dp
        addView(view, lp)
    }

    /** Override the minimum row height (defaults to 56dp). Useful for compact secondary lists. */
    fun rowHeight(dp: Int): M3ListItem = apply { minHeightOverride = dp.dp; minHeight = dp.dp }
}