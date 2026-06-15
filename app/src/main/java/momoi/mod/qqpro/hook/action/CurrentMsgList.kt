package momoi.mod.qqpro.hook.action

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tencent.aio.api.factory.IAIOFactory
import com.tencent.aio.api.list.IDataSubmitApi
import com.tencent.aio.api.list.IListUIOperationApi
import com.tencent.aio.base.chat.ChatPie
import com.tencent.aio.base.mvi.part.MsgListUiState
import com.tencent.aio.data.msglist.IMsgItem
import com.tencent.aio.main.fragment.ChatFragment
import com.tencent.aio.part.root.panel.content.firstLevel.msglist.mvx.intent.MsgListDataIntent
import com.tencent.watch.aio_impl.coreImpl.vb.WatchAIOListVB
import com.tencent.watch.aio_impl.data.WatchAIOMsgItem
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.lib.Observable
import momoi.mod.qqpro.util.ThreadManager
import momoi.mod.qqpro.util.Utils
import java.util.LinkedList

object CurrentMsgList {
    lateinit var vb: WatchAIOListVB
        private set
    // The list UI operation API from the latest render — used to submit a new list live
    // (e.g. after a local delete, which the kernel doesn't push to the open chat list).
    var uiOp: IListUIOperationApi? = null
        private set
    var msgList = Observable(mutableListOf<WatchAIOMsgItem>())
        private set

    fun getMsgIndex(msg: WatchAIOMsgItem): Int {
        return msgList.value.indexOf(msg)
    }

    /**
     * Remove messages from the currently open chat list in place, by msgId. Native local delete
     * ("删除", not 撤回) updates the DB but doesn't refresh the open AIO list — it only shows on
     * re-entry. We submit a filtered list to the data-submit API so the row disappears live.
     */
    fun removeLive(ids: Set<Long>) {
        if (ids.isEmpty()) return
        ThreadManager.runOnUiThread({
            runCatching {
                val op = uiOp ?: run { Utils.log("removeLive: uiOp null"); return@runCatching }
                val cur = op.m() ?: return@runCatching
                val newList = cur.filterNot { (it as? WatchAIOMsgItem)?.d?.msgId in ids }
                if (newList.size == cur.size) { Utils.log("removeLive: no match in live list"); return@runCatching }
                // SubmitAction's fields are final at runtime — must set them via the constructor.
                // Last arg is Kotlin's defaults mask: 0 = use all provided args (list, null scope,
                // immediate=true, null callback).
                op.A(IDataSubmitApi.SubmitAction<IMsgItem>(newList, null, true, null, 0))
                // Keep our mirror in sync so the merge in Hook.n doesn't re-add the removed item.
                msgList.update(msgList.value.filterNot { it.d.msgId in ids }.toMutableList())
                Utils.log("removeLive: removed ${cur.size - newList.size} msg(s)")
            }.onFailure { Utils.log("removeLive failed: $it") }
        })
    }

    private var isLoadingMsg = false
    private fun loadMoreMsg() {
        if (!isLoadingMsg) {
            msgList.observeOnce {
                isLoadingMsg = false
            }
            isLoadingMsg = true
            Utils.log("Load more msg. currentSize: ${msgList.value.size}")
            vb.L(MsgListDataIntent.LoadTopPage("WatchAIOListVB"))
        }
    }

    /**
     * Scroll target is [count] messages above [current]. Pages in older messages until enough
     * history is loaded, then invokes [callback] with the resulting list position.
     *
     * [onProgress] is called with a 0..100 percentage after each page so the caller can show a
     * loading indicator. [onFail] fires (on the UI thread) if a page load times out or the top of
     * history is reached before the target — so the UI can show a toast and reset instead of
     * hanging silently.
     */
    fun upwardMsg(
        current: Int,
        count: Int,
        onProgress: (Int) -> Unit = {},
        onFail: () -> Unit = {},
        callback: (Int) -> Unit
    ) {
        val target = msgList.value.size - 1 - current + count
        upwardMsgInternal(target, msgList.value.size, onProgress, onFail, callback)
    }

    private fun upwardMsgInternal(
        target: Int,
        startSize: Int,
        onProgress: (Int) -> Unit,
        onFail: () -> Unit,
        callback: (Int) -> Unit
    ) {
        if (msgList.value.size >= target) {
            callback(msgList.value.size - target - 1)
            return
        }
        val before = msgList.value.size
        // Percentage of the way from where we started to the target size.
        if (target > startSize) {
            val pct = ((before - startSize) * 100 / (target - startSize)).coerceIn(0, 99)
            onProgress(pct)
        }
        var settled = false
        msgList.observeOnce {
            if (settled) return@observeOnce
            settled = true
            ThreadManager.runOnUiThread({
                if (msgList.value.size <= before) {
                    // List stopped growing -> reached the top of history before the target.
                    Utils.log("upwardMsg: reached top of history before target=$target, size=${msgList.value.size}")
                    onFail()
                } else {
                    upwardMsgInternal(target, startSize, onProgress, onFail, callback)
                }
            })
        }
        ThreadManager.runOnUiThread({
            if (settled) return@runOnUiThread
            settled = true
            Utils.log("upwardMsg: timed out waiting for more msgs, target=$target size=${msgList.value.size}")
            onFail()
        }, 5000L)
        isLoadingMsg = false // clear any stuck guard from a previously interrupted load
        loadMoreMsg()
    }

