package momoi.mod.qqpro.hook

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qqnt.chats.core.adapter.itemdata.RecentContactChatItem
import com.tencent.qqnt.kernel.nativeinterface.DeleteRecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelRecentContactListener
import com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactExtra
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.qqnt.watch.ui.componet.tablayout.CircleIndicator
import java.lang.ref.WeakReference
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.findAll
import momoi.mod.qqpro.hook.action.RecentContacts
import momoi.mod.qqpro.hook.view.smoothScrollToStart
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.lib.material.MaterialSymbol
import momoi.mod.qqpro.lib.material.MaterialSymbols
import momoi.mod.qqpro.util.Utils

/**
 * Home/main page (conversation list) bottom navigation. We HIDE the native [CircleIndicator] and
 * build our own fully independent nav bar so nothing fights us: the native indicator re-styles its
 * children on every scroll (causing distortion) and its wired ViewPager2 isn't the pager the watch
 * actually swipes. Our bar drives whatever content pager is live (v1 androidx ViewPager *or*
 * ViewPager2) via reflection, so taps switch pages and swipes update the highlight.
 *
 * Features (settings under 主页导航):
 *  - icon for EVERY page, selected tinted blue ([Settings.mainNavAllIcons]),
 *  - spread evenly across the width in square mode ([Settings.mainNavSquare]),
 *  - independent bar/icon height ([Settings.mainNavHeight]),
 *  - bottom placement ([Settings.bottomMainNav]),
 *  - per-page unread badges ([Settings.mainNavUnread]).
 */
object MainNav {
    private const val NAV_TAG = "qqpro_main_nav"
    // Material 3 (dark) palette. The selected item sits on a tonal "active indicator" pill.
    private val ACCENT get() = M3.primary                   // on-secondary-container (selected icon)
    private val PILL_COLOR get() = M3.primaryContainer      // secondary-container (active indicator pill)
    private val IDLE_ICON = M3.onSurfaceVariant       // on-surface-variant (inactive icon)
    private val DOT_COLOR = M3.onSurfaceVariant
    private val BADGE_COLOR = M3.badge

    // Live unread, kept in sync by the kernel listener — same source RichTitlebar uses.
    private val unread = HashMap<String, Int>()
    private var listenerAdded = false

    // Published by ContactListFragmentHook (friend + group notification counts). Best-effort: only
    // fresh once the contacts page has been opened.
    @JvmField var contactUnread = 0
    @JvmField var contactFriendUnread = 0
    @JvmField var contactGroupUnread = 0

    // Live fragment refs published by the contacts / qzone page hooks so a repeat-tap on the nav can
    // reach into the page that's currently shown and open its notification screen. WeakReference so a
    // recreated (FragmentStateAdapter) fragment isn't leaked. See [openContactNotify]/[openQzoneNotify].
    @JvmField var contactFragment: WeakReference<Any>? = null
    @JvmField var qzoneFragment: WeakReference<Any>? = null

    // Round-robin cursor over the chat list's unread conversations (repeat-tap on the messages page).
    private var chatUnreadCursor = 0

    private var active: NavState? = null

    class NavState(
        val nav: LinearLayout,
        val pager: PagerCtl,
        val iconMap: Map<Int, String>,
        val pageCount: Int,
    ) {
        val cells = ArrayList<Cell>()
        var current = 0
        // Last fully-rendered (page, unread-signature) so the high-frequency scroll listener only
        // re-styles when something actually changed.
        var renderedKey = ""
    }

    class Cell(val frame: FrameLayout, val pill: View, val icon: ImageView, val dot: View, val badge: TextView)

    /** Live unread updates pushed by the kernel; recompute badges in place. */
    class UnreadListener : IKernelRecentContactListener {
        override fun onMsgUnreadCountUpdate(map: HashMap<String, Int>?) {
            if (map != null) { unread.putAll(map); refresh() }
        }
        override fun onRecentContactListChanged(
            sorted: ArrayList<Long>?, changed: ArrayList<RecentContactInfo>?, extra: RecentContactExtra?,
        ) { changed?.forEach { put(it) }; refresh() }
        override fun onRecentContactListChangedVer2(list: ArrayList<RecentContactListChangedInfo>?, seq: Int) {
            list?.forEach { it.changedList?.forEach { c -> put(c) } }; refresh()
        }
        override fun onRecentContactNotification(
            list: ArrayList<RecentContactInfo>?, common: NotificationCommonInfo?, seq: Int,
        ) { list?.forEach { put(it) }; refresh() }
        override fun onGuildDisplayRecentContactListChanged(list: ArrayList<RecentContactListChangedInfo>?) {}
        override fun onDeletedContactsNotify(list: ArrayList<DeleteRecentContactInfo>?) {}
    }

