package momoi.mod.qqpro.hook.action

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
}
