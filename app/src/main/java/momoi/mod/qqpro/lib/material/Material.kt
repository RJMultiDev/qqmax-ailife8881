package momoi.mod.qqpro.lib.material

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
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
 * set of widgets ([MaterialIconButton], [searchField], [M3Button], [M3Card], [M3ListItem], [M3Dialog],
 * [AppBar], [BottomNavigationView], [FloatingActionButton]). The palette mirrors
 * [momoi.mod.qqpro.hook.MainNav] so the home nav and any new bars read as one system.
 *
 * This is the SINGLE SOURCE OF TRUTH for app colors: the legacy [momoi.mod.qqpro.Colors] object now
 * aliases these tokens, so retheming = editing the token values here. Tokens are named by MD3 role.
 * All classes are public on purpose (a @Mixin body that references a helper needs it public, or it hits
 * IllegalAccessError at runtime).
 *
 * ### Material mode now targets the phone UI baseline
 *
 * M3 helpers, dimens and components in this directory are sized for a **phone-class** screen (status
 * bar + 56dp app bar + 72dp bottom nav, 16sp body, 14sp label, 16–28dp radii). The shape / size /
 * typography tokens below intentionally deviate from the watch's original small-screen numbers;
 * legacy call sites that still rely on the watch values can fall back via the [Legacy] block.
 */
object M3 {
    // ── Color tokens (all user-overridable) ──────────────────────────────────────
    // Every MD3 role below resolves LIVE from its own Settings color pref (a hex string). A blank
    // pref falls back to the built-in default (and for the primary-derived tokens, to the value
    // auto-derived from the accent). Editing a token in the 外观主题 settings category rethemes every
    // materialized screen the next time it's built. The single source of truth is here.
    // Whether to use the light baseline palette instead of the dark one (Settings.lightMode). Drives
    // each token's DEFAULT below; an explicit per-token 外观主题 override still wins when set.
    private val light get() = momoi.mod.qqpro.Settings.lightMode.value
    /** Pick the dark- or light-mode baseline default for the current [light] mode. */
    private fun dl(darkValue: Int, lightValue: Int) = if (light) lightValue else darkValue

    // Accent (primary). Dark = Material Blue 200 (softer tone for dark UIs); Light = Blue 700, a deeper
    // MD3-tone-40-style accent with enough contrast on light surfaces. White label on the accent.
    val DEFAULT_PRIMARY get() = dl(0xFF_90CAF9.toInt(), 0xFF_1976D2.toInt())

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
    /**
     * OPAQUE version of [primaryContainer] (which is translucent). Use where the fill is rasterized
     * with no live background behind it — e.g. an avatar/icon drawable — so the translucent tonal
     * doesn't composite over black and turn dark in light mode. Composited over [surface].
     */
    val tonalSolid: Int get() = compositeOver(primaryContainer, surface)

