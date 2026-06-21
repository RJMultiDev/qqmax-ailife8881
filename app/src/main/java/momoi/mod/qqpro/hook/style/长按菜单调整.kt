package momoi.mod.qqpro.hook.style

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.biz.richframework.util.RFWSaveUtil
import WatchPicElementExtKt
import download
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import com.tencent.watch.aio_impl.ui.cell.base.WatchAIOGroupWidgetItemCell
import com.tencent.watch.aio_impl.ui.menu.AIOLongClickMenuFragment
import com.tencent.watch.aio_impl.ui.menu.MenuItemFactory
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.hook.HistoryMsgRegistry
import momoi.mod.qqpro.hook.aio_cell.MarketFaceImage
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.CurrentGroupMembers
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.hook.action.MessageEdit
import momoi.mod.qqpro.hook.action.SelfContact
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.hook.copyImageFileToClipboard
import momoi.mod.qqpro.hook.copyImageToClipboard
import momoi.mod.qqpro.hook.forwardMsgRecord
import momoi.mod.qqpro.hook.forwardText
import momoi.mod.qqpro.hook.repeatMsgRecord
import momoi.mod.qqpro.hook.shareImageFile
import momoi.mod.qqpro.hook.shareMessage
import momoi.mod.qqpro.hook.view.ConfirmFragment
import momoi.mod.qqpro.hook.view.PartialCopyFragment
import momoi.mod.qqpro.hook.aio_cell.doAddFavEmoji
import momoi.mod.qqpro.child
import momoi.mod.qqpro.msg.getImageUrl
import momoi.mod.qqpro.safeCacheDir
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.lib.material.MaterialSymbol
import momoi.mod.qqpro.lib.material.MaterialSymbols
import momoi.mod.qqpro.util.Json
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.util.runOnUi
import java.io.File

/**
 * 长按菜单 Material 重建 — built entirely from scratch (no native rows): one ordered, data-driven
 * [Entry] list rendered as Material rows (Material Symbol icons, accent / error tint, rounded M3
 * card that clips while scrolling, ripple, dim scrim). Each action is either native (invoke the
 * fragment's callback `b` with the ItemEnum) or a QQPro custom one. Ordered most-used-first.
 *
 * Inside a 合并转发聊天记录 history viewer the message isn't live, so only read-only entries are shown
 * (no 删除/撤回/编辑/复读/回复). Rows are tagged with their order so the async 撤回 can be inserted in place.
 */
@Mixin
class 长按菜单调整(p0: (MenuItemFactory.ItemEnum) -> Unit, p1: String?) :
    AIOLongClickMenuFragment(p0, p1) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val msgId = arguments?.getLong("key_msg_id") ?: 0L
        val cellItem = runCatching {
            val field = this.b.javaClass.getDeclaredField("b")
            field.isAccessible = true
            (field.get(this.b) as WatchAIOGroupWidgetItemCell<*, *>).f()
        }.getOrNull()
        val liveItem = cellItem ?: CurrentMsgList.msgList.value.find { it.d.msgId == msgId }
        val isHistory = liveItem == null
        val msg = liveItem?.d ?: HistoryMsgRegistry.find(msgId)
        val fm = runCatching { parentFragmentManager }.getOrNull()
        Utils.log("menu: msg=${msg != null} history=$isHistory msgId=$msgId")
        return LongPressMenu.build(this, inflater, msg, liveItem, fm, isHistory)
    }
}

object LongPressMenu {

    private class Entry(
        val order: Int,
        val label: String,
        val symbol: String,
        val destructive: Boolean,
        val action: () -> Unit,
    )

    @SuppressLint("ClickableViewAccessibility")
    fun build(
        fragment: AIOLongClickMenuFragment,
        inflater: LayoutInflater,
        msg: MsgRecord?,
        msgItem: WatchAIOMsgItem?,
        fm: androidx.fragment.app.FragmentManager?,
        isHistory: Boolean,
    ): View {
        val ctx = inflater.context
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
        }

        buildEntries(fragment, card, msg, msgItem, fm, isHistory).sortedBy { it.order }.forEach { e ->
            card.addView(rowFor(fragment, e))
        }
        addRecall(fragment, card, msg, isHistory)

