package momoi.mod.qqpro.ota

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.GroupScope
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width

private val ACCENT = 0xFF_4FC3F7.toInt()
private val BG = 0xF0_121212.toInt()

/**
 * Watch-styled "update available" dialog, built to replace the framework [android.app.AlertDialog]
 * the OTA manager used to show. That default dialog renders with oversized margins / clipped buttons
 * on the round watch (the app forces a tiny compile SDK and overrides display DPI — same reason
 * [momoi.mod.qqpro.hook.view.AboutFragment] exists). This is a full-bleed dark [Dialog] with our own
 * padding, a scrollable changelog, and three stacked buttons; a left-to-right swipe dismisses it for
 * watches without a back button.
 *
 * Called from Java ([OTAManager2]); actions are [Runnable]s for Java interop.
 */
object UpdateDialog {
    @JvmStatic
    fun show(
        context: Context,
        version: String,
        changelog: String,
        onUpdate: Runnable,
        onDisable: Runnable
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context)
            .vertical()
            .padding(left = 20.dp, top = 16.dp, right = 20.dp, bottom = 16.dp)
        root.setBackgroundColor(BG)

        root.content {
            add<TextView>()
                .text("发现新版本 V$version")
                .textSize(17f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .width(FILL)
                .padding(top = 6.dp, bottom = 12.dp)
        }

        // Changelog scrolls so a long one never pushes the buttons off-screen.
        val scroll = ScrollView(context).apply { isFillViewport = false }
        val msg = TextView(context)
            .text(changelog)
            .textSize(13f)
            .textColor(0xFF_DDDDDD)
        scroll.addView(msg, ViewGroup.LayoutParams(FILL, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(FILL, 0, 1f))

        root.content {
            button("更新", ACCENT, 0xFF_000000.toInt()) {
                dialog.dismiss()
                onUpdate.run()
            }
            button("稍后", 0xFF_2A2A2A.toInt(), 0xFF_FFFFFF.toInt()) {
                dialog.dismiss()
            }
            button("不再提醒", 0xFF_1A1A1A.toInt(), 0xFF_999999.toInt()) {
                dialog.dismiss()
                onDisable.run()
            }
        }

        val swipe = SwipeBackLayout(context).apply {
            addView(root, FILL, FILL)
            onSwipeBack = { dialog.dismiss() }
        }
        dialog.setContentView(swipe)
        dialog.setCancelable(true)
        dialog.window?.apply {
            // Transparent window background + full-screen layout so OUR padding controls the
            // insets, instead of the platform dialog's DPI-scaled margins.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.show()
    }

    private fun GroupScope.button(label: String, bg: Int, fg: Int, onClick: () -> Unit) {
        add<TextView>()
            .text(label)
            .textSize(14f)
            .textColor(fg)
            .gravity(Gravity.CENTER)
            .width(FILL)
            .padding(top = 10.dp, bottom = 10.dp)
            .apply {
                background = GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = 22.dp.toFloat()
                }
            }
            .margin(top = 8.dp)
            .clickable(onClick)
    }
}
