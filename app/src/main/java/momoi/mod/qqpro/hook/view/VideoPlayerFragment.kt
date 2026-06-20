package momoi.mod.qqpro.hook.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import momoi.mod.qqpro.lib.material.M3CircularProgress
import momoi.mod.qqpro.lib.material.MaterialSymbol
import momoi.mod.qqpro.lib.material.MaterialSymbols
import android.widget.SeekBar
import android.widget.TextView
import momoi.mod.qqpro.lib.dp
import momoi.mod.qqpro.util.Utils
import kotlin.math.max

/**
 * Self-contained fullscreen video player. Replaces QQ's native RFW gallery viewer for chat videos
 * (which is obfuscated and offers no zoom). Plays an mp4 with a plain [MediaPlayer] against a
 * [TextureView].
 *
 *  - **Zoom** via pinch and double-tap (animated, no snapping). The TextureView is scaled/translated.
 *  - **Controls** mirror the original: a play/pause button, a draggable seek bar and a time label,
 *    auto-hiding after a few seconds and toggled by a single tap.
 *  - **Loading for not-yet-downloaded videos**: when [initialPath] is null we show a spinner, fire
 *    [needDownload] once, then poll [resolvePath] until the file lands and auto-play it.
 *
 * Not a `@Mixin` class (lives in our package), so the gesture/listener objects below are safe.
 */
