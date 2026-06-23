package momoi.mod.qqpro.hook.qzone

import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.feed.model.CellSummary
import momoi.mod.qqpro.util.Utils
import org.json.JSONObject

/**
 * Resolves QZone `[em]eNNNNNN[/em]` emoji codes that the watch's QZone parser (StringUtil EmoMatcher)
 * leaves as placeholder boxes.
 *
 * Discovery (from diffing a phone QQ APK): the codes in the 400xxx–402xxx range are **Unicode emoji**,
 * not custom 小黄脸 face images. The app's own `assets/face_config.json` carries an `emoji` array that
 * maps each `EMCode` → `QSid` (the literal Unicode character), e.g. `400867` → 😭. The watch renderer
 * doesn't perform that substitution, so we do it ourselves: replace `[em]e<EMCode>[/em]` with the real
 * Unicode char, which the system emoji font then draws. Codes not in the table (newer than this build)
 * are left untouched for the native EmoMatcher to handle (classic image sysfaces) or fall through.
 */
object QzoneEmoji {

    private val RE = Regex("\\[em\\]e(\\d+)\\[/em\\]")

    private val map: Map<String, String> by lazy { load() }

    private fun load(): Map<String, String> {
        val m = HashMap<String, String>()
        // Primary: our bundled, interpolation-extended map (EMCode → Unicode char), generated from the
        // phone QQ's downloaded emoji config + the watch's own face_config (see assets injection).
        runCatching {
            val txt = Utils.application.assets.open("qzone_emoji_map.json")
                .use { it.readBytes().toString(Charsets.UTF_8) }
            val o = JSONObject(txt)
            val keys = o.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                o.optString(code).takeIf { it.isNotEmpty() }?.let { m[code] = it }
            }
        }.onFailure { Utils.log("QzoneEmoji load bundled: $it") }
        // Fallback/supplement: the watch's own face_config emoji array (EMCode → QSid char).
        runCatching {
            val txt = Utils.application.assets.open("face_config.json")
                .use { it.readBytes().toString(Charsets.UTF_8) }
            val emoji = JSONObject(txt).optJSONArray("emoji")
            if (emoji != null) for (i in 0 until emoji.length()) {
                val o = emoji.optJSONObject(i) ?: continue
                val code = o.optString("EMCode"); val ch = o.optString("QSid")
                if (code.isNotEmpty() && ch.isNotEmpty() && !m.containsKey(code)) m[code] = ch
            }
        }.onFailure { Utils.log("QzoneEmoji load face_config: $it") }
        Utils.log("QzoneEmoji: loaded ${m.size} emoji mappings")
        return m
    }

    /**
     * Replace `[em]e<EMCode>[/em]`:
     *  - known emoji code → its Unicode character (the watch font draws it);
     *  - unknown emoji-range code (≥ 400000, not in this build's config — newer than the bundled
     *    9.2.80 face_config, QQ downloads these at runtime) → removed, so it doesn't reach the native
     *    EmoMatcher's ugly placeholder box;
     *  - classic 小黄脸 codes (< 400000) → left untouched for StringUtil.a to render as image faces.
     */
    fun substitute(text: CharSequence?): CharSequence {
        if (text.isNullOrEmpty()) return text ?: ""
        val s = text.toString()
        if (!s.contains("[em]")) return text
        return RE.replace(s) { mr ->
            val code = mr.groupValues[1]
            map[code] ?: run {
                val n = code.toIntOrNull()
                // Unknown emoji-range code → visible marker (so coverage is easy to eyeball);
                // classic 小黄脸 codes (< 400000) → leave for StringUtil.a image faces.
                if (n != null && n >= 400000) "[?$code]" else mr.value
            }
        }
    }

    /**
     * Patch the NATIVE QZone render path (when 完全重做空间 is off): rewrite the post's [CellSummary]
     * text in place with the emoji substitution, and clear its cached parse so the native
     * getParsedSummary re-parses the substituted text. Called from the native summary cell hook.
     */
    fun patchSummary(data: BusinessFeedData) {
        patchCell(runCatching { data.cellSummaryV2 }.getOrNull())
        patchCell(runCatching { data.originalInfo?.cellSummaryV2 }.getOrNull())
    }

    private fun patchCell(cs: CellSummary?) {
        if (cs == null) return
        val orig = cs.summary ?: return
        if (!orig.contains("[em]")) return
        val sub = substitute(orig).toString()
        if (sub != orig) {
            cs.summary = sub
            cs.parsedSummary = null   // force getParsedSummary to re-parse the substituted text
        }
    }
}
