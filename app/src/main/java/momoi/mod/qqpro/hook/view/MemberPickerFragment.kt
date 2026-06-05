package momoi.mod.qqpro.hook.view

import android.graphics.Outline
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import loadPicUrl
import momoi.mod.qqpro.hook.action.CurrentGroupMembers
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.linearLayout
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.Utils
import moye.wearqq.AtElementArg
import moye.wearqq.IMEOperation

/**
 * Group-only member picker for the "@成员" panel option. Lists the current group's members
 * (no invite/add button — selection only). Picking a member inserts an @mention into the
 * input box and opens the input method, the same way the native @ flow does.
 */
class MemberPickerFragment : MyDialogFragment() {

    /**
     * uid="all" is QQ's marker for @全体成员 (mention everyone). [name] is the list display label;
     * [atNick] is the nickname handed to the @mention (the IME prepends "@" itself, so this must
     * NOT include a leading "@" — otherwise it renders as "@@全体成员").
     */
    private data class Entry(val uid: String, val uin: Long, val name: String, val atNick: String = name)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = inflater.context
        val root = LinearLayout(ctx).vertical()
        root.setBackgroundColor(0xF0_121212.toInt())

        val entries = buildEntries()
        Utils.log("MemberPicker: ${entries.size} entries")

        root.content {
            add<TextView>()
                .text("选择要@的成员")
                .textSize(15f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .width(FILL)
                .padding(top = 14.dp, bottom = 10.dp)

            val list = add<RecyclerView>().linearLayout()
            (list.layoutParams as LinearLayout.LayoutParams).apply {
                width = FILL
                height = 0
                weight = 1f
            }
            list.content(
                data = entries,
                factory = { rowView() },
                update = { entry ->
                    val avatar = getChildAt(0) as ImageView
                    val name = getChildAt(1) as TextView
                    name.text = entry.name
                    if (entry.uid == "all") {
                        avatar.setImageDrawable(null)
                        avatar.setBackgroundColor(0xFF_4FC3F7.toInt())
                    } else {
                        avatar.setBackgroundColor(0xFF_333333.toInt())
                        avatar.loadPicUrl("https://q.qlogo.cn/headimg_dl?dst_uin=${entry.uin}&spec=100")
                    }
                    clickable { pick(entry) }
                }
            )
        }
        return root
    }

    private fun buildEntries(): List<Entry> {
        val members = CurrentGroupMembers.info?.values
            ?.map { m: MemberInfo ->
                val name = m.remark.ifBlank { m.cardName.ifBlank { m.nick.ifBlank { m.uin.toString() } } }
                Entry(m.uid, m.uin, name)
            }
            ?.sortedBy { it.name }
            .orEmpty()
        return listOf(Entry("all", 0L, "@全体成员", atNick = "全体成员")) + members
    }

    private fun pick(entry: Entry) {
        runCatching {
            IMEOperation.INSTANCE.openIMEWithExtra(AtElementArg(entry.uid, entry.atNick, ""))
            Utils.log("MemberPicker: picked ${entry.name} uid=${entry.uid}")
        }.onFailure { Utils.log("MemberPicker: pick failed: $it") }
        dismiss()
    }

    /** A row: circular avatar on the left, member name on the right. */
    private fun rowView(): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
        row.setPadding(16.dp, 8.dp, 16.dp, 8.dp)

        val avatar = ImageView(ctx)
        avatar.layoutParams = LinearLayout.LayoutParams(36.dp, 36.dp)
        avatar.scaleType = ImageView.ScaleType.CENTER_CROP
        avatar.clipToOutline = true
        avatar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        row.addView(avatar)

        val name = TextView(ctx)
        name.textSize = 14f
        name.setTextColor(0xFF_FFFFFF.toInt())
        name.gravity = Gravity.CENTER_VERTICAL
        name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 12.dp
        }
        row.addView(name)
        return row
    }
}
