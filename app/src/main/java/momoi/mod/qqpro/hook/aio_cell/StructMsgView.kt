package momoi.mod.qqpro.hook.aio_cell

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.qqnt.kernel.nativeinterface.StructMsgElement
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.EXTRA_SEARCH_PREFILL
import java.lang.ref.WeakReference
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.Utils

/**
 * Renders [StructMsgElement] (msgType 4) XML cards that the watch otherwise
 * dumps to the "view on phone" placeholder. Covers group invites
 * (serviceID 128, "邀请你加入群聊") and falls back to title/summary for other
 * struct shares.
 */
class StructMsgView(context: Context) : LinearLayout(context) {
    private lateinit var mTvTitle: TextView
    private lateinit var mTvSummary: TextView

    init {
        vertical()
        padding(6.dp)
        background(roundCornerDrawable(0x33_FFFFFF, Settings.bubbleCornerRadius.value.dpf))
        content {
            mTvTitle = add<TextView>()
                .width(FILL)
                .textSize(13f * Settings.chatScale.value)
                .textColor(0xFF_FFFFFF.toInt())
            mTvSummary = add<TextView>()
                .width(FILL)
                .textSize(11f * Settings.chatScale.value)
                .textColor(0xFF_CCCCCC.toInt())
        }
    }

    fun loadData(struct: StructMsgElement) {
        try {
            val xml = struct.xmlContent ?: ""
            val title = attr(xml, "groupname")
                ?: tag(xml, "title")
                ?: attr(xml, "brief")
                ?: "[链接]"
            val invite = tag(xml, "title") ?: attr(xml, "brief")?.removePrefix("[链接]")
            val groupCode = attr(xml, "groupcode")

            mTvTitle.text = title
            val summary = buildString {
                if (!invite.isNullOrEmpty()) append(invite)
                if (!groupCode.isNullOrEmpty()) {
                    if (isNotEmpty()) append("  ")
                    append("群号 $groupCode")
                }
            }
            if (summary.isNotEmpty()) {
                mTvSummary.visibility = VISIBLE
                mTvSummary.text = summary
            } else {
                mTvSummary.visibility = GONE
            }

            // Tapping a group invite opens the add/search numeric pad with the
            // group code prefilled (the search also returns groups).
            if (!groupCode.isNullOrEmpty()) {
                isClickable = true
                setOnClickListener { openGroupSearch(groupCode) }
            } else {
                setOnClickListener(null)
                isClickable = false
            }
        } catch (e: Exception) {
            Utils.log("StructMsgView error: ${e.message}")
        }
    }

    private fun openGroupSearch(code: String) {
        try {
            // The app's androidx.navigation is obfuscated (Navigation.findNavController
            // and NavController.navigate are renamed at runtime), so resolve the
            // NavController from the view-tree tag and call navigate via reflection.
            val nav = findNavController() ?: run {
                Utils.log("openGroupSearch: NavController not found in view tree")
                return
            }
            val actionId = resources.getIdentifier(
                "select_fragment_to_add_friend", "id", context.packageName
            )
            if (actionId == 0) {
                Utils.log("openGroupSearch: select_fragment_to_add_friend id not found")
                return
            }
            val args = Bundle().apply {
                putInt("type", 1)
                putString(EXTRA_SEARCH_PREFILL, code)
            }
            // navigate(int destId, Bundle args, NavOptions options) — name obfuscated.
            val navigate = nav.javaClass.methods.firstOrNull { m ->
                val p = m.parameterTypes
                p.size == 3 && p[0] == Int::class.javaPrimitiveType && p[1] == Bundle::class.java
            } ?: run {
                Utils.log("openGroupSearch: navigate(int,Bundle,..) not found on ${nav.javaClass.name}")
                return
            }
            navigate.invoke(nav, actionId, args, null)
        } catch (e: Exception) {
            Utils.log("openGroupSearch error: ${e.message}")
        }
    }

    /** Replicates androidx Navigation.findNavController(View) without the (obfuscated) static. */
    private fun findNavController(): Any? {
        val tagId = resources.getIdentifier("nav_controller_view_tag", "id", context.packageName)
        if (tagId == 0) return null
        var v: View? = this
        while (v != null) {
            when (val tag = v.getTag(tagId)) {
                is WeakReference<*> -> tag.get()?.let { return it }
                null -> {}
                else -> return tag
            }
            v = v.parent as? View
        }
        return null
    }

    private fun attr(xml: String, name: String): String? =
        Regex("$name=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    private fun tag(xml: String, name: String): String? =
        Regex("<$name[^>]*>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
}
