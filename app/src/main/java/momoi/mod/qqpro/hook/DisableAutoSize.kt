package momoi.mod.qqpro.hook

import android.content.res.Resources
import me.jessyan.autosize.AutoSizeCompat
import momoi.anno.mixin.StaticHook
import momoi.mod.qqpro.Settings

// Conditional AutoSize kill switch.
//
// The watch-tuned AutoSize library re-pins every Activity's DisplayMetrics density to a tiny
// "watch design" baseline, which on a phone screen inflates every view / text / padding 2-3×
// (the "UI 异常" effect). The library is initialized automatically by
// `me.jessyan.autosize.InitProvider` at app start, then `autoConvertDensityOfGlobal` is called
// per-Activity by the lifecycle callbacks — that call is the single mutation point that rewrites
// the Resources.density / scaledDensity / xdpi.
//
// When [Settings.disableAutoSize] is true, this hook short-circuits that call to a no-op, leaving
// the library installed (so other QQ code referencing `AutoSizeConfig.getInstance()` still works)
// but stopping it from ever mutating the density. All downstream effects (chat text sizing, view
// measurement) then use the system-native density.
//
// When false, the original behavior is preserved (useful if a future watch-class build wants the
// watch-tuned density back, or for debugging).
@StaticHook(AutoSizeCompat::class)
fun autoConvertDensityOfGlobal_(resources: Resources) {
    if (Settings.disableAutoSize.value) return
    // Forward to the original so other code paths that read density post-hook still get it
    // pinned. We don't recurse (different function name), so this is a one-shot call.
    // Reflection avoids needing a `me.jessyan.autosize.AutoSizeCompat_` shim class.
    runCatching {
        val m = AutoSizeCompat::class.java.getDeclaredMethod(
            "autoConvertDensityOfGlobal", Resources::class.java
        )
        m.invoke(null, resources)
    }
}
