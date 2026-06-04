package momoi.mod.qqpro.hook

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import com.tencent.qqnt.kernel.nativeinterface.VideoElement
import com.tencent.qqnt.msg.KernelServiceUtil
import com.tencent.watch.aio_impl.ext.FileUtils
import momoi.mod.qqpro.MsgUtil
import momoi.mod.qqpro.hook.action.CurrentContact
import momoi.mod.qqpro.hook.action.GalleryMultiSelectState
import momoi.mod.qqpro.util.Utils
import java.io.File
import java.io.FileOutputStream

/**
 * Capture a photo/video with the SYSTEM camera app (via intent) and send it to the current chat.
 * Used when the "use in-app camera" setting is off. When that setting is on, photos use QQ's
 * in-app CameraFragment and video uses our own VideoRecordFragment ([sendInAppVideo]).
 *
 * These are plain top-level functions (NOT inside a @Mixin body) so the background threads /
 * callbacks they create live in this package and won't hit the @Mixin anonymous-class issue.
 */

const val REQ_SYS_PHOTO = 0x7301
const val REQ_SYS_VIDEO = 0x7302

object CameraCapture {
    var pendingPhotoPath: String? = null
    var pendingVideoPath: String? = null
}

private fun outputFileAndUri(fragment: Fragment, subDir: String, ext: String): Pair<String, Uri> {
    val ctx = fragment.requireContext()
    val dir = ctx.getExternalFilesDir(subDir) ?: ctx.filesDir
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "qqpro_${System.currentTimeMillis()}.$ext")
    if (file.exists()) file.delete()
    file.createNewFile()
    val authority = ctx.applicationContext.packageName + ".fileprovider"
    return file.path to FileProvider.getUriForFile(ctx, authority, file)
}

fun launchSystemPhoto(fragment: Fragment) {
    runCatching {
        val (path, uri) = outputFileAndUri(fragment, "photos", "jpg")
        CameraCapture.pendingPhotoPath = path
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        fragment.startActivityForResult(intent, REQ_SYS_PHOTO)
        Utils.log("camera: launch system photo -> $path")
    }.onFailure {
        Utils.log("camera: launch system photo failed: $it")
        runCatching { Utils.toast(fragment.requireContext(), "未找到可拍照的相机应用") }
    }
}

fun launchSystemVideo(fragment: Fragment) {
    runCatching {
        val (path, uri) = outputFileAndUri(fragment, "videos", "mp4")
        CameraCapture.pendingVideoPath = path
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        fragment.startActivityForResult(intent, REQ_SYS_VIDEO)
        Utils.log("camera: launch system video -> $path")
    }.onFailure {
        Utils.log("camera: launch system video failed: $it")
        runCatching { Utils.toast(fragment.requireContext(), "未找到可录像的相机应用") }
    }
}

/** Handle a system capture result. Returns true if [requestCode] was one of ours. */
fun handleCaptureResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    val fallback = data?.data
    when (requestCode) {
        REQ_SYS_PHOTO -> {
            val path = CameraCapture.pendingPhotoPath
            CameraCapture.pendingPhotoPath = null
            if (resultCode == -1 && path != null) {
                // System camera is a separate activity; when the chat (WatchAIOFragment) resumes,
                // switch the ViewPager back to the chat page — same mechanism multi-select uses.
                GalleryMultiSelectState.goToChatOnResume = true
                Thread { sendPhoto(path, fallback) }.start()
            }
            return true
        }
        REQ_SYS_VIDEO -> {
            val path = CameraCapture.pendingVideoPath
            CameraCapture.pendingVideoPath = null
            if (resultCode == -1 && path != null) {
                GalleryMultiSelectState.goToChatOnResume = true
                Thread { sendVideo(path, fallback) }.start()
            }
            return true
        }
    }
    return false
}

