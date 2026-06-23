package momoi.mod.qqpro.hook.qzone

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import download
import momoi.mod.qqpro.safeCacheDir
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import com.tencent.watch.qzone_impl.frame.IAdapterHost
import com.tencent.watch.qzone_impl.publish.business.publishqueue.QZonePublishQueue
import com.tencent.watch.qzone_impl.publish.business.task.QZoneLikeFeedTask
import com.tencent.watch.qzone_impl.service.QZoneWriteOperationService
import momoi.mod.qqpro.hook.view.MediaItem
import momoi.mod.qqpro.hook.view.MediaPagerFragment
import momoi.mod.qqpro.hook.view.PartialCopyFragment
import momoi.mod.qqpro.util.Utils

/**
 * Shared QZone action helpers for the materialized feed ([Settings.materializeQzone]). NOT a @Mixin
 * class, so anonymous classes / lambdas are fine here (they must never live in a @Mixin body).
 *
 * Everything routes through the proven native paths:
 *  - like  → [QZoneWriteOperationService.LikeParams] + [QZoneLikeFeedTask] + [QZonePublishQueue]
 *            (the exact path the native FeedCommentViewHolder uses), with an optimistic local update.
 *  - media → build [MediaItem]s from the post's pictures/video and show the shared [MediaPagerFragment].
 *  - comments → the custom [QzoneCommentThread] screen (M3 list + reply bar).
 *  - overflow (⋮) → a [QzoneOverflowFragment]: copy text / free-copy / download / repost.
 *
 * All dialogs are shown via `host.b().childFragmentManager` (the host fragment), matching the rest
 * of the app's [MyDialogFragment] pattern.
 */
object QzoneActions {

    /** The author/avatar URL for a uin, matching the QZone top-bar avatar source. */
    fun avatarUrl(uin: Long): String = "https://thirdqq.qlogo.cn/headimg_dl?dst_uin=$uin&spec=100"

    fun isFake(data: BusinessFeedData): Boolean =
        runCatching { data.localInfo?.isFake == true }.getOrDefault(false)

    /** Plain text of a post (its summary), following the forwarded original when this is a repost. */
    fun postText(data: BusinessFeedData): String {
        val t = runCatching { data.originalInfo }.getOrNull() ?: data
        return runCatching { t.cellSummaryV2?.summary }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { data.cellSummaryV2?.summary }.getOrNull()
            ?: ""
    }

    /**
     * Toggle like on [data], optimistically updating its [CellLikeInfo] then enqueueing the write
     * task. Returns the new liked state (or the unchanged state if the post is still publishing).
     */
    fun toggleLike(host: IAdapterHost, data: BusinessFeedData): Boolean {
        val like = runCatching { data.likeInfo }.getOrNull() ?: return false
        if (isFake(data)) {
            Utils.toast(host.requireContext(), "正在发布, 请稍后")
            return like.isLiked
        }
        val newLiked = !like.isLiked
        like.isLiked = newLiked
        like.likeNum = (like.likeNum + if (newLiked) 1 else -1).coerceAtLeast(0)
        runCatching {
            val fc = data.feedCommInfo
            val p = QZoneWriteOperationService.LikeParams()
            p.a = fc.ugckey
            p.b = fc.curlikekey
            p.c = fc.orglikekey
            p.d = newLiked
            p.e = fc.appid
            p.f = data.operationInfo?.busiParam?.let { HashMap(it) }
            p.g = -1
            p.h = data
            p.i = data.feedType
            val task = QZoneLikeFeedTask(null, p, 1)
            runCatching { task.javaClass.getField("clientKey").set(task, fc.clientkey) }
            QZonePublishQueue.e().b(task)
            Utils.log("QzoneActions: like ${fc.feedskey} -> $newLiked (num=${like.likeNum})")
        }.onFailure { Utils.log("QzoneActions toggleLike failed: $it") }
        return newLiked
    }

    /** Open the custom comment-thread screen for [data]. */
    fun openComments(host: IAdapterHost, data: BusinessFeedData) {
        runCatching {
            QzoneCommentThread(data, host).show(host.b().childFragmentManager, "qzcomments")
        }.onFailure {
            Utils.log("QzoneActions openComments: $it")
            // Fall back to the native comment input if the custom screen fails.
            runCatching { host.z(host.b().requireView(), 1, data, 0, null) }
        }
    }

