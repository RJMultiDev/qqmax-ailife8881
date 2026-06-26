package momoi.mod.qqpro.lib.material

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.LinearScope
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.vertical

/**
 * Phone-style Material 3 dialog scaffold.
 *
 * Sized for a phone-class screen:
 *  - 24dp outer padding around the dialog body
 *  - 22sp dialog title (MD3 headlineSmall)
 *  - actions row is HORIZONTAL, right-aligned (Material 3 phone dialog spec: action buttons sit on
 *    the bottom-right, with the affirmative action on the rightmost side), background = surfaceContainer
 *    so the action area visually separates from the content body
 *
 * Usage inside a fragment's onCreateView / an Activity's onCreate:
 *
 *     return M3Dialog(ctx).title("打开链接").body {
 *         add<TextView>().text(url)...
 *     }.actions {
 *         action("取消", M3Button.Variant.TEXT) { dismiss() }
 *         action("浏览器打开", M3Button.Variant.FILLED) { ...; dismiss() }
 *     }
 *
 * Public on purpose (referenced from @Mixin fragment bodies).
 */
class M3Dialog(ctx: Context) : ScrollView(ctx) {

    private val root = LinearLayout(ctx).vertical().apply {
        gravity = Gravity.CENTER
        // 24dp outer padding — MD3 dialog spec.
        setPadding(24.dp, 24.dp, 24.dp, 24.dp)
    }
    private val titleView = TextView(ctx).apply {
        // 22sp dialog title per MD3.
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textTitle)
        setTextColor(M3.onSurface)
        gravity = Gravity.START
        setPadding(0, 0, 0, 16.dp)
        visibility = View.GONE
    }
    /** Action area background (surfaceContainer) so it visually separates from the content body. */
    private val actionsBg = LinearLayout(ctx).vertical().apply {
        // Match MD3 dialog action strip: the actions sit on a slightly raised surface strip.
        gravity = Gravity.END
        setPadding(16.dp, 8.dp, 16.dp, 8.dp)
        setBackgroundColor(M3.surfaceContainer)
    }
    /** Action button row — HORIZONTAL on phone-class dialogs, right-aligned. */
    private val actionsRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    private var actionsAttached = false

    init {
        isFillViewport = true
        setBackgroundColor(M3.surface)
        root.addView(titleView, LinearLayout.LayoutParams(FILL, WRAP))
        addView(root, LayoutParams(FILL, WRAP))
    }

    fun title(text: CharSequence): M3Dialog = apply {
        titleView.text = text; titleView.visibility = View.VISIBLE
    }

    /** Build the dialog body; children are appended below the title (call before [actions]). */
    fun body(block: LinearScope.() -> Unit): M3Dialog = apply { LinearScope(root).block() }

    /** Build the trailing action button row (appended last, horizontally arranged on phone). */
    fun actions(block: M3ActionScope.() -> Unit): M3Dialog = apply {
        if (!actionsAttached) {
            actionsBg.addView(actionsRow, LinearLayout.LayoutParams(FILL, WRAP))
            root.addView(actionsBg, LinearLayout.LayoutParams(FILL, WRAP))
            actionsAttached = true
        }
        M3ActionScope(actionsRow).block()
    }
}

/** DSL scope for [M3Button] actions inside an [M3Dialog]. Buttons are arranged HORIZONTALLY
 *  (phone-class Material 3 dialog) with the affirmative action on the right. Each button has 8dp
 *  left margin to keep them visually spaced. */
class M3ActionScope(private val container: LinearLayout) {
    fun action(label: CharSequence, variant: M3Button.Variant = M3Button.Variant.FILLED, onClick: () -> Unit): M3Button {
        val btn = M3Button(container.context).variant(variant).apply {
            text = label
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.leftMargin = 8.dp
        container.addView(btn, lp)
        return btn
    }
}