package momoi.mod.qqpro.lib.material

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import momoi.mod.qqpro.lib.dp

/**
 * Reusable Material 3 (dark) building blocks for QQPro's Kotlin-DSL UI (the project uses no XML).
 *
 * Centralized here — not inlined per feature — so every redesigned screen shares one palette and one
 * set of widgets ([MaterialIconButton], [searchField]). The palette mirrors [momoi.mod.qqpro.hook.MainNav]
 * so the home nav and any new bars read as one system. All classes are public on purpose (a @Mixin
 * body that references a helper needs it public, or it hits IllegalAccessError at runtime).
 */
object M3 {
    val ACCENT = 0xFF_4FC3F7.toInt()              // primary / selected icon
    val TONAL = 0x33_4FC3F7                        // tonal container (button background)
    val ON_SURFACE_VARIANT = 0xFF_C9C7CE.toInt()   // inactive icon/text
    val BADGE = 0xFF_E5443C.toInt()
    val SURFACE = 0xFF_222222.toInt()              // field / sunken surface
    val HINT = 0xFF_777777.toInt()
    val ON_SURFACE = 0xFF_FFFFFF.toInt()

    /** A rounded, sunken M3 search/text field. */
    fun searchField(ctx: Context, hint: String): EditText = EditText(ctx).apply {
        this.hint = hint
        setHintTextColor(HINT)
        setTextColor(ON_SURFACE)
        textSize = 13f
        setSingleLine()
        setPadding(12.dp, 6.dp, 12.dp, 6.dp)
        background = GradientDrawable().apply {
            setColor(SURFACE)
            cornerRadius = 12.dp.toFloat()
        }
    }
}

/**
 * A circular tonal Material icon button with an optional top-end unread badge. Self-contained
 * (no XML, no Material lib dependency — the watch theme isn't MaterialComponents-based, so a real
 * com.google.android.material button would crash). Reuse for any toolbar/action-bar action.
 */
class MaterialIconButton(ctx: Context) : FrameLayout(ctx) {
    private val icon = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val badge = TextView(ctx).apply {
        setTextColor(0xFF_FFFFFF.toInt())
        textSize = 8f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 9999f; setColor(M3.BADGE)
        }
        setPadding(3.dp, 0, 3.dp, 0)
        minWidth = 12.dp
        visibility = View.GONE
    }

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        // No container/tint by default: many app icons are already full-color round badges, so a
        // tonal circle behind them and an accent tint just collapse them into blobs. Opt in via
        // [setTonalContainer] / [setIconTint] for monochrome icons that need it.
        addView(icon, LayoutParams(22.dp, 22.dp, Gravity.CENTER))
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, 13.dp, Gravity.TOP or Gravity.END))
    }

    /** Set the icon from a resource name (app resources aren't on the compile-time R). */
    fun setIconResName(name: String) {
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) runCatching { icon.setImageResource(id) }
    }

    /** Set the icon from a drawable (e.g. a self-drawn [MaterialIcon]). */
    fun setIcon(drawable: android.graphics.drawable.Drawable) = icon.setImageDrawable(drawable)

    fun setIconTint(color: Int) = icon.setColorFilter(color)

    /** Enable the M3 tonal circular container behind the icon (for monochrome/line icons). */
    fun setTonalContainer() {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(M3.TONAL) }
    }

    /** Show/hide the unread badge ("99+" cap); count <= 0 hides it. */
    fun setBadgeCount(count: Int) {
        if (count > 0) {
            badge.text = if (count > 99) "99+" else count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }
}
