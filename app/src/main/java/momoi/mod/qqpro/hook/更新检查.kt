package momoi.mod.qqpro.hook

import android.os.Bundle
import com.tencent.qqnt.watch.mainframe.MainActivity
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.api.Http
import momoi.mod.qqpro.util.Utils

@Mixin
class 更新检查 : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Http.get("https://pastebin.com/raw/CsFwY6ZC") {
            val split = it.split("@", limit = 2)
            if (split[0].toInt() > VERSION_CODE) {
                runOnUiThread {
                    Utils.toast(this, split[1], longDuration = true)
                }
            }
        }
    }
}