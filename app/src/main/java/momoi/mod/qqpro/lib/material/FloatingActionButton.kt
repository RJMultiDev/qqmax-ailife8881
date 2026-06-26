package momoi.mod.qqpro.lib.material

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.dp

/**
 * Phone-style Material 3 floating action button (FAB).
 *
 * Two variants:
 *  - Default (round): a 56dp circular button with a single icon, primary container background, onPrimary
 *    icon color. Elevation 6dp at rest, 12dp when pressed.
 *  - Extended (oval): a "FAB extended" with icon + label, slightly wider but same 56dp height.
 *    Primary container background, onPrimary label/icon, corner radius = 16dp.
 *
 * Wire a tap via the returned instance's [setOnClick]:
 *
 *     FloatingActionButton(ctx, MaterialSymbol(MaterialSymbols.add, M3.onPrimary)).apply {
 *         setOnClick { composeNew() }
 *     }
 *
 * Or via the helper [M3.fab]:
 *
 *     M3.fab(ctx, MaterialSymbol(MaterialSymbols.add, M3.onPrimary)) { composeNew() }
 *
 * Public on purpose (a @Mixin body referencing it needs it public).
 */
class FloatingActionButton(
    ctx: Context,
    icon: Drawable? = null,
    label: CharSequence? = null,
) : FrameLayout(ctx) {

    private val container = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = M3.ripple(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(M3.primary)
        })
        elevation = M3.fabElevationRest
        // Minimum 48dp touch target — the default 56dp size exceeds it.
        minimumWidth = M3.touchTargetMin
        minimumHeight = M3.touchTargetMin
    }

    private val iconView: ImageView = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
            gravity = Gravity.CENTER
        }
        if (icon != null) setImageDrawable(icon)
        setColorFilter(M3.onPrimary)
    }

    private val labelView: TextView = TextView(ctx).apply {
        setTextColor(M3.onPrimary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.textLabel)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    init {
        // The container handles the click + ripple + background; this outer FrameLayout is just the
        // bounds of the FAB for layout purposes.
        clipChildren = false
        clipToPadding = false
        // Bump the elevation when pressed (M3 spec: 12dp pressed, 6dp rest).
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> container.elevation = M3.fabElevationPressed
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> container.elevation = M3.fabElevationRest
            }
            false
        }
        addView(container)

        when {
            // Extended FAB: icon + label, oval pill, 56dp tall.
            label != null -> {
                container.orientation = LinearLayout.HORIZONTAL
                container.gravity = Gravity.CENTER
                container.setPadding(20.dp, 0, 20.dp, 0)
                (container.background as? GradientDrawable)?.apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = M3.radiusMd
                }
                labelView.text = label
                container.addView(iconView)
                container.addView(labelView, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    leftMargin = 8.dp
                })
                layoutParams = LayoutParams(WRAP, M3.fabExtendedHeight)
                container.layoutParams = LayoutParams(WRAP, M3.fabExtendedHeight)
            }
            // Round FAB: icon only, 56dp square circle.
            else -> {
                container.addView(iconView)
                layoutParams = LayoutParams(M3.fabSize, M3.fabSize)
                container.layoutParams = LayoutParams(M3.fabSize, M3.fabSize)
            }
        }
    }

    /** Replace the icon. */
    fun setIcon(drawable: Drawable) {
        iconView.setImageDrawable(drawable)
    }

    /** Set the label text (extended variant). */
    fun setLabel(text: CharSequence) {
        labelView.text = text
    }

    /** Wire a click listener. */
    fun setOnClick(action: () -> Unit) {
        container.setOnClickListener { action() }
    }
}