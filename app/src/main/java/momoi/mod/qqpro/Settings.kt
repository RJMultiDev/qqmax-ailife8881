package momoi.mod.qqpro

import android.content.SharedPreferences
import androidx.core.content.edit
import momoi.mod.qqpro.util.Utils

object Settings {
    val sp: SharedPreferences = Utils.application.getSharedPreferences("qqpro", 0)
    val wear: SharedPreferences = Utils.application.getSharedPreferences("wearqq", 0)
    // OTAManager2 stores its own enable flag here; share it so the settings toggle and the
    // update dialog's "不再提醒" button reflect the same state.
    val ota: SharedPreferences = Utils.application.getSharedPreferences("OTAManager2Prefs", 0)

    // ===== QQ Pro 设置 (by java30433) =====
    val scale = FloatPref("scale", 0.7f)
    val chatScale = FloatPref("chatScale", 0.8f)
    val enableSmoothScroll = BooleanPref("enableSmoothScroll", true)
    // Multiplier applied to the rotary-encoder scroll distance (1.0 = system default).
    val encoderScrollSpeed = FloatPref("encoderScrollSpeed", 1.0f)
    val blockBack = BooleanPref("blockBack", false)
    val swapCenterKeyboard = BooleanPref("swapCenterKeyboard", true)

