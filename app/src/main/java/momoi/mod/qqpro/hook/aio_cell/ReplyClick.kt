package momoi.mod.qqpro.hook.aio_cell

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.kernel.nativeinterface.ReplyElement
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.hook.view.BubbleTextView
import momoi.mod.qqpro.hook.view.smoothScrollToStart
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils

class ReplyClick(
    val widget: AIOCellGroupWidget,
    val reply: ReplyElement
) : View.OnClickListener {
    private var finding = false
    // Floating "加载中 …" pill shown while we page up looking for the source message, same idea
    // as the jump-to-first-unread progress. Removed once the search finishes.
    private var loading: TextView? = null

    override fun onClick(v: View?) {
        val rv = widget.parent as RecyclerView
        if (finding) {
            return
        }
        finding = true
        // Remember where we are now and show the back-down button so the user can return here.
        BubbleTextView.beginJumpUp()
        showLoading(rv, "加载中…")
        // Optionally drop the page-load cap so very old reply sources can still be located.
        val limit = if (Settings.replyFullSearch.value) Int.MAX_VALUE else 1000
        CurrentMsgList.findMsg(
            seq = reply.replayMsgSeq,
            onProgress = { loaded -> loading?.text = "加载中 $loaded" },
            result = { item ->
                finding = false
                hideLoading()
                if (item != null) {
                    rv.smoothScrollToStart(CurrentMsgList.getMsgIndex(item))
                } else {
                    Utils.toast(rv.context, "无法定位消息")
                }
            },
            repeatCount = limit
        )
    }

    @SuppressLint("SetTextI18n")
    private fun showLoading(rv: RecyclerView, text: String) {
        val decor = rv.rootView as? ViewGroup ?: return
        val tv = TextView(rv.context).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF_FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(20.dp, 12.dp, 20.dp, 12.dp)
            background = roundCornerDrawable(0xDD_303030.toInt(), 16.dp.toFloat())
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        decor.addView(tv, lp)
        loading = tv
    }

    private fun hideLoading() {
        loading?.let { tv -> (tv.parent as? ViewGroup)?.removeView(tv) }
        loading = null
    }
}
