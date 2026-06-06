package momoi.mod.qqpro.hook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.watch.app.JumpActivity
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import com.tencent.qqnt.watch.ui.kit.WatchFragment
import com.tencent.watch.ime.util.ImeTextUtil
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.util.runOnUi
import mqq.app.MobileQQ
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Incoming half of the two-way share feature: accept ACTION_SEND / ACTION_SEND_MULTIPLE from other
 * apps and, once the app's UI is up, open QQ's friend selector to pick where to send — the same
 * picker the in-chat 转发 uses. The chosen text/image/video/voice is then delivered with sendMsg.
 *
 * The intent-filter that puts QQ in the system share sheet is declared in `mixin/AndroidManifest.xml`
 * (merged into the binary manifest at build time). The share lands on the launcher [JumpActivity];
 * we capture + stage it there, then trigger the picker from [MainFragment] once it's resumed.
 */

/** A single staged attachment ready to be turned into a message element. */
private enum class ShareKind { IMAGE, VIDEO, AUDIO }
private class ShareItem(val kind: ShareKind, val path: String)

/** Captured + staged share payload, plus the elements builder used after a target is chosen. */
private class SharePayload(private val text: String?, private val items: List<ShareItem>) {
    /** Build message elements off the UI thread (media builders do file IO / md5). */
    fun buildElements(): ArrayList<MsgElement> {
        val out = ArrayList<MsgElement>()
        items.forEach { item ->
            runCatching {
                when (item.kind) {
                    ShareKind.IMAGE -> out.add(com.tencent.watch.aio_impl.ext.MsgUtil().a(item.path, 0))
                    ShareKind.VIDEO -> out.add(buildVideoElement(item.path))
                    ShareKind.AUDIO -> out.add(buildPttElement(item.path))
                }
            }.onFailure { Utils.log("share-in: build ${item.kind} failed: $it") }
        }
        if (!text.isNullOrBlank()) out.addAll(ImeTextUtil.a.b(text))
        return out
    }

    fun isEmpty() = items.isEmpty() && text.isNullOrBlank()
}

/** Holds the most recent captured payload until a resumed [MainFragment] consumes it. */
private object PendingShare {
    @Volatile var payload: SharePayload? = null
    val opening = AtomicBoolean(false)

    /** Open the picker if we have both a payload and a resumed host view. */
    fun fire() {
        val view = ShareTrigger.activeView ?: return
        val p = payload ?: return
        if (!opening.compareAndSet(false, true)) return
        view.post {
            val ok = view.shareInToFriends(p)
            if (ok) payload = null
            opening.set(false)
        }
    }
}

/** Tracks the currently-resumed main host view so a late-arriving payload can still be shown. */
private object ShareTrigger {
    @Volatile var activeView: View? = null
}

/**
 * Read text + stream URIs from a share [intent] and copy each supported attachment into the app's
 * external-files dir (so the source app's transient URI grant is no longer needed when we send).
 * Returns null if the intent isn't a share or carries nothing we can handle. Blocking — call off
 * the UI thread.
 */
private fun stageIncoming(ctx: Context, intent: Intent): SharePayload? {
    val action = intent.action
    val isMulti = action == Intent.ACTION_SEND_MULTIPLE
    if (action != Intent.ACTION_SEND && !isMulti) return null

    val text = (intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
    val uris: List<Uri> = if (isMulti) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
    } else {
        listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    val dir = (ctx.getExternalFilesDir("share_in") ?: ctx.filesDir).apply { if (!exists()) mkdirs() }
    val items = ArrayList<ShareItem>()
    uris.forEachIndexed { i, uri ->
        val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: intent.type ?: ""
        val kind = when {
            mime.startsWith("image/") -> ShareKind.IMAGE
            mime.startsWith("video/") -> ShareKind.VIDEO
            mime.startsWith("audio/") -> ShareKind.AUDIO
            else -> null
        }
        if (kind == null) {
            Utils.log("share-in: unsupported mime=$mime uri=$uri")
            return@forEachIndexed
        }
        val ext = when (kind) {
            ShareKind.IMAGE -> if (mime == "image/png") "png" else if (mime == "image/gif") "gif" else "jpg"
            ShareKind.VIDEO -> "mp4"
            ShareKind.AUDIO -> if (mime.contains("amr")) "amr" else "m4a"
        }
        val dst = File(dir, "in_${System.currentTimeMillis()}_$i.$ext")
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dst).use { input.copyTo(it) }
            }
        }.onFailure { Utils.log("share-in: copy failed $uri: $it") }
        if (dst.exists() && dst.length() > 0) items.add(ShareItem(kind, dst.path))
    }

    val payload = SharePayload(text, items)
    return if (payload.isEmpty()) null else payload
}

