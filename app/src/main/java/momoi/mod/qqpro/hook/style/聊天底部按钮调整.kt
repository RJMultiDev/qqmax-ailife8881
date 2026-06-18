package momoi.mod.qqpro.hook.style

import android.annotation.SuppressLint
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.core.widget.doAfterTextChanged
import com.tencent.mobileqq.app.ThreadManagerV2
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.watch.aio_impl.coreImpl.vb.`InputBarController$inputContent$2`
import com.tencent.watch.aio_impl.coreImpl.vb.InputBarControllerKt
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.asGroup
import momoi.mod.qqpro.drawable.plusIconDrawable
import momoi.mod.qqpro.drawable.roundCornerDrawable
import momoi.mod.qqpro.drawable.sendIconDrawable
import momoi.mod.qqpro.hook.AttachmentOverlay
import momoi.mod.qqpro.hook.InlineEmojiPanel
import momoi.mod.qqpro.hook.InlineInput
import momoi.mod.qqpro.hook.VoiceRecord
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.ImeEditText
import momoi.mod.qqpro.lib.GroupScope
import momoi.mod.qqpro.lib.adjustViewBounds
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.bitmapDecodeAssets
import momoi.mod.qqpro.lib.clickable
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.create
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.dpf
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.height
import momoi.mod.qqpro.lib.imageResource
import momoi.mod.qqpro.lib.longClickable
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.marginHorizontal
import momoi.mod.qqpro.lib.onAttach
import momoi.mod.qqpro.lib.onDetach
import momoi.mod.qqpro.lib.onEachLayout
import momoi.mod.qqpro.lib.onFocusChange
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.paddingHorizontal
import momoi.mod.qqpro.lib.scaleType
import momoi.mod.qqpro.lib.size
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.util.Utils
import java.io.File
import java.io.FileOutputStream

// Last emitted inlineGrow debug line; used to suppress repeated identical logs across layout passes.
private var lastInlineGrowLog = ""

