package momoi.mod.qqpro.hook.qzone

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tencent.watch.qzone_impl.common.QZoneBusinessLooper
import com.tencent.watch.qzone_impl.common.task.QZoneTask
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.feed.model.Comment
import com.tencent.watch.qzone_impl.frame.IAdapterHost
import com.tencent.watch.qzone_impl.protocol.request.QZoneAddCommentRequest
import com.tencent.watch.qzone_impl.protocol.request.QZoneAddReplyRequest
import com.tencent.watch.qzone_impl.service.QZoneWriteOperationService
import com.tencent.watch.qzone_impl.utils.UinUtils
import momoi.mod.qqpro.hook.view.MyDialogFragment
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.lib.material.M3QQEditText
import momoi.mod.qqpro.lib.material.MaterialSymbol
import momoi.mod.qqpro.lib.material.MaterialSymbols
import momoi.mod.qqpro.util.Utils
import android.widget.ImageView
import java.util.UUID

/**
 * Custom Material 3 comment-thread screen for a QZone post ([Settings.materializeQzone]). Replaces the
 * cramped native inline blob: each comment/reply is a structured row (avatar + author + full-width
 * body), replies carry a compact "回复 〈target〉" overline (never inline) so who-replies-to-whom is
 * clear and the body wraps cleanly on the watch.
 *
 * The reply bar at the bottom is an [M3QQEditText] (emoji + hold-to-mic STT built in) — typing and
 * voice edit the same field, with no input-mode dialog. Tapping a comment targets it for a reply;
 * otherwise the send posts a top-level comment. Posting goes straight through the kernel:
 *  - comment → [QZoneAddCommentRequest] + [QZoneTask] + [QZoneBusinessLooper]
 *  - reply   → [QZoneAddReplyRequest] (content encoded as @{uin:..,nick:..,who:1,auto:1}<text>)
 *
 * Args are passed via the constructor (host is not Parcelable); a no-arg secondary constructor lets
 * the framework recreate it harmlessly (it just closes).
 */