/**
 * Open the friend selector (same as 转发) and send [payload] to each chosen target. Returns true if
 * the selector was opened (payload consumed), false if it couldn't be (no nav fragment / runtime).
 */
private fun View.shareInToFriends(payload: SharePayload): Boolean {
    val navFragment = WatchPicElementExtKt.W(this)?.let { WatchPicElementExtKt.Y(it) }
    if (navFragment == null) {
        Utils.log("share-in: no nav fragment yet")
        return false
    }
    val app = MobileQQ.getMobileQQ().peekAppRuntime() ?: run {
        Utils.log("share-in: no app runtime")
        return false
    }
    val contactService = app.getRuntimeService(IContactRuntimeService::class.java, "")
    contactService.startFriendSelect(
        navFragment,
        emptyList(),
        arrayListOf(app.currentUid),
        "分享到",
        0x7e0805cd,
        1, 10, null, false, true
    ) { _, friends ->
        Utils.log("share-in: selected ${friends.size} target(s)")
        if (friends.isNotEmpty()) {
            Thread {
                val elements = payload.buildElements()
                if (elements.isEmpty()) {
                    Utils.log("share-in: nothing to send")
                    return@Thread
                }
                friends.forEach { friend ->
                    val dst = Contact(if (friend.e) 2 else 1, friend.b, "")
                    MsgUtil.msgService.sendMsg(
                        dst, 0L, ArrayList(elements),
                        IOperateCallback { code, msg -> Utils.log("share-in: send result=$code msg=$msg peer=${dst.peerUid}") }
                    )
                }
            }.start()
        }
        kotlin.Unit
    }
    return true
}

/**
 * PUBLIC entry points called from the @Mixin method bodies below. The bodies are copied verbatim
 * into the target classes (JumpActivity / MainFragment), which live in a different package, so they
 * may only touch PUBLIC members — referencing the private [PendingShare] / [ShareTrigger] directly
 * crashes with IllegalAccessError (see [[qqpro-mixin-anon-class]]). All real work happens here, in
 * the hook package, where the private state is reachable.
 */
fun handleShareIntent(ctx: Context, intent: Intent?) {
    intent ?: return
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return
    val appCtx = ctx.applicationContext
    Utils.log("share-in: received ${intent.action} type=${intent.type}")
    Thread {
        val payload = runCatching { stageIncoming(appCtx, intent) }
            .onFailure { Utils.log("share-in: stage failed: $it") }
            .getOrNull()
        if (payload != null) {
            PendingShare.payload = payload
            runOnUi { PendingShare.fire() }
        }
    }.start()
}

fun handleShareHostResume(view: View?) {
    ShareTrigger.activeView = view
    PendingShare.fire()
}

/** Capture shares delivered to the launcher activity, stage them, then arm the picker trigger. */
@Mixin
class ShareInJump : JumpActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(this, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(this, intent)
    }
}

/**
 * Whenever ANY watch fragment is resumed, expose its view as the picker host and fire any pending
 * share. Hooking the base [WatchFragment] (rather than just the home MainFragment) means a share
 * that arrives while a chat is open fires the target picker right over the chat, instead of waiting
 * until the chat is closed and the home screen resumes.
 */
@Mixin
class ShareInHost : WatchFragment() {
    override fun onResume() {
        super.onResume()
        handleShareHostResume(view)
    }
}
