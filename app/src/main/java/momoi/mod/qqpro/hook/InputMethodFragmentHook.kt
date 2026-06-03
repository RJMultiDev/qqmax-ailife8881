package momoi.mod.qqpro.hook

import com.tencent.watch.ime.InputMethodFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils

@Mixin
class InputMethodFragmentHook : InputMethodFragment() {
    override fun onDestroy() {
        super.onDestroy()
        Utils.log("VP sync: InputMethodFragment onDestroy")
        val act = activity ?: return
        act.window?.decorView?.post { fixViewPagerSync(act.window.decorView) }
    }
}
