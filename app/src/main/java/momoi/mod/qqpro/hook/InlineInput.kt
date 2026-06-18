package momoi.mod.qqpro.hook

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.edit
import com.huanli233.qplus.utils.TextUtilKt
import com.tencent.mobileqq.aio.msglist.holder.base.PicSize
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.qqnt.msg.api.impl.MsgUtilApiImpl
import com.tencent.watch.aio_impl.coreImpl.vb.InputBarController
import com.tencent.watch.aio_impl.ext.AIOPicDownloader
import com.tencent.watch.aio_impl.ext.MsgUtil as WatchMsgUtil
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.MessageEdit
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.lib.AtTag
import momoi.mod.qqpro.lib.ImageTag
import momoi.mod.qqpro.lib.ImeEditText
import momoi.mod.qqpro.lib.InlineTag
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import moye.wearqq.AtElementArg
import moye.wearqq.IMEOperation
import moye.wearqq.ReplyElementArg
import java.lang.ref.WeakReference

/**
 * Controller for "完全行内输入" (Settings.fullInlineInput): everything that would normally open the
 * InputMethodFragment is represented inline in the chat EditText instead.
 *
 *  - @member  → an atomic "@nick " [AtTag] span (blue, deletes in one backspace)
 *  - image    → an atomic "[图片]" [ImageTag] span carrying the ready PicElement
 *  - reply    → a banner floating just above the input bar ("回复 xxx（点击取消）"); tap to cancel
 *  - edit     → same banner ("编辑中（点击取消）"); the next send recalls + resends
 *  - 复读/STT → the text is just dropped into the box as plain text
 *
 * The active EditText is registered by the input-bar hook (聊天底部按钮调整). The StartImeUtil
 * interception (InlineImeRoute) calls [consumePending] / [insertText] instead of navigating to the
 * keyboard page. At send time [send] walks the spans back into MsgElements.
 */
object InlineInput {
    private val tokenColor = 0xFF_4FC3F7.toInt()
    private const val BANNER_TAG = "qqpro_inline_banner"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var editTextRef: WeakReference<ImeEditText>? = null
    private var controllerRef: WeakReference<InputBarController>? = null
    private var bannerRef: WeakReference<TextView>? = null
    private var bannerLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private data class ReplyState(val msgId: Long, val senderUid: String, val nick: String)
    private var reply: ReplyState? = null

    // ---- per-chat draft persistence (Settings.rememberDraft) ----
    // In-memory map keeps full fidelity within a session (incl. [图片] image tokens). It is also
    // mirrored to a "qqpro_drafts" SharedPreferences (as JSON) so a draft survives the app being
    // closed/killed: text + @ mentions + reply target + image *local file paths* (the pic MsgElement
    // itself isn't serializable, so we persist its source path and rebuild a fresh element on restore
    // — only works while the original file still exists). Cleared (both) when the msg is sent.
    private val drafts = HashMap<String, CharSequence>()
    private val replyDrafts = HashMap<String, ReplyState?>()
    private var currentChatKey: String? = null
    private val draftPrefs by lazy { Utils.application.getSharedPreferences("qqpro_drafts", 0) }

    private fun chatKey(): String? {
        val uid = CurrentContact.peerUid
        if (uid.isNullOrEmpty()) return null
        return "${CurrentContact.chatType}:$uid"
    }

    /** Snapshot the current box (text + spans) and reply target into the per-chat draft store. */
    private fun saveDraft() {
        if (!Settings.rememberDraft.value) return
        val key = currentChatKey ?: return
        val et = editText() ?: return
        drafts[key] = SpannableStringBuilder(et.text ?: "")
        replyDrafts[key] = reply
        // Mirror to disk so the draft survives an app restart.
        runCatching {
            val json = serializeDraft(et)
            draftPrefs.edit { if (json == null) remove(key) else putString(key, json) }
        }.onFailure { Utils.log("InlineInput persist draft failed: $it") }
    }

