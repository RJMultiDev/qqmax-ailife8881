package momoi.mod.qqpro

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.lib.material.MaterialSymbol
import momoi.mod.qqpro.lib.material.MaterialSymbols
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.onClick
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.rippleTouch
import momoi.mod.qqpro.lib.size
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.material.AppBar
import momoi.mod.qqpro.lib.material.BottomNavItem
import momoi.mod.qqpro.lib.material.BottomNavigationView
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.lib.material.Spacing
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.Utils
import kotlin.system.exitProcess

/**
 * First-launch (and settings-reachable) "choose your UI style" screen. Offers two presets — Material
 * design vs the original QQ design — each with a small example preview. Picking one flips every
 * [Settings.styleToggles] pref (the whole "Material 化" category + the home-nav method + the
 * 联系人分组 dependency), records the reserved [Settings.uiStyle], then cold-restarts the app so the
 * change takes effect everywhere.
 *
 * The Material preview renders a phone-style mock: a 24dp status-bar placeholder, a 56dp top app bar
 * (with arrow-back + title + action icons), a body with a rounded chat bubble, and a 72dp bottom nav
 * with three icon+label tabs (selected tab gets a primary indicator + primary tint). The Original
 * preview keeps a flat dark bubble + the native page-indicator dots so the contrast is obvious.
 *
 * Standalone [Activity] (the settings screen is a plain Activity with no FragmentManager, and this
 * must also be launchable from the MainActivity onCreate hook). Registered in app/mixin/AndroidManifest.xml.
 */
class StyleChooserActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(M3.surface))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(M3.surface)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val root = LinearLayout(this)
            .vertical()
            // 16dp horizontal / 24dp vertical root padding so the page reads as a phone-class screen
            // with breathing room from the status bar.
            .padding(left = 16.dp, top = 24.dp, right = 16.dp, bottom = 16.dp)
        scroll.addView(root, FILL, WRAP)

        root.content {
            add<TextView>()
                .text("选择界面风格")
                .textSize(28f)
                .textColor(M3.onSurface)
                .gravity(Gravity.CENTER)
                .padding(bottom = 8.dp)
            add<TextView>()
                .text("可随时在「设置 › Material 化 › 界面风格」重新选择")
                .textSize(14f)
                .textColor(M3.onSurfaceVariant)
                .gravity(Gravity.CENTER)
                .padding(bottom = 24.dp)

            styleCard(
                title = "Material 设计",
                desc = "M3 风格的聊天、菜单、列表、资料卡、空间与底部导航栏",
                material = true,
            )
            styleCard(
                title = "原始设计",
                desc = "保留 QQ 原生界面与翻页指示器，关闭所有 Material 重设计",
                material = false,
            )

            // Escape hatch so the first-launch prompt is never a trap: dismiss without changing
            // anything, and don't auto-show it again (still reachable from settings).
            add<TextView>()
                .text("暂不选择")
                .textSize(14f)
                .textColor(M3.hint)
                .gravity(Gravity.CENTER)
                .padding(top = 16.dp, bottom = 8.dp)
                .apply {
                    width(FILL)
                    rippleTouch()
                    onClick { dismissWithoutChange() }
                }
        }

        // Wrap in SwipeBackLayout so a left-to-right swipe leaves the screen (watches without a
        // hardware back button). Same effect as the back button: dismiss without changing anything.
        // ignoreDisableSetting so our own onboarding screen is always swipeable even if the user
        // turned off in-app swipe-back.
        setContentView(
            SwipeBackLayout(this).apply {
                addView(scroll, FrameLayout.LayoutParams(FILL, FILL))
                ignoreDisableSetting = true
                onSwipeBack = { dismissWithoutChange() }
            }
        )
    }

    /** A full-width tappable option card: heading + example preview + description. */
    private fun momoi.mod.qqpro.lib.GroupScope.styleCard(
        title: String,
        desc: String,
        material: Boolean,
    ) {
        val card = add<LinearLayout>()
        card.vertical()
        card.width(FILL)
        card.margin(bottom = 20.dp)
        card.padding(left = 20.dp, top = 20.dp, right = 20.dp, bottom = 20.dp)
        card.background(M3.rounded(M3.surfaceContainer, M3.radiusLg))
        card.content {
            add<TextView>()
                .text(title)
                .textSize(22f)
                .textColor(M3.onSurface)
                .padding(bottom = 12.dp)

            // Example preview mock of what the style looks like.
            if (material) materialPreview() else originalPreview()

            add<TextView>()
                .text(desc)
                .textSize(14f)
                .textColor(M3.onSurfaceVariant)
                .padding(top = 12.dp)
        }
        card.rippleTouch()
        card.onClick { pick(material) }
    }

    /**
     * Phone-style Material preview. Renders a self-contained mock of the new Material UI:
     *  - 24dp "status bar" placeholder strip (just a colored bar with the current time)
     *  - 56dp [AppBar] with back arrow + "消息" title + a search icon action
     *  - rounded primary chat bubble + an incoming reply (using a generic avatar circle)
     *  - 72dp [BottomNavigationView] with 3 icon+label tabs (messages / contacts / settings)
     */
    private fun momoi.mod.qqpro.lib.GroupScope.materialPreview() {
        val phone = add<LinearLayout>()
        phone.vertical()
        phone.width(FILL)
        phone.background(M3.rounded(M3.surface, M3.radiusLg))
        phone.clipToOutline = true

        phone.content {
            // ── Status bar mock ────────────────────────────────────────────────
            val status = add<LinearLayout>()
            status.width(FILL)
            status.height(24.dp)
            status.gravity(Gravity.CENTER_VERTICAL or Gravity.END)
            status.padding(left = 16.dp, right = 16.dp)
            status.setBackgroundColor(M3.surface)
            val clock = add<TextView>()
            clock.text = "12:34"
            clock.setTextColor(M3.onSurfaceVariant)
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            (clock.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.END

            // ── Top app bar mock (56dp) ────────────────────────────────────────
            val bar = AppBar(this@StyleChooserActivity).apply {
                setTitle("消息")
                setNavIcon(MaterialSymbol(MaterialSymbols.arrow_back, M3.onSurface))
                setOnNavClick { /* preview-only */ }
                // Trailing action: a search icon (48dp touch target).
                addAction(MaterialSymbol(MaterialSymbols.search, M3.onSurface)) { /* preview-only */ }
                setAppBarElevation(0f)
            }
            addView(bar, LinearLayout.LayoutParams(FILL, M3.appBarHeight))

            // ── Body: two chat bubbles + avatar + reply ─────────────────────────
            val body = add<LinearLayout>()
            body.vertical()
            body.width(FILL)
            body.padding(left = 16.dp, top = 16.dp, right = 16.dp, bottom = 16.dp)
            body.setBackgroundColor(M3.surface)

            body.content {
                // Incoming row: avatar + bubble
                val incoming = add<LinearLayout>()
                incoming.width(FILL)
                incoming.vertical()
                incoming.gravity(Gravity.START)

                val bubble = add<TextView>()
                bubble.text = "晚上一起吃饭?"
                bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                bubble.setTextColor(M3.onSurface)
                bubble.setPadding(Spacing.md, 10.dp, Spacing.md, 10.dp)
                bubble.background = M3.rounded(M3.surfaceContainer, 18.dp.toFloat())
                val bubbleLp = LinearLayout.LayoutParams(WRAP, WRAP)
                bubbleLp.gravity = Gravity.START
                incoming.addView(bubble, bubbleLp)

                // Outgoing row: bubble right-aligned, primary fill.
                val outgoing = add<LinearLayout>()
                outgoing.width(FILL)
                outgoing.gravity = Gravity.END
                outgoing.padding(top = 8.dp)
                val reply = add<TextView>()
                reply.text = "好👋"
                reply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                reply.setTextColor(M3.onPrimary)
                reply.setPadding(Spacing.md, 10.dp, Spacing.md, 10.dp)
                reply.background = M3.rounded(M3.primary, 18.dp.toFloat())
                outgoing.addView(reply)
            }

            // ── Bottom navigation mock (72dp) ───────────────────────────────────
            val nav = BottomNavigationView(
                this@StyleChooserActivity,
                listOf(
                    BottomNavItem(
                        MaterialSymbol(MaterialSymbols.chat_bubble, M3.primary),
                        "消息",
                        badgeCount = 3,
                    ),
                    BottomNavItem(
                        MaterialSymbol(MaterialSymbols.person, M3.onSurfaceVariant),
                        "联系人",
                    ),
                    BottomNavItem(
                        MaterialSymbol(MaterialSymbols.settings, M3.onSurfaceVariant),
                        "设置",
                    ),
                ),
                selectedIndex = 0,
                onSelect = { /* preview-only */ },
            )
            addView(nav, LinearLayout.LayoutParams(FILL, M3.bottomNavHeight))
        }
    }

    /** Mock of the original look: flat dark bubble with square-ish corners and tiny indicator dots. */
    private fun momoi.mod.qqpro.lib.GroupScope.originalPreview() {
        val box = add<LinearLayout>()
        box.vertical()
        box.width(FILL)
        box.padding(left = 12.dp, top = 12.dp, right = 12.dp, bottom = 12.dp)
        box.background(M3.rounded(0xFF_111111.toInt(), 4.dp.toFloat()))
        box.content {
            val bubble = add<TextView>()
                .text("你好")
                .textSize(12f)
                .textColor(0xFF_DDDDDD.toInt())
            bubble.padding(left = 10.dp, top = 6.dp, right = 10.dp, bottom = 6.dp)
            bubble.background(M3.rounded(0xFF_3A3A3A.toInt(), 3.dp.toFloat()))
            (bubble.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.START

            // Native page-indicator: three small equal grey dots.
            val nav = add<LinearLayout>()
            nav.width(FILL)
            nav.padding(top = 12.dp)
            (nav as LinearLayout).gravity = Gravity.CENTER
            nav.content {
                dot(0xFF_888888.toInt(), 6)
                dot(0xFF_BBBBBB.toInt(), 6)
                dot(0xFF_888888.toInt(), 6)
            }
            // Icon example — the original way: the native QQ search resource image, shown as-is
            // (no tint, no container).
            iconExample(material = false)
        }
    }

    /**
     * Shows a search-icon example illustrating the two styles: Material = the M3 [MaterialSymbols.search]
     * vector tinted with the accent on a tonal circular container; Original = the native QQ search
     * drawable resource (qui_search_icon_navigation_01) rendered as-is.
     */
    private fun momoi.mod.qqpro.lib.GroupScope.iconExample(material: Boolean) {
        if (material) {
            val frame = add<FrameLayout>()
            frame.size(48.dp, 48.dp)
            frame.background(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(M3.TONAL)
            })
            (frame.layoutParams as LinearLayout.LayoutParams).also {
                it.topMargin = 16.dp
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            frame.content {
                val icon = add<ImageView>()
                icon.setImageDrawable(MaterialSymbol(MaterialSymbols.search, M3.primary))
                icon.size(24.dp, 24.dp)
                (icon.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
            }
        } else {
            val icon = add<ImageView>()
            icon.size(30.dp, 30.dp)
            // Native QQ search drawable as the "resource image". Many native glyphs are monochrome and
            // designed to be tinted by the app, so left untinted they render invisibly on the dark
            // preview — tint to the on-surface color so the shape shows. Fall back to a plain (un-
            // accented, no container) search glyph if the resource can't be resolved on this build.
            val id = resources.getIdentifier("qui_search_icon_navigation_01", "drawable", packageName)
            val nativeIcon = if (id != 0) runCatching { resources.getDrawable(id, theme) }.getOrNull() else null
            icon.setImageDrawable(nativeIcon ?: MaterialSymbol(MaterialSymbols.search, M3.onSurfaceVariant))
            icon.setColorFilter(M3.onSurfaceVariant)
            (icon.layoutParams as LinearLayout.LayoutParams).also {
                it.topMargin = 12.dp
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    private fun momoi.mod.qqpro.lib.GroupScope.dot(color: Int, widthDp: Int) {
        val d = add<View>()
        d.size(widthDp.dp, 6.dp)
        d.background(M3.rounded(color, 3.dp.toFloat()))
        (d.layoutParams as LinearLayout.LayoutParams).also {
            it.leftMargin = 3.dp
            it.rightMargin = 3.dp
        }
    }

    private fun pick(material: Boolean) {
        Settings.applyUiStyle(material)
        Utils.toast(this, if (material) "已切换为 Material 设计，正在重启…" else "已切换为原始设计，正在重启…")
        restartApp()
    }

    private fun dismissWithoutChange() {
        // Mark seen so the first-launch prompt doesn't reappear, but leave all settings untouched.
        Settings.styleChooserSeen.value = true
        finish()
    }

    override fun onBackPressed() {
        // Back from the first-launch prompt = dismiss (don't keep nagging on every launch).
        dismissWithoutChange()
    }

    /**
     * Cold-restart the app so every setting is re-read from scratch (a soft relaunch would keep the
     * current process, leaving once-per-process reads stale). Schedule the launch intent via
     * AlarmManager, then kill our own process.
     */
    private fun restartApp() {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pi)
            }
        }.onFailure { Utils.log("StyleChooser: restart schedule failed: $it") }
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}