class VideoPlayerFragment(
    private val initialPath: String? = null,
    private val needDownload: (() -> Unit)? = null,
    private val resolvePath: (() -> String?)? = null,
    // Returns the kernel download progress (0..1) while downloading, or null if unknown. When it
    // yields a value the spinner flips to determinate; otherwise it stays an indeterminate spinner.
    private val progressProvider: (() -> Float?)? = null,
) : MyDialogFragment() {

    private var textureView: TextureView? = null
    private var spinner: M3CircularProgress? = null
    private var controls: View? = null
    private var playButton: ImageView? = null
    private var seekBar: SeekBar? = null
    private var timeLabel: TextView? = null

    private var mp: MediaPlayer? = null
    private var surface: Surface? = null
    private var pendingPath: String? = initialPath
    private var started = false
    private var playing = false
    private var releasing = false
    private var seeking = false

    private var videoW = 0
    private var videoH = 0

    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var zoomAnimator: ValueAnimator? = null

    private val ui = Handler(Looper.getMainLooper())
    private val pollDeadline = SystemClock.elapsedRealtime() + 90_000

    private val pollTick = object : Runnable {
        override fun run() {
            if (started || view == null) return
            // Show real download progress while we wait (determinate when the kernel reports bytes).
            runCatching { progressProvider?.invoke() }.getOrNull()?.let { pr ->
                spinner?.let { it.indeterminate = false; it.progress = pr }
            }
            val p = runCatching { resolvePath?.invoke() }.getOrNull()
            if (p != null) {
                Utils.log("player: download resolved -> $p")
                pendingPath = p
                tryStart()
            } else if (SystemClock.elapsedRealtime() > pollDeadline) {
                Utils.log("player: download poll timed out")
                runCatching { Utils.toast(requireContext(), "视频下载失败") }
                dismissAllowingStateLoss()
            } else {
                ui.postDelayed(this, 120)
            }
        }
    }

    private val progressTick = object : Runnable {
        override fun run() {
            val player = mp ?: return
            runCatching {
                if (!seeking) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    seekBar?.max = dur
                    seekBar?.progress = pos
                    timeLabel?.text = "${fmt(pos)} / ${fmt(dur)}"
                }
            }
            ui.postDelayed(this, 500)
        }
    }

    private val hideControls = Runnable { controls?.visibility = View.GONE }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = inflater.context
        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.BLACK)

        val tv = TextureView(ctx)
        textureView = tv
        root.addView(tv, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                surface = Surface(st)
                tryStart()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                surface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        val pb = M3CircularProgress(ctx)
        spinner = pb
        root.addView(pb, FrameLayout.LayoutParams(40.dp, 40.dp).apply { gravity = Gravity.CENTER })

        root.addView(buildControls(ctx), FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            // Small insets so the seek bar gets as much width as possible on the narrow screen.
            leftMargin = 8.dp; rightMargin = 8.dp; bottomMargin = 14.dp
        })

        val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomAnimator?.cancel()
                scale = (scale * d.scaleFactor).coerceIn(1f, 5f)
                if (scale <= 1.01f) { tx = 0f; ty = 0f }
                clampTranslation()
                applyTransform()
                return true
            }
        })
        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > 1.01f) animateZoom(1f, 0f, 0f) else animateZoom(2.5f, 0f, 0f)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scale > 1.01f) {
                    zoomAnimator?.cancel()
                    tx -= dx; ty -= dy
                    clampTranslation()
                    applyTransform()
                }
                return true
            }
        })
        root.setOnTouchListener { _, e ->
            scaleDetector.onTouchEvent(e)
            gestureDetector.onTouchEvent(e)
            true
        }

        if (pendingPath == null) {
            pb.visibility = View.VISIBLE
            Utils.log("player: no local file, triggering download")
            runCatching { needDownload?.invoke() }
            ui.postDelayed(pollTick, 500)
        } else {
            pb.visibility = View.VISIBLE // until first frame renders
            tryStart()
        }

        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildControls(ctx: android.content.Context): View {
        // Vertical so the time label sits BELOW the seek bar (the watch screen is too narrow to
        // fit play + seek + time on one row without the bar being tiny).
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(14.dp, 6.dp, 14.dp, 6.dp)
            background = GradientDrawable().apply {
                setColor(0x80_000000.toInt())
                cornerRadius = 20.dp.toFloat()
            }
        }

        // Top row: play/pause button + full-width seek bar.
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val play = ImageView(ctx).apply {
            setImageDrawable(MaterialSymbol(MaterialSymbols.pause, Color.WHITE))
            setColorFilter(Color.WHITE)
            setOnClickListener { togglePlay() }
        }
        playButton = play
        row.addView(play, LinearLayout.LayoutParams(24.dp, 24.dp))

        val seek = SeekBar(ctx)
        seekBar = seek
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) timeLabel?.text = "${fmt(progress)} / ${fmt(mp?.duration ?: 0)}"
            }
            override fun onStartTrackingTouch(sb: SeekBar) { seeking = true; cancelAutoHide() }
            override fun onStopTrackingTouch(sb: SeekBar) {
                seeking = false
                runCatching { mp?.seekTo(sb.progress) }
                scheduleAutoHide()
            }
        })
        row.addView(seek, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 6.dp })
        bar.addView(row, LinearLayout.LayoutParams(-1, -2))

        val time = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            text = "00:00 / 00:00"
            gravity = Gravity.CENTER
        }
        timeLabel = time
        bar.addView(time, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 2.dp })

        controls = bar
        bar.visibility = View.GONE // shown once playback starts
        return bar
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun tryStart() {
        if (started) return
        val s = surface ?: return
        val path = pendingPath ?: return
        started = true
        ui.removeCallbacks(pollTick)
        runCatching {
            val player = MediaPlayer()
            player.setSurface(s)
            player.setDataSource(path)
            player.isLooping = true
            player.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) { videoW = w; videoH = h; resizeTexture() }
            }
            player.setOnPreparedListener {
                spinner?.visibility = View.GONE
                resizeTexture()
                it.start()
                playing = true
                playButton?.setImageDrawable(MaterialSymbol(MaterialSymbols.pause, Color.WHITE))
                ui.post(progressTick)
                showControls()
                Utils.log("player: playback started $path")
            }
            player.setOnErrorListener { _, what, extra ->
                Utils.log("player: MediaPlayer error what=$what extra=$extra")
                // -19/-38 etc. fire on surface teardown at dismiss; only surface a toast for a
                // genuine pre-playback failure, never for teardown noise (which lands on the chat list).
                if (!releasing && !playing) runCatching { Utils.toast(requireContext(), "视频播放失败") }
                true
            }
            mp = player
            player.prepareAsync()
        }.onFailure {
            Utils.log("player: setup failed: $it")
            runCatching { Utils.toast(requireContext(), "视频播放失败") }
            started = false
        }
    }

    private fun togglePlay() {
        val player = mp ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
                playing = false
                playButton?.setImageDrawable(MaterialSymbol(MaterialSymbols.play_arrow, Color.WHITE))
            } else {
                player.start()
                playing = true
                playButton?.setImageDrawable(MaterialSymbol(MaterialSymbols.pause, Color.WHITE))
            }
        }
        showControls()
    }

    private fun toggleControls() {
        val c = controls ?: return
        if (c.visibility == View.VISIBLE) {
            cancelAutoHide()
            c.visibility = View.GONE
        } else {
            showControls()
        }
    }

    private fun showControls() {
        controls?.visibility = View.VISIBLE
        scheduleAutoHide()
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        ui.postDelayed(hideControls, 3000)
    }

    private fun cancelAutoHide() = ui.removeCallbacks(hideControls)

    private fun animateZoom(targetScale: Float, targetTx: Float, targetTy: Float) {
        zoomAnimator?.cancel()
        val fromS = scale; val fromX = tx; val fromY = ty
        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                scale = fromS + (targetScale - fromS) * f
                tx = fromX + (targetTx - fromX) * f
                ty = fromY + (targetTy - fromY) * f
                clampTranslation()
                applyTransform()
            }
            start()
        }
    }

    /** Fit the TextureView to the video aspect ratio inside the screen (letterbox). */
    private fun resizeTexture() {
        val tv = textureView ?: return
        val root = tv.parent as? View ?: return
        if (videoW <= 0 || videoH <= 0) return
        val rw = root.width
        val rh = root.height
        if (rw == 0 || rh == 0) { tv.post { resizeTexture() }; return }
        val fit = minOf(rw.toFloat() / videoW, rh.toFloat() / videoH)
        val w = (videoW * fit).toInt()
        val h = (videoH * fit).toInt()
        val lp = tv.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            tv.layoutParams = lp
        }
    }

    private fun clampTranslation() {
        val tv = textureView ?: return
        val maxX = max(0f, tv.width * (scale - 1f) / 2f)
        val maxY = max(0f, tv.height * (scale - 1f) / 2f)
        tx = tx.coerceIn(-maxX, maxX)
        ty = ty.coerceIn(-maxY, maxY)
    }

    private fun applyTransform() {
        val tv = textureView ?: return
        tv.scaleX = scale
        tv.scaleY = scale
        tv.translationX = tx
        tv.translationY = ty
    }

    private fun fmt(ms: Int): String {
        val total = (if (ms < 0) 0 else ms) / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasing = true
        zoomAnimator?.cancel()
        ui.removeCallbacks(pollTick)
        ui.removeCallbacks(progressTick)
        cancelAutoHide()
        // Drop the error listener before teardown so surface-destroy errors don't fire a toast.
        runCatching { mp?.setOnErrorListener(null) }
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        mp = null
        runCatching { surface?.release() }
        surface = null
    }
}
