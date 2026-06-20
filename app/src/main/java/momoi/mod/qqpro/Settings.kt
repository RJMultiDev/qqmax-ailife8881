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
    val disableSwipeBack = BooleanPref("disableSwipeBack", false)
    val swapCenterKeyboard = BooleanPref("swapCenterKeyboard", true)

    // ===== QQ Max 设置 (by AILIFE) =====
    // Material 主题色 (the M3 accent/primary), as a hex string (#RRGGBB or RRGGBB). Blank = the
    // built-in default (#4FC3F7). Read live by M3.primary, which derives the tonal/onPrimary tokens
    // from it, so a change rethemes every materialized non-chat screen the next time it's built.
    val themeColor = StringPref("themeColor", "")
    val showGroupAvatar = BooleanPref("showGroupAvatar", true)
    // Also show avatar + two-line nick header for your own messages, like others.
    val showSelfAvatar = BooleanPref("showSelfAvatar", false)
    // Group chat avatar size, as a multiple of the nickname text size. Default 3x.
    val avatarSizeScale = FloatPref("avatarSizeScale", 2.5f)
    val hideRepeatedSender = BooleanPref("hideRepeatedSender", true)
    // Replace the group message sender NAME with our resolved 群名片/备注/昵称. Off by default:
    // keep the native sender name (the role/头衔 tag is appended either way — it's independent).
    val replaceGroupNick = BooleanPref("replaceGroupNick", false)
    // Show the member's group LV<n> level badge in the nick tag. No UI toggle; off by default
    // because it needs a per-member detail query (getMemberInfoForMqq) that the bulk member list
    // doesn't carry. Enable manually to render levels.
    val showMemberLevel = BooleanPref("showMemberLevel", false)
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
    // Rich titlebar side margin (in dp). Adds left/right inset to the titlebar
    // name/count row and the floating unread badge so they aren't clipped by a
    // round watch screen's corners. Separate from the chat EditText spacing above.
    val titlebarSideMargin = FloatPref("titlebarSideMargin", 15f)
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
    // Master switch for the custom main-page navigation. On = replace the native page-indicator
    // strip with our rebuilt nav (all the options below apply). Off = leave the native nav as-is.
    val mainNavCustom = BooleanPref("mainNavCustom", true)
    // Move the home/main page's page-indicator navigation to the bottom of the screen.
    val bottomMainNav = BooleanPref("bottomMainNav", true)
    // Main-page (home) navigation height in dp. Controls the icon/bar size of the page-indicator
    // navigation independently of 标题栏高度. Default 16 (matches the original strip height).
    val mainNavHeight = FloatPref("mainNavHeight", 16f)
    // Square/spread mode: distribute the navigation icons evenly across the full width instead of
    // grouping them close together in the center. Default off.
    val mainNavSquare = BooleanPref("mainNavSquare", false)
    // Show an icon for EVERY page (not just the current one), with the currently selected page
    // tinted blue and the others a muted white. Off = native style (only the current page's icon,
    // dots for the rest). Default on.
    val mainNavAllIcons = BooleanPref("mainNavAllIcons", true)
    // Show each page's unread count as a red badge in the navigation (except the last/settings page).
    // Messages page shows total unread; contacts page shows friend+group notification counts.
    val mainNavUnread = BooleanPref("mainNavUnread", true)
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
    // Override the chat message text color, as a hex string (#RRGGBB or #AARRGGBB / with or
    // without the leading #). Blank keeps the original (native) text color.
    val textColor = StringPref("textColor", "")
    // Override the color of tappable links/numbers/@mentions in chat text. Blank keeps the
    // platform default link color.
    val linkColor = StringPref("linkColor", "")
    // Multiplier applied to chat message text size (1.0 = original). Applies to all message text
    // (plain, text+image and special cells), independent of the overall 缩放/聊天文本缩放 settings.
    val textSizeScale = FloatPref("textSizeScale", 1.0f)
    // Contacts page (2nd main page): show "好友"/"群聊" section headers, split the single
    // "我的通知" entry into separate friend/group notification entries (each with its own
    // count and direct navigation), and drop the trailing group icon on every group row.
    val contactSections = BooleanPref("contactSections", true)
    // Apply the Material redesign to the contacts page (top bar with search/add/notify buttons,
    // M3-styled section headers and list rows). Requires contactSections. Default on.
    val materialContactsList = BooleanPref("materialContactsList", true)
    // Apply the Material top bar to the QZone feed page (动态, 3rd tab): replaces the three
    // header rows (发布/通知/我的空间) with compact icon buttons above the feed. Default on.
    val materialQZoneBar = BooleanPref("materialQZoneBar", true)
    // QZone top bar button layout: spread evenly across the full bar width (true), or group
    // the three buttons close together in the center (false, better for round screens).
    val qzoneBarSpread = BooleanPref("qzoneBarSpread", false)
    // QZone single-video posts: play inline in the feed cell (tap to start/pause) instead of
    // opening the fullscreen viewer. Default off.
    val qzoneInlineVideo = BooleanPref("qzoneInlineVideo", false)
    // QZone mini-app (小程序) shares: instead of the "请在手机QQ查看" placeholder, fetch the share
    // landing page and render the real app name/icon/description as a card. Default on.
    val qzoneMiniAppCard = BooleanPref("qzoneMiniAppCard", true)
    // Chat-settings panel (好友/群资料页 header name): show the contact/group name on multiple
    // lines instead of truncating to one, and allow long-pressing it to copy. Replaces the
    // single-line nick view with a multiline TextView that mirrors the original's (async) text.
    // Opt-in escape hatch since it swaps a custom widget; takes effect next time the panel opens.
    val profileNameMultiline = BooleanPref("profileNameMultiline", true)
    // Fully replace QQ's simple profile-card page with a rebuilt Material-style page that also shows
    // the contact's age / birthday / zodiac / location / signature (fetched from the kernel). When
    // off, the original page is kept (with only the minor enrich tweaks). Takes effect next open.
    val useRichProfile = BooleanPref("useRichProfile", true)
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
    // Persist Utils.log output to the on-device log file. Always on in debug builds; in release
    // builds logging is off unless this is enabled (default off). Read live by Utils.log.
    val enableLog = BooleanPref("enableLog", false)

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
        scale, chatScale, enableSmoothScroll, encoderScrollSpeed, blockBack, disableSwipeBack, swapCenterKeyboard,
        themeColor, showGroupAvatar, showSelfAvatar, avatarSizeScale, hideRepeatedSender, replaceGroupNick, showMemberLevel, inlineSendButton,
        inlineChatInput, fullInlineInput, inlineEmojiButton, rememberDraft, emojiPickerToInput,
        screenCornerDiameter, titlebarSideMargin,
        hideVoiceButton, backToFirstPage, attachmentOverlay, enableTitlebar, titlebarShowUnread,
        floatUnreadInChat, titlebarHeight, mainNavCustom, bottomMainNav, mainNavHeight, mainNavSquare, mainNavAllIcons, mainNavUnread,
        replyFullSearch, useInAppCamera, gallerySortByDateTaken,
        useSystemImagePicker, useSystemAudioPicker, confirmOpenLink, wideUrlMatch, parseNumber, parseAtMember, enableLinkPreview,
        picMaxHeightRatio, bubbleCornerRadius, bubbleColorSelf, bubbleColorOther, textColor, linkColor, textSizeScale, contactSections, materialContactsList, materialQZoneBar, qzoneBarSpread, qzoneInlineVideo, qzoneMiniAppCard,
        chatBgDarken, autoUpdateCheck, watchdogEnabled, singleLineInput, sendWithImage, replyWithAt,
        doubleSpeak, doubleReply, allowNotification, residentNotification, notifySoundMode,
        notifyVibrateMode, voiceBtnText, watchdogEnabled, enableLog,
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
