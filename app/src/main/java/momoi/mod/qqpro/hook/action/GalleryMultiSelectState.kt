package momoi.mod.qqpro.hook.action

/**
 * Cross-activity signal: the multi-select gallery (separate activity) sets this true after
 * sending images, so when the chat activity's WatchAIOFragment resumes it switches its
 * ViewPager back to the chat page (page 0).
 */
object GalleryMultiSelectState {
    @Volatile
    var goToChatOnResume = false
}
