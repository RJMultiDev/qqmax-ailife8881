package momoi.mod.qqpro.hook.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.SwipeBackLayout

/**
 * Full-screen image viewer with pinch-zoom, double-tap zoom and pan — mirrors the gesture model of
 * [VideoPlayerFragment]. Single tap (when not zoomed) dismisses. Reusable; used by the group
 * announcement viewer ([GroupBulletinFragment]) to show bulletin images.
 */
class ZoomableImageFragment(private val bmp: Bitmap) : MyDialogFragment() {

    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var zoomAnimator: ValueAnimator? = null
    private lateinit var image: ImageView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = inflater.context
        val root = FrameLayout(ctx)
        root.setBackgroundColor(0xCC_000000.toInt())

        image = ImageView(ctx).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(image, FrameLayout.LayoutParams(FILL, FILL))

        val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomAnimator?.cancel()
                scale = (scale * d.scaleFactor).coerceIn(1f, 5f)
                if (scale <= 1.01f) { tx = 0f; ty = 0f }
                clampTranslation()
                applyTransform()
                return true
            }
        })
        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Tap on the empty (letterbox) area outside the image always dismisses; a tap on the
                // image dismisses only while not zoomed (mirrors the old behaviour).
                if (isOutsideImage(e.x, e.y) || scale <= 1.01f) dismiss()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > 1.01f) animateZoom(1f, 0f, 0f) else animateZoom(2.5f, 0f, 0f)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scale > 1.01f) {
                    zoomAnimator?.cancel()
                    tx -= dx; ty -= dy
                    clampTranslation()
                    applyTransform()
                }
                return true
            }
        })
        root.setOnTouchListener { _, e ->
            scaleDetector.onTouchEvent(e)
            gestureDetector.onTouchEvent(e)
            true
        }

        // Right-swipe back to dismiss, but only while fully zoomed out — once zoomed, a horizontal
        // drag pans the image instead (canSwipe is re-checked on every gesture).
        val swipe = SwipeBackLayout(ctx)
        swipe.onSwipeBack = { dismiss() }
        swipe.canSwipe = { scale <= 1.01f }
        swipe.addView(root, FrameLayout.LayoutParams(FILL, FILL))
        return swipe
    }

    /** True if [x],[y] (root coords) fall outside the currently displayed (fit + zoomed) bitmap. */
    private fun isOutsideImage(x: Float, y: Float): Boolean {
        val vw = image.width.toFloat(); val vh = image.height.toFloat()
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        if (vw == 0f || vh == 0f || bw == 0f || bh == 0f) return false
        val fit = minOf(vw / bw, vh / bh)
        val dispW = bw * fit * scale
        val dispH = bh * fit * scale
        val cx = vw / 2f + tx
        val cy = vh / 2f + ty
        return x < cx - dispW / 2f || x > cx + dispW / 2f ||
            y < cy - dispH / 2f || y > cy + dispH / 2f
    }

    private fun applyTransform() {
        image.scaleX = scale
        image.scaleY = scale
        image.translationX = tx
        image.translationY = ty
    }

    /** Keep the scaled image from being dragged past its own edges. */
    private fun clampTranslation() {
        val w = image.width
        val h = image.height
        if (w == 0 || h == 0) return
        val maxX = (w * (scale - 1f)) / 2f
        val maxY = (h * (scale - 1f)) / 2f
        tx = tx.coerceIn(-maxX, maxX)
        ty = ty.coerceIn(-maxY, maxY)
    }

    private fun animateZoom(targetScale: Float, targetTx: Float, targetTy: Float) {
        zoomAnimator?.cancel()
        val fromS = scale; val fromX = tx; val fromY = ty
        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                scale = fromS + (targetScale - fromS) * f
                tx = fromX + (targetTx - fromX) * f
                ty = fromY + (targetTy - fromY) * f
                clampTranslation()
                applyTransform()
            }
            start()
        }
    }
}