    /**
     * Serialize the box to JSON: an ordered list of text / @-mention / image segments, plus the
     * reply target. Images are stored as their local source file path (resolved via AIOPicDownloader,
     * == WatchPicElementExtKt.C0); an image whose path can't be resolved is dropped. Returns null when
     * there is nothing worth keeping (so the caller removes the key).
     */
    private fun serializeDraft(et: ImeEditText): String? {
        val text = et.text ?: return null
        val segs = JSONArray()
        val spans = text.getSpans(0, text.length, InlineTag::class.java)
            .sortedBy { text.getSpanStart(it) }
        var i = 0
        for (tag in spans) {
            val s = text.getSpanStart(tag); val e = text.getSpanEnd(tag)
            if (s < i || s < 0 || e <= s) continue
            if (s > i) segs.put(JSONObject().put("t", text.subSequence(i, s).toString()))
            when (tag) {
                is AtTag -> segs.put(JSONObject().put("at", JSONObject()
                    .put("uid", tag.uid).put("nick", tag.nick).put("type", tag.atType)))
                is ImageTag -> imagePath(tag.element)?.let { segs.put(JSONObject().put("img", it)) }
            }
            i = e
        }
        if (i < text.length) segs.put(JSONObject().put("t", text.subSequence(i, text.length).toString()))
        val o = JSONObject().put("segs", segs)
        reply?.let { o.put("reply", JSONObject().put("id", it.msgId).put("uid", it.senderUid).put("nick", it.nick)) }
        return if (segs.length() == 0 && !o.has("reply")) null else o.toString()
    }

    /** Append a colored, tagged token (used when rebuilding a draft from disk). */
    private fun appendToken(sb: SpannableStringBuilder, label: String, tag: InlineTag) {
        val start = sb.length
        sb.append(label)
        sb.setSpan(tag, start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(tokenColor), start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** Local source-file path of a staged pic [element], or null (video / unresolved / missing file). */
    private fun imagePath(element: MsgElement): String? {
        if (element.picElement == null) return null
        val path = runCatching { AIOPicDownloader.a.d(element, PicSize.e) }.getOrNull()
        return path?.takeIf { it.isNotEmpty() && File(it).exists() }
    }

    /** Rebuild the box (text + @ spans) and [reply] from the on-disk JSON. Returns true if applied. */
    private fun restoreFromPrefs(et: ImeEditText, key: String): Boolean {
        val raw = draftPrefs.getString(key, null) ?: return false
        return runCatching {
            val o = JSONObject(raw)
            val sb = SpannableStringBuilder()
            o.optJSONArray("segs")?.let { segs ->
                for (idx in 0 until segs.length()) {
                    val seg = segs.getJSONObject(idx)
                    when {
                        seg.has("t") -> sb.append(seg.getString("t"))
                        seg.has("at") -> {
                            val at = seg.getJSONObject("at")
                            appendToken(sb, "@${at.getString("nick")} ",
                                AtTag(at.getString("uid"), at.getString("nick"), at.optInt("type", 2)))
                        }
                        seg.has("img") -> {
                            // Rebuild a fresh sendable pic element from the persisted local path; skip
                            // if the file is gone (moved/deleted since the draft was saved).
                            val path = seg.getString("img")
                            if (File(path).exists()) {
                                runCatching { WatchMsgUtil.a.a(path, 0) }.getOrNull()
                                    ?.let { appendToken(sb, "[图片]", ImageTag(it)) }
                            }
                        }
                    }
                }
            }
            et.setText(sb)
            et.setSelection(et.text?.length ?: 0)
            val r = o.optJSONObject("reply")
            reply = if (r != null) ReplyState(r.getLong("id"), r.getString("uid"), r.getString("nick")) else null
            drafts[key] = SpannableStringBuilder(et.text ?: "")
            replyDrafts[key] = reply
            true
        }.getOrElse { Utils.log("InlineInput restore-from-prefs failed: $it"); false }
    }

    private val draftWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = saveDraft()
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    /** True once an inline EditText is live (a chat is open with the inline pill built). */
    val isReady: Boolean get() = editTextRef?.get() != null

    private fun editText(): ImeEditText? = editTextRef?.get()

    /** Called from the input-bar hook each time the inline pill is (re)built for a chat. */
    fun register(editText: ImeEditText, controller: InputBarController) {
        editTextRef = WeakReference(editText)
        controllerRef = WeakReference(controller)
        reply = null
        hideBanner()
        // Restore this chat's unsent draft (text + @/image spans + reply target), if any. The draft is
        // kept current by draftWatcher on every keystroke and by saveDraft() whenever reply changes, so
        // the store already holds the latest state from the last time this chat was open.
        val key = if (Settings.rememberDraft.value) chatKey() else null
        currentChatKey = key
        if (key != null) {
            val mem = drafts[key]
            if (mem != null) {
                // Same session: restore the full in-memory copy (keeps [图片] image tokens too).
                runCatching {
                    editText.setText(SpannableStringBuilder(mem))
                    editText.setSelection(editText.text?.length ?: 0)
                    reply = replyDrafts[key]
                }.onFailure { Utils.log("InlineInput restore draft failed: $it") }
            } else {
                // After an app restart the in-memory map is empty — rebuild from the on-disk JSON.
                restoreFromPrefs(editText, key)
            }
            editText.addTextChangedListener(draftWatcher)
        }
        // The input bar auto-hides on scroll, detaching this EditText; drop the floating banner then.
        // When it reattaches (bar reshown) the reply/edit state is still live, so rebuild the banner —
        // otherwise the reply/edit indicator vanishes after a scroll-hide/reshow.
        editText.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { updateBanner() }
            override fun onViewDetachedFromWindow(v: View) { hideBanner() }
        })
        // Show the reply banner for a restored draft (the attach listener above only fires on a later
        // (re)attach, so an already-attached box wouldn't get it otherwise).
        if (reply != null) updateBanner()
        // 上拉打开键盘: pulling the chat list up past its bottom opens the inline keyboard.
        ChatPullUpKeyboard.attach(editText)
    }