    // ── Surfaces (light defaults = MD3 baseline neutral surfaces / surface-container tones) ───────
    val surface: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurface.value, dl(0xFF_1A1A1A.toInt(), 0xFF_FFFBFE.toInt()))              // screen background
    val surfaceContainer: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurfaceContainer.value, dl(0xFF_222222.toInt(), 0xFF_F3EDF7.toInt()))     // field / sunken surface / card
    val surfaceContainerHigh: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurfaceContainerHigh.value, dl(0xFF_2A2A2A.toInt(), 0xFF_ECE6F0.toInt())) // raised card / row pressed
    val surfaceVariant: Int get() = parseColor(momoi.mod.qqpro.Settings.themeSurfaceVariant.value, dl(0xFF_2E2E2E.toInt(), 0xFF_E7E0EC.toInt()))

    // ── Content (light defaults = MD3 baseline on-surface / outline tones) ───────────────────────
    val onSurface: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOnSurface.value, dl(0xFF_FFFFFF.toInt(), 0xFF_1C1B1F.toInt()))
    val onSurfaceVariant: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOnSurfaceVariant.value, dl(0xFF_C9C7CE.toInt(), 0xFF_49454F.toInt()))     // inactive icon/text
    val onSurfaceTip: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOnSurfaceTip.value, dl(0xFF_999999.toInt(), 0xFF_6A6A70.toInt()))         // secondary/hint text
    val hint: Int get() = parseColor(momoi.mod.qqpro.Settings.themeHint.value, dl(0xFF_777777.toInt(), 0xFF_8A8A90.toInt()))
    val outline: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOutline.value, dl(0xFF_444444.toInt(), 0xFF_79747E.toInt()))
    val outlineVariant: Int get() = parseColor(momoi.mod.qqpro.Settings.themeOutlineVariant.value, dl(0xFF_333333.toInt(), 0xFF_CAC4D0.toInt()))

    // ── Status ─────────────────────────────────────────────────────────────────
    val error: Int get() = parseColor(momoi.mod.qqpro.Settings.themeError.value, dl(0xFF_E5443C.toInt(), 0xFF_B3261E.toInt()))
    val badge: Int get() = error                   // unread/notification badge follows the error token
    val legacyBtn = 0xFF_1B9AF7.toInt()            // older link/button blue (Colors.btn)

    // ── Chat tokens (referenced by Colors aliases; values must stay exact) ───────
    val replyText = 0xFF_DDDDDD.toInt()
    val replyBackground = 0x22_FFFFFF
    val atMe = 0xFF_F74C30.toInt()

    // Group role/level tag badges (头衔/普通/管理/群主). Mode-aware: the dark values are unchanged; light
    // mode uses a pale tint background with a deeper, readable text color so the tags don't show as dark
    // chips on a light bubble. (Nested object can use M3's private [dl].)
    object NickTag {
        val specialBg get() = dl(0xFF_30263F.toInt(), 0xFF_ECE3FB.toInt()); val specialText get() = dl(0xFF_BB87F7.toInt(), 0xFF_6A3FD0.toInt())
        val normalBg  get() = dl(0xFF_2E2E2E.toInt(), 0xFF_E4E4E4.toInt()); val normalText  get() = dl(0xFF_9E9E9E.toInt(), 0xFF_5F5F5F.toInt())
        val adminBg   get() = dl(0xFF_0E2D41.toInt(), 0xFF_D6E9FB.toInt()); val adminText   get() = dl(0xFF_0088EE.toInt(), 0xFF_0066CC.toInt())
        val ownerBg   get() = dl(0xFF_412917.toInt(), 0xFF_FCE6D2.toInt()); val ownerText   get() = dl(0xFF_FF8D40.toInt(), 0xFF_C25700.toInt())
    }

    // ── Shape / corner radius (phone baseline) ─────────────────────────────────
    // Material 3 corner radius scale for phone-class screens.
    //  - radiusSm  = 8dp  (chips, small buttons)
    //  - radiusMd  = 16dp (cards, fields, FAB extended)
    //  - radiusLg  = 20dp (large cards, sheets)
    //  - radiusXl  = 28dp (extra-large emphasis, dialogs)
    val radiusSm get() = 8.dp.toFloat()
    val radiusMd get() = 16.dp.toFloat()
    val radiusLg get() = 20.dp.toFloat()
    val radiusXl get() = 28.dp.toFloat()
    val radiusPill = 9999f

    // ── Phone-class typography (sp) ──────────────────────────────────────────────
    // Material 3 type scale, expressed in sp (so they respect the user's font scale preference).
    // Use [textSizeSp] / [setTextSize] with COMPLEX_UNIT_SP for every body / label / title.
    val textDisplayLarge get() = 57f        // hero display
    val textDisplay get() = 36f             // screen-display
    val textHeadline get() = 28f            // page title
    val textTitle get() = 22f               // dialog title, app-bar title
    val textBody get() = 16f                // list body / chat message body
    val textLabel get() = 14f               // list supporting text / button label / nav label
    val textCaption get() = 12f             // overline / caption
    val textSubtitle get() = 14f            // subtitle / supporting text in lists
    val textNavLabel get() = 12f            // bottom-nav item label (M3 spec)

    /** Apply [sizeInSp] (already an sp value) to a TextView. Centralized so callers don't have to remember the unit flag. */
    fun textSizeSp(view: TextView, sizeInSp: Float) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp)
    }

    // ── Phone-class component dimensions (dp) ───────────────────────────────────
    // Material 3 reference dimensions for a phone-class screen — these replace the watch's small-screen
    // Phone-class component dimensions (matches phone QQ: title_bar_height=50dp,
    // 72dp bottom nav, 48dp touch targets, etc.).
    val touchTargetMin get() = 48.dp
    val appBarHeight get() = 50.dp
    val bottomNavHeight get() = 72.dp
    val fabSize get() = 56.dp
    val fabExtendedHeight get() = 56.dp
    val listItemMinHeight get() = 56.dp
    val listItemTwoLine get() = 72.dp
    val listItemThreeLine get() = 88.dp
    val cardElevation get() = 1f
    val cardElevatedElevation get() = 1f
    val cardOutlinedElevation get() = 0f
    val fabElevationRest get() = 6f
    val fabElevationPressed get() = 12f
    val bottomNavIndicatorHeight get() = 3.dp
    val bottomNavIndicatorWidth get() = 64.dp
    val bottomNavIconSize get() = 24.dp
    val appBarElevation get() = 0f             // M3 flat top app bar by default
    val dividerThickness get() = 1.dp

    /**
     * Watch-only legacy dimensions, kept so any code path that was sized for the round screen
     * can still compile and render at its historical values. New phone-class screens should NOT use
     * these — prefer the tokens above. (Watch builds typically target ~400dp × 400dp.)
     */
    @Deprecated("Watch-class dimens — new phone screens should use the tokens above (appBarHeight, bottomNavHeight, …).")
    object Legacy {
        val watchTitlebarHeight get() = 16.dp.toFloat()
        val watchMainNavHeight get() = 16.dp.toFloat()
        val watchBubbleRadius get() = 10.dp.toFloat()
        val watchCornerMargin get() = 15.dp.toFloat()
        const val watchBodyText = 14f
        const val watchSubtitleText = 11f
        const val watchNavLabelText = 12f
    }

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
    /** Composite a (possibly translucent) [fg] over an opaque [bg], returning an opaque color. */
    fun compositeOver(fg: Int, bg: Int): Int {
        val a = (fg ushr 24 and 0xFF) / 255f
        fun mix(sh: Int) = (((fg shr sh and 0xFF) * a) + ((bg shr sh and 0xFF) * (1f - a))).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

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

    /** A rounded rect that is BOTH filled ([fillColor]) and outlined ([strokeColor]) — a filled field
     *  with a visible border (e.g. the materialized chat input). */
    fun filledOutlined(
        fillColor: Int, strokeColor: Int, radius: Float = radiusMd, strokeWidthDp: Int = 1,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fillColor)
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

    /** A rounded, sunken M3 search/text field sized for phone-class screens (16sp body, 16dp padding). */
    fun searchField(ctx: Context, hint: String): EditText = EditText(ctx).apply {
        this.hint = hint
        setHintTextColor(M3.hint)
        setTextColor(onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textBody)
        setSingleLine()
        setPadding(16.dp, 10.dp, 16.dp, 10.dp)
        background = rounded(surfaceContainer, radiusLg)
        // Material spec: text fields should be touchable and easily tappable on phone-class screens.
        minHeight = touchTargetMin
    }

    /**
     * Build a phone-style top app bar as a LinearLayout: [nav] (back/menu icon slot) + title (and optional
     * subtitle) column + [actions] slot on the right. Sized 56dp tall, surface background. Returned view is
     * NOT attached — caller decides where it lives.
     *
     *     M3.appBar(ctx,
     *         title = "设置",
     *         subtitle = "12 项",
     *         navIcon = MaterialSymbol(MaterialSymbols.arrow_back, M3.onSurface),
     *         onNav = { onBackPressed() },
     *         actions = listOf(searchIcon, moreIcon),
     *     )
     */
    fun appBar(
        ctx: Context,
        title: CharSequence,
        subtitle: CharSequence? = null,
        navIcon: Drawable? = null,
        showBackButton: Boolean = true,
        onNav: (() -> Unit)? = null,
        actions: List<Drawable> = emptyList(),
        onAction: ((Int) -> Unit)? = null,
        elevated: Boolean = false,
    ): AppBar {
        return AppBar(ctx).apply {
            setTitle(title)
            if (subtitle != null) setSubtitle(subtitle)
            when {
                navIcon != null -> setNavIcon(navIcon)
                showBackButton -> setNavVisible(true) // default; back arrow already wired in init
                else -> setNavVisible(false)
            }
            if (onNav != null) setOnNavClick(onNav)
            // Add action buttons (48×48dp touch target).
            actions.forEachIndexed { index, d ->
                addAction(d) {
                    onAction?.invoke(index)
                }
            }
            if (elevated) setAppBarElevation(appBarElevation)
        }
    }

    /**
     * Build a phone-style bottom navigation bar with [items] (icon + label + optional badge). When a tab is
     * selected the indicator pill above the icon + the icon/label color flips to primary. Sized 72dp tall
     * (M3 phone-class spec), with a surfaceContainer background and elevation shadow.
     *
     *     val nav = M3.bottomNav(ctx, listOf(
     *         BottomNavItem(MaterialSymbol(MaterialSymbols.chat_bubble, M3.primary), "消息", 3),
     *         BottomNavItem(MaterialSymbol(MaterialSymbols.person, M3.onSurfaceVariant), "联系人"),
     *         BottomNavItem(MaterialSymbol(MaterialSymbols.settings, M3.onSurfaceVariant), "设置"),
     *     ), selectedIndex = 0) { pick(it) }
     */
    fun bottomNav(
        ctx: Context,
        items: List<BottomNavItem>,
        selectedIndex: Int = 0,
        onSelect: (Int) -> Unit = {},
    ): BottomNavigationView {
        return BottomNavigationView(ctx, items, selectedIndex, onSelect)
    }

    /**
     * Build a phone-style circular FAB: 56dp round, primary container, onPrimary icon. Use the returned
     * [FloatingActionButton.setOnClick] (or pass [onClick]) to wire a tap action. Phone spec: elevation 6dp
     * at rest, 12dp when pressed.
     *
     *     M3.fab(ctx, MaterialSymbol(MaterialSymbols.add, M3.onPrimary)) { composeNew() }
     */
    fun fab(
        ctx: Context,
        icon: Drawable,
        onClick: (() -> Unit)? = null,
    ): FloatingActionButton {
        return FloatingActionButton(ctx, icon).apply {
            if (onClick != null) setOnClick { onClick() }
        }
    }
}

