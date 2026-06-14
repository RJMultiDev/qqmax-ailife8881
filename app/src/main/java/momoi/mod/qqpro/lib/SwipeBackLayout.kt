package momoi.mod.qqpro.lib

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AbsSeekBar
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps a screen's content and detects a left-to-right "swipe back" drag, then invokes
 * [onSwipeBack] (typically `finish()`). Lets watches without a hardware back button leave a
 * plain [android.app.Activity] (e.g. the settings page) the same way the QQ fling framework
 * does for the main activity — but fully self-contained, so it doesn't depend on QQ's
 * WatchActivity / MobileQQ lifecycle wiring.
 *
 * Vertical drags pass through to children (e.g. a ScrollView) untouched; only a horizontal,
 * rightward drag is intercepted.
 */
class SwipeBackLayout(context: Context) : FrameLayout(context) {

    var onSwipeBack: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    // Set on DOWN when the touch lands on a horizontal-drag widget (e.g. a settings SeekBar).
    // Those live inside a ScrollView, so they don't claim the gesture on DOWN — without this
    // guard our MOVE interception steals the drag before the slider can move.
    private var blockSwipe = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Use raw (screen) coords: ev.x is relative to this view, so once we start
                // translating it the local space shifts and the offset oscillates/jumps.
                downX = ev.rawX
                downY = ev.rawY
                tracking = false
                blockSwipe = isOnHorizontalDragWidget(this, ev.rawX, ev.rawY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (blockSwipe) return false
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                // Horizontal-dominant rightward drag → grab it for swipe-back.
                if (dx > touchSlop && dx > abs(dy) * 1.5f) {
                    tracking = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    translationX = (ev.rawX - downX).coerceAtLeast(0f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking) {
                    tracking = false
                    val dx = ev.rawX - downX
                    if (ev.actionMasked == MotionEvent.ACTION_UP && dx > width * 0.3f) {
                        onSwipeBack?.invoke()
                    } else {
                        animate().translationX(0f).setDuration(150).start()
                    }
                }
            }
        }
        return tracking
    }

    /** True if [x],[y] (screen coords) fall on a visible [AbsSeekBar] descendant of [v]. */
    private fun isOnHorizontalDragWidget(v: View, x: Float, y: Float): Boolean {
        if (v.visibility != View.VISIBLE) return false
        if (v is AbsSeekBar) {
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            // Pad the hit area so a drag started just beside the thumb still counts.
            val pad = touchSlop
            if (x >= loc[0] - pad && x <= loc[0] + v.width + pad &&
                y >= loc[1] - pad && y <= loc[1] + v.height + pad
            ) {
                return true
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (isOnHorizontalDragWidget(v.getChildAt(i), x, y)) return true
            }
        }
        return false
    }
}
