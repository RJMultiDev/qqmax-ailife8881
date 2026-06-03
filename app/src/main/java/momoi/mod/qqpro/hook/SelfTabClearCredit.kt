package momoi.mod.qqpro.hook

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tencent.qqnt.watch.selftab.ui.SelfFragment
import momoi.anno.mixin.Mixin

/**
 * The self tab (outer-most list) shows a base-app credit line at the bottom. The real about
 * content now lives in the dedicated 关于 dialog ([AboutItemHook]), so blank this line out.
 */
@Mixin
class SelfTabClearCredit : SelfFragment() {
    @SuppressLint("ResourceType")
    override fun Y(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val result = super.Y(inflater, container, savedInstanceState)
        result.findViewById<TextView>(2114521808)?.text = ""
        return result
    }
}
