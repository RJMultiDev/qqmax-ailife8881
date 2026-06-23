package momoi.mod.qqpro.hook.qzone

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tencent.mobileqq.activity.photo.LocalMediaInfo
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.frame.IAdapterHost
import com.tencent.watch.qzone_impl.publish.business.model.QzoneShuoShuoParams
import com.tencent.watch.qzone_impl.publish.business.publishqueue.QZonePublishQueue
import com.tencent.watch.qzone_impl.publish.business.task.QZoneUploadShuoShuoTask
import com.tencent.watch.qzone_impl.service.QZoneWriteOperationService
import momoi.mod.qqpro.hook.view.MyDialogFragment
import momoi.mod.qqpro.lib.bitmapDecodeFile
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.lib.material.M3Button
import momoi.mod.qqpro.lib.material.M3QQEditText
import momoi.mod.qqpro.lib.material.MaterialSymbols
import momoi.mod.qqpro.lib.material.symbolImage
import momoi.mod.qqpro.util.Utils
import java.io.File
import java.util.UUID

/**
 * Single-page Material 3 QZone publish/compose screen ([Settings.materializeQzone]). Replaces the
 * native multi-level menu (发布 → entry menu → mode → editor): one page with an [M3QQEditText]
 * (type or hold-to-mic into the same field) plus an "add image" button and a 发表 button.
 *
 * Publishing goes straight through the kernel (no UI relay):
 *  - text only → [QZoneWriteOperationService.i]
 *  - with media → [QZoneUploadShuoShuoTask] + [QZonePublishQueue]
 *
 * Location attach is deferred (kernel-supported via QzoneShuoShuoParams.d / LbsInfo) — see the plan.
 */
object QzoneCompose {
    fun open(host: IAdapterHost) {
        runCatching { ComposeFragment(host).show(host.b().childFragmentManager, "qzcompose") }
            .onFailure { Utils.log("QzoneCompose open: $it") }
    }

    /** Open compose pre-filled with a quote of [data] (watch QZone has no native forward UI). */
    fun openRepost(host: IAdapterHost, data: BusinessFeedData) {
        val author = runCatching { (data.originalInfo ?: data).user?.nickName }.getOrNull()
        val quote = QzoneActions.postText(data)
        val prefill = if (author != null) "\n\n//@$author: $quote" else "\n\n//$quote"
        runCatching { ComposeFragment(host, prefill).show(host.b().childFragmentManager, "qzrepost") }
            .onFailure { Utils.log("QzoneCompose repost: $it") }
    }
}

class ComposeFragment(
    private val host: IAdapterHost? = null,
    private val prefill: String = "",
) : MyDialogFragment() {

    constructor() : this(null, "")

    private val media = ArrayList<LocalMediaInfo>()
    private var thumbStrip: LinearLayout? = null
    private var input: M3QQEditText? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = inflater.context
        val edge = if (Utils.isRoundScreen) 16.dp else 8.dp
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(M3.surface)
            setPadding(edge, 6.dp, edge, 8.dp)
        }

        // top bar: title + 发表
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(ctx).apply {
            text = "发表说说"; setTextColor(M3.onSurface); textSize = 15f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(M3Button(ctx).apply {
            text = "发表"; variant(M3Button.Variant.FILLED)
            setOnClickListener { onSend() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val edit = M3QQEditText(ctx).apply {
            setHint("这一刻的想法…")
            setMultiline(true)
            permissionFragmentProvider = { this@ComposeFragment }
            if (prefill.isNotEmpty()) setText(prefill)
        }
        input = edit
        body.addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dp })

        val strip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        thumbStrip = strip
        body.addView(strip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp })
        refreshThumbs()

        // action row: add image (location deferred)
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(iconAction(ctx, MaterialSymbols.image, "图片") { pickImages() })
        body.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dp })

        val scroll = ScrollView(ctx).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return swipeBackWrap(root)
    }

    private fun iconAction(ctx: android.content.Context, symbol: String, label: String, onClick: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = M3.rounded(M3.surfaceContainerHigh, M3.radiusPill)
            setPadding(12.dp, 8.dp, 14.dp, 8.dp); isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(symbolImage(ctx, symbol, M3.primary, 18))
        row.addView(TextView(ctx).apply { text = label; setTextColor(M3.onSurface); textSize = 13f }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = 6.dp })
        return row
    }

    private fun refreshThumbs() {
        val strip = thumbStrip ?: return
        val ctx = strip.context
        strip.removeAllViews()
        media.take(6).forEach { mi ->
            val iv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = M3.rounded(M3.surfaceContainerHigh, M3.radiusSm)
            }
            runCatching { mi.c?.let { iv.bitmapDecodeFile(File(it)) } }
            strip.addView(iv, LinearLayout.LayoutParams(56.dp, 56.dp).apply { marginEnd = 6.dp })
        }
        if (media.isNotEmpty()) strip.addView(TextView(ctx).apply {
            text = "已选 ${media.size}"; setTextColor(M3.onSurfaceTip); textSize = 11f
        })
    }

    private fun pickImages() {
        val cb: (Boolean, ArrayList<LocalMediaInfo>) -> Unit = { ok, list ->
            if (ok) { media.clear(); media.addAll(list); runCatching { activity?.runOnUiThread { refreshThumbs() } } }
        }
        runCatching {
            WatchPicElementExtKt.S2(this, null, media, cb, 2)
        }.onFailure { Utils.log("QzoneCompose pickImages: $it"); Utils.toast(requireContext(), "无法打开相册") }
    }

    private fun onSend() {
        val text = input?.trimmedText().orEmpty()
        if (text.isBlank() && media.isEmpty()) { Utils.toast(requireContext(), "发布内容为空"); return }
        val ok = runCatching {
            val params = QzoneShuoShuoParams()
            params.a = text
            if (media.isNotEmpty()) {
                params.c = ArrayList(media)
                params.b = media.mapNotNull { it.c }
                val task = QZoneUploadShuoShuoTask(6, 1, params)
                task.uploadEntrance = 0
                task.refer = null
                runCatching { task.javaClass.getField("clientKey").set(task, UUID.randomUUID().toString()) }
                QZonePublishQueue.e().b(task)
            } else {
                QZoneWriteOperationService.h().i(params)
            }
            Utils.log("QzoneCompose: published (textLen=${text.length}, media=${media.size})")
        }.onFailure { Utils.log("QzoneCompose send: $it") }.isSuccess
        if (ok) { Utils.toast(requireContext(), "已发表"); runCatching { dismiss() } }
        else Utils.toast(requireContext(), "发表失败")
    }
}