class QzoneCommentThread(
    private val data: BusinessFeedData? = null,
    private val host: IAdapterHost? = null,
) : MyDialogFragment() {

    constructor() : this(null, null)

    private var replyTo: Comment? = null
    private var input: M3QQEditText? = null
    private var listColumn: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = inflater.context
        val d = data
        if (d == null) { runCatching { dismiss() }; return View(ctx) }

        val edge = if (Utils.isRoundScreen) 16.dp else 8.dp
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(M3.surface)
            setPadding(edge, 6.dp, edge, 6.dp)
        }

        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        listColumn = column
        rebuildList(d)
        val scroll = ScrollView(ctx).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(column) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // --- reply bar ---
        val bar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val edit = M3QQEditText(ctx).apply {
            setHint("评论…")
            setMultiline(true)
            permissionFragmentProvider = { this@QzoneCommentThread }
        }
        input = edit
        bar.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val send = ImageView(ctx).apply {
            setImageDrawable(MaterialSymbol(MaterialSymbols.send, M3.primary))
            setPadding(8.dp, 8.dp, 4.dp, 8.dp)
            isClickable = true
            setOnClickListener { onSend() }
        }
        bar.addView(send, LinearLayout.LayoutParams(40.dp, 40.dp).apply { marginStart = 4.dp })
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 4.dp })

        return swipeBackWrap(root)
    }

    private fun rebuildList(d: BusinessFeedData) {
        val column = listColumn ?: return
        val ctx = column.context
        column.removeAllViews()
        // Comments only — the post body is already on the feed card.
        val comments = runCatching { d.cellCommentInfo?.c }.getOrNull().orEmpty()
        if (comments.isEmpty()) column.addView(TextView(ctx).apply {
            text = "还没有评论，快来抢沙发"
            setTextColor(M3.onSurfaceTip); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, 20.dp, 0, 20.dp)
        })
        comments.forEach { c ->
            val rowView = QzoneFeedCard.commentRow(ctx, c.user, c.user?.nickName, null, c.comment, small = false)
            rowView.isClickable = true
            rowView.setOnClickListener { setReplyTarget(c) }
            column.addView(rowView)
            // replies, indented
            runCatching {
                c.replies?.forEach { r ->
                    val rr = QzoneFeedCard.commentRow(ctx, r.user, r.user?.nickName, r.targetUser?.nickName, r.content, small = true)
                    rr.setPadding(20.dp, rr.paddingTop, 0, rr.paddingBottom)
                    rr.isClickable = true
                    rr.setOnClickListener { setReplyTarget(c) }
                    column.addView(rr)
                }
            }
        }
    }

    private fun setReplyTarget(c: Comment?) {
        replyTo = c
        input?.setHint(if (c == null) "评论…" else "回复 ${c.user?.nickName ?: ""}…")
        input?.focusAndShowKeyboard()
    }

    private fun onSend() {
        val d = data ?: return
        val h = host ?: return
        val text = input?.trimmedText().orEmpty()
        if (text.isBlank()) { Utils.toast(requireContext(), "内容为空"); return }
        if (QzoneActions.isFake(d)) { Utils.toast(requireContext(), "正在发布, 请稍后"); return }
        val ok = runCatching {
            val target = replyTo
            if (target == null) postComment(d, text) else postReply(d, target, text)
        }.onFailure { Utils.log("QzoneCommentThread send: $it") }.isSuccess
        if (ok) {
            input?.setText("")
            setReplyTarget(null)
            Utils.toast(requireContext(), "已发送")
        } else {
            Utils.toast(requireContext(), "发送失败")
        }
    }

    private fun postComment(d: BusinessFeedData, text: String) {
        val fc = d.feedCommInfo
        val ownerUin = runCatching { d.user?.uin }.getOrNull() ?: d.owner_uin
        val cellId = runCatching { d.idInfo?.cellId }.getOrNull() ?: ""
        val busi = runCatching { d.operationInfo?.busiParam }.getOrNull()
        val req = QZoneAddCommentRequest(fc.appid, ownerUin, cellId, text, null, false, busi)
        val task = QZoneTask(req, null, QZoneWriteOperationService.h(), 0)
        task.addParameter("ugckey", fc.ugckey)
        task.addParameter("feedkey", fc.feedskey)
        task.addParameter("uniKey", UUID.randomUUID().toString())
        task.addParameter("clickScene", 0)
        QZoneBusinessLooper.a().c(task)
        Utils.log("QzoneCommentThread: posted comment on ${fc.feedskey}")
    }

    private fun postReply(d: BusinessFeedData, target: Comment, text: String) {
        val fc = d.feedCommInfo
        val cellId = runCatching { d.idInfo?.cellId }.getOrNull() ?: ""
        val busi = runCatching { d.operationInfo?.busiParam }.getOrNull()
        val targetUin = target.user?.uin ?: 0L
        val targetNick = target.user?.nickName ?: ""
        val commentId = target.commentid ?: ""
        // who:1 (reply to a comment author), auto:1 — same encoding the native reply path uses.
        val encodedNick = targetNick.replace("%", "%25").replace(",", "%2C")
            .replace("{", "%7B").replace("}", "%7D").replace(":", "%3A")
        val content = "@{uin:$targetUin,nick:$encodedNick,who:1,auto:1}$text"
        val req = QZoneAddReplyRequest(fc.appid, UinUtils.b(), targetUin, cellId, commentId, content, null, 0, busi, "", HashMap())
        val task = QZoneTask(req, null, QZoneWriteOperationService.h(), 0)
        task.addParameter("ugckey", fc.ugckey)
        task.addParameter("feedkey", fc.feedskey)
        task.addParameter("uniKey", UUID.randomUUID().toString())
        task.addParameter("clickScene", 0)
        QZoneBusinessLooper.a().c(task)
        Utils.log("QzoneCommentThread: posted reply to $targetNick on ${fc.feedskey}")
    }
}
