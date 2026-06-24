package momoi.mod.qqpro.hook

import android.content.Intent
import android.os.Bundle
import com.tencent.qqnt.watch.mainframe.MainActivity
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.StyleChooserActivity
import momoi.mod.qqpro.ota.OTAManager2
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.watchdog.Watchdog

/**
 * Update check on launch. Delegates to [OTAManager2], which queries the GitLab Releases API of
 * https://gitlab.com/ailife8881/qqmax, compares the latest release tag against this app's own
 * versionName, and (if newer) prompts to download+install the release APK in-app. Respects the
 * user's "不再提醒" choice (stored by OTAManager2 itself).
 */
@Mixin
class 更新检查 : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Watchdog.install(this)
        OTAManager2(this).checkUpdate(false)
        // First launch (never picked a UI style): show the Material-vs-original chooser on top.
        if (!Settings.styleChooserSeen.value) {
            runCatching { startActivity(Intent(this, StyleChooserActivity::class.java)) }
                .onFailure { Utils.log("StyleChooser: launch on start failed: $it") }
        }
    }
}
