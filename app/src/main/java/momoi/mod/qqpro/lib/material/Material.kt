package momoi.mod.qqpro.lib.material

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
 * set of widgets ([MaterialIconButton], [searchField], [M3Button], [M3Card], [M3ListItem], [M3Dialog]).
 * The palette mirrors [momoi.mod.qqpro.hook.MainNav] so the home nav and any new bars read as one system.
 *
 * This is the SINGLE SOURCE OF TRUTH for app colors: the legacy [momoi.mod.qqpro.Colors] object now
 * aliases these tokens, so retheming = editing the token values here. Tokens are named by MD3 role.
 * All classes are public on purpose (a @Mixin body that references a helper needs it public, or it hits
 * IllegalAccessError at runtime).
 */
object M3 {
    // ── Color tokens (all user-overridable) ──────────────────────────────────────
    // Every MD3 role below resolves LIVE from its own Settings color pref (a hex string). A blank
    // pref falls back to the built-in default (and for the primary-derived tokens, to the value
    // auto-derived from the accent). Editing a token in the 外观主题 settings category rethemes every
    // materialized screen the next time it's built. The single source of truth is here.
    // Material Blue 200 — the MD3-recommended (softer, less-saturated) accent tone for dark themes.
    val DEFAULT_PRIMARY = 0xFF_90CAF9.toInt()

    val primary: Int get() = parseColor(momoi.mod.qqpro.Settings.themeColor.value, DEFAULT_PRIMARY)
    /**
     * Label/icon color on a filled primary surface. Blank pref = auto: white on a normal accent (the
     * right look for a dark UI); only a very LIGHT accent (e.g. pale yellow) flips to a dark tone for
     * contrast. Avoids the near-black text a blanket darken() produced on mid-bright accents.
     */
    val onPrimary: Int get() = parseColorOrNull(momoi.mod.qqpro.Settings.themeOnPrimary.value)
        ?: if (luminance(primary) > 0.72f) darken(primary, 0.78f) else 0xFF_FFFFFF.toInt()
    /** Translucent tonal container; blank pref = auto (the accent at 0x33 alpha). */
    val primaryContainer: Int get() = parseColorOrNull(momoi.mod.qqpro.Settings.themePrimaryContainer.value)
        ?: ((primary and 0x00FFFFFF) or 0x33_000000)
    val onPrimaryContainer: Int get() = parseColorOrNull(momoi.mod.qqpro.Settings.themeOnPrimaryContainer.value)
        ?: primary

