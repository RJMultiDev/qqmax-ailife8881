package momoi.mod.qqpro.hook

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.tencent.qqnt.kernel.nativeinterface.VideoElement
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import com.tencent.watch.aio_impl.ui.cell.base.BaseWatchItemCell
import com.tencent.watch.aio_impl.ui.cell.video.WatchVideoGroupWidget
import com.tencent.watch.aio_impl.ui.cell.video.WatchVideoMsgItem
import com.tencent.watch.aio_impl.ui.widget.AIOCellGroupWidget
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.hook.view.VideoPlayerFragment
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.util.runOnUi
import java.io.File

/**
 * 点击聊天里的视频——替换原生 RFW 相册播放器,改用我们自己的 [VideoPlayerFragment]:
 *  - 支持双指缩放 / 双击放大(原生无此功能)。
 *  - 未下载时不再只弹"正在下载"toast,而是进入播放器显示加载圈,下载完成后自动播放。
 *
 * 实现:不直接继承 WatchVideoItemCell(它带泛型擦除的抽象 d/e,子类化会触发 AbstractMethodError 或
 * 平台声明冲突),而是和 [momoi.mod.qqpro.hook.aio_cell.AIOCell] / 缩小文本 一样挂在 BaseWatchItemCell.i()
 * 这个每次绑定都会走的通用钩子上,绑定视频 cell 时把封面图(原生点击目标 getCoverImage)的点击监听换成我们的。
 * 多个 @Mixin 挂同一方法会经 super.i() 链式调用,互不影响。
 */
@Mixin
abstract class VideoPlay : BaseWatchItemCell<WatchAIOMsgItem, View>() {
    override fun i(
        view: View,
        item: WatchAIOMsgItem,
        p2: Int,
        p3: List<Any>,
        p4: Lifecycle,
        p5: LifecycleOwner?
    ) {
        super.i(view, item, p2, p3, p4, p5)
        if (item !is WatchVideoMsgItem) return
        val cover: View = when {
            view is WatchVideoGroupWidget -> view.m
            view is AIOCellGroupWidget ->
                (view.getContentWidget<View>() as? WatchVideoGroupWidget)?.m ?: return
            else -> return
        }
        // Attach via a top-level helper, not an inline lambda here (see ImagePlay): an inline lambda
        // in this @Mixin body is merged into BaseWatchItemCell and collides with ImagePlay's.
        attachVideoClick(cover, item)
    }
}

private fun attachVideoClick(cover: View, item: WatchVideoMsgItem) {
    cover.setOnClickListener { v -> handleVideoClick(item, v) }
}

// 非 @Mixin 普通函数:内部创建的匿名类(Fragment 回调 / lambda / OnClickListener)生成在本包,不会被
// ApkMixin 拷贝进目标包,避免跨包匿名类构造器不可访问的 IllegalAccessError。
private fun handleVideoClick(item: WatchVideoMsgItem, v: View) {
    if (runCatching { item.A() }.getOrDefault(false)) {
        runCatching { Utils.toast(v.context, "视频已过期") }
        return
    }

    val host = runCatching { WatchPicElementExtKt.W(v) }.getOrNull()
    if (host == null) {
        Utils.log("VideoPlay: host fragment null, ignore")
        return
    }

    val localPath = localVideoPath(item)
    Utils.log("VideoPlay: open player local=$localPath")

    val fragment = VideoPlayerFragment(
        initialPath = localPath,
        needDownload = { runOnUi { runCatching { item.t(true) }.onFailure { Utils.log("VideoPlay: t() failed: $it") } } },
        resolvePath = { localVideoPath(item) },
    )
    runCatching { fragment.show(host.parentFragmentManager, "qqpro_video") }
        .onFailure { Utils.log("VideoPlay: show failed: $it") }
}

/** Resolve the on-disk video file path (subType 1, the real video, not the thumbnail), or null. */
private fun localVideoPath(item: WatchVideoMsgItem): String? {
    fun valid(p: String?): String? =
        p?.takeIf { it.isNotEmpty() && runCatching { File(it).let { f -> f.exists() && f.length() > 0 } }.getOrDefault(false) }

    valid(runCatching { item.s() }.getOrNull())?.let { return it }
    val ve: VideoElement? = runCatching { item.x() }.getOrNull()
    if (ve != null) valid(runCatching { WatchPicElementExtKt.a1(ve) }.getOrNull())?.let { return it }
    valid(runCatching { item.y() }.getOrNull())?.let { return it }
    return null
}
