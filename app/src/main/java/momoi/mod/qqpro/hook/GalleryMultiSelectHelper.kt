package momoi.mod.qqpro.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.watch.gallery.GalleryFragment
import com.tencent.watch.aio_impl.ext.MsgUtil as WatchMsgUtil
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.GalleryMultiSelectState
import momoi.mod.qqpro.lib.dp

private const val HTAG = "QQPro"

/** All multi-select state and logic, kept in a top-level class so its constructor is public. */
class GalleryMultiSelectHelper(private val fragment: GalleryFragment) {

    var multiSelectMode = false
    val selectedPaths = linkedSetOf<String>()
    var sendButton: TextView? = null

    // Set to true by gesture handlers when an UP event should be consumed
    // (long-press UP or single-tap-in-multiselect). Cleared on every ACTION_DOWN.
    private var interceptNextUp = false

    private val overlayPaint = Paint().apply { color = Color.parseColor("#661B9AF7") }

    // ---- public API called from the mixin hook --------------------------------

    fun buildSendButton(ctx: Context): TextView {
        return TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            background = roundCornerDrawable(Color.parseColor("#1B9AF7"), 9999f)
            setPadding(24.dp, 0, 24.dp, 0)
            text = "发送"
        }
    }

    fun setupGestureDetector(rv: RecyclerView) {
        val gesture = GestureDetector(rv.context, makeGestureListener(rv))
        rv.addOnItemTouchListener(makeTouchListener(gesture))
    }

    fun setupItemDecoration(rv: RecyclerView) {
        rv.addItemDecoration(makeItemDecoration(rv))
    }

    fun toggleSelection(path: String, rv: RecyclerView) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path)
        else selectedPaths.add(path)
        updateSendButton()
        rv.invalidateItemDecorations()
        if (multiSelectMode && selectedPaths.isEmpty()) exitMultiSelectMode(rv)
    }

    fun onSendClicked(rv: RecyclerView) {
        Log.e(HTAG, "GalleryMultiSelect: send button clicked, selected=${selectedPaths.size}")
        Utils.log("MultiSelect onSendClicked selected=${selectedPaths.size}")
        if (selectedPaths.isEmpty()) {
            Log.e(HTAG, "GalleryMultiSelect: nothing selected, aborting")
            Utils.log("MultiSelect nothing selected")
            return
        }

        val contact = Contact(CurrentContact.chatType, CurrentContact.peerUid, CurrentContact.guildId)
        Utils.log("MultiSelect contact=${CurrentContact.chatType}/${CurrentContact.peerUid}")
        val allItems = fragment.i?.currentList ?: emptyList()
        Utils.log("MultiSelect allItems=${allItems.size} selectedPaths=${selectedPaths.size}")
        val selectedItems = allItems.filter { selectedPaths.contains(it.c) }
        Utils.log("MultiSelect selectedItems=${selectedItems.size}")
        Log.e(HTAG, "GalleryMultiSelect: sending ${selectedItems.size} images")

        for (item in selectedItems) {
            Utils.log("MultiSelect building element for ${item.c}")
            try {
                val element = WatchMsgUtil.a.a(item.c, 0)
                Utils.log("MultiSelect element built, calling sendMsg")
                MsgUtil.msgService.sendMsg(contact, arrayListOf(element), null)
                Log.e(HTAG, "GalleryMultiSelect: sent ${item.c}")
                Utils.log("MultiSelect sendMsg called for ${item.c}")
            } catch (t: Throwable) {
                Log.e(HTAG, "GalleryMultiSelect: failed ${item.c}: ${t.message}")
                Utils.log("MultiSelect FAILED ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        Utils.log("MultiSelect calling exitMultiSelectMode")
        exitMultiSelectMode(rv)
        Utils.log("MultiSelect calling navigateToChatThenPop")
        navigateToChatThenPop()
        Utils.log("MultiSelect navigateToChatThenPop returned")
    }

    // ---- private helpers -------------------------------------------------------

    private fun navigateToChatThenPop() {
        // Gallery is a separate activity from the chat, so we can't touch the chat ViewPager here.
        // Signal WatchAIOFragment (chat activity) to switch to the chat page when it resumes.
        GalleryMultiSelectState.goToChatOnResume = true
        Utils.log("MultiSelect set goToChatOnResume=true, calling pop()")
        Log.e(HTAG, "GalleryMultiSelect: calling pop()")
        fragment.pop()
        Utils.log("MultiSelect pop() returned")
    }

    private fun enterMultiSelectMode() {
        multiSelectMode = true
        Log.e(HTAG, "GalleryMultiSelect: entered multi-select mode")
    }

    private fun exitMultiSelectMode(rv: RecyclerView) {
        multiSelectMode = false
        selectedPaths.clear()
        sendButton?.visibility = View.GONE
        rv.invalidateItemDecorations()
        Log.e(HTAG, "GalleryMultiSelect: exited multi-select mode")
    }

    private fun updateSendButton() {
        val btn = sendButton ?: return
        if (multiSelectMode && selectedPaths.isNotEmpty()) {
            btn.text = "发送 ${selectedPaths.size}"
            btn.visibility = View.VISIBLE
        } else {
            btn.visibility = View.GONE
        }
    }

    // ---- anonymous-class factories (all in this package, so constructors are accessible) ------

    private fun makeGestureListener(rv: RecyclerView) = object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            val child = rv.findChildViewUnder(e.x, e.y) ?: return
            val pos = rv.getChildAdapterPosition(child)
            if (pos < 0) return
            val item = fragment.i?.currentList?.getOrNull(pos) ?: return
            val path = item.c ?: return
            if (!multiSelectMode) enterMultiSelectMode()
            // consume the ACTION_UP that follows a long-press so the item's click doesn't fire
            interceptNextUp = true
            toggleSelection(path, rv)
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!multiSelectMode) return false
            val child = rv.findChildViewUnder(e.x, e.y) ?: return false
            val pos = rv.getChildAdapterPosition(child)
            if (pos < 0) return false
            val item = fragment.i?.currentList?.getOrNull(pos) ?: return false
            val path = item.c ?: return false
            // consume this UP event so the item's default click (open viewer) doesn't fire
            interceptNextUp = true
            toggleSelection(path, rv)
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    }

    private fun makeTouchListener(gesture: GestureDetector) = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    interceptNextUp = false
                    gesture.onTouchEvent(e)
                    return false  // always let DOWN through so scroll tracking starts
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gesture.onTouchEvent(e)
                    // interceptNextUp may have been set synchronously by onLongPress / onSingleTapUp above
                    val intercept = interceptNextUp
                    interceptNextUp = false
                    return intercept
                }
                else -> {
                    gesture.onTouchEvent(e)
                    return false  // never intercept MOVE → scrolling works in multi-select mode
                }
            }
        }
        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) { gesture.onTouchEvent(e) }
        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    private fun makeItemDecoration(rv: RecyclerView) = object : RecyclerView.ItemDecoration() {
        private val radius = 10.dp.toFloat()
        private val margin = 6.dp.toFloat()
        private val circleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B9AF7") }
        private val circleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!multiSelectMode) return
            val list = fragment.i?.currentList ?: return
            for (idx in 0 until parent.childCount) {
                val child = parent.getChildAt(idx)
                val pos = parent.getChildAdapterPosition(child)
                if (pos < 0) continue
                val item = list.getOrNull(pos) ?: continue
                val path = item.c ?: continue
                val selected = selectedPaths.contains(path)

                if (selected) c.drawRect(
                    child.left.toFloat(), child.top.toFloat(),
                    child.right.toFloat(), child.bottom.toFloat(), overlayPaint
                )

                val cx = child.right - margin - radius
                val cy = child.top + margin + radius

                if (selected) {
                    c.drawCircle(cx, cy, radius, circleFill)
                    val s = radius * 0.35f
                    c.drawLine(cx - s, cy, cx - s * 0.3f, cy + s * 0.8f, checkPaint)
                    c.drawLine(cx - s * 0.3f, cy + s * 0.8f, cx + s, cy - s * 0.6f, checkPaint)
                } else {
                    c.drawCircle(cx, cy, radius, circleBorder)
                }
            }
        }
    }
}