    private fun put(c: RecentContactInfo?) {
        val uid = c?.peerUid ?: return
        if (uid.isEmpty()) return
        unread[uid] = c.unreadCnt.toInt()
        // Capture the muted/DND state the kernel carries so DND chats are excluded from the badge
        // total even before their list row renders (see RecentContacts.mutedMap).
        RecentContacts.recordMuted(uid, RecentContacts.isMuted(c.isMsgDisturb, c.shieldFlag))
    }

    private fun messagesUnread(): Int {
        RecentContacts.map.forEach { (k, v) -> if (!unread.containsKey(k)) unread[k] = v.unreadCntCached }
        return unread.entries
            .filter { it.value > 0 && !RecentContacts.isDisturb(it.key) }
            .sumOf { it.value }
    }

    private fun unreadFor(state: NavState, page: Int): Int = when {
        page == state.pageCount - 1 -> 0   // last page = self/settings, never badged
        page == 0 -> messagesUnread()
        page == 1 -> contactUnread
        else -> 0
    }

    private fun ensureListener() {
        if (listenerAdded) return
        runCatching {
            KernelServiceUtil.g()?.recentContactService?.addKernelRecentContactListener(UnreadListener())
            listenerAdded = true
        }.onFailure { Utils.log("MainNav: listener register failed: $it") }
    }

    fun refresh() {
        val state = active ?: return
        state.nav.post { render(state) }
    }

