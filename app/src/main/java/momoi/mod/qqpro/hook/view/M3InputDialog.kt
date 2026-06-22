package momoi.mod.qqpro.hook.view

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.WRAP
import momoi.mod.qqpro.lib.content
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.lib.gravity
import momoi.mod.qqpro.lib.padding
import momoi.mod.qqpro.lib.text
import momoi.mod.qqpro.lib.textColor
import momoi.mod.qqpro.lib.textSize
import momoi.mod.qqpro.lib.vertical
import momoi.mod.qqpro.lib.width
import momoi.mod.qqpro.lib.material.M3
import momoi.mod.qqpro.lib.material.M3Button

/**
 * A Material 3 single/short-multi-line text input dialog for the round watch screen. Replaces QQ's
 * native full-screen keyboard page ([com.tencent.watch.ime.util.StartImeUtil]) for the small
 * "change info" edits — group name / friend remark / own nickname — when the M3 settings redesign is
 * on. Full-screen (a native AlertDialog renders broken at the watch DPI), with a title, a themed
 * field and 确定/取消 actions. [onConfirm] receives the trimmed text; [onConfirm] is the place the
 * caller's backend write happens (the native callback).
 */
class M3InputDialog(
    private val title: String,
    private val initial: String = "",
    private val placeholder: String = "",
    private val confirmLabel: String = "确定",
    private val multiline: Boolean = false,
    private val onConfirm: (String) -> Unit,
) : MyDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = inflater.context
        val root = LinearLayout(ctx).vertical().padding(20.dp)
        root.gravity = Gravity.CENTER

        lateinit var edit: EditText
        root.content {
            add<TextView>()
                .text(title)
                .textSize(16f)
                .textColor(M3.onSurface)
                .gravity(Gravity.CENTER)
                .width(FILL)
                .padding(bottom = 14.dp)

            edit = field(ctx).also { add(it) }

            val confirm = M3Button(ctx).variant(M3Button.Variant.FILLED).apply {
                text = confirmLabel
                layoutParams = LinearLayout.LayoutParams(FILL, WRAP).apply { topMargin = 12.dp }
                setOnClickListener {
                    val value = edit.text?.toString()?.trim().orEmpty()
                    hideKeyboard(edit)
                    dismiss()
                    runCatching { onConfirm(value) }
                }
            }
            add(confirm)

            val cancel = M3Button(ctx).variant(M3Button.Variant.TONAL).apply {
                text = "取消"
                layoutParams = LinearLayout.LayoutParams(FILL, WRAP).apply { topMargin = 8.dp }
                setOnClickListener { hideKeyboard(edit); dismiss() }
            }
            add(cancel)
        }

        // Auto-focus and raise the keyboard so the user can type straight away.
        edit.post {
            edit.requestFocus()
            edit.setSelection(edit.text?.length ?: 0)
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }

        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(M3.surface)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root, ViewGroup.LayoutParams(FILL, WRAP))
        }
        return swipeBackWrap(scroll)
    }

    private fun field(ctx: Context): EditText = EditText(ctx).apply {
        setText(initial)
        hint = placeholder
        setTextColor(M3.onSurface)
        setHintTextColor(M3.hint)
        textSize = 15f
        background = M3.outlined(M3.outline, M3.radiusMd).apply { setColor(M3.surfaceContainerHigh) }
        setPadding(14.dp, 12.dp, 14.dp, 12.dp)
        inputType = if (multiline) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        if (multiline) { isSingleLine = false; maxLines = 4 } else isSingleLine = true
        layoutParams = LinearLayout.LayoutParams(FILL, WRAP)
    }

    private fun hideKeyboard(view: View) {
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
