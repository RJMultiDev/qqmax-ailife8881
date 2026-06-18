package momoi.mod.qqpro.hook.aio_cell

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.View
import com.tencent.mobileqq.text.style.EmoticonSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import com.tencent.watch.aio_impl.ui.cell.base.BaseWatchItemCell
import com.tencent.watch.aio_impl.ui.cell.unsupport.WatchToQQViewMsgItem
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.enums.NTMsgType
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.CurrentGroupMembers
import momoi.mod.qqpro.hook.action.CurrentMsgList
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.lib.create
import momoi.mod.qqpro.hook.parseAtMembers
import momoi.mod.qqpro.util.linkify
import momoi.mod.qqpro.util.parseHexColor
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.warpOnce
import android.widget.LinearLayout
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private fun lpName(v: Int?) = when (v) {
    null -> "?"
    ViewGroup.LayoutParams.MATCH_PARENT -> "FILL"
    ViewGroup.LayoutParams.WRAP_CONTENT -> "WRAP"
    else -> v.toString()
}

object AIOCell {
    val AIOCellGroupWidget.contentWidget get() = this.getContentWidget<View>()!!
    val hooks = mutableListOf<Hook<*>>()

    // Per-TextView native (pre-scale) text size in px, captured once so the size multiplier is
    // applied against the original size and never compounds across rebinds.
    private val baseTextSize = WeakHashMap<TextView, Float>()

    // Per-EmoticonSpan native (pre-scale) emoji size in px, captured once so the multiplier is
    // applied against the original size and never compounds if applyMsgTextStyle runs twice.
    private val baseEmojiSize = WeakHashMap<EmoticonSpan, Int>()

