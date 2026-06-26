package momoi.mod.qqpro.lib.material

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import momoi.mod.qqpro.lib.dp

/**
 * A self-contained Material 3 button (no com.google.android.material dependency — the project ships
 * its own MD3 implementation in Kotlin, since the host app theme isn't MaterialComponents-based and
 * pulling the real Material library would crash).
 *
 * Sized for a phone-class screen per Material 3 spec:
 *  - default height 40dp (FILLED / TONAL / OUTLINED / ERROR), min-width 64dp, padding 24×10dp
 *  - [.large] modifier for the high-emphasis primary action button: 48dp tall, padding 32×12dp
 *  - text size 14sp (labelLarge in MD3 type scale)
 *  - touch target (48dp min) is enforced by the M3 ripple + padding scheme; the visual button height
 *    can be smaller for inline use.
 *
 * Variants mirror MD3:
 *  - [filled]   solid primary container, on-primary label (high emphasis)
 *  - [tonal]    translucent primary container, accent label (medium emphasis)
 *  - [text]     no container, accent label (low emphasis)
 *  - [outlined] outline stroke, accent label
 *  - [error]    destructive — translucent error container with error-colored label
 *
 * Public on purpose: a @Mixin body referencing it needs it public (else runtime IllegalAccessError).
 */
class M3Button(ctx: Context) : TextView(ctx) {

    enum class Variant { FILLED, TONAL, TEXT, OUTLINED, ERROR }

    private var isLarge = false

    init {
        gravity = Gravity.CENTER
        isSingleLine = true
        // MD3 labelLarge = 14sp. Using COMPLEX_UNIT_SP so it scales with the user's font scale.
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textLabel)
        typeface = Typeface.DEFAULT_BOLD
        // Default button: 40dp tall, 24dp horizontal padding (M3 spec). Min width 64dp.
        setPadding(24.dp, 10.dp, 24.dp, 10.dp)
        minWidth = 64.dp
        // Ensure touch target ≥ 48dp even if the visible button is shorter — the clickable area is
        // bumped by the parent layout; this just makes the view report at least 48dp minimums.
        minimumHeight = M3.touchTargetMin
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
            // Destructive: translucent error container with an error-colored label.
            Variant.ERROR    -> ((M3.error and 0x00FFFFFF) or 0x33_000000) to M3.error
        }
        setTextColor(label)
        val base = when (v) {
            Variant.OUTLINED -> M3.outlined(M3.outline, M3.radiusPill)
            else -> M3.rounded(container, M3.radiusPill)
        }
        background = M3.ripple(base)
    }

    /**
     * Apply the "large" button modifier (used for the highest-emphasis primary action): 48dp tall,
     * padding 32×12dp. Combine with [variant] (typically [Variant.FILLED]).
     *
     *     M3Button(ctx).variant(Variant.FILLED).large()
     */
    fun large(): M3Button = apply {
        isLarge = true
        // M3 spec: large (or "high emphasis") buttons are 48dp tall with 32dp horizontal padding.
        setPadding(32.dp, 12.dp, 32.dp, 12.dp)
        minHeight = 48.dp
        // A slightly larger label (labelLarge -> titleSmall) for emphasis.
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textBody)
    }

    /** Convenience for the common dim-when-disabled look. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }

    /** Keep the view non-focusable when disabled so keyboard navigation skips it cleanly. */
    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        isFocusable = visibility == View.VISIBLE && isEnabled
    }
}