    // ===== QQ Max 设置 (by AILIFE) =====
    val showGroupAvatar = BooleanPref("showGroupAvatar", true)
    // Also show avatar + two-line nick header for your own messages, like others.
    val showSelfAvatar = BooleanPref("showSelfAvatar", false)
    // Group chat avatar size, as a multiple of the nickname text size. Default 3x.
    val avatarSizeScale = FloatPref("avatarSizeScale", 2.5f)
    val hideRepeatedSender = BooleanPref("hideRepeatedSender", true)
    // Replace the group message sender NAME with our resolved 群名片/备注/昵称. Off by default:
    // keep the native sender name (the role/头衔 tag is appended either way — it's independent).
    val replaceGroupNick = BooleanPref("replaceGroupNick", false)
    val inlineSendButton = BooleanPref("inlineSendButton", true)
    val inlineChatInput = BooleanPref("inlineChatInput", true)
    // Fully replace the InputMethodFragment with the inline EditText: @/图片/回复/编辑/STT
    // are all represented inline (atomic @xxx and [图片] spans, a reply/edit banner above the
    // input box) so the keyboard page never opens. Requires inlineChatInput.
    val fullInlineInput = BooleanPref("fullInlineInput", true)
    // Show an emoji button in the inline input pill while typing. Tapping it collapses the keyboard
    // and opens a sysface picker at the keyboard position that inserts faces into the EditText.
    val inlineEmojiButton = BooleanPref("inlineEmojiButton", true)
    // Remember the inline input box contents (typed text, @/image tokens and the reply target)
    // per chat. Leaving a chat with an unsent draft and coming back restores it. Requires
    // fullInlineInput. Drafts are kept in memory for the app session, cleared once the message is sent.
    val rememberDraft = BooleanPref("rememberDraft", true)
    // When picking an emoji / sticker / image-gif from QQ's native emoji selector, insert it into
    // the inline EditText as a token instead of sending it immediately. Lets you keep composing
    // (mix text + faces + images) and send once. Only applies while fullInlineInput is active.
    val emojiPickerToInput = BooleanPref("emojiPickerToInput", true)
    // Screen rounded-corner diameter (in dp). Adds left/right margin of this
    // width to the inline chat EditText so the side buttons aren't clipped by a
    // round watch screen's corners.
    val screenCornerDiameter = FloatPref("screenCornerDiameter", 15f)
    // Hide the voice (microphone) button in the chat input bar entirely, in all input
    // modes (inline and non-inline) and regardless of whether text has been typed.
    val hideVoiceButton = BooleanPref("hideVoiceButton", false)
    val backToFirstPage = BooleanPref("backToFirstPage", true)
    // When tapping a reply to jump to its source message, drop the page-load cap (normally ~1000
    // pages) and keep paging up until the source is found or the top of history is reached. Lets
    // very old reply sources be located, at the cost of a possibly long load.
    val replyFullSearch = BooleanPref("replyFullSearch", false)
    // Replace the input bar's emoji button with a "+" button that opens the attachment
    // list as an overlay over the chat (like the long-press menu). Removes the attachment
    // ViewPager page (友: 聊天+设置 两页; 非好友: 仅聊天), and moves 表情 into the overlay list.
    val attachmentOverlay = BooleanPref("attachmentOverlay", true)
    // Rich chat titlebar: replaces the top page-indicator strip with a bar holding a back
    // button, the indicator dots, other-chats unread count, group member count and the
    // group/contact name. titlebarHeight (dp) defaults to the current strip height (16).
    val enableTitlebar = BooleanPref("enableTitlebar", true)
    // Show the other-chats unread count badge in the chat titlebar. When off, the
    // titlebar shows only the name + member count (no red badge).
    val titlebarShowUnread = BooleanPref("titlebarShowUnread", true)
    // Show the other-chats unread badge floating over the chat's top-left corner instead of in
    // the titlebar header. Works even when the titlebar is off; when on, the header badge is hidden.
    val floatUnreadInChat = BooleanPref("floatUnreadInChat", false)
    val titlebarHeight = FloatPref("titlebarHeight", 16f)
    // When the titlebar name is too long, scroll it as marquee instead of truncating with "…".
    val titlebarMarquee = BooleanPref("titlebarMarquee", false)
    // Move the home/main page's page-indicator dots to the bottom and scale their size by
    // titlebarHeight (relative to the default 16dp).
    val bottomMainNav = BooleanPref("bottomMainNav", true)
    // Use the in-app camera for 拍照. When off, launch the system camera app (third-party)
    // via an intent for photos. Video recording always uses the system app (the in-app
    // camera can't record video).
    val useInAppCamera = BooleanPref("useInAppCamera", true)
    // Sort the image/gallery picker by date taken (EXIF capture time) instead of
    // the default date_modified. Falls back to date_modified when a file has no
    // capture time recorded.
    val gallerySortByDateTaken = BooleanPref("gallerySortByDateTaken", false)
    // Use the system image picker (Android photo picker if available, otherwise the
    // SAF document picker) for 相册 instead of QQ's in-app gallery. Avoids needing
    // storage permission and works around in-app picker problems on some devices.
    // Supports selecting multiple images at once.
    val useSystemImagePicker = BooleanPref("useSystemImagePicker", false)
    // Use the system file picker (ACTION_GET_CONTENT, audio/*) for the 音频文件 panel
    // item instead of QQPro's in-app audio browser. Off (default) shows the in-app
    // browser that lists local audio files via MediaStore; on uses the system picker.
    val useSystemAudioPicker = BooleanPref("useSystemAudioPicker", false)
    // Ask before opening a tapped link in the browser.
    val confirmOpenLink = BooleanPref("confirmOpenLink", true)
    // Also detect links without an http(s):// prefix (e.g. "example.com/x").
    val wideUrlMatch = BooleanPref("wideUrlMatch", true)
    // Make bare 6–15 digit numbers (QQ/group numbers) tappable to search a
    // friend/group. Independent of the URL-matching settings.
    val parseNumber = BooleanPref("parseNumber", true)
    // In group chats, make @member mentions (and member names highlighted in grey
    // system tips) tappable — opens the member's profile card, same as tapping their
    // avatar/name.
    val parseAtMember = BooleanPref("parseAtMember", true)
    // Try to resolve a client-side preview (icon/title/description) for links in
    // messages and show it below the text. Makes a network request per unique link.
    val enableLinkPreview = BooleanPref("enableLinkPreview", true)
    // Max display height for chat images, as a fraction of the screen height. Caps tall
    // images so they don't fill the watch screen. Default 0.5 (half the screen).
    val picMaxHeightRatio = FloatPref("picMaxHeightRatio", 0.5f)
    // Rounded-corner radius (in dp) for chat bubbles, the merged-forward/chat-history
    // blocks and the reply block. 0 = square.
    val bubbleCornerRadius = FloatPref("bubbleCornerRadius", 10f)
    // Override chat-bubble fill color, as a hex string (#RRGGBB or #AARRGGBB / with or
    // without the leading #). Blank keeps the original bubble color (sampled per side).
    val bubbleColorSelf = StringPref("bubbleColorSelf", "")
    val bubbleColorOther = StringPref("bubbleColorOther", "")
    // Contacts page (2nd main page): show "好友"/"群聊" section headers, split the single
    // "我的通知" entry into separate friend/group notification entries (each with its own
    // count and direct navigation), and drop the trailing group icon on every group row.
    val contactSections = BooleanPref("contactSections", true)
    // Chat-settings panel (好友/群资料页 header name): show the contact/group name on multiple
    // lines instead of truncating to one, and allow long-pressing it to copy. Replaces the
    // single-line nick view with a multiline TextView that mirrors the original's (async) text.
    // Opt-in escape hatch since it swaps a custom widget; takes effect next time the panel opens.
    val profileNameMultiline = BooleanPref("profileNameMultiline", true)
    // How much to darken the chat background image for readability.
    // 0 = original image, 0.9 = almost black. Applied as a black overlay on top
    // of the picked image. Takes effect the next time a chat is opened.
    val chatBgDarken = FloatPref("chatBgDarken", 0.35f)
    // Automatically check for a new QQ Max release on launch (via OTAManager2). Backed by
    // OTAManager2's own prefs so the toggle and the dialog's "不再提醒" share one state.
    val autoUpdateCheck = OtaBooleanPref("update_check_enabled", true)

