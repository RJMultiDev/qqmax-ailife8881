package momoi.mod.qqpro.hook

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.watch.gallery.GalleryFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.lib.dp

@Mixin
class GalleryMultiSelect : GalleryFragment() {

    override fun Y(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val baseView = super.Y(inflater, container, savedInstanceState)

        val helper = GalleryMultiSelectHelper(this)

        val rv = findRecyclerView(baseView)
        if (rv != null) {
            helper.setupGestureDetector(rv)
            helper.setupItemDecoration(rv)
        } else {
            Log.e("QQPro", "GalleryMultiSelect: RecyclerView not found")
        }

        val ctx = inflater.context
        val wrapper = FrameLayout(ctx)
        wrapper.layoutParams = ViewGroup.LayoutParams(-1, -1)
        wrapper.addView(baseView, FrameLayout.LayoutParams(-1, -1))

        val btn = helper.buildSendButton(ctx)
        helper.sendButton = btn
        val lp = FrameLayout.LayoutParams(-2, 44.dp).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 16.dp
        }
        wrapper.addView(btn, lp)
        btn.visibility = View.GONE

        if (rv != null) {
            btn.setOnClickListener {
                Log.e("QQPro", "GalleryMultiSelect: send button view clicked")
                Utils.log("MultiSelect send button view clicked")
                helper.onSendClicked(rv)
                Utils.log("MultiSelect onSendClicked returned")
            }
        }

        return wrapper
    }

    private fun findRecyclerView(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view !is ViewGroup) return null
        for (idx in 0 until view.childCount) {
            val found = findRecyclerView(view.getChildAt(idx))
            if (found != null) return found
        }
        return null
    }
}