@Mixin
class 聊天底部按钮调整() : `InputBarController$inputContent$2`() {
    @SuppressLint("ResourceType", "ClickableViewAccessibility")
    override fun invoke(): Any = (super.invoke() as ConstraintLayout).apply {
        forEach {
            it.visibility = View.INVISIBLE
        }
        // Inline mode (with the "单行输入" setting off): let the bar grow with a multiline EditText
        // instead of the fixed 36dp height. When 单行输入 is on, keep the original single-line bar.
        // The container ConstraintLayout doesn't wrap-content reliably around our unconstrained
        // child, so its height is driven explicitly from the EditText line count (see onEachLayout).
        val inlineGrow = Settings.inlineChatInput.value && !Settings.singleLineInput.value
        val rootContainer = this
        val emoji = getChildAt(0)
        val keyboard = getChildAt(2)
        GroupScope(this).apply {
            val roundBg = roundCornerDrawable(0xFF_1B9AF7.toInt(), 9999f)
            val sideSpaceDp = Settings.screenCornerDiameter.value.toInt()
            // Baseline single-line height of the bar; buttons stay this tall while the EditText grows.
            val lineH = 36.dp
            add<LinearLayout>().size(FILL, FILL).apply {
                    if (Utils.isRoundScreen) {
                        paddingHorizontal((14.dp / Settings.scale.value).toInt())
                    }
                }.content {
                    val voice =
                        create<ImageView>()
                            .height(if (Settings.inlineChatInput.value) lineH else FILL)
                            .adjustViewBounds()
                            .bitmapDecodeAssets("pro/ic_voice.png").padding(6.dp)
                            .scaleType(ImageView.ScaleType.FIT_CENTER)
                    if (Settings.fullInlineInput.value) {
                        // 完全行内输入: hold-to-record inline (timer + slide-to-cancel / slide-to-STT
                        // overlay) instead of opening the native VoiceInputFragment page.
                        VoiceRecord.attach(voice)
                    } else {
                        ThreadManagerV2.getUIHandlerV2().post {
                            b.e.invoke(voice)
                        }
                    }

                    if (Settings.inlineChatInput.value) {
                        // One large pill holds the emoji button (left), the EditText
                        // (middle) and the mic/send button (right). The emoji button
                        // hides while the keyboard is open; the mic turns into a send
                        // button whenever there is text to send.
                        val pill = create<LinearLayout>().height(FILL).weight(1f)
                            .background(roundCornerDrawable(0x22_FFFFFF, 9999f))
                            .gravity(Gravity.CENTER_VERTICAL)
                        val send = create<ImageView>().height(lineH).adjustViewBounds()
                            .padding(8.dp).scaleType(ImageView.ScaleType.FIT_CENTER).apply {
                                setImageDrawable(sendIconDrawable())
                                visibility = View.GONE
                            }
                        lateinit var emojiBtn: ImageView
                        lateinit var emojiToggle: ImageView
                        lateinit var editText: ImeEditText
                        lateinit var hintView: TextView
                        // Bar auto-grow state + routine. The input bar has two homes (see
                        // WatchAIOListVB / InputBarController):
                        //  • at the chat bottom it lives INSIDE a list footer (the 44dp "sliver"
                        //    container b.d()), so growing that footer naturally pushes messages up;
                        //  • scrolled up it floats in an overlay (b.a(), WRAP_CONTENT) that covers the
                        //    list — we just let it overlay (like the native float bar). We must NOT
                        //    change the RecyclerView padding here: that fires onScrolled, and the native
                        //    scroll listener then collapses the floating bar to the up-arrow (state 0),
                        //    making the EditText vanish on the next newline.
                        // Driven from text changes and re-attach as well as layout passes, because in
                        // the floating overlay global-layout passes are sparse (the list is static), so
                        // onEachLayout alone makes the bar grow only after a collapse/reopen.
                        var sliverBaseH = -1
                        val applyInlineGrow = fun() {
                            val parent = rootContainer.parent as? ViewGroup ?: return
                            var p: ViewGroup? = parent
                            var depth = 0
                            while (p != null && depth < 4) {
                                p.clipChildren = false
                                p.clipToPadding = false
                                p = p.parent as? ViewGroup
                                depth++
                            }
                            val lines = editText.lineCount.coerceIn(1, 4)
                            val target = lineH + (lines - 1) * editText.lineHeight
                            val lp = rootContainer.layoutParams ?: return
                            (lp as? FrameLayout.LayoutParams)?.gravity = Gravity.BOTTOM
                            if (lp.height != target) {
                                lp.height = target
                                rootContainer.layoutParams = lp
                            }
                            val sliver = runCatching { b.d() }.getOrNull()
                            if (sliver != null && sliverBaseH < 0) {
                                sliverBaseH = sliver.layoutParams?.height?.takeIf { it > 0 } ?: (44.dp)
                            }
                            val inFooter = sliver != null && parent === sliver
                            // Footer mode (at bottom): grow the footer item so it pushes messages up and
                            // the taller bar is fully laid out & touchable. Float mode: keep the now empty
                            // footer at its base height so it leaves no blank gap at the list end.
                            sliver?.layoutParams?.let { slp ->
                                val want = if (inFooter) target + sliver.paddingTop + sliver.paddingBottom else sliverBaseH
                                if (slp.height != want) {
                                    slp.height = want
                                    sliver.layoutParams = slp
                                }
                            }
                            // applyInlineGrow runs on every layout pass; only log when the values
                            // actually change so the debug log isn't spammed with identical lines.
                            val inlineGrowMsg = "inlineGrow state=${runCatching { b.g }.getOrNull()} parent=${parent.javaClass.simpleName} inFooter=$inFooter lines=$lines target=$target sliverH=${sliver?.layoutParams?.height} rootTop=${rootContainer.top} rootH=${rootContainer.height}"
                            if (inlineGrowMsg != lastInlineGrowLog) {
                                lastInlineGrowLog = inlineGrowMsg
                                Utils.log(inlineGrowMsg)
                            }
                        }
                        pill.content {
                            emojiBtn = add<ImageView>().height(lineH).adjustViewBounds()
                                .scaleType(ImageView.ScaleType.FIT_CENTER).padding(8.dp)
                            if (Settings.attachmentOverlay.value) {
                                // Emoji button becomes a "+" that opens the attachment overlay.
                                emojiBtn.setImageDrawable(plusIconDrawable())
                                emojiBtn.clickable { hideIme(emojiBtn); AttachmentOverlay.show(emojiBtn, emoji) }
                            } else {
                                emojiBtn.bitmapDecodeAssets("pro/ic_emoji.png")
                                emojiBtn.clickable { hideIme(emojiBtn); emoji.callOnClick() }
                            }
                            // Emoji-picker toggle, shown only while typing (inlineEmojiButton). It sits
                            // in the same left slot as emojiBtn (which hides once there is text), so it
                            // appears as an emoji key regardless of the attachment-overlay "+" setting.
                            emojiToggle = add<ImageView>().height(lineH).adjustViewBounds()
                                .scaleType(ImageView.ScaleType.FIT_CENTER).padding(8.dp)
                                .bitmapDecodeAssets("pro/ic_emoji.png")
                            emojiToggle.visibility = View.GONE
                            emojiToggle.clickable { InlineEmojiPanel.toggle(editText) }
                            // Long-press the typing emoji key to open the attachment overlay (+ menu)
                            // and collapse the keyboard, mirroring the "+" button's behaviour.
                            emojiToggle.longClickable {
                                InlineEmojiPanel.dismiss()
                                hideIme(emojiToggle)
                                AttachmentOverlay.show(emojiToggle, emoji)
                            }
                            // The built-in single-line hint and autosize both fight a growing
                            // multiline field, so drop them and overlay a custom hint TextView inside
                            // a FrameLayout that we toggle with the text. When 单行输入 is on (inlineGrow
                            // false) the field stays single-line; otherwise it grows up to 4 lines.
                            val inputWrap = add<FrameLayout>().height(FILL).weight(1f)
                            editText = create<ImeEditText>()
                                .background(null)
                                .paddingHorizontal(4.dp)
                                .textColor(0xFF_FFFFFF).textSize(14f)
                                .gravity(Gravity.CENTER_VERTICAL)
                                .apply {
                                    if (inlineGrow) {
                                        inputType = InputType.TYPE_CLASS_TEXT or
                                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                        // Grow up to 4 lines, then the field scrolls internally.
                                        maxLines = 4
                                    } else {
                                        inputType = InputType.TYPE_CLASS_TEXT or
                                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                        isSingleLine = true
                                        maxLines = 1
                                    }
                                }
                            editText.onImageUri = { uri -> sendImeImage(uri) }
                            hintView = create<TextView>()
                                .text("说点什么...")
                                .textColor(0x80_FFFFFF)
                                .textSize(14f)
                                .gravity(Gravity.CENTER_VERTICAL)
                                .paddingHorizontal(4.dp)
                                .apply { isSingleLine = true; maxLines = 1 }
                            inputWrap.addView(editText, FrameLayout.LayoutParams(
                                FILL, FILL, Gravity.CENTER_VERTICAL))
                            inputWrap.addView(hintView, FrameLayout.LayoutParams(
                                WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START))
                            // Drive the bottom-bar container height from the EditText's line count so
                            // the bar (whose ConstraintLayout won't wrap-content reliably) grows with
                            // the text, up to 4 lines, then the field scrolls inside the fixed height.
                            // Bottom-anchor the bar and disable clipping on its parents so it grows
                            // UPWARD off the fixed-height float/sliver containers instead of off-screen.
                            if (inlineGrow) {
                                (rootContainer.layoutParams as? FrameLayout.LayoutParams)?.gravity = Gravity.BOTTOM
                                editText.onEachLayout { applyInlineGrow() }
                                // Re-show after a scroll-hide doesn't change the text, so onEachLayout
                                // alone (sparse in the float overlay) may leave the bar collapsed though
                                // it still holds multiline text — re-apply on attach.
                                editText.onAttach { editText.post { applyInlineGrow() } }
                                // When the bar collapses (state 0 / scroll-hide) the EditText detaches
                                // and onEachLayout stops firing, so a grown footer height would otherwise
                                // linger as a blank gap with no bar present. Reset it to base on detach;
                                // it re-applies when the bar shows again.
                                editText.onDetach {
                                    runCatching {
                                        val sliver = b.d()
                                        sliver.layoutParams?.let {
                                            if (sliverBaseH > 0 && it.height != sliverBaseH) {
                                                it.height = sliverBaseH
                                                sliver.layoutParams = it
                                            }
                                        }
                                    }
                                }
                            }
                            if (!Settings.hideVoiceButton.value) add(voice.background(null))
                            add(send)
                        }
                        send.clickable {
                            if (Settings.fullInlineInput.value) InlineInput.send()
                            else sendInline(editText)
                        }
                        editText.doAfterTextChanged {
                            // Use isNullOrEmpty (not isNullOrBlank): a space is real content the
                            // user typed (the hint already disappeared), so it must flip to send.
                            val hasText = !it.isNullOrEmpty()
                            hintView.visibility = if (hasText) View.GONE else View.VISIBLE
                            val emojiMode = hasText && Settings.inlineEmojiButton.value
                            // Hide the emoji / "+" button when there is text, to make room for
                            // the send button; show the emoji-picker toggle in its place if enabled.
                            emojiBtn.visibility = if (hasText) View.GONE else View.VISIBLE
                            emojiToggle.visibility = if (emojiMode) View.VISIBLE else View.GONE
                            voice.visibility =
                                if (hasText || Settings.hideVoiceButton.value) View.GONE else View.VISIBLE
                            send.visibility = if (hasText) View.VISIBLE else View.GONE
                            if (!emojiMode) InlineEmojiPanel.dismiss()
                            // Grow/shrink the bar on every edit. Posted so editText.lineCount reflects
                            // the new text (it updates after the next measure). In the floating overlay
                            // layout passes are too sparse to catch this on their own.
                            if (inlineGrow) editText.post { applyInlineGrow() }
                        }
                        if (Settings.fullInlineInput.value) {
                            // The reply/edit banner is a floating overlay above the bar (InlineInput
                            // manages it) so it never shrinks the input box.
                            InlineInput.register(editText, b)
                        }
                        add(pill.marginHorizontal(sideSpaceDp.dp))
                    } else {
                        val left = add<ImageView>().height(FILL).adjustViewBounds()
                            .scaleType(ImageView.ScaleType.FIT_CENTER).background(roundBg).padding(8.dp)
                        if (Settings.attachmentOverlay.value) {
                            left.setImageDrawable(plusIconDrawable())
                            left.clickable { hideIme(left); AttachmentOverlay.show(left, emoji) }
                        } else {
                            left.bitmapDecodeAssets("pro/ic_emoji.png")
                            left.clickable { hideIme(left); emoji.callOnClick() }
                        }
                        voice.background(roundBg)
                        val input = if (Settings.text.isEmpty()) {
                            create<ImageView>().bitmapDecodeAssets("pro/ic_keyboard.png")
                                .scaleType(ImageView.ScaleType.FIT_CENTER).padding(8.dp)
                        } else {
                            create<TextView>().gravity(Gravity.CENTER).textSize(14f)
                                .textColor(0xFF_FFFFFF).text(Settings.text)
                        }.height(FILL).weight(1f)
                            .background(ContextCompat.getDrawable(context, 2114457248)).clickable {
                                keyboard.callOnClick()
                            }

                        if (Settings.hideVoiceButton.value) {
                            add(input)
                        } else if (Settings.swapCenterKeyboard.value) {
                            add(input.marginHorizontal(2.dp))
                            add(voice)
                        } else {
                            add(voice.marginHorizontal(2.dp))
                            add(input)
                        }
                    }
                }
        }
    }

