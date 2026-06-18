package momoi.mod.qqpro.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.tencent.widget.Switch
import momoi.anno.mixin.Mixin
import momoi.mod.qqpro.Pref
import momoi.mod.qqpro.Settings
import momoi.mod.qqpro.hook.aio_cell.GroupAvatarHook
import momoi.mod.qqpro.hook.style.CARD_MARGIN_DP
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.background
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.height
import momoi.mod.qqpro.lib.margin
import momoi.mod.qqpro.lib.onCheckedChange
import momoi.mod.qqpro.lib.onClick
import momoi.mod.qqpro.lib.onProgressChanged
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.SwipeBackLayout
import momoi.mod.qqpro.lib.progressMax
import momoi.mod.qqpro.lib.size
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.util.ChatBackground
import momoi.mod.qqpro.util.SettingsBackup
import momoi.mod.qqpro.util.Utils
import momoi.mod.qqpro.watchdog.LogExporter
import momoi.mod.qqpro.watchdog.DebugActivity
import momoi.mod.qqpro.watchdog.WatchdogTestActivity
import moye.wearqq.SettingsActivity
import kotlin.math.roundToInt

private val ACCENT = 0xFF_4FC3F7.toInt()
private val TRACK_INACTIVE = 0xFF_3A3A3A.toInt()
private const val REQ_PICK_CHAT_BG = 0x9B01
private const val REQ_IMPORT_SETTINGS = 0x9B02

// Kept at file scope (not a @Mixin field — those can't have initializers) so
// onActivityResult can refresh the picker's status text after picking/clearing.
private var bgStatusLabel: TextView? = null

// Two-level navigation state, also at file scope to avoid @Mixin field initializers.
// settingsRoot is the scrollable content column; we clear+refill it to switch between the
// top-level category list and a single category's detail page. inSettingsDetail tells the
// swipe/back gestures whether to pop back to the list or finish the activity.
private var settingsRoot: LinearLayout? = null
private var settingsScroll: ScrollView? = null
private var inSettingsDetail = false

/**
 * One entry in the top-level list; [build] fills the detail page with that category's rows.
 * Must be public (not private): the @Mixin body merges into moye.wearqq.SettingsActivity, and a
 * package-private class here would throw IllegalAccessError on cross-package runtime access.
 */
class SettingsCategory(
    val title: String,
    val subtitle: String,
    val build: momoi.mod.qqpro.lib.LinearScope.() -> Unit,
)

@Mixin
class 设置页 : SettingsActivity() {
    @SuppressLint("ResourceType", "SetTextI18n", "UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF_121212.toInt())
        }
        val root = LinearLayout(this)
            .vertical()
            .padding(left = (2 * CARD_MARGIN_DP).dp, top = 10.dp, right = (2 * CARD_MARGIN_DP).dp, bottom = 10.dp)
        scroll.addView(root, FILL, WRAP)

        // Watches without a hardware back button rely on a left-to-right swipe to leave a
        // screen (the main activity gets this from QQ's fling framework). Wrap the settings
        // content so the same gesture finishes this plain Activity.
        val swipeBack = SwipeBackLayout(this).apply {
            // Opaque background so the strip revealed while the content slides right
            // doesn't flash the window background.
            setBackgroundColor(0xFF_121212.toInt())
            addView(scroll, FILL, FILL)
            // In a detail page the swipe pops back to the category list; on the list it leaves.
            onSwipeBack = { if (inSettingsDetail) showCategoryList() else finish() }
        }
        setContentView(swipeBack)

