package momoi.mod.qqpro.hook.aio_cell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import download
import momoi.mod.qqpro.Colors
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.child
import momoi.mod.qqpro.hook.action.SelfContact
import momoi.mod.qqpro.lib.RadiusBackgroundSpan
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import java.util.WeakHashMap
import kotlin.concurrent.thread

private const val TYPE_OWNER = 1
private const val TYPE_ADMIN = 2
private const val TYPE_SPECIAL = 3
private const val TYPE_NORMAL = 0

fun MemberInfo.displayName(): String = when {
    cardName.isNotEmpty() -> cardName
    remark.isNotEmpty() -> remark
    else -> nick
}

private fun MemberInfo.memberType() = when {
    role == MemberRole.OWNER -> TYPE_OWNER
    role == MemberRole.ADMIN -> TYPE_ADMIN
    !memberSpecialTitle.isNullOrEmpty() -> TYPE_SPECIAL
    else -> TYPE_NORMAL
}

fun MemberInfo.levelTagSpan(): CharSequence {
    val type = memberType()
    // Level display removed: the kernel never populates MemberInfo.memberLevel
    // and the server refuses getMemberExtInfo, so only render role / special-title
    // tags. Normal members get no tag at all.
    val label = when {
        !memberSpecialTitle.isNullOrEmpty() -> memberSpecialTitle!!
        type == TYPE_OWNER -> "群主"
        type == TYPE_ADMIN -> "管理员"
        else -> return ""
    }
    return buildSpannedString {
        inSpans(
            RadiusBackgroundSpan(
                bgColor = when (type) {
                    TYPE_ADMIN -> Colors.NickTag.adminBg
                    TYPE_OWNER -> Colors.NickTag.ownerBg
                    TYPE_SPECIAL -> Colors.NickTag.specialBg
                    else -> Colors.NickTag.normalBg
                },
                textColor = when (type) {
                    TYPE_ADMIN -> Colors.NickTag.adminText
                    TYPE_OWNER -> Colors.NickTag.ownerText
                    TYPE_SPECIAL -> Colors.NickTag.specialText
                    else -> Colors.NickTag.normalText
                }
            ),
            RelativeSizeSpan(0.8f)
        ) {
            append(label)
        }
    }
}

/** Single-line "name + role tag" ([name] kept verbatim; tag floats to the trailing edge). */
fun MemberInfo.oneLineNick(name: CharSequence): CharSequence {
    val isSelf = uid == SelfContact.peerUid
    val tag = levelTagSpan()
    if (tag.isEmpty()) return name
    return buildSpannedString {
        if (isSelf) {
            append(name); append(" "); append(tag)
        } else {
            append(tag); append(" "); append(name)
        }
    }
}

/**
 * Two-line nick text shown beside the avatar:
 *  - no tag: just the (possibly long) [name], free to wrap onto both lines;
 *  - with tag: the name on a single (ellipsized) line, the role tag on the line below.
 *
 * The name must be ellipsized to one line ourselves: a single TextView with "name\ntag" + maxLines=2
 * would otherwise let a long name wrap across both lines and push the tag off (clipped). [avail] is
 * the usable text width (0 = unknown yet → don't ellipsize, caller re-runs after layout).
 */
fun MemberInfo.twoLineNick(name: CharSequence, namePaint: TextPaint, avail: Int): CharSequence {
    val tag = levelTagSpan()
    if (tag.isEmpty()) return name // no tag → let the name use up to maxLines (2)
    val nameLine = if (avail > 0)
        TextUtils.ellipsize(name, namePaint, avail.toFloat(), TextUtils.TruncateAt.END)
    else name
    return buildSpannedString {
        append(nameLine)
        append("\n")
        append(tag)
    }
}

object GroupAvatarHook {
    // Re-download a cached avatar once it's older than this, so avatar changes
    // eventually show up instead of being pinned to the first-ever download.
    private const val AVATAR_CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours

