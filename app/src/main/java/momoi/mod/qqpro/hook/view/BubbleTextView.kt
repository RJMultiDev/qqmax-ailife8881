package momoi.mod.qqpro.hook.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils

/**
 * The native chat unread bubble ([com.tencent.watch.aio_impl.reserve1.unreadbubble.UnreadBubbleVB])
 * drives this view through [setText] / [setBackgroundResource] / [setVisibility].
 *
 * We restyle it into a large pill anchored at the bottom-right, with only the left side
 * rounded (right side flush/cut by the screen edge):
 *  - blue "↓ N" while there are new/unread messages,
 *  - grey "↓" back-to-bottom button while merely scrolled up.
 *
 * Visibility for the grey back-to-bottom state follows scroll direction (shown while
 * scrolling down, hidden while scrolling up); the blue new-message count is always shown.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class BubbleTextView(context: Context) : TextView(context) {
    // Left corners fully rounded (semicircle), right corners square so it sits flush to the screen edge.
    private val blueBg = roundCornerDrawable(0xFF_22a6f2.toInt(), 9999f, 0f, 9999f, 0f)
    private val greyBg = roundCornerDrawable(0xCC_303030.toInt(), 9999f, 0f, 9999f, 0f)

    private var nativeWantsShow = false
    private var isCountMode = false
    private var hiddenByScrollUp = false
    private var scrollAttached = false

    init {
        gravity = Gravity.CENTER
        setTextColor(0xFF_FFFFFF.toInt())
        textSize = 14f
        setPadding(18.dp, 9.dp, 14.dp, 9.dp)
    }

    // Native K() sets aio_unread_bg (the blue count circle) — replace with our blue pill.
    override fun setBackgroundResource(resid: Int) {
        background = blueBg
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val t = text?.toString().orEmpty()
        isCountMode = t.isNotEmpty()
        if (isCountMode) {
            background = blueBg
            super.setText("↓ $t", type)
        } else {
            // Empty text means the back-to-bottom state: grey down-arrow pill.
            background = greyBg
            super.setText("↓", type)
        }
        applyVisibility()
    }

    // Native uses VISIBLE(0) for the count, INVISIBLE(4) for back-to-bottom, GONE(8) to hide.
    // Treat INVISIBLE as "show" so the back-to-bottom button is actually visible.
    override fun setVisibility(visibility: Int) {
        nativeWantsShow = visibility != View.GONE
        applyVisibility()
    }

    private fun applyVisibility() {
        val show = nativeWantsShow && (isCountMode || !hiddenByScrollUp)
        super.setVisibility(if (show) View.VISIBLE else View.GONE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachScrollListener(0)
    }

    private fun attachScrollListener(tries: Int) {
        if (scrollAttached || tries > 20) return
        val rv = runCatching { CurrentMsgList.vb.H }.getOrNull()
        if (rv == null) {
            postDelayed({ attachScrollListener(tries + 1) }, 200)
            return
        }
        scrollAttached = true
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                when {
                    // Scrolling down (towards the latest message) -> allow showing.
                    dy > 4 -> if (hiddenByScrollUp) {
                        hiddenByScrollUp = false
                        applyVisibility()
                    }
                    // Scrolling up (reading history) -> hide the grey back-to-bottom button.
                    dy < -4 -> if (!hiddenByScrollUp) {
                        hiddenByScrollUp = true
                        applyVisibility()
                    }
                }
            }
        })
        Utils.log("BubbleTextView scroll listener attached")
    }
}
