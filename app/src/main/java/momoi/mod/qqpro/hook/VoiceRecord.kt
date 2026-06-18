package momoi.mod.qqpro.hook

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.mobileqq.ptt.IQQRecorder
import com.tencent.mobileqq.ptt.IQQRecorderUtils
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.utils.RecordParams
import com.tencent.mobileqq.utils.RecordParams.RecorderParam
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.watch.ptt.AudioFileWriterNT
import com.tencent.qqnt.watch.ptt.PttRecordCallback
import com.tencent.qqnt.watch.ptt.api.ITranslateTextService
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import mqq.app.MobileQQ
import java.io.File
import java.lang.ref.WeakReference

/**
 * 完全行内输入 voice messaging. Holding the voice button in the inline input bar records a PTT
 * directly — no native [VoiceInputFragment] page is opened. While held, two floating action targets
 * appear above the bar (slide-to-cancel on the left, slide-to-转文字/STT on the right) plus a live
 * timer and a volume pulse driven by the recorder's amplitude callback.
 *
 *  - release on the mic           → send as a voice (PTT) message
 *  - slide onto 取消 then release   → discard
 *  - slide onto 文 (STT) release    → recognize speech in place; the text is inserted into the inline
 *                                     box for editing (the recording itself is discarded)
 *
 * Recording mirrors VoiceInputFragment: [IQQRecorderUtils.createQQRecorder] driven with the watch's
 * default 16kHz SILK [RecorderParam]; STT mirrors VoiceInputFragment.startTranslate via
 * [ITranslateTextService.translateText] over the recorded file + its pcmforvad.pcm.
 *
 * This is a normal package object (NOT a @Mixin body), so the anonymous recorder/STT listener
 * classes it creates are fine.
 */
object VoiceRecord {
    private val main = Handler(Looper.getMainLooper())

    private val colorIdle = 0xFF_2A2A2A.toInt()
    private val colorBlue = 0xFF_1B9AF7.toInt()
    private val colorRed = 0xFF_E0_53_53.toInt()
    private val scrim = 0xAA_000000.toInt()

    private enum class Target { SEND, CANCEL, STT }

    private var recorder: IQQRecorder? = null
    private var recording = false
    private var audioPath: String? = null
    private var pcmPath: String? = null
    private var startUptime = 0L
    private var target = Target.SEND
    private var pendingTarget = Target.SEND

    // overlay views
    private var overlay: FrameLayout? = null
    private var cancelCircle: TextView? = null
    private var sttCircle: TextView? = null
    private var timerView: TextView? = null
    private var hintView: TextView? = null
    private var pulse: View? = null
    private var anchorContainerRef: WeakReference<ViewGroup>? = null

    private val timerTick = object : Runnable {
        override fun run() {
            if (!recording) return
            timerView?.text = formatMs(SystemClock.uptimeMillis() - startUptime)
            main.postDelayed(this, 100)
        }
    }

    // Hard stop at 60s, same as VoiceInputFragment. Treat as a normal release (send).
    private val autoStop = Runnable {
        if (recording) { target = Target.SEND; finish() }
    }

