package momoi.mod.qqpro.hook.view

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.api.GroupBulletinApi
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.util.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG = M3.surface
private val ACCENT = M3.primary
private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * Full-screen viewer for a group's active announcements (群公告). Opened from the group info
 * page ([com.tencent.watch.aio_impl.ui.frames.SettingFrame]) entry added in [GroupBulletin].
 * Fetches via [GroupBulletinApi.fetch] (kernel getGroupBulletin) and lists each announcement's
 * text. See [GroupBulletinApi] for why only active announcements are available on the watch.
 */
class GroupBulletinFragment(private val groupCode: Long) : MyDialogFragment() {

    private lateinit var root: LinearLayout
    private var active = true

    override fun onDestroyView() {
        active = false
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val swipe = SwipeBackLayout(inflater.context)
        swipe.layoutParams = ViewGroup.LayoutParams(FILL, FILL)
        swipe.setBackgroundColor(BG)
        swipe.onSwipeBack = { dismiss() }
        root = LinearLayout(inflater.context).vertical()
        root.setBackgroundColor(BG)
        swipe.addView(root, FrameLayout.LayoutParams(FILL, FILL))

        showCentered("加载中…")
        GroupBulletinApi.fetch(groupCode) { items ->
            if (!active) return@fetch
            if (items.isEmpty()) showCentered("暂无群公告")
            else showList(items)
        }
        return swipe
    }

    private fun showCentered(msg: String) {
        root.removeAllViews()
        root.gravity = Gravity.CENTER
        val tv = TextView(requireContext()).apply {
            text = msg
            textSize = 14f
            setTextColor(M3.onSurfaceVariant)
            gravity = Gravity.CENTER
        }
        root.addView(tv, LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun showList(items: List<GroupBulletinApi.Item>) {
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        val ctx = requireContext()
        val sv = ScrollView(ctx)
        sv.isFillViewport = true
        sv.layoutParams = LinearLayout.LayoutParams(FILL, 0, 1f)
        val col = LinearLayout(ctx).vertical()
        col.setPadding(16.dp, 8.dp, 16.dp, 16.dp)
        col.layoutParams = ViewGroup.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
        sv.addView(col)
        root.addView(sv)

        // Title
        col.addView(TextView(ctx).apply {
            text = "群公告"
            textSize = 15f
            setTextColor(M3.onSurface)
            gravity = Gravity.CENTER
            setPadding(0, 14.dp, 0, 12.dp)
        }, LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Pinned announcements first, then newest first.
        items.sortedWith(compareByDescending<GroupBulletinApi.Item> { it.pinned }.thenByDescending { it.time })
            .forEach { col.addView(card(it)) }
    }

    private fun card(item: GroupBulletinApi.Item): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).vertical()
        card.setPadding(14.dp, 12.dp, 14.dp, 12.dp)
        card.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(M3.surfaceContainer)
            cornerRadius = 14.dp.toFloat()
        }
        val lp = LinearLayout.LayoutParams(FILL, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 8.dp
        card.layoutParams = lp

        // header: pin tag + time
        val header = StringBuilder()
        if (item.pinned) header.append("📌 ")
        if (item.time > 0) header.append(timeFmt.format(Date(item.time.toLong() * 1000L)))
        if (header.isNotEmpty()) {
            card.addView(TextView(ctx).apply {
                text = header.toString()
                textSize = 11f
                setTextColor(ACCENT)
                setPadding(0, 0, 0, 6.dp)
            })
        }

        val body = item.text.ifBlank { "(无文字内容)" }
        card.addView(TextView(ctx).apply {
            text = body
            textSize = 14f
            setTextColor(M3.onSurface)
            setTextIsSelectable(true)
        })

        item.images.forEachIndexed { idx, image ->
            val label = if (item.images.size == 1) "查看图片" else "查看图片 ${idx + 1}"
            card.addView(TextView(ctx).apply {
                text = "🖼 $label"
                textSize = 13f
                setTextColor(ACCENT)
                setPadding(0, 8.dp, 0, 0)
                setOnClickListener { openImage(this, image) }
            })
        }
        return card
    }

    private fun openImage(view: TextView, image: GroupBulletinApi.Image) {
        val original = view.text
        view.text = "⏳ 下载中…"
        view.isEnabled = false
        GroupBulletinApi.downloadImage(image) { bmp ->
            if (!active) return@downloadImage
            view.text = original
            view.isEnabled = true
            if (bmp == null) {
                Utils.toast(requireContext(), "图片下载失败")
                return@downloadImage
            }
            runCatching {
                ZoomableImageFragment(bmp).show(childFragmentManager, "bulletin_image")
            }.onFailure { Utils.log("GroupBulletin: show image failed: $it") }
        }
    }
}
