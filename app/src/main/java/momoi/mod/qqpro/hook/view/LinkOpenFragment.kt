package momoi.mod.qqpro.hook.view

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
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
import momoi.mod.qqpro.util.Utils

private val ACCENT = 0xFF_4FC3F7.toInt()

/**
 * Full-screen confirmation shown when a link is tapped. A native AlertDialog is
 * unusable here — the app forces a tiny compile SDK and overrides the display
 * DPI, so a windowed dialog renders broken on the round watch screen. A
 * full-screen [MyDialogFragment] is both reliable and a better fit for the watch.
 */
class LinkOpenFragment(private val url: String) : MyDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = inflater.context
        // Whole page scrolls: a long URL plus the action buttons can exceed the round watch
        // screen, so wrap everything in a ScrollView (fillViewport keeps it centred when short).
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            setBackgroundColor(0xF0_121212.toInt())
        }
        val root = LinearLayout(ctx)
            .vertical()
            .padding(20.dp)
        root.gravity = Gravity.CENTER
        scroll.addView(root, ViewGroup.LayoutParams(FILL, WRAP))

        root.content {
            add<TextView>()
                .text("打开链接")
                .textSize(16f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .padding(bottom = 10.dp)

            add<TextView>()
                .text(url)
                .textSize(12f)
                .textColor(0xFF_BBBBBB)
                .gravity(Gravity.CENTER)
                .width(FILL)
                .padding(top = 4.dp, bottom = 10.dp)

            button("浏览器打开", ACCENT, 0xFF_000000.toInt()) {
                Utils.openUrl(url)
                dismiss()
            }
            button("复制链接", 0xFF_3A3A3A.toInt(), 0xFF_FFFFFF.toInt()) {
                Utils.copyToClipboard(ctx, url, "已复制链接")
                dismiss()
            }
            button("取消", 0xFF_2A2A2A.toInt(), 0xFF_FFFFFF.toInt()) {
                dismiss()
            }
        }
        return scroll
    }

    private fun momoi.mod.qqpro.lib.LinearScope.button(
        label: String,
        bg: Int,
        fg: Int,
        onClick: () -> Unit
    ) {
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
            .margin(top = 6.dp)
            .clickable(onClick)
    }
}
