package momoi.mod.qqpro.watchdog

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.util.Utils

/**
 * In-app debug menu, opened from the settings "调试" section. Shows live device info plus the tail
 * of the on-device debug log ([Utils.debugLogFile]) so a problem can be inspected / copied / shared
 * straight from the watch without `adb pull`.
 *
 * Like [CrashReportActivity] this is NOT an ApkMixin hook, so it may freely use lambdas / anonymous
 * classes. It runs in the normal (main) process, where [Utils.application] is initialised.
 */
class DebugActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun sp(view: TextView, value: Float) =
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = buildReport()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF_121212.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        root.addView(TextView(this).apply {
            text = "调试菜单"
            setTextColor(Color.WHITE)
            sp(this, 15f)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "设备信息与调试日志"
            setTextColor(0xFF_BBBBBB.toInt())
            sp(this, 10f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        })

        val body = TextView(this).apply {
            this.text = report
            setTextColor(0xFF_DDDDDD.toInt())
            sp(this, 9f)
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF_0A0A0A.toInt())
        }
        root.addView(
            ScrollView(this).apply { addView(body) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // 复制 / 分享
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        fun cell(v: View, left: Int, right: Int) = row.addView(
            v,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = left
                rightMargin = right
            }
        )
        cell(button("复制", 0xFF_2196F3.toInt()) { copy(report) }, 0, dp(3))
        cell(button("分享", 0xFF_4CAF50.toInt()) { share(report) }, dp(3), dp(3))
        cell(button("保存", 0xFF_009688.toInt()) { save(report) }, dp(3), 0)
        root.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        root.addView(
            button("关闭", 0xFF_555555.toInt()) { finish() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        )

        setContentView(
            SwipeBackLayout(this).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                onSwipeBack = { finish() }
            }
        )
    }

    /** Device info block + the last [LOG_TAIL_BYTES] of the debug log (never cleared — read only). */
    private fun buildReport(): String = buildString {
        append(runCatching { DeviceInfo.collect(this@DebugActivity) }
            .getOrElse { "== 设备信息 ==\n(收集失败: $it)\n" })
        append("\n== 调试日志 ==\n")
        append(runCatching {
            val f = Utils.debugLogFile
            if (!f.exists()) {
                "(无日志文件)"
            } else {
                val all = f.readText()
                if (all.length > LOG_TAIL_BYTES) {
                    "…(已截断，仅显示最后 ${LOG_TAIL_BYTES / 1024}KB)\n" +
                        all.substring(all.length - LOG_TAIL_BYTES)
                } else {
                    all.ifBlank { "(日志为空)" }
                }
            }
        }.getOrElse { "(读取日志失败: $it)" })
    }

    private fun button(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            sp(this, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun copy(text: String) {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("debug", text))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }.onFailure { Log.e("Watchdog", "copy failed", it) }
    }

    private var pendingSave: String? = null

    private fun save(text: String) {
        // Pre-Q needs WRITE_EXTERNAL_STORAGE to reach the public Downloads dir; request it once.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = text
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_WRITE)
            return
        }
        doSave(text)
    }

    private fun doSave(text: String) {
        runCatching {
            val saved = LogExporter.save(this, "qqpro_debug", text)
            if (saved == null) {
                Toast.makeText(this, "保存失败", Toast.LENGTH_LONG).show()
            } else {
                // Long toast + the saved path so it's findable on a watch (no file browser).
                val msg = if (saved.inDownloads) "已保存到下载文件夹:\n${saved.location}"
                else "无法写入下载，已保存到:\n${saved.location}"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }.onFailure { Log.e("Watchdog", "save failed", it) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_WRITE) return
        // Save either way: LogExporter falls back to app storage if the permission was denied.
        pendingSave?.let { doSave(it) }
        pendingSave = null
    }

    private fun share(text: String) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(
                Intent.createChooser(send, "分享调试信息").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.e("Watchdog", "share failed", it) }
    }

    companion object {
        private const val LOG_TAIL_BYTES = 64 * 1024
        private const val REQ_WRITE = 0x5101
    }
}