/**
 * A circular tonal Material icon button with an optional top-end unread badge. Self-contained
 * (no XML, no Material lib dependency — the project is a Material 3 implementation written from
 * scratch in Kotlin, since the host app theme isn't MaterialComponents-based and pulling the real
 * com.google.android.material dependency would crash). Reuse for any toolbar/action-bar action.
 *
 * Sized for a phone-class screen — the touch target is at least [M3.touchTargetMin] (48dp), even when
 * the visible icon glyph is smaller.
 */
class MaterialIconButton(ctx: Context) : FrameLayout(ctx) {
    private val icon = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val badge = TextView(ctx).apply {
        setTextColor(0xFF_FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textCaption - 2f)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 9999f; setColor(M3.BADGE)
        }
        setPadding(4.dp, 0, 4.dp, 0)
        minWidth = 14.dp
        visibility = View.GONE
    }

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        // Phone-class minimum touch target. Override via the caller's layout params if a different
        // hit area is required; this keeps the icon accessible by default.
        minimumWidth = M3.touchTargetMin
        minimumHeight = M3.touchTargetMin
        // No container/tint by default: many app icons are already full-color round badges, so a
        // tonal circle behind them and an accent tint just collapse them into blobs. Opt in via
        // [setTonalContainer] / [setIconTint] for monochrome icons that need it.
        addView(icon, LayoutParams(24.dp, 24.dp, Gravity.CENTER))
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, 16.dp, Gravity.TOP or Gravity.END))
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