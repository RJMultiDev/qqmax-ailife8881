package momoi.mod.qqpro.util

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.confirmOpenUrl
import java.util.regex.Pattern

// CJK punctuation (and brackets) that should terminate a URL match.
private const val STOP = "\\s\\u4e00-\\u9fa5\\u3002\\uff1f\\uff01\\uff0c\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09\\u300a\\u300b\\u3008\\u3009\\u3010\\u3011\\u300e\\u300f\\u300c\\u300d\\uff43\\uff44\\u3014\\u3015\\u2026\\u2014\\uff5e\\uff4f\\uffe5"

// Strict: only matches URLs that carry an explicit http(s):// scheme.
private val strictPattern: Pattern =
    Pattern.compile("(http(s)?://)\\w+\\S+(\\.[^$STOP]+)+")

// Wide: also matches bare hosts like "example.com/path" with no scheme. Requires
// at least one dot and a 2+ letter TLD so plain numbers/words don't match.
private val widePattern: Pattern = Pattern.compile(
    "(?i)(?:https?://)?(?:[\\w-]+\\.)+[a-z]{2,}(?:[:/?#][^$STOP]*)?"
)

fun currentUrlPattern(): Pattern =
    if (Settings.wideUrlMatch.value) widePattern else strictPattern

/** First URL found in [text], honoring the wide-match setting, or null. */
fun firstUrl(text: CharSequence): String? {
    val matcher = currentUrlPattern().matcher(text)
    return if (matcher.find()) text.substring(matcher.start(), matcher.end()) else null
}

/** Add "https://" when a (wide-matched) URL has no scheme, so it can be opened/fetched. */
fun String.withScheme(): String =
    if (contains("://")) this else "https://$this"

fun TextView.linkify() {
    val spannable = SpannableStringBuilder(text)
    val existingSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
    existingSpans.forEach { spannable.removeSpan(it) }
    val matcher = currentUrlPattern().matcher(spannable)
    val links = mutableListOf<Pair<Int, Int>>()
    while (matcher.find()) {
        links.add(matcher.start() to matcher.end())
    }
    links.reversed().forEach { (start, end) ->
        val url = spannable.substring(start, end)

        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (Settings.confirmOpenLink.value) {
                        widget.confirmOpenUrl(url)
                    } else {
                        Utils.openUrl(url)
                    }
                }
            },
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    text = spannable
    movementMethod = LinkMovementMethod.getInstance()
}