    private val mainHandler = Handler(Looper.getMainLooper())
    private val avatarBitmaps = HashMap<Long, Bitmap>()
    private val pendingCallbacks = HashMap<Long, ArrayDeque<() -> Unit>>()
    private val widgetCurrentUin = WeakHashMap<AIOCellGroupWidget, Long>()

    /**
     * Apply (or clear) the avatar drawable on a cell's nick view. Depends ONLY on the message
     * record (sender uin/uid) + settings — never on the async group-member lookup, which silently
     * drops self and members missing from the (possibly partial) member list. Call this directly
     * and unconditionally on every (non-collapsed) bind so a recycled cell never keeps the previous
     * sender's avatar, and self / unknown-member avatars still show.
     *
     * The nick *text* is handled separately by [bindNick] once the member info is available.
     */
    fun bindAvatar(widget: AIOCellGroupWidget, record: MsgRecord) {
        val nickView = widget.getNickWidget<TextView>() ?: return
        val isSelf = record.senderUid == SelfContact.peerUid
        if (Settings.showGroupAvatar.value && (!isSelf || Settings.showSelfAvatar.value)) {
            val uin = record.senderUin
            widgetCurrentUin[widget] = uin
            val bitmap = avatarBitmaps[uin]
            if (bitmap != null) {
                applyAvatar(nickView, bitmap, isSelf)
            } else {
                // Clear immediately so the recycled cell doesn't show the previous avatar
                // while the new one downloads.
                nickView.setCompoundDrawables(null, null, null, null)
                val needsDownload = !pendingCallbacks.containsKey(uin)
                pendingCallbacks.getOrPut(uin) { ArrayDeque() }.addLast {
                    if (widgetCurrentUin[widget] == uin) {
                        widget.getNickWidget<TextView>()?.let { nv ->
                            applyAvatar(nv, avatarBitmaps[uin]!!, isSelf)
                        }
                    }
                }
                if (needsDownload) {
                    loadAvatarBitmap(uin) { bmp ->
                        avatarBitmaps[uin] = bmp
                        pendingCallbacks.remove(uin)?.forEach { it() }
                    }
                }
            }
        } else {
            widgetCurrentUin.remove(widget)
            nickView.setCompoundDrawables(null, null, null, null)
            nickView.setPaddingRelative(nickView.paddingStart, 0, nickView.paddingEnd, nickView.paddingBottom)
        }
    }

    /**
     * Set the nick text (and its layout: two-line + end-aligned when an avatar is shown). Requires
     * member info, so it runs from the group-member callback. The avatar itself is already handled
     * by [bindAvatar]; this only touches text/alignment/padding.
     */
    fun bindNick(widget: AIOCellGroupWidget, record: MsgRecord, member: MemberInfo) {
        val nickView = widget.getNickWidget<TextView>() ?: return
        val isSelf = record.senderUid == SelfContact.peerUid
        // The name only: keep the native (correct group-card) text unless the user opted in to our
        // resolved 群名片/备注/昵称. The role tag is independent and gets appended regardless.
        // Captured up front (before we overwrite the view) so it reflects what QQ set in super.i.
        val name: CharSequence = if (Settings.replaceGroupNick.value) {
            member.displayName()
        } else {
            nickView.text?.takeIf { it.isNotEmpty() } ?: member.displayName()
        }
        if (Settings.showGroupAvatar.value && (!isSelf || Settings.showSelfAvatar.value)) {
            nickView.maxLines = 2
            nickView.ellipsize = TextUtils.TruncateAt.END
            // Self messages are right-aligned, so anchor avatar + nick to the end.
            nickView.gravity = if (isSelf) Gravity.END else Gravity.START
            // Usable text width = view width minus paddings and the avatar drawable. Unknown before
            // the first layout (width==0) → set text now without ellipsizing, then re-run in a post
            // once the width is settled so a long name + tag collapses the name to a single line.
            val applyText = {
                val avail = nickView.width - nickView.compoundPaddingLeft - nickView.compoundPaddingRight
                nickView.text = member.twoLineNick(name, nickView.paint, avail)
            }
            applyText()
            if (nickView.width == 0) nickView.post(applyText)
        } else {
            nickView.maxLines = 1
            nickView.gravity = Gravity.START
            nickView.setPaddingRelative(nickView.paddingStart, 0, nickView.paddingEnd, nickView.paddingBottom)
            nickView.text = member.oneLineNick(name)
        }
    }

