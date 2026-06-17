package momoi.mod.qqpro

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import androidx.core.view.forEach
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.create
import momoi.mod.qqpro.lib.vertical
import java.io.File
import java.lang.reflect.Method

fun View.asGroupOrNull() = this as? ViewGroup
fun ViewParent.asGroup() = this as ViewGroup
fun View.asGroup() = this as ViewGroup
fun <E> List<E>.join(block: (E)->CharSequence) = joinToString("", transform = block)
fun ViewGroup.forEachAll(block: (View) -> Unit) {
    forEach { child ->
        block(child)
        child.asGroupOrNull()?.forEachAll(block)
    }
}
fun ViewGroup.findAll(block: (View) -> Boolean): View? {
    forEach { child ->
        if (block(child)) {
            return@findAll child
        } else child.asGroupOrNull()?.findAll(block)?.let {
            return@findAll it
        }
    }
    return null
}
fun ViewGroup.anyAll(block: (View) -> Boolean) = findAll(block) != null

fun String.removeBefore(key: String) = split(key, limit = 2)[1]
fun String.removeAfter(key: String) = split(key, limit = 2)[0]

fun View.warp(): LinearLayout {
    val lp = this.layoutParams
    val warp = create<LinearLayout>(context).vertical()
    parent.asGroup().let {
        it.removeView(this)
        warp.layoutParams = lp
        this.layoutParams = LinearLayout.LayoutParams(FILL, 0, 1f)
        warp.addView(this)
        it.addView(warp)
    }
    return warp
}

/**
 * Idempotent [warp]: if this view is already inside one of our vertical wrapper LinearLayouts,
 * return that instead of nesting another. Every cell hook (reply/forward/ark/file, link preview,
 * +1) used to wrap the content independently, each guarded only by its own cache — so a recycled
 * cell accumulated nested wrappers, each carrying a leftover `FILL/0/weight=1` lp that ballooned
 * a plain one-line message into a full-screen bubble. Sharing a single wrapper prevents that.
 */
fun View.warpOnce(): LinearLayout = (parent as? LinearLayout) ?: warp()

fun String?.emptyUse(other: String) = if (isNullOrEmpty()) other else this

fun File.child(path: String) = File(this, path)

/**
 * Best-effort cache directory. On some ROMs (e.g. XTC watches) externalCacheDir can be
 * null when external storage isn't mounted, so fall back to internal cache then filesDir.
 */
val android.content.Context.safeCacheDir: File?
    get() = externalCacheDir ?: cacheDir ?: filesDir

fun <T> Class<T>.findMethod(name: String, args: List<Class<Any>>? = null): Method {
    return try {
        if (args == null) getDeclaredMethod(name)
        else getDeclaredMethod(name, *args.toTypedArray())
    } catch (e: Exception) {
        superclass.findMethod(name, args)
    }
}