    /**
     * Apply the user's chat text color / size overrides to every TextView under [view]
     * (recursively). Used for all message bodies — plain text, text+image and the special-cell
     * views (reply/forward/card/struct/file) — so the style is consistent everywhere.
     */
    fun applyMsgTextStyle(view: View?) {
        view ?: return
        val color = parseHexColor(Settings.textColor.value)
        val scale = Settings.textSizeScale.value
        if (color == null && scale == 1.0f) return
        fun walk(v: View) {
            if (v is TextView) {
                color?.let { v.setTextColor(it) }
                if (scale != 1.0f) {
                    val base = baseTextSize.getOrPut(v) { v.textSize }
                    v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, base * scale)
                    // QQ builds inline face emoji as fixed-size EmoticonSpans (QQ's default chat
                    // text size), so they stay small while the text scales up. Re-size each emoji
                    // span by the same multiplier so it matches the surrounding text.
                    (v.text as? Spanned)
                        ?.getSpans(0, v.text.length, EmoticonSpan::class.java)
                        ?.forEach { span ->
                            val emBase = baseEmojiSize.getOrPut(span) { span.c }
                            val newSize = (emBase * scale).toInt()
                            if (newSize > 0) span.h(newSize)
                        }
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
    }

    init {
        addHook<ReplyView>(
            type = NTMsgType.REPLY,
            onBind = { msg, widget ->
                val reply = msg.elements.firstNotNullOf { it.replyElement }
                loadData(CurrentContact, reply)
                setOnClickListener(ReplyClick(widget, reply))
            },
            appendMode = true
        )
        addHook<ForwardMsgView>(
            type = NTMsgType.MULTIMSGFORWARD,
            onBind = { msg, widget ->
                if (msg.forwardData == null) {
                    msg.forwardData = ForwardMsgData(CurrentContact, msg, msg)
                }
                loadData(CurrentContact, msg.forwardData!!)
            },
        )
        addHook<CardMsgView>(
            type = NTMsgType.ARKSTRUCT,
            onBind = { msg, widget ->
                loadData(msg.elements.firstNotNullOf { it.arkElement })
            }
        )
        addHook<StructMsgView>(
            type = NTMsgType.STRUCT,
            onBind = { msg, widget ->
                loadData(msg.elements.firstNotNullOf { it.structMsgElement })
            }
        )
        // File transfers (local FILE and group ONLINEFILE) otherwise fall through
        // to the orange "view on phone" placeholder; render a name + size card.
        val fileBind: FileMsgView.(MsgRecordEx, AIOCellGroupWidget) -> Unit = { msg, widget ->
            loadData(msg.elements.firstNotNullOf { it.fileElement })
        }
        addHook<FileMsgView>(type = NTMsgType.FILE, onBind = fileBind)
        addHook<FileMsgView>(type = NTMsgType.ONLINEFILE, onBind = fileBind)
    }

    inline fun <reified T : View> addHook(
        type: Int,
        noinline onBind: T.(MsgRecordEx, AIOCellGroupWidget) -> Unit,
        appendMode: Boolean = false
    ) {
        hooks.add(
            Hook(
                type = type,
                onBind = onBind,
                createView = { create<T>(it) },
                appendMode = appendMode
            )
        )
    }

    class Hook<T : View>(
        val type: Int,
        private val onBind: T.(MsgRecordEx, AIOCellGroupWidget) -> Unit,
        val createView: (Context) -> T,
        val appendMode: Boolean
    ) {
        private val views = WeakHashMap<AIOCellGroupWidget, WeakReference<T>>()
        @Suppress("UNCHECKED_CAST")
        fun bind(widget: AIOCellGroupWidget, view: View, msg: MsgRecordEx) {
            view.visibility = View.VISIBLE
            if (!appendMode) {
                widget.contentWidget.visibility = View.GONE
            }
            onBind(view as T, msg, widget)
        }

        fun getOrCreate(widget: AIOCellGroupWidget): T {
            return views.getOrPut(widget) {
                val view = createView(widget.context)
                val warp = widget.contentWidget.warpOnce()
                warp.addView(view, 0)
                WeakReference(view)
            }.get()!!
        }

        fun recover(widget: AIOCellGroupWidget) {
            views[widget]?.get()?.let {
                it.visibility = View.GONE
                if (!appendMode) {
                    widget.contentWidget.visibility = View.VISIBLE
                }
            }
        }
    }

    @Mixin
    abstract class HookCell : BaseWatchItemCell<WatchAIOMsgItem, View>() {
        @SuppressLint("SetTextI18n")
        override fun i(
            view: View,
            item: WatchAIOMsgItem,
            p3: Int,
            p4: List<Any>,
            p5: Lifecycle,
            p6: LifecycleOwner?
        ) {
            super.i(view, item, p3, p4, p5, p6)
            // Grey-tip cells (WatchGrayTipsCell) have a bare TextView as their root view —
            // not an AIOCellGroupWidget — and the native cell sets no movement method, so
            // the member-name spans built into tipsContent (see GrayTipMention.kt) are
            // inert. Enable them here; the spans themselves are created at decode time.
            if (Settings.parseAtMember.value && view is TextView) {
                if (view.movementMethod !is LinkMovementMethod) {
                    view.movementMethod = LinkMovementMethod.getInstance()
                    view.highlightColor = 0x33888888
                }
            }
            val widget = view as? AIOCellGroupWidget ?: return
            run {
                val senderUid = item.d.senderUid
                val nickView = widget.getNickWidget<TextView>()
                // hide the avatar/nick header when the previous (older) message in
                // the list is from the same sender, so consecutive messages only
                // show the header once. Applies in both group AND DM chats.
                val hideHeader = Settings.hideRepeatedSender.value
                    && item.d.msgType != NTMsgType.GRAYTIPS
                    && run {
                        val idx = CurrentMsgList.getMsgIndex(item)
                        val prev = CurrentMsgList.msgList.value.getOrNull(idx - 1)
                        prev != null && prev.d.msgType != NTMsgType.GRAYTIPS
                            && prev.d.senderUid == senderUid
                    }
                if (hideHeader) {
                    // Keep the view VISIBLE so the widget's onMeasure measures it
                    // (it reads getMeasuredHeight() ignoring visibility); collapse
                    // it to zero height instead. Using GONE would leave a stale
                    // measured height and cause random large gaps.
                    nickView?.let {
                        it.tag = null
                        it.visibility = View.VISIBLE
                        it.text = ""
                        it.setCompoundDrawables(null, null, null, null)
                        it.layoutParams = it.layoutParams?.apply { height = 0 }
                    }
                } else {
                    nickView?.let {
                        it.visibility = View.VISIBLE
                        it.layoutParams = it.layoutParams?.apply {
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        it.tag = senderUid
                    }
                    // The custom avatar/nick rebind is group-only: it relies on the
                    // group-member lookup and group-card naming. In DM, leave the
                    // header as the native super.i() rendered it (only the collapse
                    // above is our doing) so consecutive-message combining still works.
                    if (CurrentContact.isGroup) {
                        // Avatar depends only on the message record, so apply it now and
                        // unconditionally — never gate it on the async member lookup, which
                        // silently drops self / missing members and would otherwise leave a
                        // recycled cell showing the previous sender's avatar.
                        GroupAvatarHook.bindAvatar(widget, item.d)
                        // Nick text needs member info, so it follows the member callback.
                        CurrentGroupMembers.get(senderUid) { member ->
                            val apply = {
                                if (widget.getNickWidget<TextView>()?.tag == senderUid) {
                                    GroupAvatarHook.bindNick(widget, item.d, member)
                                }
                            }
                            // Cache hits call back synchronously on the main thread during this bind
                            // pass — apply immediately so the leveled nick replaces the native text in
                            // the SAME frame (no one-frame flicker while scrolling). Only the async
                            // (kernel network) callback, off the main thread, needs to post.
                            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) apply()
                            else widget.post(apply)
                        }
                    }
                }
            }
            // Resolve the single matching hook first, then recover every other
            // hook BEFORE binding the match. recover() re-shows contentWidget when
            // that hook previously owned a view for this (recycled) widget; running
            // it after bind() would re-reveal the orange "view on phone" text on top
            // of e.g. the ark card. Binding last guarantees contentWidget ends hidden.
            val matched = hooks.firstOrNull { item.d.msgType == it.type }
            hooks.forEach { if (it !== matched) it.recover(widget) }
            var matchedView: View? = null
            matched?.let {
                val view = it.getOrCreate(widget)
                matchedView = view
                it.bind(widget, view, item.d as MsgRecordEx)
                (item as? WatchToQQViewMsgItem)?.o = ""
            }
            // Only linkify the native text bubble. Special messages (file/struct/
            // ark/forward) hide contentWidget and render their own view, so running
            // linkify on it would e.g. match a file extension in the hidden text.
            // Append-mode hooks (reply) keep contentWidget, so still linkify those.
            if (matched == null || matched.appendMode) {
                (widget.contentWidget as? TextView)?.let {
                    it.linkify()
                    it.parseAtMembers()
                    // Reset the content AND every wrapper LinearLayout it sits inside back to
                    // "hug content". When this cell is recycled from one that had a
                    // +1/link-preview/special view, the content stays wrapped in one (or, from
                    // older builds, several NESTED) vertical LinearLayouts whose lp still carries
                    // weight=1 (FILL/0/1f). A weighted child balloons to fill all remaining vertical
                    // space — a one-line message rendered a full-screen-tall bubble. WRAP alone
                    // doesn't undo weight; only weight=0 does, and the weight may live on an
                    // intermediate wrapper, not the TextView. Walk the whole chain up to the bubble
                    // FrameLayout. LinkPreview re-asserts FILL/weight on the content afterwards when
                    // a preview is actually present, so this only neutralises plain text.
                    it.layoutParams?.let { lp ->
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        (lp as? LinearLayout.LayoutParams)?.weight = 0f
                    }
                    var p = it.parent
                    while (p is LinearLayout) {
                        (p.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                            lp.weight = 0f
                        }
                        p = p.parent
                    }
                    it.requestLayout()
                    // Diagnostic: dump the full ancestor lp chain for wrapped plain-text cells so we
                    // can see exactly what is forcing the bubble height on the real device.
                    if (it.parent is LinearLayout) {
                        val sb = StringBuilder("bubble-chain text='${it.text?.take(8)}' ")
                        var v: View? = it
                        var depth = 0
                        while (v != null && depth < 8) {
                            val lp = v.layoutParams
                            val w = lp?.width; val h = lp?.height
                            val wt = (lp as? LinearLayout.LayoutParams)?.weight
                            sb.append("\n  [${depth}] ${v.javaClass.simpleName} lp=${lpName(w)}x${lpName(h)} wt=$wt mH=${v.measuredHeight} vis=${v.visibility}")
                            v = v.parent as? View
                            depth++
                        }
                        Utils.log(sb.toString())
                    }
                }
            }
            // Apply the user's message text color / size override to ALL message text, not just
            // plain-text bubbles: recurse the content body (covers text+image and other mixed
            // cells) and the matched special-cell view (reply/forward/card/struct/file). The
            // nick/time header lives outside contentWidget, so it's left untouched.
            applyMsgTextStyle(runCatching { widget.contentWidget }.getOrNull())
            applyMsgTextStyle(matchedView)
            BubbleCorner.apply(widget)
            // Same guard as linkify: don't run link preview off a special message's
            // hidden contentWidget text (e.g. a file extension matched as a URL).
            if (matched == null || matched.appendMode) {
                LinkPreview.bind(widget)
            } else {
                LinkPreview.hide(widget)
            }
            PlusOneButton.bind(widget, item)
        }
    }

}