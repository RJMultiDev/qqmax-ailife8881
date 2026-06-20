package momoi.mod.qqpro.lib.material

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import momoi.mod.qqpro.lib.dp

/**
 * A self-contained Material 3 button (no com.google.android.material dependency — the watch theme
 * isn't MaterialComponents-based, so a real Material button would crash). Pill-shaped, with ripple
 * feedback and a disabled state. Reuse for any confirm/cancel/action button across non-chat screens.
 *
 * Variants mirror MD3:
 *  - [filled]   solid primary container, dark on-primary label (high emphasis)
 *  - [tonal]    translucent primary container, accent label (medium emphasis)
 *  - [text]     no container, accent label (low emphasis)
 *  - [outlined] outline stroke, accent label
 *
 * Public on purpose: a @Mixin body referencing it needs it public (else runtime IllegalAccessError).
 */
class M3Button(ctx: Context) : TextView(ctx) {

    enum class Variant { FILLED, TONAL, TEXT, OUTLINED }

    init {
        gravity = Gravity.CENTER
        isSingleLine = true
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(20.dp, 9.dp, 20.dp, 9.dp)
        isClickable = true
        isFocusable = true
        variant(Variant.FILLED)
    }

    fun variant(v: Variant): M3Button = apply {
        val (container, label) = when (v) {
            Variant.FILLED   -> M3.primary to M3.onPrimary
            Variant.TONAL    -> M3.primaryContainer to M3.onPrimaryContainer
            Variant.TEXT     -> 0 to M3.primary
            Variant.OUTLINED -> 0 to M3.primary
        }
        setTextColor(label)
        val base = when (v) {
            Variant.OUTLINED -> M3.outlined(M3.outline, M3.radiusPill)
            else -> M3.rounded(container, M3.radiusPill)
        }
        background = M3.ripple(base)
    }

    /** Convenience for the common dim-when-disabled look. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }
}
