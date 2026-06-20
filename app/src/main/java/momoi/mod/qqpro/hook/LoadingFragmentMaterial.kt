package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.tencent.qqnt.watch.ui.componet.tips.LoadingFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.lib.material.M3CircularProgress
import momoi.mod.qqpro.util.Utils

/**
 * Materialize the app-wide native loading screen ([LoadingFragment], shown via NavController by
 * TipsUtils for the group member list, etc.). The native screen draws a colorful animated bitmap into
 * its `loading_icon` ImageView; replace that with our themed [M3CircularProgress] spinner so every
 * "loading…" reads as one MD3 system. Keeping the spinner under the SAME view id means the "tips"
 * text constrained below the icon stays positioned.
 */
@Mixin
class LoadingFragmentMaterial : LoadingFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runCatching {
            val ctx = view.context
            val iconId = ctx.resources.getIdentifier("loading_icon", "id", ctx.packageName)
            val icon = (if (iconId != 0) view.findViewById<View>(iconId) else null) as? ImageView ?: return
            val parent = icon.parent as? ViewGroup ?: return
            val lp = icon.layoutParams
            val idx = parent.indexOfChild(icon)
            parent.removeView(icon)
            val spinner = M3CircularProgress(ctx).apply { id = iconId }
            parent.addView(spinner, idx, lp)
            Utils.log("LoadingFragmentMaterial: replaced native loading icon with M3 spinner")
        }.onFailure { Utils.log("LoadingFragmentMaterial: $it") }
    }
}