    /** Collect a post's media as [MediaItem]s (pictures first, then a single video). */
    fun mediaItems(data: BusinessFeedData): List<MediaItem> {
        val t = runCatching { data.originalInfo }.getOrNull() ?: data
        val items = ArrayList<MediaItem>()
        runCatching {
            t.pictureInfo?.pics?.forEach { pi ->
                val url = (pi.bigUrl ?: pi.currentUrl)?.url
                if (!url.isNullOrEmpty()) items.add(MediaItem(imageUrl = url, imageLocalPath = null, videoUrl = null))
            }
        }
        runCatching {
            val url = t.videoInfo?.videoUrl?.url
            if (!url.isNullOrEmpty()) items.add(MediaItem(imageUrl = null, imageLocalPath = null, videoUrl = url))
        }
        return items
    }

    fun openMedia(host: IAdapterHost, data: BusinessFeedData, index: Int) {
        val items = mediaItems(data)
        if (items.isEmpty()) return
        runCatching {
            MediaPagerFragment(items, index.coerceIn(0, items.size - 1))
                .show(host.b().childFragmentManager, "qzmedia")
        }.onFailure { Utils.log("QzoneActions openMedia: $it") }
    }

    /** The ⋮ overflow menu for a post: copy text / free-copy / download / repost. */
    fun showOverflowMenu(host: IAdapterHost, data: BusinessFeedData) {
        val ctx = host.requireContext()
        val text = postText(data)
        val rows = ArrayList<Pair<String, () -> Unit>>()
        rows.add("复制文本" to {
            copyToClipboard(ctx, text); Utils.toast(ctx, "已复制")
        })
        rows.add("自由复制" to {
            runCatching { PartialCopyFragment(text).show(host.b().childFragmentManager, "qzcopy") }
        })
        if (mediaItems(data).isNotEmpty()) {
            rows.add("下载图片/视频" to { downloadMedia(host, data) })
        }
        // 转发 (repost) stays last in the menu.
        rows.add("转发" to { repost(host, data) })
        runCatching {
            QzoneOverflowFragment(rows).show(host.b().childFragmentManager, "qzmenu")
        }.onFailure { Utils.log("QzoneActions overflow: $it") }
    }

    /** Download every image/video of [data] into the device gallery (Pictures/Movies → QZone). */
    fun downloadMedia(host: IAdapterHost, data: BusinessFeedData) {
        val items = mediaItems(data)
        if (items.isEmpty()) { Utils.toast(host.requireContext(), "没有可下载的媒体"); return }
        val ctx = host.requireContext().applicationContext
        val main = Handler(Looper.getMainLooper())
        Utils.toast(host.requireContext(), "开始下载 ${items.size} 个文件…")
        val cache = ctx.safeCacheDir ?: ctx.cacheDir
        val remaining = AtomicInteger(items.size)
        val okCount = AtomicInteger(0)
        val ts = System.currentTimeMillis()
        items.forEachIndexed { i, item ->
            val isVideo = item.videoUrl != null
            val url = item.videoUrl ?: item.imageUrl
            val ext = if (isVideo) "mp4" else "jpg"
            val finish = {
                if (remaining.decrementAndGet() == 0) {
                    val n = okCount.get()
                    main.post { Utils.toast(ctx, if (n > 0) "已保存 $n 个文件到相册" else "下载失败") }
                }
            }
            if (url.isNullOrEmpty()) { finish(); return@forEachIndexed }
            val tmp = File(cache, "qzdl_${ts}_$i.$ext")
            runCatching {
                download(url, tmp) { success ->
                    val saved = success && MediaSave.toGallery(
                        ctx, tmp, "QZone_${ts}_$i.$ext",
                        if (isVideo) "video/mp4" else "image/jpeg", isVideo,
                    )
                    if (saved) okCount.incrementAndGet()
                    runCatching { tmp.delete() }
                    finish()
                }
            }.onFailure { Utils.log("QzoneActions download $i: $it"); finish() }
        }
    }

    private fun copyToClipboard(ctx: Context, text: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("qzone", text))
        }.onFailure { Utils.log("QzoneActions copy: $it") }
    }

    /** Repost: open the compose page pre-filled with a quote of the original 说说. */
    private fun repost(host: IAdapterHost, data: BusinessFeedData) {
        runCatching { QzoneCompose.openRepost(host, data) }
            .onFailure { Utils.log("QzoneActions repost: $it"); Utils.toast(host.requireContext(), "转发失败") }
    }
}
