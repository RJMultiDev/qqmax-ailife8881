package momoi.mod.qqpro.hook

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.view.View
import android.widget.TextView
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import com.tencent.qqnt.watch.profile.ProfileData
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.CurrentGroupMembers
import momoi.mod.qqpro.hook.action.isGroup
import momoi.mod.qqpro.util.Utils
import mqq.app.MobileQQ

// Link color for tappable @member spans (matches the bright nick-tag blue).
private const val AT_LINK_COLOR = 0xFF_5E97F6.toInt()

/**
 * Open [member]'s profile card — the same destination reached by tapping their
 * avatar or name in a group chat. Replicates the app's
 * `ProfileRuntimeServiceImpl.startMemberProfileCard`: builds a [ProfileData] and
 * navigates to `profileCardFragment` via the (obfuscated) NavController resolved
 * from this view's tree (see [findNavControllerFromTree]).
 */
fun View.openMemberProfile(member: MemberInfo) {
    try {
        val nav = findNavControllerFromTree() ?: run {
            Utils.log("openMemberProfile: NavController not found in view tree")
            return
        }
        val destId = resources.getIdentifier("profileCardFragment", "id", context.packageName)
        if (destId == 0) {
            Utils.log("openMemberProfile: profileCardFragment id not found")
            return
        }
        val showNick = member.showName()
        val isFriend = try {
            val app = MobileQQ.getMobileQQ().peekAppRuntime()
            (app?.getRuntimeService(IContactRuntimeService::class.java, "") as? IContactRuntimeService)
                ?.isFriend(member.uid) ?: false
        } catch (e: Exception) {
            false
        }
        // ProfileData(birthday, gender, uin, uid, nickName, isFriend) — the card
        // re-fetches the full profile from the uid on open.
        val profileData = ProfileData("0-0", -1, member.uin.toString(), member.uid, showNick, isFriend)
        val bundle = Bundle().apply { putParcelable("profile_data", profileData) }
        // navigate(int destId, Bundle args, NavOptions options) — name obfuscated.
        val navigate = nav.javaClass.methods.firstOrNull { m ->
            val p = m.parameterTypes
            p.size == 3 && p[0] == Int::class.javaPrimitiveType && p[1] == Bundle::class.java
        } ?: run {
            Utils.log("openMemberProfile: navigate(int,Bundle,..) not found on ${nav.javaClass.name}")
            return
        }
        navigate.invoke(nav, destId, bundle, null)
    } catch (e: Exception) {
        Utils.log("openMemberProfile error: ${e.message}")
    }
}

/** Display name shown for a member: card name, else remark, else nick, else uin. */
private fun MemberInfo.showName(): String =
    cardName.ifEmpty { remark.ifEmpty { nick.ifEmpty { uin.toString() } } }

/** A ClickableSpan that opens [member]'s profile card, in the nick-tag link color. */
private fun memberSpan(member: MemberInfo): ClickableSpan = object : ClickableSpan() {
    override fun onClick(widget: View) = widget.openMemberProfile(member)
    override fun updateDrawState(ds: TextPaint) {
        ds.color = AT_LINK_COLOR
        ds.isUnderlineText = false
    }
}

/** True if any ClickableSpan already covers [start,end) in [sp]. */
private fun hasClickableSpan(sp: Spannable, start: Int, end: Int): Boolean =
    sp.getSpans(start, end, ClickableSpan::class.java).any {
        sp.getSpanStart(it) < end && start < sp.getSpanEnd(it)
    }

/**
 * In a group chat, make `@member` mentions in this TextView tappable — tapping
 * opens the member's profile card. Matches the longest current-group member
 * display name (card name / remark / nick) that follows an `@`, so `@all` and
 * non-member text are left alone. No-op outside groups, when the setting is off,
 * or before the member list has loaded.
 */
fun TextView.parseAtMembers() {
    if (!Settings.parseAtMember.value || !CurrentContact.isGroup) return
    val members = CurrentGroupMembers.info ?: return
    val raw = text ?: return
    if (raw.indexOf('@') < 0) return
    // Names longest-first so "@张三丰" wins over a member literally named "张三".
    val named = members.values
        .flatMap { m -> setOf(m.cardName, m.remark, m.nick).filter { it.isNotEmpty() }.map { it to m } }
        .sortedByDescending { it.first.length }
    if (named.isEmpty()) return

    val sp = raw as? Spannable ?: SpannableStringBuilder(raw)
    val s = sp.toString()
    var i = 0
    var added = false
    while (i < s.length) {
        if (s[i] == '@') {
            val rest = s.substring(i + 1)
            val match = named.firstOrNull { rest.startsWith(it.first) }
            if (match != null) {
                val end = i + 1 + match.first.length
                if (!hasClickableSpan(sp, i, end)) {
                    sp.setSpan(memberSpan(match.second), i, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    added = true
                    i = end
                    continue
                }
            }
        }
        i++
    }
    if (added) {
        text = sp
        movementMethod = LinkMovementMethod.getInstance()
    }
}

