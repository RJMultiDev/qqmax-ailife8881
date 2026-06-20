package momoi.mod.qqpro.lib.material

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf

/**
 * Self-drawn Material-style icons (no XML/vector assets, no app-resource reuse). People icons are
 * filled (more legible at toolbar sizes); the search glass is stroked. Sized to their [bounds] so
 * they scale crisply, and tinted via the constructor color. Reuse with [MaterialIconButton].
 *
 * Coordinates are fractions of a padded content box, so one icon renders correctly at any size.
 */
abstract class MaterialIcon(protected val tint: Int = 0xFF_FFFFFF.toInt()) : Drawable() {
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tint
    }
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = tint
        strokeWidth = 1.8f.dpf
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    protected class Box(val l: Float, val t: Float, val w: Float, val h: Float) {
        fun x(fx: Float) = l + w * fx
        fun y(fy: Float) = t + h * fy
    }

    protected fun box(): Box {
        val b = bounds
        val pad = b.width() * 0.16f
        return Box(b.left + pad, b.top + pad, b.width() - 2 * pad, b.height() - 2 * pad)
    }

    /** A filled person (head disc + dome torso) centered at [fcx], scaled by [scale]. */
    protected fun drawPerson(c: Canvas, box: Box, fcx: Float, scale: Float = 1f) {
        val cx = box.x(fcx)
        val headR = box.w * 0.155f * scale
        val headCy = box.y(0.30f)
        c.drawCircle(cx, headCy, headR, fill)
        // Torso: a filled dome (top semicircle) just below the head.
        val bw = box.w * 0.30f * scale
        val top = headCy + headR * 1.15f
        val rect = RectF(cx - bw, top, cx + bw, top + 2 * bw)
        val p = Path().apply { arcTo(rect, 180f, 180f); close() }
        c.drawPath(p, fill)
    }

    /**
     * A small filled bell "notification" badge centered at ([cx],[cy]) with radius [r] — drawn on
     * top of a person/group icon to signal that the button opens notifications.
     */
    protected fun drawBell(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path().apply {
            moveTo(cx - r, cy + r * 0.55f)              // bottom-left of the body
            lineTo(cx + r, cy + r * 0.55f)              // flat base
            lineTo(cx + r * 0.72f, cy + r * 0.1f)       // right wall up to the shoulder
            quadTo(cx + r * 0.72f, cy - r, cx, cy - r)  // round dome (right→top)
            quadTo(cx - r * 0.72f, cy - r, cx - r * 0.72f, cy + r * 0.1f) // top→left
            close()
        }
        c.drawPath(p, fill)
        // Clapper dot just below the base.
        c.drawCircle(cx, cy + r * 0.9f, r * 0.28f, fill)
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; stroke.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter; stroke.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 24.dp
    override fun getIntrinsicHeight(): Int = 24.dp
}

/** Person with a "+" badge — 加好友/群聊. */
class AddPersonIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) {
        val box = box()
        drawPerson(canvas, box, fcx = 0.40f, scale = 0.95f)
        // "+" at the lower-right.
        stroke.strokeWidth = 2.4f.dpf
        val cx = box.x(0.84f); val cy = box.y(0.72f); val r = box.w * 0.16f
        canvas.drawLine(cx - r, cy, cx + r, cy, stroke)
        canvas.drawLine(cx, cy - r, cx, cy + r, stroke)
    }
}

/** Single filled person — 好友通知 (count badge conveys "notification"). */
class PersonIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) = drawPerson(canvas, box(), fcx = 0.5f)
}

/** Two overlapping filled people — 群通知 / groups. */
class GroupIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) {
        val box = box()
        drawPerson(canvas, box, fcx = 0.34f, scale = 0.82f)   // back/left
        drawPerson(canvas, box, fcx = 0.64f, scale = 0.82f)   // front/right
    }
}

/** Single person with a bell badge — 好友通知. */
class FriendNotifyIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) {
        val box = box()
        drawPerson(canvas, box, fcx = 0.42f, scale = 0.88f)
        drawBell(canvas, box.x(0.80f), box.y(0.26f), box.w * 0.18f)
    }
}

/** Two people with a bell badge — 群通知. */
class GroupNotifyIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) {
        val box = box()
        drawPerson(canvas, box, fcx = 0.30f, scale = 0.74f)
        drawPerson(canvas, box, fcx = 0.56f, scale = 0.74f)
        drawBell(canvas, box.x(0.84f), box.y(0.24f), box.w * 0.17f)
    }
}

/** Magnifying glass — 搜索. */
class SearchIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) {
        val box = box()
        stroke.strokeWidth = 2f.dpf
        val r = box.w * 0.27f
        val cx = box.x(0.40f); val cy = box.y(0.40f)
        canvas.drawCircle(cx, cy, r, stroke)
        val k = r * 0.707f
        canvas.drawLine(cx + k, cy + k, box.x(0.86f), box.y(0.86f), stroke)
    }
}

/** Left-pointing back chevron — closes inline search. */
class BackArrowIcon(color: Int = 0xFF_FFFFFF.toInt()) : MaterialIcon(color) {
    override fun draw(canvas: Canvas) {
        val box = box()
        stroke.strokeWidth = 2.2f.dpf
        canvas.drawLine(box.x(0.62f), box.y(0.22f), box.x(0.32f), box.y(0.5f), stroke)
        canvas.drawLine(box.x(0.32f), box.y(0.5f), box.x(0.62f), box.y(0.78f), stroke)
    }
}
