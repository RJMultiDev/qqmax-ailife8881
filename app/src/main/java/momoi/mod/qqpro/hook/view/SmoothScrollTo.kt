package momoi.mod.qqpro.hook.view

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import momoi.mod.qqpro.hook.action.CurrentMsgList

/**
 * Jump straight to [position] with the item snapped to the top, no animation. Used by chat
 * search where a far-off target makes [smoothScrollToStart]'s per-item animation take forever.
 */
fun RecyclerView.scrollToStartInstant(position: Int) {
    (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        ?: scrollToPosition(position)
}

fun RecyclerView.smoothScrollToStart(position: Int) {
    layoutManager?.startSmoothScroll(
        object : LinearSmoothScroller(context) {
            init {
                targetPosition = position
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        })
}

/**
 * Force a continuous smooth scroll to the very bottom of the list. The native go-to-bottom (and
 * RecyclerView's own [LinearSmoothScroller]) does a fast "interim seek" over long distances that
 * jump-cuts and skips binding the messages in between. Instead we drive [scrollBy] a fixed amount
 * every animation frame until the list can no longer scroll down, so it stays a smooth, constant
 * speed scroll and every intermediate item is bound (loaded) on the way.
 */
fun RecyclerView.smoothScrollToEnd(position: Int) {
    // Cancel any in-flight smooth scroll (e.g. a previous tap) first.
    stopScroll()
    val step = (resources.displayMetrics.density * 48f).toInt().coerceAtLeast(1)
    val runner = object : Runnable {
        override fun run() {
            // canScrollVertically(1) == false means we're at the real bottom.
            if (!canScrollVertically(1)) return
            scrollBy(0, step)
            postOnAnimation(this)
        }
    }
    postOnAnimation(runner)
}