package momoi.mod.qqpro.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.tencent.qqnt.account.login.ui.QrLoginFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.util.runOnUi

/**
 * Login screen: tap the QR code to blow it up to (almost) full screen so it's
 * easy to scan, then tap anywhere to dismiss. The enlarged image is a square
 * sized to the smaller of the screen's width/height (round/square watch safe).
 */
@Mixin
class LoginQrZoom : QrLoginFragment() {
    override fun Y(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.Y(inflater, container, savedInstanceState)
        // Defer to a helper so the click-listener SAM impls are generated in this
        // (hook) package rather than inside this @Mixin method body — a body copied
        // into QQ's package can't construct a listener class from momoi.mod.qqpro.hook.
        LoginQrZoomHelper.attach(root)
        return root
    }

    // LoginQrCodeStateCallback: Q() = QR scanned, k() = QR expired. Once the QR changes
    // state the enlarged snapshot is stale (it still shows the old, now-invalid code), so
    // close the zoom overlay and let the user see the scanned/expired tip underneath.
    override fun Q() {
        super.Q()
        LoginQrZoomHelper.dismiss()
    }

    override fun k() {
        super.k()
        LoginQrZoomHelper.dismiss()
    }
}

// Must be public (not `private`/package-private): the @Mixin's Y() body is copied
// into QQ's package and references this class, so it has to be accessible there —
// a private object triggers IllegalAccessError at runtime.
object LoginQrZoomHelper {
    // The currently-shown zoom overlay (login or self QR), so a QR state change can dismiss it.
    @Volatile
    private var current: FrameLayout? = null

    /** Close the zoom overlay if one is open (safe to call from any thread / when none is open). */
    fun dismiss() {
        val o = current ?: return
        runOnUi {
            (o.parent as? ViewGroup)?.removeView(o)
            if (current === o) current = null
        }
    }

    fun attach(root: View) {
        try {
            val ctx = root.context
            val id = ctx.resources.getIdentifier("qr_code_container", "id", ctx.packageName)
            val qr = if (id != 0) root.findViewById<View>(id) else null
            if (qr == null) {
                Utils.log("LoginQrZoom: qr_code_container not found")
                return
            }
            attachTapZoom(qr)
            Utils.log("LoginQrZoom: attached zoom to QR view")
        } catch (e: Throwable) {
            Utils.log("LoginQrZoom: attach failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Tap [qr] to blow up a snapshot of it to (almost) full screen, tap again to dismiss.
     * Shared by the login-QR and self-QR hooks; the self QR is rendered tiny on a watch face,
     * so this makes it scannable. Public so @Mixin method bodies (copied into QQ's package) can
     * reach it.
     */
    fun attachTapZoom(qr: View) {
        qr.setOnClickListener { showZoom(qr) }
    }

    private fun showZoom(qr: View) {
        if (qr.width == 0 || qr.height == 0) return
        val activity = qr.context.findActivity() ?: return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Snapshot the rendered QR (white card + code + centre logo) so we can show
        // it enlarged without fighting the login layout's constraints.
        val snapshot = Bitmap.createBitmap(qr.width, qr.height, Bitmap.Config.ARGB_8888)
        qr.draw(Canvas(snapshot))

        val dm = activity.resources.displayMetrics
        val side = minOf(dm.widthPixels, dm.heightPixels)

        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xEE_000000.toInt())
            isClickable = true
        }
        val image = ImageView(activity).apply {
            setImageBitmap(snapshot)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        overlay.addView(image, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
        overlay.setOnClickListener {
            content.removeView(overlay)
            if (current === overlay) current = null
        }
        content.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        current = overlay
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
