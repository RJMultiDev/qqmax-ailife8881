package momoi.mod.qqpro.lib

import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

// EditText that advertises image/* support to the IME (Gboard GIF/sticker picker).
// Plain EditText never calls ViewCompat.onCreateInputConnection, so MIME types set via
// ViewCompat.setOnReceiveContentListener are never forwarded to EditorInfo. This subclass
// overrides onCreateInputConnection directly.
@Suppress("DEPRECATION")
class ImeEditText(context: Context) : android.widget.EditText(context) {

    var onImageUri: ((Uri) -> Unit)? = null

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(editorInfo) ?: return null
        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))
        return InputConnectionCompat.createWrapper(ic, editorInfo) { contentInfo, flags, _ ->
            if (Build.VERSION.SDK_INT >= 25 &&
                (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
            ) {
                runCatching { contentInfo.requestPermission() }
            }
            onImageUri?.invoke(contentInfo.contentUri)
            true
        }
    }
}
