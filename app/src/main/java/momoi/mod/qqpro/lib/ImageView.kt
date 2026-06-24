package momoi.mod.qqpro.lib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import momoi.mod.qqpro.util.Utils
import java.io.File
import java.io.InputStream

// The hardware (RecordingCanvas) refuses to draw a bitmap larger than ~100MB, throwing
// "Canvas: trying to draw too large bitmap" from ImageView.onDraw. Decode paths below cap the
// result well under that so a full-resolution photo (e.g. 5650x5650 = 128MB) can't crash the viewer.
const val MAX_DECODE_BYTES = 64L * 1024 * 1024
const val SAFE_MAX_DIM = 4096

fun <T : ImageView> T.imageResource(resId: Int) = apply {
    setImageResource(resId)
}
fun <T : ImageView> T.scaleType(scaleType: ImageView.ScaleType) = apply {
    setScaleType(scaleType)
}
fun <T : ImageView> T.adjustViewBounds(adjust: Boolean = true) = apply {
    adjustViewBounds = adjust
}

/** Reads the first bytes and returns true if the file starts with the GIF magic ("GIF8"). */
private fun File.isGif(): Boolean = try {
    inputStream().use { s ->
        val head = ByteArray(4)
        s.read(head) == 4 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() &&
            head[2] == 'F'.code.toByte() && head[3] == '8'.code.toByte()
    }
} catch (e: Exception) {
    false
}

fun ImageView.bitmapDecodeFile(file: File) {
    // Animated GIFs must be decoded into an AnimatedImageDrawable, otherwise BitmapFactory
    // only yields the first static frame (no animation). API 28+ has ImageDecoder.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && file.isGif()) {
        try {
            val limit = maxHeight
            val src = ImageDecoder.createSource(file)
            val drawable = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                val h = info.size.height
                val w = info.size.width
                var tw = w
                var th = h
                if (h > 300 && h > limit && limit > 0) {
                    tw = (w.toFloat() / h * limit).toInt(); th = limit
                }
                // Absolute cap (independent of maxHeight) so a huge GIF can't blow the canvas limit.
                val maxSide = maxOf(tw, th)
                if (maxSide > SAFE_MAX_DIM) {
                    val s = SAFE_MAX_DIM.toFloat() / maxSide
                    tw = (tw * s).toInt(); th = (th * s).toInt()
                }
                if (tw != w || th != h) decoder.setTargetSize(tw.coerceAtLeast(1), th.coerceAtLeast(1))
            }
            post {
                setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) drawable.start()
            }
            return
        } catch (e: Exception) {
            Utils.log("GIF decode failed, falling back to bitmap: ${e.message}")
        }
    }
    var s: InputStream? = null
    bitmapDecodeStream {
        s?.close()
        s = file.inputStream()
        s!!
    }
    s?.close()
}

fun ImageView.bitmapDecodeAssets(path: String) =
    Utils.application.assets.open(path).use {
        bitmapDecodeStream { reread ->
            if (reread) {
                it.reset()
                it
            } else {
                it.mark(0)
                it
            }
        }
        this
    }

inline fun ImageView.bitmapDecodeStream(streamProvider: (reread: Boolean)->InputStream): Bitmap? {
    val rect = Rect()
    BitmapFactory.decodeStream(streamProvider(false), rect, BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    })
    val bitmap = BitmapFactory.decodeStream(streamProvider(true), null, BitmapFactory.Options().apply {
        var sample = 1
        // Existing behavior: downsample toward maxHeight when the view has one set.
        if (rect.height() > 300 && maxHeight > 0 && rect.height() > maxHeight) {
            while (rect.height() / (sample * 2) >= maxHeight) sample *= 2
        }
        // Hard safety cap (independent of maxHeight): the full-screen viewer's ImageView has
        // maxHeight=0, so without this a full-resolution photo decodes at full size and crashes
        // onDraw with "trying to draw too large bitmap". Raise the sample until it fits the budget.
        while (rect.width() > 0 && rect.height() > 0 &&
            (rect.width().toLong() / sample) * (rect.height() / sample) * 4 > MAX_DECODE_BYTES) {
            sample *= 2
        }
        inSampleSize = sample
    })
    post {
        setImageBitmap(bitmap)
    }
    return bitmap
}