        settingsScroll = scroll
        settingsRoot = root
        showCategoryList()
    }

    /** Top-level page: app header followed by one tappable card per category. */
    private fun showCategoryList() {
        val root = settingsRoot ?: return
        inSettingsDetail = false
        root.removeAllViews()
        settingsScroll?.scrollTo(0, 0)
        root.content {
            section("NWear QQ Pro / Max", "by 爅峫 · java30433 · AILIFE")
            for (cat in buildCategories()) {
                actionCard(cat.title, cat.subtitle) { showCategory(cat) }
            }
            add<View>()
                .height(64.dp)
        }
    }

    /** Second-level page: a back header plus the rows for a single [cat]egory. */
    private fun showCategory(cat: SettingsCategory) {
        val root = settingsRoot ?: return
        inSettingsDetail = true
        root.removeAllViews()
        settingsScroll?.scrollTo(0, 0)
        root.content {
            backHeader(cat.title, cat.subtitle) { showCategoryList() }
            cat.build(this)
            add<View>()
                .height(64.dp)
        }
    }

    // Hardware/system back mirrors the swipe gesture: pop to the list before leaving.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inSettingsDetail) showCategoryList() else super.onBackPressed()
    }

    /** Tappable header at the top of a detail page; [onBack] returns to the category list. */
    private fun GroupScopeFix.backHeader(title: String, subtitle: String, onBack: () -> Unit) {
        val row = add<LinearLayout>()
            .width(FILL)
            .padding(left = 2.dp, top = 6.dp, right = 4.dp, bottom = 8.dp)
        row.gravity(Gravity.CENTER_VERTICAL)
        row.onClick { onBack() }
        row.content {
            add<TextView>()
                .text("‹")
                .textSize(26f)
                .textColor(ACCENT)
                .padding(left = 2.dp, right = 12.dp)
            add<LinearLayout>()
                .vertical()
                .content {
                    add<TextView>()
                        .text(title)
                        .textSize(17f)
                        .textColor(0xFF_FFFFFF)
                    if (subtitle.isNotEmpty()) {
                        add<TextView>()
                            .text(subtitle)
                            .textSize(10f)
                            .textColor(0xFF_888888)
                    }
                }
        }
    }

    /** The full settings tree, grouped into categories for the two-level navigation. */
    private fun buildCategories(): List<SettingsCategory> = listOf(
        SettingsCategory("聊天输入", "输入框、发送方式与表情") {
            switch("聊天页直接输入", "在聊天页用输入框替换键盘键，有文字时麦克风键变发送键", Settings.inlineChatInput)
            switch("完全行内输入", "彻底不打开输入法页面：@、图片、回复、编辑、语音转文字都在输入框内完成。@xxx 与 [图片] 整体删除，回复/编辑在输入框上方显示横幅可点击取消(需开启“聊天页直接输入”)", Settings.fullInlineInput)
            switch("行内发送按钮", "输入页将发送键移到输入框右侧，左侧加关闭键取消发送", Settings.inlineSendButton)
            switch("行内表情按钮", "聊天页输入有文字时左侧显示表情键，点击收起键盘弹出表情选择器插入表情，点输入框恢复键盘", Settings.inlineEmojiButton)
            switch("记住输入草稿", "分会话记住未发送的输入框内容(文字、@、图片、回复目标)，离开聊天再返回时自动恢复，发送后清除(需开启“完全行内输入”)", Settings.rememberDraft)
            switch("表情选择器插入输入框", "从系统表情选择器选择表情/图片/GIF 时不立即发送，而是作为 [表情]/[图片] 插入输入框，可继续编辑一起发送(需开启“完全行内输入”)", Settings.emojiPickerToInput)
            switch("附件浮层", "用输入框左侧 + 键打开附件浮层，移除附件翻页；表情移入附件列表(重进聊天页生效)", Settings.attachmentOverlay)
            switch("单行输入", "输入框固定为单行显示", Settings.singleLineInput)
            switch("输入键居中", "在聊天页面将输入键居中放置", Settings.swapCenterKeyboard)
            switch("隐藏语音按钮", "在聊天页隐藏语音(麦克风)按钮，所有输入模式下均生效", Settings.hideVoiceButton)
            switch("图片随消息发送", "发送文字时一并发送已选图片", Settings.sendWithImage)
            switch("回复带艾特", "回复消息时自动艾特对方", Settings.replyWithAt)
            slider("屏幕圆角直径", "在输入框左右各留出此宽度的空白，避免圆屏圆角裁切两侧按钮", Settings.screenCornerDiameter, min = 0f, max = 48f)
            textInput("语音键文字", "聊天页语音键上显示的文字", Settings.voiceBtnText)
        },
        SettingsCategory("聊天显示", "缩放、气泡、图片与背景") {
            slider("缩放倍数", "整体界面缩放，返回聊天页即时生效", Settings.scale)
            slider("聊天文本缩放", "聊天气泡内文字大小", Settings.chatScale)
            switch("双击朗读", "双击消息朗读文本", Settings.doubleSpeak)
            switch("双击回复", "双击消息进入回复", Settings.doubleReply)
            slider("图片最大高度", "聊天图片最大显示高度(占屏幕高度比例)，默认 0.5", Settings.picMaxHeightRatio, min = 0.3f, max = 1f)
            slider("气泡圆角半径", "聊天气泡、合并转发/聊天记录块与回复块的圆角半径(dp)", Settings.bubbleCornerRadius, min = 0f, max = 24f)
            textInput("我的气泡颜色", "16进制如 #2B6CF6，留空为默认", Settings.bubbleColorSelf)
            textInput("对方气泡颜色", "16进制如 #2B6CF6，留空为默认", Settings.bubbleColorOther)
            textInput("文字颜色", "聊天消息文字颜色，16进制如 #FFFFFF，留空为默认", Settings.textColor)
            slider("文字大小", "聊天消息文字大小倍率，默认 1.0", Settings.textSizeScale, min = 0.5f, max = 2.5f)
            chatBackgroundPicker()
            slider("背景变暗程度", "调暗背景图以便看清文字，重进聊天页生效", Settings.chatBgDarken, min = 0f, max = 0.9f)
        },
        SettingsCategory("群聊头像", "群聊中的头像与昵称") {
            switch("群聊显示头像", "在群聊消息中显示用户头像和两行昵称", Settings.showGroupAvatar)
            switch("自己也显示头像", "自己的消息也显示头像和两行昵称，与他人一致", Settings.showSelfAvatar)
            slider("头像大小", "群聊头像大小(相对昵称文字的倍数)，默认 3", Settings.avatarSizeScale, min = 1.5f, max = 6f)
            switch("合并连续消息头", "同一人连发多条时，只在第一条显示头像和昵称", Settings.hideRepeatedSender)
            switch("替换发送者名字", "用解析到的群名片/备注/昵称替换群消息发送者名字；关闭(默认)则保留原生名字。群主/管理员/头衔标签不受此开关影响，始终显示", Settings.replaceGroupNick)
            actionCard("清除头像缓存", "删除已缓存的头像，重进聊天页将重新下载最新头像") {
                val n = GroupAvatarHook.clearAvatarCache()
                Utils.toast(this@设置页, "已清除 $n 个头像缓存")
            }
        },
        SettingsCategory("标题栏与未读", "聊天顶部标题栏") {
            switch("富标题栏", "聊天顶部显示返回键、其它会话未读数、群成员数与群名(重进聊天页生效)", Settings.enableTitlebar)
            switch("标题栏显示未读数", "在富标题栏显示其它会话的未读数红标(重进聊天页生效)", Settings.titlebarShowUnread)
            switch("未读数浮在聊天左上角", "其它会话未读数红标浮在聊天页左上角而非标题栏内，无标题栏也可用(重进聊天页生效)", Settings.floatUnreadInChat)
            switch("标题栏名称滚动", "标题栏名称过长时滚动显示，而非省略号截断(重进聊天页生效)", Settings.titlebarMarquee)
            slider("标题栏高度", "富标题栏高度(dp)，默认 16", Settings.titlebarHeight, min = 16f, max = 32f)
        },
        SettingsCategory("导航与滚动", "翻页、返回与表冠") {
            switch("主页导航在底部", "主页(会话列表)翻页指示器移到底部，并随标题栏高度放大", Settings.bottomMainNav)
            switch("返回先回首页", "不在首页时按返回先滑回第一页，已在首页才退出", Settings.backToFirstPage)
            switch("回复跳转加载全部", "点击回复跳转到源消息时不限制翻页次数，一直向上加载直到找到或到达历史顶端(较旧的源消息也能定位，可能较慢)", Settings.replyFullSearch)
            switch("屏蔽返回键", "用于把右滑当作返回的手表（如米兔）", Settings.blockBack)
            switch("平滑表冠滚动", "表冠滚动没有动画时开启", Settings.enableSmoothScroll)
            slider("表冠滚动速度", "表冠滚动距离倍率，默认 1.0", Settings.encoderScrollSpeed, min = 0.3f, max = 4f)
        },
        SettingsCategory("通知与提醒", "消息通知、声音与震动") {
            switch("允许通知", "允许显示消息通知", Settings.allowNotification)
            switch("常驻通知", "保留常驻通知（更耗电）", Settings.residentNotification)
            selector("提醒声音", "新消息提示音：关闭 / 应用内音效 / 系统通知音", Settings.notifySoundMode,
                listOf("关闭", "应用内", "系统"))
            selector("提醒震动", "新消息震动：关闭 / 应用内模式 / 系统模式", Settings.notifyVibrateMode,
                listOf("关闭", "应用内", "系统"))
        },
        SettingsCategory("联系人", "联系人页面") {
            switch("联系人分组", "联系人页用「好友」「群聊」标题分组，通知拆成好友/群两项各带数量，去掉群行末尾图标", Settings.contactSections)
            switch("资料页姓名多行可复制", "好友/群资料设置页及成员资料卡的名称过长时换行显示，长按名称可复制；设置页内QQ号与昵称分两行、可分别长按复制(重进资料页生效)", Settings.profileNameMultiline)
        },
        SettingsCategory("相机与媒体", "拍照、相册与音频") {
            switch("使用应用内相机", "开启后拍照/录像都用应用内相机，关闭后改用系统/第三方相机", Settings.useInAppCamera)
            switch("相册按拍摄时间排序", "图片选择器按拍摄时间排序，关闭则按文件修改时间(默认)", Settings.gallerySortByDateTaken)
            switch("使用系统图片选择器", "相册改用系统图片选择器/文件选择器，无需相册权限，可多选，修复部分设备问题", Settings.useSystemImagePicker)
            switch("使用系统音频选择器", "音频文件改用系统文件选择器，关闭则用应用内音频浏览器(列出本机音频文件)", Settings.useSystemAudioPicker)
        },
        SettingsCategory("链接", "消息中的网址") {
            switch("点击链接确认", "点击消息中的链接时，弹窗询问是否用浏览器打开", Settings.confirmOpenLink)
            switch("识别无前缀链接", "同时识别不带 http(s):// 的网址，如 example.com/x", Settings.wideUrlMatch)
            switch("识别号码", "把消息中的 6-15 位数字(QQ号/群号)变为可点击，点击可搜索好友/群", Settings.parseNumber)
            switch("识别@成员", "群聊中把 @成员 及灰条提示中的成员名变为可点击，点击打开其资料卡(同点头像/昵称)", Settings.parseAtMember)
            switch("链接预览", "消息含链接时尝试解析网站图标、标题与简介，显示在消息下方", Settings.enableLinkPreview)
            textInput("链接颜色", "可点击链接/号码/@成员的文字颜色，16进制如 #4FC3F7，留空为默认", Settings.linkColor)
        },
        SettingsCategory("关于与更新", "版本更新") {
            switch("自动检查更新", "启动时检查 QQ Max 新版本，可在关于页手动检查", Settings.autoUpdateCheck)
        },
        SettingsCategory("调试", "诊断与日志") {
            switch("卡死监控", "监测主线程卡死并弹出报告，崩溃捕获始终开启。手表休眠可能误报，可关闭(重启应用生效)", Settings.watchdogEnabled)
            switch("启用日志", "记录调试日志到文件用于排查问题，发行版默认关闭", Settings.enableLog)
            actionCard("保存日志到下载", "将调试日志文件保存到下载文件夹") {
                val saved = Utils.saveLogToDownloads()
                Utils.toast(
                    this@设置页,
                    when {
                        saved == null -> "保存失败(无日志文件?)"
                        saved.inDownloads -> "已保存到下载文件夹:\n${saved.location}"
                        else -> "无法写入下载，已保存到:\n${saved.location}"
                    },
                    longDuration = true
                )
            }
            actionCard("导出设置", "将本页所有设置导出为文件保存到下载文件夹，可选 JSON 或 XML") {
                exportSettings()
            }
            actionCard("导入设置", "从文件恢复本页设置(自动识别 JSON / XML)，部分需重启应用生效") {
                importSettings()
            }
            actionCard("调试菜单", "查看设备信息与调试日志，可复制/分享/保存") {
                startActivity(Intent(this@设置页, DebugActivity::class.java))
            }
            actionCard("崩溃 / 卡死测试", "主动触发崩溃或卡死，验证报告功能") {
                startActivity(Intent(this@设置页, WatchdogTestActivity::class.java))
            }
        },
    )

    private fun GroupScopeFix.section(title: String, subtitle: String) {
        add<TextView>()
            .text(title)
            .textSize(15f)
            .textColor(ACCENT)
            .padding(left = 4.dp, top = 14.dp, right = 4.dp, bottom = 0.dp)
        add<TextView>()
            .text(subtitle)
            .textSize(10f)
            .textColor(0xFF_888888)
            .padding(left = 4.dp, top = 0.dp, right = 4.dp, bottom = 6.dp)
    }

    private fun GroupScopeFix.switch(
        title: String,
        desc: String,
        pref: Pref<Boolean>
    ) = card { card ->
        card.content {
            titleColumn(title, desc).weight(1f)
            // The base app's own switch — the nicer styled toggle used by the native NWear settings.
            val sw = Switch(this@设置页, null)
            sw.isChecked = pref.value
            sw.onCheckedChange { pref.value = it }
            add(sw)
        }
    }

    /** A tappable card row with a ›-style affordance; runs [onTap] when pressed. */
    private fun GroupScopeFix.actionCard(
        title: String,
        desc: String,
        onTap: () -> Unit
    ) = card { card ->
        card.content {
            titleColumn(title, desc).weight(1f)
            add<TextView>()
                .text("›")
                .textSize(18f)
                .textColor(ACCENT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        card.onClick { onTap() }
    }

    /**
     * Dropdown-style row: shows the current option (with a ▾ affordance) on the right; tapping opens
     * a hand-built dark option picker. Used for multi-state settings (e.g. 关闭 / 应用内 / 系统).
     * Built entirely with the view DSL — the app's bundled appcompat is far too old for a Material
     * dialog to look right.
     */
    private fun GroupScopeFix.selector(
        title: String,
        desc: String,
        pref: Pref<Int>,
        options: List<String>
    ) = card { card ->
        lateinit var valueLabel: TextView
        card.content {
            titleColumn(title, desc).weight(1f)
            valueLabel = add<TextView>()
                .text(selectorLabel(options, pref.value))
                .textSize(14f)
                .textColor(ACCENT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        card.onClick {
            showOptionPicker(title, options, pref.value) { which ->
                pref.value = which
                valueLabel.text = selectorLabel(options, which)
            }
        }
    }

    private fun selectorLabel(options: List<String>, index: Int) =
        options.getOrElse(index) { options.first() } + "  ▾"

    /** A hand-drawn dark single-choice popup styled like the rest of the settings cards. */
    private fun showOptionPicker(
        title: String,
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit,
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(this)
            .vertical()
            .padding(top = 16.dp, bottom = 8.dp)
        panel.background(GradientDrawable().apply {
            setColor(0xFF_1E1E1E.toInt())
            cornerRadius = 22.dp.toFloat()
        })

        panel.content {
            add<TextView>()
                .text(title)
                .textSize(15f)
                .textColor(0xFF_FFFFFF)
                .gravity(Gravity.CENTER)
                .padding(left = 16.dp, right = 16.dp, bottom = 12.dp)

            options.forEachIndexed { index, label ->
                val isSel = index == selected
                val row = add<LinearLayout>()
                    .width(FILL)
                    .padding(left = 16.dp, top = 13.dp, right = 16.dp, bottom = 13.dp)
                row.gravity(Gravity.CENTER_VERTICAL)
                row.margin(left = 8.dp, right = 8.dp, top = 2.dp, bottom = 2.dp)
                if (isSel) {
                    row.background(GradientDrawable().apply {
                        setColor(0x33_4FC3F7)
                        cornerRadius = 14.dp.toFloat()
                    })
                }
                row.content {
                    // Radio indicator: filled accent disc when selected, hollow grey ring otherwise.
                    add<View>()
                        .size(16.dp)
                        .background(GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            if (isSel) {
                                setColor(ACCENT)
                            } else {
                                setColor(0x00_000000)
                                setStroke(2.dp, 0xFF_5A5A5A.toInt())
                            }
                        })
                    add<TextView>()
                        .text(label)
                        .textSize(14f)
                        .textColor(if (isSel) ACCENT else 0xFF_FFFFFF.toInt())
                        .margin(left = 14.dp)
                }
                row.onClick {
                    onPick(index)
                    dialog.dismiss()
                }
            }
        }

        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun updateBgStatus() {
        bgStatusLabel?.text = if (ChatBackground.isSet()) "已设置背景图片" else "未设置（使用默认背景）"
    }

    private fun GroupScopeFix.chatBackgroundPicker() = card { card ->
        card.vertical()
        card.content {
            titleColumn("聊天背景图片", "选择一张图片作为聊天页背景").width(FILL)
            bgStatusLabel = add<TextView>()
                .textSize(11f)
                .textColor(0xFF_A1A1A1)
                .padding(top = 4.dp)
            updateBgStatus()
            add<LinearLayout>()
                .width(FILL)
                .padding(top = 8.dp)
                .content {
                    pillButton("选择图片", ACCENT) { pickChatBackground() }
                        .weight(1f)
                        .margin(right = 4.dp)
                    pillButton("清除", 0xFF_E57373.toInt()) {
                        ChatBackground.clear()
                        updateBgStatus()
                        Utils.toast(this@设置页, "已清除聊天背景")
                    }.weight(1f).margin(left = 4.dp)
                }
        }
    }

    private fun GroupScopeFix.pillButton(
        label: String,
        color: Int,
        onTap: () -> Unit
    ): TextView {
        val btn = add<TextView>()
            .text(label)
            .textSize(13f)
            .textColor(0xFF_FFFFFF)
            .gravity(Gravity.CENTER)
            .padding(top = 8.dp, bottom = 8.dp)
        btn.background(GradientDrawable().apply {
            setColor(color)
            cornerRadius = 18.dp.toFloat()
        })
        btn.onClick(onTap)
        return btn
    }

    private fun pickChatBackground() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择背景图片"), REQ_PICK_CHAT_BG)
        } catch (e: Exception) {
            Utils.log("pickChatBackground failed: ${e.javaClass.simpleName}: ${e.message}")
            Utils.toast(this, "无法打开图片选择器")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PICK_CHAT_BG -> {
                val uri = data?.data
                if (resultCode == Activity.RESULT_OK && uri != null && ChatBackground.save(this, uri)) {
                    Utils.toast(this, "已设置聊天背景")
                } else {
                    Utils.toast(this, "设置失败")
                }
                updateBgStatus()
            }
            REQ_IMPORT_SETTINGS -> {
                val uri = data?.data
                if (resultCode != Activity.RESULT_OK || uri == null) return
                runCatching {
                    contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }.onSuccess { text ->
                    runCatching { SettingsBackup.import(text) }
                        .onSuccess { n -> Utils.toast(this, "已导入 $n 项设置，部分需重启应用生效") }
                        .onFailure { Utils.toast(this, "导入失败: ${it.message}") }
                }.onFailure { Utils.toast(this, "读取文件失败: ${it.message}") }
            }
        }
    }

    /** Pick JSON or XML, build the backup, and save it to Downloads (with a share fallback). */
    private fun exportSettings() {
        showOptionPicker("导出设置格式", listOf("JSON", "XML"), 0) { which ->
            val json = which == 0
            val ext = if (json) "json" else "xml"
            runCatching {
                if (json) SettingsBackup.exportJson() else SettingsBackup.exportXml()
            }.onSuccess { content ->
                val saved = runCatching { LogExporter.save(this, "qqpro_settings", content, ext) }.getOrNull()
                if (saved != null) {
                    Utils.toast(this, "已保存:\n${saved.location}")
                } else {
                    Utils.toast(this, "保存失败")
                }
            }.onFailure { Utils.toast(this, "导出失败: ${it.message}") }
        }
    }

    /** Open the system file picker; the chosen file is applied in onActivityResult. */
    private fun importSettings() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择设置文件"), REQ_IMPORT_SETTINGS)
        } catch (e: Exception) {
            Utils.toast(this, "无法打开文件选择器")
        }
    }

    private fun GroupScopeFix.textInput(
        title: String,
        desc: String,
        pref: Pref<String>
    ) = card { card ->
        // Full-width input below the title/description — the right-aligned field is too
        // narrow to use on the watch screen.
        card.vertical()
        card.content {
            titleColumn(title, desc).width(FILL)
            add<EditText>()
                .text(pref.value)
                .textSize(13f)
                .textColor(0xFF_FFFFFF)
                .width(FILL)
                .doAfterTextChanged { pref.value = it?.toString() ?: "" }
        }
    }

    /** Value slider rendered full-width below the title row, for watch use. */
    private fun GroupScopeFix.slider(
        title: String,
        desc: String,
        pref: Pref<Float>,
        min: Float = 0.1f,
        max: Float = 1.2f
    ) = card { card ->
        card.vertical()
        lateinit var valueLabel: TextView
        card.content {
            add<LinearLayout>()
                .width(FILL)
                .content {
                    titleColumn(title, desc).weight(1f)
                    valueLabel = add<TextView>()
                        .text(format(pref.value))
                        .textSize(14f)
                        .textColor(ACCENT)
                        .gravity(Gravity.CENTER_VERTICAL)
                }
        }
        val steps = ((max - min) * 100).roundToInt()
        val seek = mdSeekBar()
            .progressMax(steps)
            .onProgressChanged { p, fromUser ->
                val v = min + p / 100f
                valueLabel.text = format(v)
                if (fromUser) pref.value = v
            }
        seek.progress = ((pref.value - min) * 100).roundToInt().coerceIn(0, steps)
        card.addView(seek, LinearLayout.LayoutParams(FILL, 36.dp).apply {
            topMargin = 4.dp
        })
    }

    /** Stock SeekBar dressed up MD3-style: thin rounded two-tone track + vertical pill thumb. */
    private fun mdSeekBar(): SeekBar {
        val trackH = 2.dp
        val inactive = GradientDrawable().apply {
            setColor(TRACK_INACTIVE)
            cornerRadius = trackH / 2f
            setSize(0, trackH)
        }
        val activeShape = GradientDrawable().apply {
            setColor(ACCENT)
            cornerRadius = trackH / 2f
            setSize(0, trackH)
        }
        val active = ClipDrawable(activeShape, Gravity.START, ClipDrawable.HORIZONTAL)
        val progress = LayerDrawable(arrayOf(inactive, active)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        // MD3 vertical pill thumb: taller than wide, fully rounded.
        val thumbW = 5.dp
        val thumbH = 22.dp
        val thumb = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ACCENT)
            cornerRadius = thumbW / 2f
            setSize(thumbW, thumbH)
        }
        return SeekBar(this@设置页).apply {
            progressDrawable = progress
            setThumb(thumb)
            thumbOffset = 0
            val v = thumbH / 2
            setPadding(10.dp, v, 10.dp, v)
        }
    }

    private fun format(v: Float) = String.format("%.2f", v)

    private fun GroupScopeFix.titleColumn(title: String, desc: String): LinearLayout {
        val column = add<LinearLayout>().vertical()
        column.content {
            add<TextView>()
                .text(title)
                .textSize(13f)
                .textColor(0xFF_FFFFFF)
            if (desc.isNotEmpty()) {
                add<TextView>()
                    .text(desc)
                    .textSize(10f)
                    .textColor(0xFF_A1A1A1)
            }
        }
        return column
    }

    /** A rounded dark card row with consistent margins. */
    private inline fun GroupScopeFix.card(block: (LinearLayout) -> Unit) {
        val card = add<LinearLayout>()
            .width(FILL)
            .padding(12.dp)
        card.background(GradientDrawable().apply {
            setColor(0xFF_242424.toInt())
            cornerRadius = 14.dp.toFloat()
        })
        card.margin(top = CARD_MARGIN_DP.dp, bottom = CARD_MARGIN_DP.dp)
        block(card)
    }
}

private typealias GroupScopeFix = momoi.mod.qqpro.lib.LinearScope