    /** Wire the inline voice button to hold-to-record. Replaces the native PTT delegate. */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(button: ImageView) {
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Stop the chat RecyclerView (and any other ancestor) from intercepting the
                    // gesture for scroll/overscroll — otherwise MOVE/UP get stolen the moment the
                    // finger slides toward the 取消/STT targets and we never see them.
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    start(v); true
                }
                MotionEvent.ACTION_MOVE -> { updateTarget(e.rawX, e.rawY); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    finish(); true
                }
                else -> false
            }
        }
    }

    // ---- recorder lifecycle ----

    private fun start(anchor: View) {
        if (recording) return
        runCatching {
            val ctx = Utils.application
            val app = MobileQQ.sMobileQQ.peekAppRuntime()
            val p = RecordParams.b(app, false)
            val dir = ctx.getExternalFilesDir("audio") ?: File(ctx.cacheDir, "audio")
            val sub = File(dir, if (p.d == 1) "silk" else "amr")
            if (!sub.exists()) sub.mkdirs()
            audioPath = File(sub, System.currentTimeMillis().toString()).absolutePath
            pcmPath = File(dir, "pcmforvad.pcm").absolutePath

            val rec = QRoute.api(IQQRecorderUtils::class.java).createQQRecorder(ctx)
            recorder = rec
            val cb = PttRecordCallback(null, AudioFileWriterNT(null as TextView?))
            // PttRecordCallback wraps the file writer (field b) and forwards events to the panel
            // listener (field c) — same as VoiceInputFragment's recordPanel assignment.
            cb.c = listener
            rec.c(p)
            rec.d(pcmPath)
            rec.f(cb)
            rec.a(audioPath)

            recording = true
            target = Target.SEND
            startUptime = SystemClock.uptimeMillis()
            showOverlay(anchor)
            main.post(timerTick)
            main.postDelayed(autoStop, 60_000L)
            Utils.log("VoiceRecord: start -> $audioPath fmt=${p.d}")
        }.onFailure {
            Utils.log("VoiceRecord start failed: $it")
            recording = false
            hideOverlay()
            Utils.toast(Utils.application, "无法开始录音")
        }
    }

    private fun finish() {
        if (!recording) return
        recording = false
        pendingTarget = target
        main.removeCallbacks(autoStop)
        main.removeCallbacks(timerTick)
        hideOverlay()
        // stop() finalizes the file and fires listener.g (onRecorderEnd) where we send / STT / drop.
        runCatching { recorder?.stop() }.onFailure { Utils.log("VoiceRecord stop failed: $it") }
        Utils.log("VoiceRecord: release target=$pendingTarget")
    }

    private val listener = object : IQQRecorder.OnQQRecorderListener {
        override fun a() {}
        override fun b(path: String?, slice: ByteArray?, size: Int, maxAmplitude: Int, time: Double, p: RecorderParam?) {
            main.post { onVolume(maxAmplitude) }
        }
        override fun c(path: String?, p: RecorderParam?) {}
        override fun d(path: String?, p: RecorderParam?) {}
        override fun e(path: String?, p: RecorderParam?) {}
        override fun f(): Int = 250
        override fun g(path: String?, p: RecorderParam?, totalTime: Double) {
            main.post { onRecorderEnd(path, totalTime) }
        }
        override fun h(path: String?, p: RecorderParam?, err: String?) {
            Utils.log("VoiceRecord onRecorderError $err")
        }
        override fun i(path: String?, p: RecorderParam?): Int = -1
        override fun j(state: Int) {}
    }

    private fun onRecorderEnd(path: String?, totalTimeMs: Double) {
        val t = pendingTarget
        if (t == Target.CANCEL) { runCatching { path?.let { File(it).delete() } }; return }
        if (path.isNullOrEmpty() || !File(path).exists()) {
            Utils.log("VoiceRecord: no recorded file"); return
        }
        if (totalTimeMs < 500.0) {
            Utils.toast(Utils.application, "说话时间太短")
            runCatching { File(path).delete() }
            return
        }
        when (t) {
            Target.SEND -> Thread { sendVoice(path, totalTimeMs.toLong()) }.start()
            Target.STT -> startStt(path)
            Target.CANCEL -> {}
        }
    }

    // ---- outcomes ----

    private fun sendVoice(path: String, durationMs: Long) {
        runCatching {
            val element = buildPttElement(path, durationMs)
            val contact = Contact(CurrentContact.chatType, CurrentContact.peerUid, CurrentContact.guildId)
            MsgUtil.msgService.sendMsg(contact, 0L, arrayListOf(element),
                IOperateCallback { code, msg -> Utils.log("voice send result=$code msg=$msg") })
        }.onFailure { Utils.log("VoiceRecord sendVoice failed: $it") }
    }

    private fun startStt(audioFile: String) {
        val pcm = pcmPath
        if (pcm.isNullOrEmpty() || !File(pcm).exists()) {
            Utils.toast(Utils.application, "转换失败"); return
        }
        Utils.toast(Utils.application, "正在转文字…")
        runCatching {
            val app = MobileQQ.sMobileQQ.peekAppRuntime()
            val svc = app.getRuntimeService(ITranslateTextService::class.java, "")
            svc.translateText(CurrentContact.isGroup, File(pcm), File(audioFile),
                object : ITranslateTextService.AbsTranslateTextCallback() {
                    override fun b(isSuccess: Boolean, isLast: Boolean, text: String, curKey: String) {
                        if (!isSuccess) { main.post { Utils.toast(Utils.application, "转换失败") }; return }
                        if (isLast) main.post {
                            if (text.isNotEmpty()) InlineInput.insertText(text)
                            else Utils.toast(Utils.application, "没有识别到内容")
                            runCatching { File(audioFile).delete() }
                        }
                    }
                })
        }.onFailure {
            Utils.log("VoiceRecord stt failed: $it")
            main.post { Utils.toast(Utils.application, "转换失败") }
        }
    }

    // ---- gesture target tracking ----

    private fun updateTarget(rawX: Float, rawY: Float) {
        if (!recording) return
        val t = when {
            overCircle(cancelCircle, rawX, rawY) -> Target.CANCEL
            overCircle(sttCircle, rawX, rawY) -> Target.STT
            else -> Target.SEND
        }
        if (t != target) { target = t; refreshTargetState() }
    }

    private fun overCircle(v: View?, x: Float, y: Float): Boolean {
        v ?: return false
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val cx = loc[0] + v.width / 2f
        val cy = loc[1] + v.height / 2f
        val r = v.width * 0.9f          // generous hit radius (slide doesn't need precision)
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    // ---- overlay UI (Material-ish circular targets + timer + volume pulse) ----

    private fun circleBg(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun makeCircle(label: String): TextView =
        TextView(Utils.application).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            background = circleBg(colorIdle)
        }

    private fun showOverlay(anchor: View) {
        val container = AttachmentOverlay.aioContainer(anchor) ?: run {
            Utils.log("VoiceRecord: no aio container"); return
        }
        hideOverlay()
        anchorContainerRef = WeakReference(container)

        val ctx = anchor.context
        val root = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(scrim)
            isClickable = false   // the gesture stays captured by the voice button
            // The volume pulse scales up past its bounds; don't clip it at the container edges.
            clipChildren = false
            clipToPadding = false
        }

        val side = 56.dp
        val edge = 36.dp
        // 取消 (left) and 转文字 (right) targets, around the lower-middle of the screen.
        val cancel = makeCircle("✕")
        root.addView(cancel, FrameLayout.LayoutParams(side, side).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = edge; bottomMargin = 120.dp
        })
        val stt = makeCircle("文")
        root.addView(stt, FrameLayout.LayoutParams(side, side).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = edge; bottomMargin = 120.dp
        })

        // Center bottom column: volume pulse, timer, hint.
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val pulseView = View(ctx).apply { background = circleBg(colorRed) }
        column.addView(pulseView, LinearLayout.LayoutParams(20.dp, 20.dp).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = 10.dp
        })
        val timer = TextView(ctx).apply {
            text = "0:00"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
        }
        column.addView(timer)
        val hint = TextView(ctx).apply {
            text = "松开发送"; setTextColor(0xCC_FFFFFF.toInt()); textSize = 12f; gravity = Gravity.CENTER
        }
        column.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 6.dp
        })
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 40.dp
        })

        container.addView(root)
        overlay = root
        cancelCircle = cancel
        sttCircle = stt
        timerView = timer
        hintView = hint
        pulse = pulseView
        refreshTargetState()
    }

    private fun refreshTargetState() {
        cancelCircle?.background = circleBg(if (target == Target.CANCEL) colorRed else colorIdle)
        sttCircle?.background = circleBg(if (target == Target.STT) colorBlue else colorIdle)
        hintView?.text = when (target) {
            Target.CANCEL -> "松开取消"
            Target.STT -> "松开转文字"
            Target.SEND -> "松开发送，上滑取消"
        }
    }

    private fun onVolume(maxAmplitude: Int) {
        val p = pulse ?: return
        // Map the raw amplitude (0..~32767) to a gentle 1.0–2.4x pulse on the red dot.
        val lvl = (maxAmplitude / 8000f).coerceIn(0f, 1f)
        val s = 1f + lvl * 1.4f
        p.scaleX = s; p.scaleY = s
    }

    private fun hideOverlay() {
        val root = overlay
        if (root != null) main.post { (root.parent as? ViewGroup)?.removeView(root) }
        overlay = null; cancelCircle = null; sttCircle = null
        timerView = null; hintView = null; pulse = null
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
    }
}