    // ---- inputs from the StartImeUtil interception (InlineImeRoute) ----

    /**
     * Apply everything an "aio" keyboard-page open would have carried — reply / @ / staged image(s)
     * / edit-or-复读 prefill — then clear the IMEOperation staging and focus the box.
     */
    fun consumePending() {
        val et = editText() ?: return
        runCatching {
            // Reply / @ are staged in IMEOperation.extra (newest first via add(0,…)); apply in input order.
            for (obj in IMEOperation.INSTANCE.extra.reversed()) {
                when (obj) {
                    is ReplyElementArg ->
                        setReply(obj.replayMsgId, obj.senderUidStr, TextUtilKt.b64Decode(obj.senderNickname))
                    is AtElementArg ->
                        insertAt(obj.atUid, TextUtilKt.b64Decode(obj.atNickname))
                }
            }
            // Images staged by the gallery flow.
            for (el in IMEOperation.extraMsg) insertImage(el)
            // Prefill text: 编辑 (MessageEdit active → banner) or 复读 (plain).
            IMEOperation.extraText?.let { if (it.isNotEmpty()) insertText(it) }
        }.onFailure { Utils.log("InlineInput.consumePending failed: $it") }
        IMEOperation.extraText = null
        IMEOperation.INSTANCE.clearExtra()
        IMEOperation.extraMsg.clear()
        updateBanner()
        focusAndShow()
    }

    /** STT / plain prefill: drop [s] into the box at the caret. */
    fun insertText(s: String) {
        val et = editText() ?: return
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        et.text?.replace(start, end, s)
        focusAndShow()
    }

    private fun insertAt(uid: String, nick: String) = insertToken("@$nick ", AtTag(uid, nick, 2))

    private fun insertImage(element: MsgElement) = insertToken("[图片]", ImageTag(element))

