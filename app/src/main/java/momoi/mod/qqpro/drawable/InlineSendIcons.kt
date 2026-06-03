package momoi.mod.qqpro.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** A filled "paper plane" send arrow, drawn so it always scales to its bounds. */
fun sendIconDrawable(color: Int = 0xFF_FFFFFF.toInt()): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val path = Path().apply {
            moveTo(b.left + w * 0.18f, b.top + h * 0.20f)
            lineTo(b.left + w * 0.84f, b.top + h * 0.50f)
            lineTo(b.left + w * 0.18f, b.top + h * 0.80f)
            lineTo(b.left + w * 0.36f, b.top + h * 0.50f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A simple rounded "X" close icon, drawn so it always scales to its bounds. */
fun closeIconDrawable(color: Int = 0xFF_FFFFFF.toInt()): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        paint.strokeWidth = w * 0.09f
        val l = b.left + w * 0.30f
        val r = b.left + w * 0.70f
        val t = b.top + h * 0.30f
        val bo = b.top + h * 0.70f
        canvas.drawLine(l, t, r, bo, paint)
        canvas.drawLine(r, t, l, bo, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
