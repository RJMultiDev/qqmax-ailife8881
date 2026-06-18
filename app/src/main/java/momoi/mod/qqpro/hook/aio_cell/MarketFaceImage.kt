package momoi.mod.qqpro.hook.aio_cell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import momoi.mod.qqpro.hook.view.MyDialogFragment
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.safeCacheDir
import momoi.mod.qqpro.showDialog
import momoi.mod.qqpro.util.Utils
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Market-face (sticker / saved image-emoji) cells render their image straight into an ImageView and
 * carry only a `marketFaceElement` — no `picElement` — so the native code (and our pic-based
 * share/copy helpers) can't act on them: tapping does nothing and the long-press menu has no
 * copy-image / system-share. This object captures the rendered ImageView per message so we can
 * (a) open it fullscreen on tap and (b) hand the rendered bitmap to the long-press menu actions.
 */
object MarketFaceImage {
    // msgId -> the cell's ImageView. Weak so recycled cells don't leak; resolved lazily at action
    // time, by which point the async image load has finished.
    private val views = ConcurrentHashMap<Long, WeakReference<ImageView>>()

    fun onBind(msgId: Long, root: View) {
        val iv = findImageView(root) ?: return
        views[msgId] = WeakReference(iv)
        iv.setOnClickListener(FullscreenClick)
    }

    /** Render the currently-displayed market-face drawable to a cache PNG and return it, or null. */
    fun fileFor(msgId: Long): File? {
        val iv = views[msgId]?.get() ?: return null
        val bmp = drawableToBitmap(iv.drawable) ?: return null
        val dir = iv.context.safeCacheDir ?: return null
        val f = File(dir, "marketface_$msgId.png")
        return runCatching {
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f
        }.onFailure { Utils.log("marketface render failed: $it") }.getOrNull()
    }

    private fun findImageView(v: View): ImageView? {
        if (v is ImageView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findImageView(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun drawableToBitmap(d: Drawable?): Bitmap? {
        d ?: return null
        if (d is BitmapDrawable) d.bitmap?.let { return it }
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: d.bounds.width().takeIf { it > 0 } ?: return null
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: d.bounds.height().takeIf { it > 0 } ?: return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val old = Rect(d.bounds)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        d.bounds = old
        return bmp
    }

    // Named listener (not an inline lambda) so it's safe to reference from anywhere; opens the
    // currently-displayed frame fullscreen.
    private object FullscreenClick : View.OnClickListener {
        override fun onClick(v: View) {
            val iv = v as? ImageView ?: return
            val bmp = drawableToBitmap(iv.drawable) ?: return
            iv.showDialog(BitmapImageFragment(bmp))
        }
    }
}

/** Fullscreen viewer for an already-rendered bitmap (market-face image). Tap to dismiss. */
class BitmapImageFragment(private val bmp: Bitmap) : MyDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(inflater.context).apply {
            setBackgroundColor(0xCC_000000.toInt())
            val image = ImageView(inflater.context).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(image, FrameLayout.LayoutParams(FILL, FILL))
            setOnClickListener { dismiss() }
        }
    }
}