    // compound drawable sits beside all text rows, vertically centred. For others
    // it's on the left, for self (right-aligned bubble) it's on the right:
    //   [avatar] [LV badge]        or        [LV badge] [avatar]
    //            display name                 display name
    private fun applyAvatar(nickView: TextView, bitmap: Bitmap, isSelf: Boolean) {
        val avatarSize = (nickView.textSize * Settings.avatarSizeScale.value).toInt()
        val drawable = BitmapDrawable(Utils.application.resources, bitmap).apply {
            setBounds(0, 0, avatarSize, avatarSize)
        }
        if (isSelf) {
            nickView.setCompoundDrawables(null, null, drawable, null)
        } else {
            nickView.setCompoundDrawables(drawable, null, null, null)
        }
        nickView.compoundDrawablePadding = 4.dp
        nickView.setPaddingRelative(nickView.paddingStart, 4.dp, nickView.paddingEnd, nickView.paddingBottom)
    }

    /**
     * Drop every cached avatar — the in-memory bitmaps and the on-disk `avatar_*.jpg`
     * files — so the next time each chat is opened the avatars are re-downloaded fresh.
     * Returns how many cache files were deleted.
     */
    fun clearAvatarCache(): Int {
        avatarBitmaps.clear()
        pendingCallbacks.clear()
        val cacheDir = Utils.application.externalCacheDir
            ?: Utils.application.cacheDir
            ?: Utils.application.filesDir
            ?: return 0
        var count = 0
        cacheDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("avatar_") && f.name.endsWith(".jpg") && f.delete()) {
                count++
            }
        }
        Utils.log("GroupAvatarHook: cleared $count cached avatar files")
        return count
    }

    private fun loadAvatarBitmap(uin: Long, callback: (Bitmap) -> Unit) {
        val cacheDir = Utils.application.externalCacheDir
            ?: Utils.application.cacheDir
            ?: Utils.application.filesDir
        if (cacheDir == null) {
            Utils.log("GroupAvatarHook: no cache dir available, skip avatar uin=$uin")
            return
        }
        val cacheFile = cacheDir.child("avatar_$uin.jpg")
        val url = "https://q.qlogo.cn/headimg_dl?dst_uin=$uin&spec=100"
        Utils.log("GroupAvatarHook: loading avatar uin=$uin")
        val fresh = cacheFile.exists() &&
            (System.currentTimeMillis() - cacheFile.lastModified()) < AVATAR_CACHE_TTL_MS
        if (fresh) {
            thread {
                decodeCircleBitmap(cacheFile.absolutePath)?.let { bmp ->
                    mainHandler.post { callback(bmp) }
                }
            }
        } else {
            // Cache missing or stale: re-download. download() only overwrites the
            // file on HTTP 200, so on failure we still have the old copy to show.
            download(url, cacheFile) { success ->
                if (success || cacheFile.exists()) {
                    if (!success) Utils.log("GroupAvatarHook: refresh failed, using cached avatar uin=$uin")
                    decodeCircleBitmap(cacheFile.absolutePath)?.let { bmp ->
                        mainHandler.post { callback(bmp) }
                    }
                } else {
                    Utils.log("GroupAvatarHook: failed to download avatar uin=$uin")
                }
            }
        }
    }

    private fun decodeCircleBitmap(path: String): Bitmap? {
        val raw = BitmapFactory.decodeFile(path) ?: return null
        val size = minOf(raw.width, raw.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(raw, 0f, 0f, paint)
        raw.recycle()
        return output
    }
}
