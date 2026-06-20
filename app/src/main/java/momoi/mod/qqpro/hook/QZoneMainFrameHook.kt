package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tencent.watch.qzone_impl.frame.QZoneMainFrame
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.util.Utils

/**
 * Hooks [QZoneMainFrame] (动态/QZone feed, 3rd main page) to add a Material-style top bar
 * matching the contacts page bar pattern.
 *
 * The original page buries three action rows (发布, 通知, 我的空间) inside the feed adapter's
 * headViewContainer. This moves them into a compact overlay bar above the list:
 *   [self avatar → my QZone] | [edit → publish] | [bell → notifications]
 *
 * Anonymous listener classes MUST NOT live inside a @Mixin class (IllegalAccessError). All
 * click handling is delegated to public methods here, invoked by [QZoneTopBar].
 */
@Mixin
class QZoneMainFrameHook : QZoneMainFrame() {

    override fun Y(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val orig = super.Y(inflater, container, savedInstanceState)
        if (!Settings.materialQZoneBar.value) return orig
        return runCatching { QZoneTopBar.wrap(this, orig) }
            .onFailure { Utils.log("QZoneMainFrameHook Y: $it") }
            .getOrDefault(orig)
    }

    /** Delegates publish bar-tap to the original item_publish row click listener. */
    fun barPublish() {
        runCatching { QZoneTopBar.publishRow?.performClick() }
            .onFailure { Utils.log("QZoneMainFrameHook barPublish: $it") }
    }

    /** Delegates notify bar-tap to the original item_notify row click listener. */
    fun barNotify() {
        runCatching { QZoneTopBar.notifyRow?.performClick() }
            .onFailure { Utils.log("QZoneMainFrameHook barNotify: $it") }
    }

    /** Delegates avatar bar-tap to the original layout_qzone_item_owner row click listener. */
    fun barMyQzone() {
        runCatching { QZoneTopBar.ownerRow?.performClick() }
            .onFailure { Utils.log("QZoneMainFrameHook barMyQzone: $it") }
    }
}
