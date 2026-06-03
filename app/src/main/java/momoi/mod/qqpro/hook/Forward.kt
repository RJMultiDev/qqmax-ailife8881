package momoi.mod.qqpro.hook

import android.view.View
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.util.Utils
import mqq.app.MobileQQ

/**
 * Open QQ's friend selector, then send the built elements to each chosen target — a real "forward"
 * to another chat (unlike RepeatMsg/复读 which only resends in the current chat). Mirrors the native
 * DefaultMenuHandler.doSharePic flow but works for any element list.
 *
 * jar-obfuscated FriendSelectData fields: b = uid, e = isGroup.
 * 0x7e0805cd = R.drawable.icon_share.
 */
fun View.forwardToFriends(title: String = "转发", buildElements: () -> ArrayList<MsgElement>) {
    val navFragment = WatchPicElementExtKt.W(this)?.let { WatchPicElementExtKt.Y(it) }
    if (navFragment == null) {
        Utils.log("forward: no nav fragment")
        return
    }
    val app = MobileQQ.getMobileQQ().peekAppRuntime() ?: return
    val contactService = app.getRuntimeService(IContactRuntimeService::class.java, "")
    contactService.startFriendSelect(
        navFragment,
        emptyList(),
        arrayListOf(app.currentUid),
        title,
        0x7e0805cd,
        1, 10, null, false, true
    ) { _, friends ->
        if (friends.isNotEmpty()) {
            val elements = buildElements()
            friends.forEach { friend ->
                val contact = Contact(if (friend.e) 2 else 1, friend.b, "")
                MsgUtil.msgService.sendMsg(
                    contact, 0L, elements,
                    IOperateCallback { code, msg -> Utils.log("forward send result=$code msg=$msg") }
                )
            }
        }
        kotlin.Unit
    }
}

/** Forward plain text to selected friends/groups. */
fun View.forwardText(text: CharSequence) = forwardToFriends {
    ImeTextUtil.a.b(text.toString())
}
