package momoi.mod.qqpro.lib.material

import android.content.res.ColorStateList
import android.widget.Switch

/**
 * Apply Material 3 colors to a plain [Switch] (checked = primary track/thumb, unchecked = muted).
 * Kept as an extension so existing [momoi.mod.qqpro.lib.checked]/doAfterSwitch helpers still compose.
 */
fun <T : Switch> T.m3Themed(): T = apply {
    val checkedT = intArrayOf(android.R.attr.state_checked)
    val uncheckedT = intArrayOf(-android.R.attr.state_checked)
    val states = arrayOf(checkedT, uncheckedT)

    thumbTintList = ColorStateList(states, intArrayOf(M3.primary, M3.onSurfaceVariant))
    trackTintList = ColorStateList(
        states,
        intArrayOf((M3.primary and 0x00FFFFFF) or 0x66_000000.toInt(), M3.outline)
    )
}
