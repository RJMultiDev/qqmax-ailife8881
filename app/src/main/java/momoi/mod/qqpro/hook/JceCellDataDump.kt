package momoi.mod.qqpro.hook

import com.qq.taf.jce.JceStruct
import com.tencent.watch.qzone_impl.feed.model.JceCellData
import momoi.anno.mixin.StaticHook
import momoi.mod.qqpro.util.Utils

/**
 * DIAGNOSTIC (no behavior change): dump the raw QZone feed cell map for every parsed feed item.
 *
 * The watch receives each post as a Map<cellIndex, rawBytes> and only decodes 13 known indices
 * (0-5,7,10,11,14,18,21,23 — see JceCellData); anything else is dropped, which is why mini-app /
 * 小程序 shares fall back to "请在手机QQ查看". This logs which indices the SERVER actually sent (and
 * their byte lengths), so we can tell whether a mini-app post carries an extra (currently-undecoded)
 * cell — i.e. whether it's renderable via a hook, or genuinely absent (needs server/kernel changes).
 *
 * We hook the static decoder `JceCellData.a(map, index, struct)` (the constructor reuses the map
 * register, so a @ConstructorHook reads null by the time it runs). `a` is called once per known
 * index with the SAME map; we dump on index==0 (always the first call) for one line per post.
 *
 * Read with: adb pull .../cache/qqpro_debug.log ; grep "JceCellDump".
 */
@StaticHook(JceCellData::class)
fun a(map: Map<Int, ByteArray>?, index: Int, struct: JceStruct): JceStruct? {
    if (index == 0) QZoneCellDump.log(map)
    return JceCellData.a(map, index, struct)
}

object QZoneCellDump {
    private val KNOWN = setOf(0, 1, 2, 3, 4, 5, 7, 10, 11, 14, 18, 21, 23)

    @JvmStatic
    fun log(map: Map<*, *>?) {
        runCatching {
            if (map == null) { Utils.log("JceCellDump: map=null"); return }
            val entries = map.entries
                .mapNotNull { e ->
                    val idx = (e.key as? Int) ?: return@mapNotNull null
                    val len = (e.value as? ByteArray)?.size ?: -1
                    idx to len
                }
                .sortedBy { it.first }
            val sb = StringBuilder("JceCellDump: indices=[")
            entries.forEachIndexed { i, (idx, len) ->
                if (i > 0) sb.append(", ")
                val mark = if (idx !in KNOWN) "*" else ""
                sb.append("$idx$mark:${len}B")
            }
            sb.append("]")
            Utils.log(sb.toString())

            // For each UNKNOWN cell, print a readable-text preview so we can see if it carries the
            // mini-app share's title/url/appid/icon (Jce strings are length-prefixed UTF-8).
            @Suppress("UNCHECKED_CAST")
            val raw = map as Map<Any?, Any?>
            entries.filter { it.first !in KNOWN }.forEach { (idx, _) ->
                val bytes = raw[idx] as? ByteArray ?: return@forEach
                Utils.log("JceCellDump:   [$idx] text=\"${printablePreview(bytes)}\"")
            }
        }
    }

    /** Extract printable text (incl. UTF-8 CJK) from raw Jce bytes, for eyeballing cell content. */
    private fun printablePreview(bytes: ByteArray): String {
        val s = String(bytes, Charsets.UTF_8)
        val out = StringBuilder()
        for (c in s) out.append(if (c >= ' ' && c.code != 0x7f) c else '·')
        return out.toString().replace(Regex("·{2,}"), " ").trim().take(300)
    }
}
