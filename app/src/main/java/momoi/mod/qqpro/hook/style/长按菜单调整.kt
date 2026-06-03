package momoi.mod.qqpro.hook.style

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.forEach
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.watch.aio_impl.ui.cell.base.WatchAIOGroupWidgetItemCell
import com.tencent.watch.aio_impl.ui.menu.AIOLongClickMenuFragment
import com.tencent.watch.aio_impl.ui.menu.MenuItemFactory
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Colors
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.hook.forwardText
import momoi.mod.qqpro.asGroup
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.forEachAll
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.hook.action.CurrentGroupMembers
import momoi.mod.qqpro.hook.action.SelfContact
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.LinearScope
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.create
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.height
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.paddingHorizontal
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vh
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.Utils

val menuSort = arrayOf(
    "回复",
    "@Ta",
    "复制文本",
    "复读文本",
    "去聊天",
    "加好友",
    "删除",
)

private fun process(group: ViewGroup, msg: MsgRecord?, dismiss: () -> Unit) {
    group.removeViewAt(0)
    val linear = group.getChildAt(0).asGroup()
        .getChildAt(0).asGroup()
        .getChildAt(0) as LinearLayout
    linear.background(0x44_000000)
    val items = mutableMapOf<String, View>()
    linear.forEach { item ->
        item.asGroup().forEachAll {
            if (it is AppCompatTextView) {
                items[it.text.toString()] = item
            }
        }
    }
    linear.removeAllViews()
    LinearScope(linear).add<View>()
        .width(FILL)
        .height(if (Utils.isRoundScreen) 0.16f.vh else 0)
    if (Utils.isRoundScreen) {
        linear.paddingHorizontal(0.1f.vh)
    }
    if (msg != null && CurrentContact.isGroup) {
        CurrentGroupMembers.get(SelfContact.peerUid) {
            if (it.role == MemberRole.OWNER || it.role == MemberRole.ADMIN) {
                linear.post {
                    linear.addView(
                        create<TextView>(linear.context)
                            .width(FILL)
                            .gravity(Gravity.CENTER)
                            .padding(6.dp)
                            .text("撤回")
                            .textSize(16f)
                            .background(roundCornerDrawable(
                                color = Colors.replyBackground,
                                radius = 16.dpf
                            ))
                            .clickable {
                                KernelServiceUtil.c()?.recallMsg(CurrentContact, msg.msgId, null)
                            }
                    )
                }
            }
        }
    }
    menuSort.forEach {
        items[it]?.let { item ->
            linear.addView(item)
        }
    }
    items.values.forEach {
        if (it.parent == null) {
            linear.addView(it, 1)
        }
    }
    // 图片消息的"分享"就是转发到其它会话，改名为"转发"。
    var hasShare = false
    linear.forEachAll {
        if (it is AppCompatTextView && it.text?.toString() == "分享") {
            it.text = "转发"
            hasShare = true
        }
    }
    // 文本消息没有分享按钮。注入一个真正的"转发"(打开好友选择器把文本发到其它会话)，
    // 注意复读文本只能在当前会话重发，不能转发到别处。
    val fwdText = msg?.elements
        ?.mapNotNull { it.textElement?.content }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
    Utils.log("menu inject: hasShare=$hasShare fwdText=${fwdText?.take(20)} elems=${msg?.elements?.map { it.elementType }}")
    if (!hasShare && fwdText != null) {
        // Clone the native menu item layout (icon + desc + switch) so 转发 matches the other items.
        val ctx = linear.context
        val res = ctx.resources
        val pkg = ctx.packageName
        val itemView = LayoutInflater.from(ctx).inflate(
            res.getIdentifier("item_setting_with_switch", "layout", pkg), linear, false
        )
        itemView.findViewById<ImageView>(res.getIdentifier("icon", "id", pkg))
            ?.setImageResource(0x7e0805cd) // R.drawable.icon_share
        itemView.findViewById<TextView>(res.getIdentifier("desc", "id", pkg))
            ?.text = "转发"
        itemView.findViewById<View>(res.getIdentifier("button", "id", pkg))
            ?.visibility = View.GONE
        itemView.setOnClickListener {
            linear.forwardText(fwdText)
            dismiss()
        }
        linear.addView(itemView, 1)
    }
    if (Utils.isRoundScreen) {
        LinearScope(linear).add<View>()
            .width(FILL)
            .height(0.16f.vh)
    }
}

@Mixin
class 长按菜单调整(p0: (MenuItemFactory.ItemEnum) -> Unit, p1: String?) :
    AIOLongClickMenuFragment(p0, p1) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // For menus we create ourselves (e.g. the forward-history view) the callback
        // is not the native cell callback, so resolving the cell may fail — degrade
        // gracefully and just skip the cell-dependent parts (the 撤回 button).
        val msg = runCatching {
            val field = this.b.javaClass.getDeclaredField("b")
            field.isAccessible = true
            val cell = field.get(this.b) as WatchAIOGroupWidgetItemCell<*, *>
            cell.f()!!.d
        }.getOrNull() ?: runCatching {
            val msgId = arguments?.getLong("key_msg_id") ?: 0L
            CurrentMsgList.msgList.value.find { it.d.msgId == msgId }?.d
        }.getOrNull()
        Utils.log("menu: msg=${msg != null} msgId=${arguments?.getLong("key_msg_id")}")
        return super.onCreateView(inflater, container, savedInstanceState).apply {
            this.asGroup().getChildAt(0).asGroup().let { group ->
                process(group, msg) { dismiss() }
            }
        }
    }
}