    private fun sendInline(editText: EditText) {
        val text = editText.text?.toString().orEmpty()
        // Don't drop whitespace — a space is valid content the user chose to send.
        if (text.isEmpty()) return
        runCatching {
            val elements = InlineInput.parseTextElements(text)
            val contact = Contact(
                CurrentContact.chatType,
                CurrentContact.peerUid,
                CurrentContact.guildId
            )
            MsgUtil.msgService.sendMsg(contact, 0L, elements, IOperateCallback { code, msg ->
                Utils.log("inline chat send result=$code msg=$msg")
            })
            editText.setText("")
        }.onFailure { Utils.log("inline chat send failed: $it") }
    }
}

/**
 * Hide the soft keyboard if it's open. Called when the "+"/emoji button is tapped so the IME
 * collapses automatically before the attachment overlay or emoji panel opens.
 * Top-level (not inside the @Mixin body) to stay clear of the mixin-copy package issues.
 */
fun hideIme(view: View) {
    runCatching {
        val imm = view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }.onFailure { Utils.log("hideIme failed: $it") }
}

/**
 * Copy an image/GIF URI from the IME keyboard into a temp file and send it as a chat message.
 * Must be a top-level function (not inside a @Mixin body) so the Thread lambda has a public
 * constructor when the mixin is copied into the target package.
 */
fun sendImeImage(uri: Uri) {
    Thread {
        runCatching {
            val ctx = Utils.application
            val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: ""
            val ext = when {
                mime == "image/gif" -> "gif"
                mime == "image/png" -> "png"
                mime == "image/webp" -> "webp"
                else -> "jpg"
            }
            val dir = ctx.getExternalFilesDir("photos") ?: ctx.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "qqpro_ime_${System.currentTimeMillis()}.$ext")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { input.copyTo(it) }
            }
            if (!file.exists() || file.length() == 0L) {
                Utils.log("IME image: empty file for $uri"); return@runCatching
            }
            val element = com.tencent.watch.aio_impl.ext.MsgUtil().a(file.path, 0)
            MsgUtil.msgService.sendMsg(
                CurrentContact, 0L, arrayListOf(element),
                IOperateCallback { code, msg -> Utils.log("IME image send result=$code msg=$msg") }
            )
            Utils.log("IME image sent: $uri -> ${file.path}")
        }.onFailure { Utils.log("IME image send failed: $it") }
    }.start()
}
