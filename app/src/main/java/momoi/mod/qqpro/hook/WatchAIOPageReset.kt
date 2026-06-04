package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.View
import com.tencent.watch.aio_impl.ui.WatchAIOFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.hook.action.GalleryMultiSelectState
import momoi.mod.qqpro.util.ChatBackground
import momoi.mod.qqpro.util.Utils

/**
 * After the multi-select gallery (a separate activity) sends images, the chat activity resumes.
 * Switch the AIO ViewPager back to the chat page (page 0), mirroring what single-image send
 * achieves via MenuFrame's selector.invoke(0).
 */
@Mixin
class WatchAIOPageReset : WatchAIOFragment() {
    // The base WatchFragment builds a full-screen background ImageView (field `d`)
    // behind the chat pages, normally showing R.drawable.bg_blue2white. If the user
    // picked a custom chat background, swap that image in (darkened for readability).
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (ChatBackground.isSet()) {
            Utils.log("WatchAIOFragment.onViewCreated applying custom chat background, bgView=${this.d}")
            ChatBackground.applyTo(this.d)
        }
    }

    override fun onResume() {
        super.onResume()
        if (GalleryMultiSelectState.goToChatOnResume) {
            GalleryMultiSelectState.goToChatOnResume = false
            Utils.log("MultiSelect WatchAIOFragment.onResume switching to chat page 0, vp=${f}")
            // The bundled (R8-minified) ViewPager2 only exposes setCurrentItem(int);
            // the two-arg smoothScroll overload was stripped, so calling it crashes with NoSuchMethodError.
            f?.setCurrentItem(0)
        }
    }
}