    // ── Surfaces ───────────────────────────────────────────────────────────────
    val surface: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurface.value, 0xFF_1A1A1A.toInt())              // screen background
    val surfaceContainer: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurfaceContainer.value, 0xFF_222222.toInt())     // field / sunken surface / card
    val surfaceContainerHigh: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurfaceContainerHigh.value, 0xFF_2A2A2A.toInt()) // raised card / row pressed
    val surfaceVariant: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurfaceVariant.value, 0xFF_2E2E2E.toInt())

    // ── Content ────────────────────────────────────────────────────────────────
    val onSurface: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOnSurface.value, 0xFF_FFFFFF.toInt())
    val onSurfaceVariant: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOnSurfaceVariant.value, 0xFF_C9C7CE.toInt())     // inactive icon/text
    val onSurfaceTip: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOnSurfaceTip.value, 0xFF_999999.toInt())         // secondary/hint text
    val hint: Int get() = parseColor(momoi.mod.qqpro.Settings.themeHint.value, 0xFF_777777.toInt())
    val outline: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOutline.value, 0xFF_444444.toInt())
    val outlineVariant: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOutlineVariant.value, 0xFF_333333.toInt())

    // ── Status ─────────────────────────────────────────────────────────────────
    val error: Int get() = parseColor(momoi.mod.qqpro.Settings.themeError.value, 0xFF_E5443C.toInt())
    val badge: Int get() = error                   // unread/notification badge follows the error token
    val legacyBtn = 0xFF_1B9AF7.toInt()            // older link/button blue (Colors.btn)

    // ── Chat tokens (referenced by Colors aliases; values must stay exact) ───────
    val replyText = 0xFF_DDDDDD.toInt()
    val replyBackground = 0x22_FFFFFF
    val atMe = 0xFF_F74C30.toInt()

    object NickTag {
        val specialBg = 0xFF_30263F.toInt(); val specialText = 0xFF_BB87F7.toInt()
        val normalBg = 0xFF_2E2E2E.toInt();  val normalText = 0xFF_9E9E9E.toInt()
        val adminBg = 0xFF_0E2D41.toInt();   val adminText = 0xFF_0088EE.toInt()
        val ownerBg = 0xFF_412917.toInt();   val ownerText = 0xFF_FF8D40.toInt()
    }

    // ── Shape / dimens ───────────────────────────────────────────────────────────
    val radiusSm get() = 8.dp.toFloat()
    val radiusMd get() = 12.dp.toFloat()
    val radiusLg get() = 16.dp.toFloat()
    val radiusPill = 9999f

    // ── Deprecated aliases (kept so existing call sites keep compiling) ──────────
    val ACCENT get() = primary
    val TONAL get() = primaryContainer
    val ON_SURFACE_VARIANT get() = onSurfaceVariant
    val BADGE get() = badge
    val SURFACE get() = surfaceContainer
    val HINT get() = hint
    val ON_SURFACE get() = onSurface

    /**
     * Parse a hex color string (`#RRGGBB`, `RRGGBB`, `#AARRGGBB` or `AARRGGBB`, with optional spaces);
     * a parse without an alpha component is treated as fully opaque. Returns [fallback] when blank/invalid.
     */
    fun parseColor(raw: String?, fallback: Int): Int = parseColorOrNull(raw) ?: fallback

    /** Like [parseColor] but returns null (not a fallback) when the string is blank/invalid. */
    fun parseColorOrNull(raw: String?): Int? {
        val s = raw?.trim()?.removePrefix("#")?.replace(" ", "") ?: return null
        if (s.length != 6 && s.length != 8) return null
        val v = s.toLongOrNull(16) ?: return null
        return if (s.length == 6) (0xFF000000.toInt() or v.toInt()) else v.toInt()
    }

    /**
     * Best-contrast label color (near-black or white) for content drawn ON a filled [bg] of an
     * arbitrary color. Use for any button/badge whose background isn't a fixed token — e.g. the
     * accent (which is now a light tone by default), error red, or a status color — so the label
     * stays legible regardless of theme. (For a background that is exactly [primary], [onPrimary] is
     * the equivalent purpose-built token.)
     */
    fun onColor(bg: Int): Int = if (luminance(bg) > 0.6f) 0xFF_111111.toInt() else 0xFF_FFFFFF.toInt()

    /** Perceived luminance of [color] in 0..1 (ignores alpha). */
    fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Blend a color toward black by [amount] (0 = unchanged, 1 = black); alpha preserved. */
    fun darken(color: Int, amount: Float): Int {
        val k = (1f - amount).coerceIn(0f, 1f)
        val a = color ushr 24 and 0xFF
        val r = ((color shr 16 and 0xFF) * k).toInt()
        val g = ((color shr 8 and 0xFF) * k).toInt()
        val b = ((color and 0xFF) * k).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** A solid rounded rect drawable. */
    fun rounded(color: Int, radius: Float = radiusMd): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    /** A stroked (outlined) rounded rect drawable. */
    fun outlined(strokeColor: Int, radius: Float = radiusMd, strokeWidthDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(0)
            cornerRadius = radius
            setStroke(strokeWidthDp.dp, strokeColor)
        }

    /**
     * Wrap a content drawable with an M3 ripple (pressed feedback). The mask BOUNDS the ripple: when
     * no [content] is given we still pass an opaque mask so the ripple is clipped to the view's bounds
     * instead of spilling out as an unbounded full circle.
     */
    fun ripple(content: Drawable?, rippleColor: Int = 0x33_FFFFFF): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, content ?: ColorDrawable(Color.WHITE))

    /** A rounded, sunken M3 search/text field. */
    fun searchField(ctx: Context, hint: String): EditText = EditText(ctx).apply {
        this.hint = hint
        setHintTextColor(M3.hint)
        setTextColor(onSurface)
        textSize = 13f
        setSingleLine()
        setPadding(12.dp, 6.dp, 12.dp, 6.dp)
        background = rounded(surfaceContainer, radiusMd)
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