    /**
     * Route ready-to-send elements from QQ's native emoji selector (sysface / fav / market face /
     * image-gif) into the box as atomic tokens instead of sending them immediately
     * ([Settings.emojiPickerToInput]). Each element is carried by an [ImageTag] (which buildElements
     * emits verbatim at send time), labelled by its kind so faces show as "[表情]" and pics as "[图片]".
     * Returns false when there is no live inline box to receive them (caller then sends normally).
     */
    fun insertElements(elements: List<MsgElement>): Boolean {
        val et = editText() ?: return false
        runCatching {
            for (el in elements) {
                val label = if (el.picElement != null) "[图片]" else "[表情]"
                insertToken(label, ImageTag(el))
            }
            focusAndShow()
        }.onFailure { Utils.log("InlineInput.insertElements failed: $it") }
        return true
    }

    private fun insertToken(label: String, tag: InlineTag) {
        val et = editText() ?: return
        val sp = SpannableString(label)
        sp.setSpan(tag, 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(tokenColor), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        et.text?.replace(start, end, sp)
    }

    fun setReply(msgId: Long, senderUid: String, nick: String) {
        reply = ReplyState(msgId, senderUid, nick)
        // When 回复带@ is on for a group reply, inject the @sender as a real inline token at the
        // caret so it's visible and the caret can be moved around it — instead of silently
        // prepending it at send time. buildElements emits it from the span (and no longer auto-adds).
        if (Settings.replyWithAt.value && CurrentContact.isGroup) {
            insertAt(senderUid, nick)
        }
        updateBanner()
    }

    private fun onBannerClick() {
        if (MessageEdit.editingMsgId != 0L) {
            // Cancel an edit: drop edit state, the prefilled original text/@/image tokens, AND the
            // staged reply (editing a reply message also carries the reply over).
            MessageEdit.consume()
            editText()?.text?.clear()
            reply = null
        } else {
            reply = null
        }
        updateBanner()
    }

    // ---- floating reply/edit banner (overlay above the input bar, so it never shrinks the box) ----

    private fun updateBanner() {
        // updateBanner() is the single chokepoint after any reply/edit-state change, so persist the
        // per-chat draft (text is already tracked live by draftWatcher; this captures reply changes).
        saveDraft()
        val label = when {
            MessageEdit.editingMsgId != 0L -> "编辑中（点击取消）"
            reply != null -> "回复 ${reply?.nick}（点击取消）"
            else -> null
        }
        if (label == null) hideBanner() else showBanner(label)
    }

    private fun showBanner(label: String) {
        val et = editText() ?: return
        et.post {
            runCatching {
                // Host the banner in the AIO fragment's container (the same FrameLayout the
                // attachment overlay's scrim is added to) rather than android.R.id.content. That
                // way the overlay — added to this container later — draws ABOVE the banner, so the
                // banner sits at the input-bar layer and is covered by the overlay instead of
                // floating on top of it. (No elevation, so plain child order decides z.)
                val content = AttachmentOverlay.aioContainer(et) ?: return@post
                var banner = bannerRef?.get()
                if (banner == null || banner.parent !== content) {
                    (content.findViewWithTag<View>(BANNER_TAG))?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    banner = TextView(et.context).apply {
                        tag = BANNER_TAG
                        setTextColor(tokenColor)
                        textSize = 11f
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(12.dp, 4.dp, 12.dp, 4.dp)
                        setBackgroundColor(0xF2_1C1C1C.toInt())
                        isClickable = true
                        setOnClickListener { onBannerClick() }
                    }
                    content.addView(banner, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                    ))
                    bannerRef = WeakReference(banner)
                    // Keep the banner pinned just above the input bar as the keyboard pushes it around.
                    // Register on the banner's OWN observer (which is the shared window observer once
                    // attached, so it still sees every global layout) — never the input bar's, since
                    // that view detaches on scroll and would make the matching remove fail. Drop any
                    // previous listener first: re-entering this branch across scroll hide/reshow cycles
                    // otherwise leaks a listener every time, and the pile of stale listeners each
                    // mutate layout during the traversal until the container's child array corrupts
                    // (null-child NPE in FrameLayout.layoutChildren).
                    removeBannerListener()
                    val listener = ViewTreeObserver.OnGlobalLayoutListener { repositionBanner() }
                    bannerLayoutListener = listener
                    banner.viewTreeObserver.addOnGlobalLayoutListener(listener)
                }
                banner.text = label
                banner.visibility = View.VISIBLE
                repositionBanner()
            }.onFailure { Utils.log("InlineInput.showBanner failed: $it") }
        }
    }

