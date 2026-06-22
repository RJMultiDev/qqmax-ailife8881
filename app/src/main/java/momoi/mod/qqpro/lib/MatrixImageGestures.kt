package momoi.mod.qqpro.lib

import android.content.Context
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Shared gesture helpers for the native zoomable image view (`RFWMatrixImageView`, which draws via
 * `ScaleType.MATRIX` and exposes its live zoom/pan through `getImageMatrix()`). Used by every custom
 * photo viewer (chat `BigImageFragment`, the QZone/album `MediaPager`) so they all behave the same:
 * tap the empty area to dismiss, and swipe-back only while fully zoomed out.
 */

/** Displayed image rect in view coords, from the live image matrix (captures zoom/pan) + padding. */
fun ImageView.displayedImageRect(): RectF? {
    val d = drawable ?: return null
    val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
    imageMatrix.mapRect(r)
    r.offset(paddingLeft.toFloat(), paddingTop.toFloat())
    return r
}

/** True if [x],[y] (view coords) fall outside the currently displayed image. */
fun ImageView.isTapOutsideImage(x: Float, y: Float): Boolean {
    val r = displayedImageRect() ?: return false
    return x < r.left || x > r.right || y < r.top || y > r.bottom
}

/** True if the displayed image fits within the view width → no horizontal pan room (safe to swipe-back). */
fun ImageView.imageFitsHorizontally(): Boolean {
    val r = displayedImageRect() ?: return true
    return r.left >= -1f && r.right <= width + 1f
}

/**
 * A [FrameLayout] that observes single taps WITHOUT consuming touches, so a child zoomable image
 * keeps its own pinch / double-tap / pan. The detector is fed from [dispatchTouchEvent], which sees
 * every event regardless of which child consumes it. [onSingleTap] gets the tap in this view's coords.
 */
class TapObserverLayout(ctx: Context) : FrameLayout(ctx) {
    var onSingleTap: ((Float, Float) -> Unit)? = null

    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke(e.x, e.y)
            return false
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        runCatching { detector.onTouchEvent(ev) }
        return super.dispatchTouchEvent(ev)
    }
}
