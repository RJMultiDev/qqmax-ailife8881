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