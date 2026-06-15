package momoi.mod.qqpro.hook

import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import com.tencent.qqnt.watch.profile.ui.ProfileCardFragment
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.util.Utils

/**
 * The profile card's "去聊天" button ([R.id.goto_chat]) ships its chat-bubble via the MaterialButton
 * `app:icon` at a fixed `app:iconSize` (16dp), so it doesn't track the (larger) button text and
 * looks mismatched. Instead of fighting the fixed-size drawable, drop the icon entirely and prepend
 * a 💬 emoji to the button text — being text, it always renders at the current text size.
 */
@Mixin
class ProfileCardIconFix : ProfileCardFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val id = resources.getIdentifier("goto_chat", "id", requireContext().packageName)
            val btn = (if (id != 0) view.findViewById<View>(id) else null) as? MaterialButton ?: return
            // Only the friend ("去聊天") state ships the chat icon; the "加好友" state has none.
            if (btn.icon == null) return
            val label = btn.text?.toString().orEmpty()
            btn.icon = null
            // MaterialButton defaults to an all-caps transformation that can mangle the prefix; clear it.
            btn.transformationMethod = null
            btn.text = "💬 $label" // 💬 + space + original label
            Utils.log("ProfileCardIconFix: replaced icon with emoji, text='${btn.text}'")
        } catch (e: Exception) {
            Utils.log("ProfileCardIconFix error: ${e.message}")
        }
    }
}