    /** Drop the global-layout listener from the (shared, still-attached) banner observer, if any. */
    private fun removeBannerListener() {
        val l = bannerLayoutListener ?: return
        bannerRef?.get()?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(l)
        bannerLayoutListener = null
    }

    private fun repositionBanner() {
        val banner = bannerRef?.get() ?: return
        // A leaked/late listener must never touch a detached banner: mutating its layout would run
        // against a stale parent during a traversal. Bail unless it's still attached to the tree.
        if (!banner.isAttachedToWindow) return
        val et = editText() ?: return
        val content = banner.parent as? View ?: return
        val pill = et.parent as? View ?: et
        val loc = IntArray(2); pill.getLocationInWindow(loc)
        val cloc = IntArray(2); content.getLocationInWindow(cloc)
        val bottomMargin = ((cloc[1] + content.height) - loc[1]).coerceAtLeast(0)
        val lp = banner.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.bottomMargin != bottomMargin) {
            lp.bottomMargin = bottomMargin
            banner.layoutParams = lp
        }
    }

    private fun hideBanner() {
        val banner = bannerRef?.get()
        if (banner != null) {
            removeBannerListener()
            // Defer the removeView off the current frame. hideBanner() is also called from the
            // EditText's onViewDetachedFromWindow, which fires *inside* the AIO fragment view's
            // detach traversal during a chat-exit (fragment pop) transition. Since the banner is a
            // direct child of that fragment view, removing it mid-traversal leaves a null entry in
            // the ViewGroup's children array and crashes later in FragmentContainerView
            // .endViewTransition → dispatchDetachedFromWindow (NPE on the null child). Posting the
            // removal runs it as its own message — never nested inside the traversal — so the
            // children array stays consistent.
            mainHandler.post { (banner.parent as? ViewGroup)?.removeView(banner) }
        }
        bannerLayoutListener = null
        bannerRef = null
    }

    /** Send button enabled when the box has any content (text or @/image tokens make it non-empty). */
    fun hasContent(): Boolean = !editText()?.text.isNullOrEmpty()

    /** Turn the current box contents (text + @ + image spans) and the active reply into MsgElements and send. */
    fun send() {
        val et = editText() ?: return
        val elements = buildElements()
        if (elements.isEmpty()) return
        runCatching {
            val editId = MessageEdit.editingMsgId
            if (editId != 0L) {
                MessageEdit.consume()
                Utils.log("inline edit: recall original msgId=$editId then send")
                runCatching { KernelServiceUtil.c()?.recallMsg(CurrentContact, editId, null) }
                    .onFailure { Utils.log("inline edit recall failed: $it") }
            }
            val contact = Contact(CurrentContact.chatType, CurrentContact.peerUid, CurrentContact.guildId)
            MsgUtil.msgService.sendMsg(contact, 0L, elements, IOperateCallback { code, msg ->
                Utils.log("inline full send result=$code msg=$msg")
            })
            et.text?.clear()
            reply = null
            updateBanner()
        }.onFailure { Utils.log("inline full send failed: $it") }
    }

    private fun buildElements(): ArrayList<MsgElement> {
        val et = editText() ?: return arrayListOf()
        val text = et.text ?: return arrayListOf()
        val out = ArrayList<MsgElement>()
        val api = MsgUtilApiImpl.instance

        // Reply element only. The @sender (回复带@) is now an inline AtTag token in the box (injected
        // by setReply), so the span walk below emits it — don't also prepend it here (would double it).
        reply?.let { r ->
            val replyEl = api.createReplyElement(r.msgId)
            replyEl.replyElement?.let { it.senderUid = r.senderUid.toLongOrNull() ?: 0L }
            out.add(replyEl)
        }

        // Walk the text in order, splitting at InlineTag spans.
        val spans = text.getSpans(0, text.length, InlineTag::class.java)
            .sortedBy { text.getSpanStart(it) }
        var i = 0
        for (tag in spans) {
            val s = text.getSpanStart(tag)
            val e = text.getSpanEnd(tag)
            if (s < i || s < 0 || e <= s) continue
            if (s > i) appendText(out, text.subSequence(i, s))
            when (tag) {
                is AtTag -> {
                    // Ensure a real space text element before/after the @mention unless it sits at
                    // the very start/end of the message (skip if the neighbour is already whitespace).
                    // Spaces must be their own TextElements — baking them into the @ name doesn't render.
                    if (s > 0 && !text[s - 1].isWhitespace()) out.add(api.createTextElement(" "))
                    out.add(api.createAtTextElement("@${tag.nick}", tag.uid, tag.atType))
                    if (e < text.length && !text[e].isWhitespace()) out.add(api.createTextElement(" "))
                }
                is ImageTag -> out.add(tag.element)
            }
            i = e
        }
        if (i < text.length) appendText(out, text.subSequence(i, text.length))
        return out
    }

    /** Parse a plain-text run (preserving typed emoji via QQText) into elements. */
    private fun appendText(out: ArrayList<MsgElement>, seq: CharSequence) {
        val str = seq.toString()
        if (str.isEmpty()) return
        val api = MsgUtilApiImpl.instance
        // ImeTextUtil.b builds a QQText for emoji parsing, which chokes on embedded newlines and
        // silently yields no elements — that blocks the whole send. Parse each line on its own and
        // rejoin with explicit "\n" TextElements so multi-line messages send intact.
        val lines = str.split("\n")
        for ((idx, line) in lines.withIndex()) {
            if (idx > 0) out.add(api.createTextElement("\n"))
            if (line.isEmpty()) continue
            runCatching { out.addAll(ImeTextUtil.a.b(line)) }
                .onFailure {
                    Utils.log("InlineInput.appendText parse failed: $it")
                    out.add(api.createTextElement(line))
                }
        }
    }

    /** Newline-safe plain-text → MsgElements, for callers without span handling (e.g. sendInline). */
    fun parseTextElements(str: String): ArrayList<MsgElement> {
        val out = ArrayList<MsgElement>()
        appendText(out, str)
        return out
    }

    /**
     * Public entry for "pull up over chat to open the keyboard" (ChatPullUpKeyboard). Pops the
     * floating input bar and focuses the inline EditText, same as a reply/@/STT route would.
     */
    fun openKeyboard() {
        if (editText() == null) return
        focusAndShow()
    }

    private fun focusAndShow() {
        // The input bar auto-hides (state g=0, arrow only) when the chat list is scrolled up. f(true)
        // targets the sliver state (g=1) which only shows at the list bottom, so it does nothing here.
        // Instead simulate pressing the up-arrow (showArrowListener m) which pops the floating input
        // (g=2) overlaid on the chat regardless of scroll. Skip when already shown (g==1 sliver / g==2).
        runCatching {
            controllerRef?.get()?.let { c ->
                if (c.g != 1 && c.g != 2) {
                    Utils.log("InlineInput: bar hidden (state=${c.g}), simulating up-arrow")
                    c.m.onClick(editText())
                }
            }
        }.onFailure { Utils.log("InlineInput.forceShowBar failed: $it") }
        val et = editText() ?: return
        et.post {
            runCatching {
                et.isFocusableInTouchMode = true
                et.requestFocus()
                et.setSelection(et.text?.length ?: 0)
                et.requestRectangleOnScreen(Rect(0, 0, et.width, et.height), true)
                // requestFocus alone won't raise the soft keyboard; ask the IMM explicitly.
                (et.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager)
                    ?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }.onFailure { Utils.log("InlineInput.focusAndShow failed: $it") }
        }
    }
}