    // ===== 调试 =====
    // Enable the main-thread hang watchdog (HangWatcher). When on, a stalled main thread for
    // 8s+ shows a "应用卡死" report. Crash capture is always on; this gates only hang detection,
    // which can false-positive on a watch that suspends/dozes. Read once at install time.
    val watchdogEnabled = BooleanPref("watchdogEnabled", true)

    // ===== NWear QQ 设置 (by 爅峫) — backed by the base app's "wearqq" prefs =====
    val singleLineInput = WearBooleanPref("single_line_input", false)
    val sendWithImage = WearBooleanPref("send_with_image", true)
    val replyWithAt = WearBooleanPref("reply_with_at", true)
    val doubleSpeak = WearBooleanPref("double_speak", false)
    val doubleReply = WearBooleanPref("double_reply", true)
    val allowNotification = WearBooleanPref("allow_notification", true)
    val residentNotification = WearBooleanPref("resident_notification", false)
    // New-message alert, chosen independently for sound and vibration. Each mode is
    // 0=关闭 (off), 1=应用内 (QQ's own message tone / the app's vibration pattern), 2=系统 (the
    // system default notification ringtone / vibration pattern). These drive QQPro's own
    // notification path (NotificationReply); they replace the old single 震动提醒 toggle.
    val notifySoundMode = IntPref("notify_sound_mode", 2)
    val notifyVibrateMode = IntPref("notify_vibrate_mode", 2)
    val voiceBtnText = WearStringPref("voice_btn_text", "QQ")

    val text get() = wear.getString("voice_btn_text", "")?.let {
        if (it == "QQ") {
            ""
        } else {
            it
        }
    } ?: ""

