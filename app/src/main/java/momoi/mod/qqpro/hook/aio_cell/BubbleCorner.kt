package momoi.mod.qqpro.hook.aio_cell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.util.parseHexColor

/**
 * Replace the native chat-bubble background (a stretched nine-patch set via
 * `setBackgroundResource(R.drawable.bubble_*_bg_new)` on the long-click wrapper) with a
 * rounded rectangle whose corner radius is configurable in settings.
 *
 * The fill color and the nine-patch's content padding are captured once per
 * [AIOCellGroupWidget.getLocationType] (guest/host) from the original bubble. The padding is
 * re-applied (plus a little extra so text clears the rounded corners) because a plain
 * GradientDrawable carries none, which would otherwise let the text spill past the corners.
 * Applied after every bind because the native cell re-sets the nine-patch on rebind.
 */
object BubbleCorner {
    private class Style(val color: Int, val pad: Rect)

    // locationType -> sampled bubble style
    private val styles = HashMap<Int, Style>()

    fun apply(widget: AIOCellGroupWidget) {
        val wrapper = runCatching { widget.getLongClickWrapper<View>() }.getOrNull()
        Utils.log("BubbleCorner: wrapper=${wrapper?.javaClass?.simpleName} bg=${wrapper?.background?.javaClass?.simpleName} loc=${runCatching { widget.locationType }.getOrNull()}")
        if (wrapper == null) return
        val bg = wrapper.background ?: return
        // Already replaced by us (native didn't re-set the nine-patch this bind) — leave it.
        if (bg is GradientDrawable) return
        val loc = widget.locationType
        val style = styles.getOrPut(loc) {
            val pad = Rect()
            bg.getPadding(pad)
            Style(sampleColor(bg), pad)
        }
        // Optional per-side color override from settings; blank/invalid keeps the sampled color.
        val override = if (loc == 0) Settings.bubbleColorOther else Settings.bubbleColorSelf
        val color = parseHexColor(override.value) ?: style.color
        val r = Settings.bubbleCornerRadius.value.dpf
        wrapper.background = roundCornerDrawable(color, r)
        // Keep the original text inset and add ~half the radius horizontally so glyphs near
        // the corners aren't clipped by the rounded edge.
        val extra = (r * 0.5f).toInt()
        wrapper.setPadding(
            style.pad.left + extra, style.pad.top,
            style.pad.right + extra, style.pad.bottom
        )
    }

    /** Sample the fill color by rendering the drawable to a small bitmap and reading its center. */
    private fun sampleColor(d: Drawable): Int {
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 40
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 88
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val old = Rect(d.bounds)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        d.bounds = old
        val color = runCatching { bmp.getPixel(w / 2, h / 2) }.getOrDefault(0xFF_2B2B2B.toInt())
        bmp.recycle()
        return color
    }
}
