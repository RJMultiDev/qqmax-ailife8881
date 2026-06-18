package momoi.mod.qqpro.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.watch.troop.ui.member.mvi.GroupMemberListIntent
import com.tencent.qqnt.watch.troop.ui.member.ui.GroupMemberFragment
import com.tencent.qqnt.watch.troop.ui.member.ui.item.GroupMemberBaseItem
import com.tencent.qqnt.watch.troop.ui.member.ui.item.GroupMemberItem
import com.tencent.qqnt.watch.troop.ui.member.ui.rv.GroupMemberAdapter
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import java.lang.ref.WeakReference

/**
 * 群成员列表改造 (watch-friendly): replaces the native group member list's in-list "邀请成员" row with a
 * compact top bar holding two small white-outline icons (drawn programmatically, no app resources) —
 * 加 (add/invite) and 搜索 (search). The member list scrolls below. Tapping 搜索 collapses both icons
 * into a single inline EditText on the left with an ✕ on the right; the ✕ exits search.
 *
 * We do NOT subclass the adapter (overriding its abstract VH methods via Mixin crashes with
 * AbstractMethodError). Instead an [RecyclerView.AdapterDataObserver] snapshots the native list, we
 * strip the invite item, and re-submit a members-only (optionally filtered) copy. The add icon fires
 * the same [GroupMemberListIntent.InvitedToGroup] intent the native invite row used.
 */
object MemberListSearch {
    private const val SEARCH_TAG = "qqpro_member_search"

    // Members only (invite row stripped); this is the source we filter from.
    private var fullList: List<GroupMemberItem> = emptyList()
    private var lastSubmittedByUs: List<GroupMemberBaseItem>? = null
    private var query = ""
    private var adapterRef: WeakReference<GroupMemberAdapter>? = null