    // Every setting exposed on the settings page, in display order. Used by SettingsBackup to
    // export/import only these custom settings (not unrelated keys like drafts or the chat-bg path).
    // Declared last so all the Pref properties above are already initialised.
    val all: List<Pref<*>> = listOf(
        scale, chatScale, enableSmoothScroll, encoderScrollSpeed, blockBack, swapCenterKeyboard,
        showGroupAvatar, showSelfAvatar, avatarSizeScale, hideRepeatedSender, replaceGroupNick, inlineSendButton,
        inlineChatInput, fullInlineInput, inlineEmojiButton, rememberDraft, emojiPickerToInput,
        screenCornerDiameter,
        hideVoiceButton, backToFirstPage, attachmentOverlay, enableTitlebar, titlebarShowUnread,
        floatUnreadInChat, titlebarHeight, bottomMainNav, replyFullSearch, useInAppCamera, gallerySortByDateTaken,
        useSystemImagePicker, useSystemAudioPicker, confirmOpenLink, wideUrlMatch, parseNumber, parseAtMember, enableLinkPreview,
        picMaxHeightRatio, bubbleCornerRadius, bubbleColorSelf, bubbleColorOther, contactSections,
        chatBgDarken, autoUpdateCheck, watchdogEnabled, singleLineInput, sendWithImage, replyWithAt,
        doubleSpeak, doubleReply, allowNotification, residentNotification, notifySoundMode,
        notifyVibrateMode, voiceBtnText,
    )
}

abstract class Pref<T>(val key: String, def: T) {
    var value: T = def
        set(value) {
            field = value
            set(value)
        }

    protected abstract fun set(value: T)

    /** Apply a value parsed from a backup string (settings import). Invalid input is ignored. */
    abstract fun importString(raw: String)
}

class FloatPref(key: String, def: Float) :
    Pref<Float>(key, Settings.sp.getFloat(key, def)) {
    override fun set(value: Float) = Settings.sp.edit {
        putFloat(key, value)
    }
    override fun importString(raw: String) { raw.trim().toFloatOrNull()?.let { value = it } }
}

class StringPref(key: String, def: String) :
    Pref<String>(key, Settings.sp.getString(key, def) ?: def) {
    override fun set(value: String) = Settings.sp.edit {
        putString(key, value)
    }
    override fun importString(raw: String) { value = raw }
}

class BooleanPref(key: String, def: Boolean) :
    Pref<Boolean>(key, Settings.sp.getBoolean(key, def)) {
    override fun set(value: Boolean) = Settings.sp.edit {
        putBoolean(key, value)
    }
    override fun importString(raw: String) { value = parseBool(raw) }
}

class IntPref(key: String, def: Int) :
    Pref<Int>(key, Settings.sp.getInt(key, def)) {
    override fun set(value: Int) = Settings.sp.edit {
        putInt(key, value)
    }
    override fun importString(raw: String) { raw.trim().toIntOrNull()?.let { value = it } }
}

/** Lenient boolean parse shared by the boolean prefs: accepts true/false and 1/0. */
internal fun parseBool(raw: String): Boolean {
    val t = raw.trim()
    return t.equals("true", ignoreCase = true) || t == "1"
}

/**
 * Boolean setting stored in the base app's "wearqq" SharedPreferences so the
 * original NWear-QQ code keeps reading it. Seeds [def] on first run when the key
 * is absent, so the requested default actually takes effect (the base app reads
 * the key with its own hard-coded default otherwise).
 */
class WearBooleanPref(key: String, def: Boolean) :
    Pref<Boolean>(key, seed(key, def)) {
    override fun set(value: Boolean) = Settings.wear.edit {
        putBoolean(key, value)
    }
    override fun importString(raw: String) { value = parseBool(raw) }

    companion object {
        private fun seed(key: String, def: Boolean): Boolean {
            if (!Settings.wear.contains(key)) {
                Settings.wear.edit { putBoolean(key, def) }
            }
            return Settings.wear.getBoolean(key, def)
        }
    }
}

/** Boolean setting stored in OTAManager2's SharedPreferences (shared with the update library). */
class OtaBooleanPref(key: String, def: Boolean) :
    Pref<Boolean>(key, Settings.ota.getBoolean(key, def)) {
    override fun set(value: Boolean) = Settings.ota.edit {
        putBoolean(key, value)
    }
    override fun importString(raw: String) { value = parseBool(raw) }
}

class WearStringPref(key: String, def: String) :
    Pref<String>(key, Settings.wear.getString(key, def) ?: def) {
    override fun set(value: String) = Settings.wear.edit {
        putString(key, value)
    }
    override fun importString(raw: String) { value = raw }
}
