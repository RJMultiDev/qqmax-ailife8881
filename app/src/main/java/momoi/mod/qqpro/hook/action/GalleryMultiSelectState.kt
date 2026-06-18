package momoi.mod.qqpro.hook.action

import com.tencent.qqnt.kernel.nativeinterface.MsgElement

/**
 * Cross-activity signal: the multi-select gallery (separate activity) sets this true after
 * sending images, so when the chat activity's WatchAIOFragment resumes it switches its
 * ViewPager back to the chat page (page 0).
 */
object GalleryMultiSelectState {
    @Volatile
    var goToChatOnResume = false

    /**
     * Set true after gallery picks attach image(s) to [moye.wearqq.IMEOperation.extraMsg].
     * When the chat's WatchAIOFragment resumes (after the gallery pops) it opens the input-bar
     * preview so the user can review and send/cancel — without pre-switching to the chat page.
     */
    @Volatile
    var pendingOpenIme = false

    /**
     * Images picked in the gallery, staged HERE (not in [moye.wearqq.IMEOperation.extraMsg])
     * because QQ's [com.tencent.watch.aio_impl.coreImpl.vb.WatchAIOListVB.n] reassigns
     * `IMEOperation.extraMsg = new ArrayList()` on every msg-list UI-state render — which fires
     * when the chat resumes after the gallery pops, wiping anything we added before the pop. We
     * keep our own list QQ never touches and copy it into `extraMsg` at the last moment, right
     * before [moye.wearqq.IMEOperation.openIME], so that wipe can't race away the picked images.
     * Read-and-cleared by [consumePendingImages].
     */
    @Volatile
    var pendingImages: List<MsgElement> = emptyList()

    /** Read-and-clear [pendingImages]. */
    fun consumePendingImages(): List<MsgElement> {
        val v = pendingImages
        pendingImages = emptyList()
        return v
    }

    /**
     * Set true immediately before the chat "+" panel opens QQ's native gallery (相册). QQ's
     * [com.tencent.qqnt.watch.gallery.GalleryFragment] is shared by several flows (chat send,
     * avatar/profile-picture change, etc.); each non-chat flow registers its own
     * `setFragmentResult` listener and works natively. Our multi-select tap-interception must run
     * ONLY for the chat flow — whose listener lives on the [MenuFrame] that the attachment overlay
     * tears down, so the native result never arrives. Consumed (read-and-cleared) by
     * [GalleryMultiSelect] at gallery creation: when false, the gallery keeps its native behavior
     * so avatar/status pickers still return their selection. See [consumeChatLaunch].
     */
    @Volatile
    var chatLaunch = false

    /** Read-and-clear [chatLaunch]. Returns whether the gallery being created was opened from chat. */
    fun consumeChatLaunch(): Boolean {
        val v = chatLaunch
        chatLaunch = false
        return v
    }
}
