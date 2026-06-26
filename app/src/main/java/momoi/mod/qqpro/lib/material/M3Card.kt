package momoi.mod.qqpro.lib.material

import android.content.Context
import android.widget.LinearLayout
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.vertical

/**
 * A Material 3 surface container card, sized for a phone-class screen.
 *
 *  - Default inner padding is 12dp (roomier than the original 4dp — readable body text needs air).
 *  - Default corner radius is [M3.radiusLg] (20dp — M3 "large" shape), the standard card rounding.
 *  - Three styles via helpers:
 *    - [M3Card]    : filled surfaceContainer card (default), elevation 0.
 *    - [raised]    : surfaceContainerHigh tone, still no shadow. For "selected" emphasis.
 *    - [elevated]  : surface color with a soft 1dp shadow. For floated panels.
 *    - [outlinedCard]: transparent surface + 1dp outline. For low-emphasis grouping.
 *
 *     M3Card(ctx).content { add<TextView>().text("hi") }
 *
 * Public on purpose (a @Mixin body referencing it needs it public).
 */
class M3Card(ctx: Context) : LinearLayout(ctx) {

    private var elevationLevel: Float = 0f
    private var baseFill: Int = M3.surfaceContainer
    private var baseRadius: Float = M3.radiusLg

    init {
        vertical()
        background = M3.rounded(baseFill, baseRadius)
        // Default inner padding: 12dp (a real phone card needs breathing room around body text).
        setPadding(12.dp, 12.dp, 12.dp, 12.dp)
        clipToOutline = false
    }

    /** Switch to the raised (surfaceContainerHigh) tone — no shadow, just a slight contrast bump. */
    fun raised(): M3Card = apply {
        baseFill = M3.surfaceContainerHigh
        background = M3.rounded(baseFill, baseRadius)
    }

    /** Apply an elevation shadow (phone default 1dp). Pair with a surface-color fill. */
    fun elevated(elevation: Float = M3.cardElevation): M3Card = apply {
        elevationLevel = elevation
        baseFill = M3.surface
        background = M3.rounded(baseFill, baseRadius)
        this.elevation = elevation
        // Outline-clipping isn't applied (the rounded drawable IS the outline for clipping), but
        // clipChildren=false keeps inner content from getting cut at the rounded corners.
        clipChildren = false
        clipToPadding = false
    }

    /** Switch to the outlined card style: transparent fill + 1dp outline. */
    fun outlinedCard(): M3Card = apply {
        baseFill = 0
        background = M3.outlined(M3.outline, baseRadius, 1)
    }

    /** Inner padding around the card content (default 12dp). */
    fun contentPadding(value: Int): M3Card = apply { setPadding(value, value, value, value) }

    /** Asymmetric padding (left/top/right/bottom in dp). */
    fun contentPadding(leftDp: Int, topDp: Int, rightDp: Int, bottomDp: Int): M3Card = apply {
        setPadding(leftDp.dp, topDp.dp, rightDp.dp, bottomDp.dp)
    }
}