        // The rounded frame is the (fixed) ScrollView; rows scroll INSIDE it, clipped to the rounded
        // corners — so the corners stay put instead of scrolling off with the content.
        val scroll = ScrollView(ctx).apply {
            background = GradientDrawable().apply { setColor(M3.surfaceContainer); cornerRadius = M3.radiusLg }
            clipToOutline = true
            setPadding(0, 6.dp, 0, 6.dp)
            isVerticalScrollBarEnabled = false
            isClickable = true
            addView(card, FrameLayout.LayoutParams(MP, WC))
        }
        val scrim = FrameLayout(ctx).apply {
            setBackgroundColor(0x99_000000.toInt())
            setOnClickListener { runCatching { fragment.dismiss() } }
            addView(scroll, FrameLayout.LayoutParams(MP, WC, Gravity.CENTER).apply {
                val m = 14.dp; leftMargin = m; rightMargin = m; topMargin = m; bottomMargin = m
            })
        }
        return SwipeBackLayout(ctx).apply {
            onSwipeBack = { runCatching { fragment.dismiss() } }
            addView(scrim, FrameLayout.LayoutParams(MP, MP))
        }
    }

    private fun buildEntries(
        fragment: AIOLongClickMenuFragment,
        host: View,
        msg: MsgRecord?,
        msgItem: WatchAIOMsgItem?,
        fm: androidx.fragment.app.FragmentManager?,
        isHistory: Boolean,
    ): List<Entry> {
        val names = runCatching { fragment.requireArguments().getStringArrayList("key_item_list") }
            .getOrNull().orEmpty().toSet()
        val out = ArrayList<Entry>()
        fun add(order: Int, label: String, symbol: String, destructive: Boolean = false, action: () -> Unit) =
            out.add(Entry(order, label, symbol, destructive, action))
        fun native(name: String): () -> Unit = {
            runCatching { fragment.b.invoke(MenuItemFactory.ItemEnum.valueOf(name)) }
                .onFailure { Utils.log("menu native $name: $it") }
        }

        val isArk = msg?.elements?.any { it.arkElement != null } == true
        val fwdText = msg?.elements?.mapNotNull { it.textElement?.content }?.joinToString("")
            ?.takeIf { it.isNotBlank() }
        // Copyable text: plain text, or — for an ark card (share / mini-app / etc.) — its prompt.
        val copyText = fwdText ?: (if (isArk) arkText(msg) else null)
        // Resendable (sendMsg-rebuildable) media; ark / file / combined-forward are NOT.
        val forwardable = msg?.elements?.any {
            it.faceElement != null || it.marketFaceElement != null || it.videoElement != null ||
                it.pttElement != null || it.picElement != null || it.giphyElement != null ||
                it.faceBubbleElement != null
        } == true
        val hasPic = msg?.elements?.any { it.picElement != null } == true
        val mfFile: File? = msg?.takeIf { r -> r.elements?.any { it.marketFaceElement != null } == true }
            ?.let { MarketFaceImage.fileFor(it.msgId) }
        val hasShareableMedia = msg?.elements?.any {
            it.picElement != null || it.videoElement != null || it.pttElement != null
        } == true
        val isSelf = msg != null && msg.senderUid == SelfContact.peerUid

        // 1 回复 (live only)
        if (!isHistory && "ReplyMsg" in names) add(1, "回复", MaterialSymbols.reply, action = native("ReplyMsg"))
        // 2 复制 (works for ark via its prompt; native toast breaks at watch DPI so copy ourselves)
        if (copyText != null) add(2, "复制", MaterialSymbols.content_copy) { Utils.copyToClipboard(host.context, copyText) }
        else if ("CopyMsg" in names) add(2, "复制", MaterialSymbols.content_copy, action = native("CopyMsg"))
        // 3 撤回 (own message, native) — red; non-own admin recall added async in addRecall
        if (!isHistory && isSelf && "RevokeMsg" in names)
            add(3, "撤回", MaterialSymbols.undo, destructive = true, action = native("RevokeMsg"))
        // 4 编辑 (own message WITH text), just below 撤回 — media-only messages have nothing to edit.
        if (!isHistory && isSelf && msg != null && fwdText != null)
            add(4, "编辑", MaterialSymbols.edit) { MessageEdit.beginFull(msg) }
        // 5 部分复制 (text or ark)
        if (copyText != null && fm != null) add(5, "部分复制", MaterialSymbols.content_copy) {
            runCatching { PartialCopyFragment(copyText).show(fm, "qqpro_partial_copy") }
        }
        // 6 复制图片
        if (hasPic && msg != null) add(6, "复制图片", MaterialSymbols.image) { host.copyImageToClipboard(msg, msgItem) }
        else if (mfFile != null) add(6, "复制图片", MaterialSymbols.image) { host.copyImageFileToClipboard(mfFile) }
        // 7 转发 (to other chats)
        if (msg != null && (fwdText != null || forwardable)) add(7, "转发", MaterialSymbols.forward) {
            if (forwardable) host.forwardMsgRecord(msg) else if (fwdText != null) host.forwardText(fwdText)
        }
        // 8 复读 (resend whole message; not ark/file/combined; live only) — below 转发
        if (!isHistory && msg != null && (forwardable || fwdText != null) && !isArk)
            add(8, "复读", MaterialSymbols.repeat) { repeatMsgRecord(msg) }
        // 9 系统分享 (to other apps)
        if (mfFile != null) add(9, "系统分享", MaterialSymbols.send) { host.shareImageFile(mfFile) }
        else if (msg != null && (fwdText != null || hasShareableMedia)) add(9, "系统分享", MaterialSymbols.send) { host.shareMessage(msg, msgItem) }
        // 10 收藏 / 11 保存. Native dispatch (which shows its own toast) when the cell offers it;
        // otherwise our own for the in-bubble image / marketface that the native menu omits.
        val picEl: PicElement? = msg?.elements?.firstNotNullOfOrNull { it.picElement }
        when {
            "SaveFavEmoji" in names -> add(10, "收藏", MaterialSymbols.star, action = native("SaveFavEmoji"))
            picEl != null -> add(10, "收藏", MaterialSymbols.star) { withPicFile(host, picEl) { f -> doAddFavEmoji(host.context, f) } }
            mfFile != null -> add(10, "收藏", MaterialSymbols.star) { doAddFavEmoji(host.context, mfFile) }
        }
        when {
            "SavePic" in names -> add(11, "保存", MaterialSymbols.download, action = native("SavePic"))
            picEl != null -> add(11, "保存", MaterialSymbols.download) { withPicFile(host, picEl) { f -> saveFileTo(host, f) } }
            mfFile != null -> add(11, "保存", MaterialSymbols.download) { saveFileTo(host, mfFile) }
        }
        // 12-14 翻译 / 隐藏翻译 / 朗读 (live only)
        if (!isHistory && "TranslateText" in names) add(12, "翻译", MaterialSymbols.translate, action = native("TranslateText"))
        if (!isHistory && "HideTranslateText" in names) add(13, "隐藏翻译", MaterialSymbols.translate, action = native("HideTranslateText"))
        if (!isHistory && "SpeakText" in names) add(14, "朗读", MaterialSymbols.volume_up, action = native("SpeakText"))
        // 15 删除 (local delete, live only) — red, last
        val deleteId = msg?.msgId
        if (!isHistory && deleteId != null && deleteId != 0L && msg != null)
            add(15, "删除", MaterialSymbols.delete, destructive = true) {
                // The menu has dismissed; show the confirm on the chat fragment's manager.
                val doDelete = {
                    val contact = Contact(msg.chatType, msg.peerUid, "")
                    runCatching {
                        MsgUtil.msgService.deleteMsg(contact, arrayListOf(deleteId), IOperateCallback { code, reason ->
                            Utils.log("menu delete: id=$deleteId code=$code reason=$reason")
                            runOnUi {
                                if (code == 0) { CurrentMsgList.removeLive(setOf(deleteId)); Utils.toast(host.context, "删除成功") }
                                else Utils.toast(host.context, "删除失败")
                            }
                        })
                    }.onFailure { Utils.log("menu delete failed: $it"); Utils.toast(host.context, "删除失败") }
                }
                if (fm != null) runCatching {
                    ConfirmFragment("仅从本机删除此消息，不会撤回，对方仍可看到。", "删除", destructive = true) { doDelete() }
                        .show(fm, "qqpro_delete_confirm")
                }.onFailure { doDelete() } else doDelete()
            }

        return out
    }

    /**
     * 撤回 row logic: own message uses the native revoke (added in [buildEntries]). For others'
     * messages in a group the role check is async: owner can recall anyone; admin only a normal
     * member. Inserted at the order-3 position (above 编辑) via the row tags.
     */
    private fun addRecall(fragment: AIOLongClickMenuFragment, card: LinearLayout, msg: MsgRecord?, isHistory: Boolean) {
        if (isHistory || msg == null) return
        val recallId = msg.msgId
        if (recallId == 0L) return
        if (msg.senderUid == SelfContact.peerUid) return // own → native revoke handled in buildEntries
        if (!CurrentContact.isGroup) return
        val targetUid = msg.senderUid
        fun addRow() = runOnUi {
            val e = Entry(3, "撤回", MaterialSymbols.undo, true) {
                runCatching { KernelServiceUtil.c()?.recallMsg(CurrentContact, recallId, null) }
                    .onFailure { Utils.log("menu recall failed: $it") }
            }
            insertByOrder(card, rowFor(fragment, e), 3)
        }
        CurrentGroupMembers.get(SelfContact.peerUid) { self ->
            when (self.role) {
                MemberRole.OWNER -> addRow() // owner: recall anyone
                MemberRole.ADMIN -> CurrentGroupMembers.get(targetUid) { target ->
                    // admin: only normal members (not owner / other admins)
                    if (target.role != MemberRole.OWNER && target.role != MemberRole.ADMIN) addRow()
                }
                else -> {}
            }
        }
    }

    /** Save a captured media [file] (marketface/sticker) to the gallery, with feedback. */
    private fun saveFileTo(host: View, file: File) {
        runCatching { RFWSaveUtil.a(host.context, file.path, null); Utils.toast(host.context, "已保存到相册") }
            .onFailure { Utils.log("menu save file: $it"); Utils.toast(host.context, "保存失败") }
    }

    /**
     * Resolve a picture message's local file, then run [use] with it. Live chat images are owned by
     * the kernel (resolve its local path); fall back to our cache / an HTTP download (history images
     * carry a real url). Runs [use] on the UI thread.
     */
    private fun withPicFile(host: View, pic: com.tencent.qqnt.kernel.nativeinterface.PicElement, use: (File) -> Unit) {
        runCatching { WatchPicElementExtKt.C0(pic) }.getOrNull()
            ?.takeIf { it.isNotEmpty() && File(it).let { f -> f.exists() && f.length() > 0 } }
            ?.let { use(File(it)); return }
        val cacheDir = host.context.safeCacheDir ?: run { Utils.toast(host.context, "保存失败"); return }
        val cacheFile = cacheDir.child("${pic.md5HexStr}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) { use(cacheFile); return }
        download(pic.getImageUrl(), cacheFile) { ok ->
            runOnUi { if (ok) use(cacheFile) else Utils.toast(host.context, "保存失败") }
        }
    }

    /** Ark card display text for copy — the `prompt` summary (e.g. "[分享]标题"). Non-destructive. */
    private fun arkText(msg: MsgRecord): String? = runCatching {
        val ark = msg.elements.firstNotNullOfOrNull { it.arkElement } ?: return null
        Json(ark.bytesData).str("prompt")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun rowFor(fragment: AIOLongClickMenuFragment, e: Entry): View =
        row(fragment.requireContext(), e) { runCatching { e.action() }; runCatching { fragment.dismiss() } }
            .apply { tag = e.order }

    private fun insertByOrder(card: LinearLayout, rowView: View, order: Int) {
        val idx = (0 until card.childCount).firstOrNull { (card.getChildAt(it).tag as? Int ?: Int.MAX_VALUE) > order }
        if (idx != null) card.addView(rowView, idx) else card.addView(rowView)
    }

    private fun row(ctx: Context, e: Entry, onClick: () -> Unit): View {
        val tint = if (e.destructive) M3.error else M3.primary
        val textColor = if (e.destructive) M3.error else M3.onSurface
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            isClickable = true
            background = RippleDrawable(ColorStateList.valueOf(0x33_888888), null, ColorDrawable(Color.WHITE))
            setOnClickListener { onClick() }
            addView(ImageView(ctx).apply { setImageDrawable(MaterialSymbol(e.symbol, tint)) },
                LinearLayout.LayoutParams(22.dp, 22.dp))
            addView(TextView(ctx).apply { text = e.label; setTextColor(textColor); textSize = 15f },
                LinearLayout.LayoutParams(0, WC, 1f).apply { marginStart = 16.dp })
        }
    }

    private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT
}