    fun install(root: ViewGroup) {
        runCatching {
            val indicator = root.findAll { it is CircleIndicator } as? CircleIndicator
                ?: run { Utils.log("MainNav: indicator not found"); return }
            val parent = indicator.parent as? ViewGroup
                ?: run { Utils.log("MainNav: indicator has no parent"); return }
            // The real content pager is the ViewPager2 (MainFragment.vp); the tree may also contain
            // an unrelated v1 ViewPager, so match ViewPager2 first (same as the 屏蔽返回键 hook).
            val pagerView = root.findAll { it.javaClass.name.endsWith("ViewPager2") }
                ?: root.findAll { it !== indicator && it.javaClass.name.contains("ViewPager") }
                ?: run { Utils.log("MainNav: pager not found"); return }
            Utils.log("MainNav: pager=${pagerView.javaClass.name}")
            val pager = PagerCtl(pagerView)
            val pageCount = pager.count()
            if (pageCount <= 0) { Utils.log("MainNav: empty pager"); return }
            val iconMap = materialIconMapOf(pageCount)

            // Remove a nav we built on a previous onViewCreated (returning to the home page).
            (parent.findAll { it.tag == NAV_TAG } as? ViewGroup)?.let { parent.removeView(it) }
            active?.pager?.removeListener()

            // Hide the native indicator entirely; we render our own.
            indicator.visibility = View.GONE

            val nav = LinearLayout(parent.context).apply {
                tag = NAV_TAG
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                // The bar otherwise sits over the window's pure black; fill it with the M3 page
                // surface so it matches the materialized pages (chat list etc.) seamlessly.
                setBackgroundColor(M3.surface)
            }
            val state = NavState(nav, pager, iconMap, pageCount)
            state.current = pager.current()
            active = state

            buildCells(state)
            parent.clipChildren = false
            parent.clipToPadding = false
            val barHeight = Settings.mainNavHeight.value.toInt().coerceIn(8, 48).dp + 6.dp
            parent.addView(nav, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight))

            pager.observe { render(state) }
            positionBar(parent, pagerView, indicator, nav, barHeight)

            ensureListener()
            render(state)
        }.onFailure { Utils.log("MainNav install failed: $it") }
    }

    /**
     * Custom nav disabled: leave the native page-indicator as-is but still move it to the bottom
     * when [Settings.bottomMainNav] is on (the pre-session behavior). Top mode = native default.
     */
    fun installNative(root: ViewGroup) {
        if (!Settings.bottomMainNav.value) return
        runCatching {
            val indicator = root.findAll { it is CircleIndicator } as? CircleIndicator
                ?: run { Utils.log("MainNav(native): indicator not found"); return }
            val scale = (Settings.mainNavHeight.value / 16f).coerceIn(1f, 3f)
            indicator.scaleX = scale
            indicator.scaleY = scale
            (indicator.parent as? ViewGroup)?.apply { clipChildren = false; clipToPadding = false }
            indicator.post {
                runCatching {
                    val parent = indicator.parent as? View ?: return@post
                    val vp = root.findAll { it.javaClass.name.endsWith("ViewPager2") }
                        ?: root.findAll { it.javaClass.name.contains("ViewPager") }
                    val band = (indicator.height * scale).toInt()
                    vp?.let {
                        it.layoutParams = it.layoutParams.apply { height = parent.height - band }
                        it.requestLayout()
                        it.post { it.translationY = -it.top.toFloat() }
                    }
                    val bandCenter = parent.height - band / 2f
                    indicator.translationY = bandCenter - (indicator.top + indicator.height / 2f)
                }.onFailure { Utils.log("MainNav(native) position failed: $it") }
            }
        }.onFailure { Utils.log("MainNav(native) failed: $it") }
    }

    private fun buildCells(state: NavState) {
        val nav = state.nav
        val ctx = nav.context
        val square = Settings.mainNavSquare.value
        val iconSize = Settings.mainNavHeight.value.toInt().coerceIn(8, 48).dp
        val dotSize = (iconSize / 3).coerceAtLeast(4.dp)
        // M3 active-indicator pill. Kept modest so the bar stays compact on a round screen, and the
        // cell is sized to the pill so toggling it never reflows the row (icons must not shift).
        val pillW = (iconSize * 1.5f).toInt()
        val pillH = iconSize + 4.dp
        val cellW = pillW + 4.dp
        val barHeight = pillH + 2.dp

        state.cells.clear()
        nav.removeAllViews()
        for (i in 0 until state.pageCount) {
            val frame = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
            // M3 active-indicator pill behind the selected icon.
            val pill = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = 9999f; setColor(PILL_COLOR)
                }
                visibility = View.GONE
            }
            val icon = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                state.iconMap[i]?.let { setImageDrawable(MaterialSymbol(it, IDLE_ICON)) }
            }
            val dot = View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(DOT_COLOR) }
            }
            val badge = TextView(ctx).apply {
                setTextColor(M3.onSurface)
                textSize = 8f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = 9999f; setColor(BADGE_COLOR)
                }
                setPadding(3.dp, 0, 3.dp, 0)
                minWidth = 12.dp
                visibility = View.GONE
            }
            // Inner container sized to the icon and centered in the cell. The badge anchors to the
            // inner's top-right so it hugs the icon even when the cell is stretched (square mode).
            val inner = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
            val innerW = iconSize + 6.dp
            inner.addView(pill, FrameLayout.LayoutParams(pillW, pillH, Gravity.CENTER))
            inner.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            inner.addView(dot, FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER))
            inner.addView(badge, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, 12.dp, Gravity.TOP or Gravity.END))
            frame.addView(inner, FrameLayout.LayoutParams(innerW, barHeight, Gravity.CENTER))

            val pos = i
            frame.isClickable = true
            frame.setOnClickListener { handleNavTap(state, pos) }

            // Fixed cell width (or weighted in square mode) so the pill never reflows the row.
            val lp = if (square) {
                LinearLayout.LayoutParams(0, barHeight, 1f)
            } else {
                LinearLayout.LayoutParams(cellW, barHeight).apply {
                    marginStart = 2.dp; marginEnd = 2.dp
                }
            }
            nav.addView(frame, lp)
            state.cells.add(Cell(frame, pill, icon, dot, badge))
        }
    }

    private fun render(state: NavState, force: Boolean = false) {
        val allIcons = Settings.mainNavAllIcons.value
        val showUnread = Settings.mainNavUnread.value
        state.current = runCatching { state.pager.current() }.getOrDefault(state.current)
        val counts = if (showUnread) (0 until state.pageCount).map { unreadFor(state, it) } else emptyList()
        val key = "${state.current}|$allIcons|$showUnread|$counts"
        if (!force && key == state.renderedKey) return
        state.renderedKey = key
        state.cells.forEachIndexed { i, cell ->
            val selected = i == state.current
            val hasIcon = state.iconMap[i] != null
            val showIcon = hasIcon && (allIcons || selected)
            cell.icon.visibility = if (showIcon) View.VISIBLE else View.GONE
            cell.dot.visibility = if (showIcon) View.GONE else View.VISIBLE
            // M3 active indicator: pill only behind the selected page's icon.
            cell.pill.visibility = if (selected && showIcon) View.VISIBLE else View.GONE
            if (showIcon) {
                cell.icon.setColorFilter(if (selected) ACCENT else IDLE_ICON)
            } else {
                (cell.dot.background as? GradientDrawable)?.setColor(if (selected) ACCENT else DOT_COLOR)
            }
            val count = if (showUnread) counts[i] else 0
            if (count > 0) {
                cell.badge.text = if (count > 99) "99+" else count.toString()
                cell.badge.visibility = View.VISIBLE
            } else {
                cell.badge.visibility = View.GONE
            }
        }
    }

    /**
     * Nav cell tapped. When [Settings.mainNavUnreadJump] is on and the tap is on the page the user is
     * already viewing, jump to that page's pending item ([onRepeatTap]) instead of a no-op page
     * switch. Otherwise switch pages normally (resetting the chat-unread cursor for a fresh cycle).
     */
    private fun handleNavTap(state: NavState, pos: Int) {
        runCatching {
            val onThisPage = runCatching { state.pager.current() }.getOrDefault(state.current) == pos
            if (onThisPage && Settings.mainNavUnread.value && Settings.mainNavUnreadJump.value &&
                onRepeatTap(state, pos)
            ) return
            chatUnreadCursor = 0
            state.pager.setCurrent(pos)
        }.onFailure { Utils.log("MainNav tap($pos) failed: $it") }
    }

    /** Per-page repeat-tap action. Returns true when the tap was consumed (no page switch needed). */
    private fun onRepeatTap(state: NavState, pos: Int): Boolean = when (pos) {
        0 -> cycleChatUnread(state)
        1 -> openContactNotify()
        2 -> openQzoneNotify()
        else -> false
    }

    /**
     * Messages page: scroll the next unread conversation to the top of the chat list, cycling back to
     * the first after the last. Reads the live [ChatsListAdapter] items (RecentContactChatItem) and
     * skips muted/DND chats — same filter the unread badge uses ([messagesUnread]).
     */
    private fun cycleChatUnread(state: NavState): Boolean {
        val pagerView = state.pager.pager as? ViewGroup ?: return false
        val ctx = pagerView.context
        val rvId = ctx.resources.getIdentifier("chat_list", "id", ctx.packageName)
        val rv = (if (rvId != 0) pagerView.findAll { it is RecyclerView && it.id == rvId } else null)
            as? RecyclerView ?: run { Utils.log("MainNav: chat_list RV not found"); return false }
        val adapter = rv.adapter ?: return false
        val getItem = runCatching {
            adapter.javaClass.getMethod("getItem", Int::class.javaPrimitiveType)
        }.getOrNull() ?: run { Utils.log("MainNav: adapter.getItem not found"); return false }

        val positions = ArrayList<Int>()
        for (i in 0 until adapter.itemCount) {
            val item = runCatching { getItem.invoke(adapter, i) }.getOrNull()
            if (item is RecentContactChatItem &&
                item.a.unreadCnt > 0 && !RecentContacts.isMuted(item.p, item.q)
            ) positions.add(i)
        }
        if (positions.isEmpty()) { chatUnreadCursor = 0; return true }

        if (chatUnreadCursor >= positions.size) chatUnreadCursor = 0
        val target = positions[chatUnreadCursor]
        chatUnreadCursor = (chatUnreadCursor + 1) % positions.size
        rv.stopScroll()
        rv.smoothScrollToStart(target)
        Utils.log("MainNav: chat unread jump pos=$target (cursor=$chatUnreadCursor/${positions.size})")
        return true
    }

    /**
     * Contacts page: open the notification screen that actually has a pending count (好友通知 first,
     * else 群通知). Delegates to the live fragment's copied-in methods via reflection — the @Mixin
     * class type doesn't exist at runtime, so we can't reference it directly.
     */
    private fun openContactNotify(): Boolean {
        val f = contactFragment?.get() ?: return false
        val method = when {
            contactFriendUnread > 0 -> "topFriendNotify"
            contactGroupUnread > 0 -> "topGroupNotify"
            else -> {
                // Nothing pending — jump the contacts list to the top instead of doing nothing.
                Utils.log("MainNav: contacts no unread → scroll to top")
                scrollToTop(findRecycler((f as? androidx.fragment.app.Fragment)?.view))
                return true
            }
        }
        return runCatching { f.javaClass.getMethod(method).invoke(f); true }
            .onFailure { Utils.log("MainNav: openContactNotify failed: $it") }
            .getOrDefault(true)
    }

    /**
     * QZone page: open the 通知 screen only when there's an active notification (the page's QUIBadge
     * `unread` field `k`, set VISIBLE in QZoneMainFrame.f0() iff the undeal count > 0); otherwise jump
     * the feed (SmartRefreshLayout `n` → RecyclerView) to the top.
     */
    private fun openQzoneNotify(): Boolean {
        val f = qzoneFragment?.get() ?: return false
        val hasNotify = runCatching {
            (f.javaClass.getField("k").get(f) as? View)?.visibility == View.VISIBLE
        }.getOrDefault(false)
        if (hasNotify) {
            return runCatching { f.javaClass.getMethod("barNotify").invoke(f); true }
                .onFailure { Utils.log("MainNav: openQzoneNotify failed: $it") }
                .getOrDefault(true)
        }
        // No active notification — scroll the feed to the top instead of opening 通知.
        Utils.log("MainNav: qzone no notify → scroll to top")
        runCatching {
            scrollToTop(findRecycler(f.javaClass.getField("n").get(f) as? View))
        }.onFailure { Utils.log("MainNav: qzone scroll-top failed: $it") }
        return true
    }

    /** The first RecyclerView at or under [v] (the page root may itself be the list). */
    private fun findRecycler(v: View?): RecyclerView? =
        v as? RecyclerView ?: (v as? ViewGroup)?.findAll { it is RecyclerView } as? RecyclerView

    private fun scrollToTop(rv: RecyclerView?) {
        rv ?: return
        rv.stopScroll()
        rv.smoothScrollToStart(0)
    }

    /**
     * Reserve a band for the bar (bottom or top) by shrinking the pager and sliding the bar into the
     * freed space — so the nav never floats over the page content in either mode.
     */
    private fun positionBar(
        parent: ViewGroup, pagerView: View, indicator: View, nav: LinearLayout, barHeight: Int,
    ) {
        nav.post {
            runCatching {
                pagerView.layoutParams = pagerView.layoutParams.apply { height = parent.height - barHeight }
                pagerView.requestLayout()
                if (Settings.bottomMainNav.value) {
                    // Bar at the bottom; content flush to the top.
                    nav.translationY = (parent.height - barHeight).toFloat()
                    pagerView.post { pagerView.translationY = -pagerView.top.toFloat() }
                } else {
                    // Bar at the top; push content down below it.
                    nav.translationY = 0f
                    pagerView.post { pagerView.translationY = (barHeight - pagerView.top).toFloat() }
                }
            }.onFailure { Utils.log("MainNav positionBar failed: $it") }
        }
    }

    // Fixed page order: 0=chat, 1=contacts(person), 2=qzone(star), 3=self(settings)
    private val PAGE_ICONS = listOf(
        MaterialSymbols.chat_bubble,
        MaterialSymbols.person,
        MaterialSymbols.star,
        MaterialSymbols.settings,
    )

    private fun materialIconMapOf(pageCount: Int): Map<Int, String> = buildMap {
        for (i in 0 until minOf(pageCount, PAGE_ICONS.size)) put(i, PAGE_ICONS[i])
    }

    /**
     * Reflection wrapper over the live content pager, which may be a v1 androidx ViewPager or a
     * ViewPager2 — both expose getAdapter/getCurrentItem/setCurrentItem with the same signatures.
     */
    class PagerCtl(val pager: View) {
        fun count(): Int = runCatching {
            val adapter = pager.javaClass.getMethod("getAdapter").invoke(pager) ?: return 0
            runCatching { adapter.javaClass.getMethod("getItemCount").invoke(adapter) as Int }
                .getOrElse { adapter.javaClass.getMethod("getCount").invoke(adapter) as Int }
        }.getOrDefault(0)

        fun current(): Int = runCatching {
            pager.javaClass.getMethod("getCurrentItem").invoke(pager) as Int
        }.getOrDefault(0)

        fun setCurrent(i: Int) {
            // The app's ViewPager2 is R8-minified: setCurrentItem(int,boolean) may be stripped, so
            // fall back to setCurrentItem(int) (same approach as the 屏蔽返回键 hook).
            runCatching {
                pager.javaClass.getMethod(
                    "setCurrentItem", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
                ).invoke(pager, i, true)
            }.recoverCatching {
                pager.javaClass.getMethod("setCurrentItem", Int::class.javaPrimitiveType).invoke(pager, i)
            }.onFailure { Utils.log("MainNav setCurrent($i) failed: $it") }
        }

        // registerOnPageChangeCallback is also stripped, so we watch page changes via the framework
        // OnScrollChangedListener (fires on any descendant scroll, incl. page swipes) and re-read
        // getCurrentItem(), which is NOT minified. Cheap: render only acts when the page changed.
        private var scrollListener: android.view.ViewTreeObserver.OnScrollChangedListener? = null

        fun observe(onChange: () -> Unit) {
            val l = android.view.ViewTreeObserver.OnScrollChangedListener { onChange() }
            scrollListener = l
            runCatching { pager.viewTreeObserver.addOnScrollChangedListener(l) }
        }

        fun removeListener() {
            scrollListener?.let { l -> runCatching { pager.viewTreeObserver.removeOnScrollChangedListener(l) } }
            scrollListener = null
        }
    }
}