    /**
     * Page in older messages until the top of history is reached (the list stops growing),
     * then call [onDone] on the UI thread. [onProgress] is called after each page with the
     * current total size. Used by chat search, which needs the whole history in memory.
     *
     * [shouldContinue] is checked before each page and before every callback — pass a
     * lifecycle predicate (e.g. `{ isAdded }`) so a dismissed/cancelled search stops the chain
     * instead of loading the whole history in the background and firing stale callbacks.
     *
     * Loading is triggered after clearing [isLoadingMsg]: that guard can be left stuck `true`
     * by a previous load that was interrupted (chat closed mid-load), which would otherwise make
     * [loadMoreMsg] silently no-op and this never progress.
     */
    fun loadAll(
        onProgress: (Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
        onDone: () -> Unit
    ) {
        if (!shouldContinue()) {
            Utils.log("loadAll: cancelled before start")
            return
        }
        val before = msgList.value.size
        var settled = false
        msgList.observeOnce {
            if (settled) return@observeOnce
            settled = true
            ThreadManager.runOnUiThread({
                if (!shouldContinue()) {
                    Utils.log("loadAll: cancelled, stopping")
                    return@runOnUiThread
                }
                if (msgList.value.size <= before) {
                    Utils.log("loadAll: reached top of history, total=${msgList.value.size}")
                    onDone()
                } else {
                    onProgress(msgList.value.size)
                    loadAll(onProgress, shouldContinue, onDone)
                }
            })
        }
        ThreadManager.runOnUiThread({
            if (settled) return@runOnUiThread
            settled = true
            Utils.log("loadAll: timed out waiting for more msgs, total=${msgList.value.size}")
            if (shouldContinue()) onDone()
        }, 5000L)
        isLoadingMsg = false // clear any stuck guard from a previously interrupted load
        loadMoreMsg()
    }

    fun findMsg(
        seq: Long,
        result: (WatchAIOMsgItem?) -> Unit,
        repeatCount: Int = 1000
    ) {
        val msg = msgList.value.find { it.d.msgSeq == seq }
        if (msg != null) {
            result(msg)
            return
        }
        if (repeatCount <= 0) {
            Utils.log("findMsg: give up (repeat exhausted) seq=$seq")
            result(null)
            return
        }
        // Load older messages and retry. Two ways to stop instead of hanging forever:
        // 1) the list stopped growing after a load -> we reached the top of history;
        // 2) no update arrived within the timeout -> load is stuck / nothing to load.
        val sizeBefore = msgList.value.size
        var settled = false
        msgList.observeOnce {
            if (settled) return@observeOnce
            settled = true
            if (msgList.value.size <= sizeBefore) {
                // Reached the top of history without finding the target.
                Utils.log("findMsg: reached top of history, seq=$seq not found")
                result(null)
            } else {
                findMsg(seq, result, repeatCount - 1)
            }
        }
        ThreadManager.runOnUiThread({
            if (settled) return@runOnUiThread
            settled = true
            Utils.log("findMsg: timed out waiting for more msgs, seq=$seq")
            result(null)
        }, 3000L)
        loadMoreMsg()
    }

    @Mixin
    class Hook : WatchAIOListVB() {
        @Suppress("UNCHECKED_CAST")
        override fun n(state: MsgListUiState, uiHelper: IListUIOperationApi) {
            vb = this
            uiOp = uiHelper
            val msg = msgList.value
            val list = state as LinkedList<WatchAIOMsgItem>
            var insertIndex = -1
            while (true) {
                val last = list.pollLast()
                if (last == null) {
                    list.addAll(msg)
                    break
                }
                val index = msg.indexOfLast { last.d.msgId == it.d.msgId }
                if (index == -1) {
                    if (insertIndex == -1) {
                        msg.add(last)
                        insertIndex = msg.lastIndex
                    } else {
                        msg.add(insertIndex, last)
                    }
                } else {
                    msg[index] = last
                    //if (insertIndex == -1) {
                    //    insertIndex = 0
                    //}
                    //for (i in insertIndex until msg.size) {
                    //    msg[i].checkAndSetSameSender(msg.getOrNull(i-1))
                    //}
                    list.addAll(msg.subList(index, msg.size))
                    break
                }
            }
            msgList.update(list.toMutableList())
            super.n(list as MsgListUiState, uiHelper)
        }
    }

    @Mixin
    class Clear(p0: IAIOFactory) : ChatPie(p0) {
        override fun a(
            fragment: ChatFragment,
            inflater: LayoutInflater,
            container: ViewGroup,
            isPreload: Boolean
        ): View {
            msgList = Observable(ArrayList())
            return super.a(fragment, inflater, container, isPreload)
        }
    }

}