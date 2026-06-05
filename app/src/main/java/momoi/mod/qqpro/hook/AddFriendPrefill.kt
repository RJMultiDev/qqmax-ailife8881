package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.View
import com.tencent.qqnt.watch.add.QQAddFriendFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils

/** Bundle key carrying a number to prefill into the add/search numeric pad. */
const val EXTRA_SEARCH_PREFILL = "qqpro_search_prefill"

/**
 * When the add-friend/search numeric pad ([QQAddFriendFragment]) is opened with a
 * [EXTRA_SEARCH_PREFILL] argument (e.g. from tapping a group-invite card), prefill
 * the typed value so the user only has to hit the search button. Searching also
 * returns groups, so a group code resolves the invited group.
 */
@Mixin
class AddFriendPrefill : QQAddFriendFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val code = arguments?.getString(EXTRA_SEARCH_PREFILL)
        if (code.isNullOrEmpty()) return
        try {
            // g = curString (drives the search), e = showText (the visible field)
            g = code
            e.text = code
        } catch (ex: Exception) {
            Utils.log("AddFriendPrefill error: ${ex.message}")
        }
    }
}