private fun ensureFile(path: String, fallback: Uri?): Boolean {
    val file = File(path)
    if ((!file.exists() || file.length() == 0L) && fallback != null) {
        runCatching {
            Utils.application.contentResolver.openInputStream(fallback)?.use { input ->
                FileOutputStream(file).use { input.copyTo(it) }
            }
        }.onFailure { Utils.log("camera: copy from result uri failed: $it") }
    }
    return file.exists() && file.length() > 0L
}

/**
 * Send a video recorded by the in-app recorder ([VideoRecordFragment]). Runs the build/send on a
 * background thread (off the UI thread) and flags the chat to return to page 0 on resume.
 */
fun sendInAppVideo(path: String) {
    GalleryMultiSelectState.goToChatOnResume = true
    Thread { sendVideo(path, null) }.start()
    Utils.log("camera: send in-app recorded video -> $path")
}

private fun send(element: MsgElement) {
    MsgUtil.msgService.sendMsg(
        CurrentContact, 0L, arrayListOf(element),
        IOperateCallback { code, msg -> Utils.log("camera send result=$code msg=$msg") }
    )
}

private fun sendPhoto(path: String, fallback: Uri?) {
    runCatching {
        if (!ensureFile(path, fallback)) { Utils.log("camera: photo empty $path"); return }
        send(com.tencent.watch.aio_impl.ext.MsgUtil().a(path, 0))
    }.onFailure { Utils.log("camera: sendPhoto failed: $it") }
}

private fun sendVideo(origPath: String, fallback: Uri?) {
    runCatching {
        if (!ensureFile(origPath, fallback)) { Utils.log("camera: video empty $origPath"); return }
        send(buildVideoElement(origPath))
    }.onFailure { Utils.log("camera: sendVideo failed: $it") }
}

/** Replicates MenuFrame's gallery video-element build sequence. */
private fun buildVideoElement(origPath: String): MsgElement {
    val fileName = FileUtils().a(origPath)
    val md5 = QQNTWrapperUtil.CppProxy.genFileMd5Hex(origPath)
    val svc = KernelServiceUtil.c()

    val videoPath = svc?.getRichMediaFilePathForMobileQQSend(
        RichMediaFilePathInfo(5, 2, md5, fileName, 1, 0, null, "", true)
    ) ?: ""
    if (!QQNTWrapperUtil.CppProxy.fileIsExist(videoPath)) com.tencent.qqnt.util.file.FileUtils.b(origPath, videoPath)

    val thumbPath = svc?.getRichMediaFilePathForMobileQQSend(
        RichMediaFilePathInfo(5, 1, md5, fileName, 2, 0, null, "", true)
    ) ?: ""
    if (!QQNTWrapperUtil.CppProxy.fileIsExist(thumbPath)) {
        runCatching {
            FileOutputStream(thumbPath).use { out ->
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(origPath)
                retriever.frameAtTime?.compress(Bitmap.CompressFormat.JPEG, 60, out)
                out.flush()
            }
        }.onFailure { Utils.log("camera: thumb extract failed: $it") }
    }

    val opts = BitmapFactory.Options()
    BitmapFactory.decodeFile(thumbPath, opts)

    val video = VideoElement()
    video.filePath = videoPath
    video.videoMd5 = QQNTWrapperUtil.CppProxy.genFileMd5Hex(videoPath)
    video.fileTime = 0
    video.fileSize = QQNTWrapperUtil.CppProxy.getFileSize(videoPath)
    video.fileName = FileUtils().a(videoPath)
    video.fileFormat = 2
    video.thumbSize = QQNTWrapperUtil.CppProxy.getFileSize(thumbPath).toInt()
    video.thumbWidth = opts.outWidth
    video.thumbHeight = opts.outHeight
    video.thumbMd5 = QQNTWrapperUtil.CppProxy.genFileMd5Hex(thumbPath)
    video.thumbPath = hashMapOf(0 to thumbPath)

    val element = MsgElement()
    element.elementType = 5
    element.videoElement = video
    return element
}