    /** Wraps [root] with the top bar and returns the new root (or null to keep [root]). */
    fun install(fragment: GroupMemberFragment, root: View): View? {
        if (root.tag == SEARCH_TAG) return null
        val rv = findRecyclerView(root) ?: return null
        val adapter = rv.adapter as? GroupMemberAdapter ?: return null
        val ctx = root.context
        // The list has a fixed 18dp top margin (round-edge inset); our bar now provides that
        // spacing, so drop it to avoid a doubled gap. topMargin lives on MarginLayoutParams, so
        // it's safe despite the obfuscated ConstraintLayout fields.
        (rv.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0

        // Fresh state for this screen.
        fullList = emptyList()
        lastSubmittedByUs = null
        query = ""
        adapterRef = WeakReference(adapter)
        val fragmentRef = WeakReference(fragment)

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            tag = SEARCH_TAG
            // Stacked above the list; paddingTop provides the round-edge clearance.
            setPadding(10.dp, 10.dp, 10.dp, 6.dp)
        }

        val addIcon = icon(ctx, OutlineIcon.ADD)
        val searchIcon = icon(ctx, OutlineIcon.SEARCH)
        val et = EditText(ctx).apply {
            visibility = View.GONE
            hint = "搜索成员"
            setHintTextColor(0xFF_777777.toInt())
            setTextColor(0xFF_FFFFFF.toInt())
            textSize = 13f
            setSingleLine()
            setPadding(10.dp, 5.dp, 10.dp, 5.dp)
            background = GradientDrawable().apply {
                setColor(0xFF_222222.toInt())
                cornerRadius = 10.dp.toFloat()
            }
        }
        val exit = TextView(ctx).apply {
            visibility = View.GONE
            text = "✕"
            textSize = 15f
            setTextColor(0xFF_BBBBBB.toInt())
            setPadding(12.dp, 4.dp, 6.dp, 4.dp)
        }

        // icon() already gives each a fixed 22dp LayoutParams; just add a gap before the search icon.
        (searchIcon.layoutParams as LinearLayout.LayoutParams).marginStart = 22.dp
        bar.addView(addIcon)
        bar.addView(searchIcon)
        bar.addView(et, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(exit)

        fun enterSearch() {
            addIcon.visibility = View.GONE
            searchIcon.visibility = View.GONE
            et.visibility = View.VISIBLE
            exit.visibility = View.VISIBLE
            et.requestFocus()
            et.post {
                (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        fun exitSearch() {
            et.setText("")
            query = ""
            apply()
            et.visibility = View.GONE
            exit.visibility = View.GONE
            addIcon.visibility = View.VISIBLE
            searchIcon.visibility = View.VISIBLE
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(et.windowToken, 0)
        }

        addIcon.setOnClickListener {
            runCatching {
                val frag = fragmentRef.get() ?: return@runCatching
                val uids = fullList.mapNotNull { infoOf(it)?.uid }
                frag.b0(GroupMemberListIntent.InvitedToGroup(uids))
            }.onFailure { Utils.log("MemberSearch: invite failed: $it") }
        }
        searchIcon.setOnClickListener { enterSearch() }
        exit.setOnClickListener { exitSearch() }
        et.doAfterTextChanged {
            query = et.text?.toString()?.trim().orEmpty()
            apply()
        }

        // Stack the bar above the list (no overlap; the cleared RV margin keeps total height tight).
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            tag = SEARCH_TAG
        }
        wrap.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP))
        wrap.addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = onAdapterChanged()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onAdapterChanged()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onAdapterChanged()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = onAdapterChanged()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = onAdapterChanged()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = onAdapterChanged()
        })
        onAdapterChanged()
        Utils.log("MemberSearch: top bar installed")
        return wrap
    }

    /** Native list changed — snapshot members (drop the invite row) unless it's our own echo. */
    private fun onAdapterChanged() {
        val adapter = adapterRef?.get() ?: return
        val cur = runCatching { adapter.currentList }.getOrNull() ?: return
        if (cur == lastSubmittedByUs) return
        fullList = cur.filterIsInstance<GroupMemberItem>()
        apply()
    }

    private fun apply() {
        val adapter = adapterRef?.get() ?: return
        val q = query
        val result: List<GroupMemberBaseItem> =
            if (q.isEmpty()) fullList
            else fullList.filter { nameOf(it).contains(q, ignoreCase = true) }
        lastSubmittedByUs = result
        runCatching { adapter.submitList(result) }.onFailure { Utils.log("MemberSearch apply: $it") }
    }

    private fun icon(ctx: Context, kind: Int): ImageView = ImageView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(22.dp, 22.dp)
        setImageDrawable(OutlineIcon(kind))
    }

    private fun infoOf(item: GroupMemberItem): MemberInfo? = runCatching {
        // The MemberInfo field is obfuscated to `a` (and collides with method a()), so reflect it.
        item.javaClass.getDeclaredField("a").apply { isAccessible = true }.get(item) as? MemberInfo
    }.getOrNull()

    /** Best display name for a member; falls back to the item's own sort key [GroupMemberItem.b]. */
    private fun nameOf(item: GroupMemberItem): String {
        val info = infoOf(item)
        val name = info?.let {
            sequenceOf(it.remark, it.cardName, it.nick).firstOrNull { s -> !s.isNullOrBlank() }
        }
        return name ?: runCatching { item.b() }.getOrNull().orEmpty()
    }

    private fun findRecyclerView(v: View): RecyclerView? {
        if (v is RecyclerView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findRecyclerView(v.getChildAt(i))?.let { return it }
        }
        return null
    }

    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}

/** A white-outline-only icon drawn with strokes (no fill, no app resources). */
private class OutlineIcon(private val kind: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF_FFFFFF.toInt()
        strokeWidth = 2f.dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val pad = 3f.dp
        val l = b.left + pad; val t = b.top + pad
        val r = b.right - pad; val bottom = b.bottom - pad
        val cx = (l + r) / 2f; val cy = (t + bottom) / 2f
        val w = r - l
        when (kind) {
            ADD -> {
                val h = w * 0.45f
                canvas.drawLine(cx - h, cy, cx + h, cy, paint)
                canvas.drawLine(cx, cy - h, cx, cy + h, paint)
            }
            SEARCH -> {
                val rad = w * 0.30f
                val ccx = cx - w * 0.08f; val ccy = cy - w * 0.08f
                canvas.drawCircle(ccx, ccy, rad, paint)
                // handle, from the circle's lower-right edge outward
                val k = rad * 0.707f
                canvas.drawLine(ccx + k, ccy + k, ccx + k + w * 0.22f, ccy + k + w * 0.22f, paint)
            }
        }
    }

    override fun getIntrinsicWidth(): Int = (22f.dp).toInt()
    override fun getIntrinsicHeight(): Int = (22f.dp).toInt()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val ADD = 0
        const val SEARCH = 1
    }
}

private val Float.dp: Float get() = this * android.content.res.Resources.getSystem().displayMetrics.density

@Mixin
class GroupMemberListSearch : GroupMemberFragment() {
    override fun Y(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.Y(inflater, container, savedInstanceState)
        return runCatching { MemberListSearch.install(this, root) }
            .onFailure { Utils.log("MemberSearch: install failed: $it") }
            .getOrNull() ?: root
    }
}
