package com.chaomixian.vflow.services

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.core.animation.doOnEnd
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import kotlin.math.max

object ScreenOperationPointerOverlay {
    const val STYLE_STANDARD = "standard"
    const val STYLE_STANDARD_WHITE = "standard_white"
    const val STYLE_GENSHIN = "genshin"
    const val STYLE_NAHIDA = "nahida"
    const val STYLE_FURINA = "furina"
    const val STYLE_BTR_AHOGE = "btr_ahoge"
    const val STYLE_SILENT_WITCH = "silent_witch"
    const val DEFAULT_STYLE = STYLE_STANDARD

    val styleOptions = listOf(
        STYLE_STANDARD,
        STYLE_STANDARD_WHITE,
        STYLE_GENSHIN,
        STYLE_NAHIDA,
        STYLE_FURINA,
        STYLE_BTR_AHOGE,
        STYLE_SILENT_WITCH
    )

    private const val HOLD_AFTER_CLICK_MS = 900L
    private const val LONG_PRESS_RELEASE_HOLD_MS = 420L
    private const val MOVE_DURATION_MIN_MS = 180L
    private const val MOVE_DURATION_MAX_MS = 420L
    private const val CURSOR_SIZE_DP = 42f
    private const val HOTSPOT_X_RATIO = 0.10f
    private const val HOTSPOT_Y_RATIO = 0.08f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val moveInterpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private var windowManager: WindowManager? = null
    private var overlayView: PointerView? = null
    private var moveAnimator: ValueAnimator? = null
    private var currentPoint: Point? = null
    private var hideRunnable: Runnable? = null

    fun notifyTap(context: Context, x: Int, y: Int) {
        notifyOperation(context, Point(x, y), null, 80L)
    }

    fun notifyLongPress(context: Context, x: Int, y: Int, durationMs: Long) {
        notifyOperation(context, Point(x, y), null, durationMs.coerceAtLeast(350L))
    }

    fun notifySwipe(context: Context, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        notifyOperation(context, Point(x1, y1), Point(x2, y2), durationMs.coerceAtLeast(160L))
    }

    private fun notifyOperation(context: Context, start: Point, end: Point?, pressDurationMs: Long) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ModuleConfigActivity.KEY_SCREEN_OPERATION_POINTER_ENABLED, false)) return

        val style = ModuleConfigActivity.readScreenOperationPointerStyle(prefs)
        mainHandler.post {
            showPress(context, start, end, pressDurationMs, style)
        }
    }

    private fun showPress(context: Context, start: Point, end: Point?, pressDurationMs: Long, style: String) {
        hideRunnable?.let(mainHandler::removeCallbacks)
        hideRunnable = null

        val view = ensureOverlay(context, style) ?: return
        view.setStyle(style)

        val onArrived: () -> Unit = {
            view.press()
            if (end == null) {
                mainHandler.postDelayed({
                    view.release()
                    scheduleHide(HOLD_AFTER_CLICK_MS)
                }, pressDurationMs.coerceAtMost(1600L))
            } else {
                movePointer(
                    view = view,
                    from = start,
                    target = end,
                    duration = pressDurationMs,
                    onEnd = {
                        view.release()
                        scheduleHide(HOLD_AFTER_CLICK_MS)
                    }
                )
            }
        }

        val from = currentPoint
        if (from == null) {
            currentPoint = Point(start)
            view.setPointerPosition(start.x.toFloat(), start.y.toFloat())
            onArrived()
            return
        }

        movePointer(
            view = view,
            from = from,
            target = start,
            duration = calculateMoveDuration(from, start),
            onEnd = onArrived
        )
    }

    private fun movePointer(
        view: PointerView,
        from: Point,
        target: Point,
        duration: Long,
        onEnd: () -> Unit
    ) {
        moveAnimator?.cancel()
        if (from == target) {
            currentPoint = Point(target)
            view.setPointerPosition(target.x.toFloat(), target.y.toFloat())
            onEnd()
            return
        }
        val start = Point(from)
        val distance = max(kotlin.math.abs(target.x - from.x), kotlin.math.abs(target.y - from.y))
        val actualDuration = duration.coerceAtLeast(if (distance == 0) 0L else 80L)
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = actualDuration
            interpolator = moveInterpolator
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                val x = start.x + (target.x - start.x) * t
                val y = start.y + (target.y - start.y) * t
                view.setPointerPosition(x, y)
            }
            doOnEnd {
                currentPoint = Point(target)
                view.setPointerPosition(target.x.toFloat(), target.y.toFloat())
                onEnd()
            }
            start()
        }
    }

    private fun calculateMoveDuration(from: Point, target: Point): Long {
        val distance = max(kotlin.math.abs(target.x - from.x), kotlin.math.abs(target.y - from.y))
        return (MOVE_DURATION_MIN_MS + distance * 0.18f)
            .toLong()
            .coerceIn(MOVE_DURATION_MIN_MS, MOVE_DURATION_MAX_MS)
    }

    private fun ensureOverlay(context: Context, style: String): PointerView? {
        val existing = overlayView
        if (existing != null) return existing

        val service = ServiceStateBus.getAccessibilityService()
        val overlayContext = service ?: context.applicationContext
        val type = if (service != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            if (!Settings.canDrawOverlays(context.applicationContext)) return null
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        val wm = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = PointerView(overlayContext).apply {
            setStyle(style)
            alpha = 0f
            animate().alpha(1f).setDuration(120L).start()
        }
        val metrics = overlayContext.resources.displayMetrics
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(metrics)
        val params = WindowManager.LayoutParams(
            metrics.widthPixels,
            metrics.heightPixels,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        return try {
            wm.addView(view, params)
            windowManager = wm
            overlayView = view
            view
        } catch (_: Exception) {
            null
        }
    }

    private fun scheduleHide(delayMs: Long = LONG_PRESS_RELEASE_HOLD_MS) {
        val runnable = Runnable {
            val view = overlayView ?: return@Runnable
            view.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { removeOverlay() }
                .start()
        }
        hideRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun removeOverlay() {
        moveAnimator?.cancel()
        moveAnimator = null
        hideRunnable?.let(mainHandler::removeCallbacks)
        hideRunnable = null
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // Overlay may already be gone if the hosting service disconnected.
        }
        overlayView = null
        windowManager = null
        currentPoint = null
    }

    private class PointerView(context: Context) : View(context) {
        private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x669E9E9E
            style = Paint.Style.FILL
            setShadowLayer(dp(8f), 0f, 0f, 0x55909090)
        }
        private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x669E9E9E
            style = Paint.Style.FILL
            setShadowLayer(dp(10f), 0f, 0f, 0x55909090)
        }
        private var cursorBitmap: Bitmap? = null
        private var pointerX = -100f
        private var pointerY = -100f
        private var pulseProgress = 1f
        private var pressProgress = 0f
        private var pulseAnimator: ValueAnimator? = null
        private var pressAnimator: ValueAnimator? = null

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setStyle(style: String) {
            val resId = when (style) {
                STYLE_STANDARD_WHITE -> R.drawable.stmc_cursor_standard_white
                STYLE_GENSHIN -> R.drawable.stmc_cursor_genshin
                STYLE_NAHIDA -> R.drawable.stmc_cursor_nahida
                STYLE_FURINA -> R.drawable.stmc_cursor_furina
                STYLE_BTR_AHOGE -> R.drawable.stmc_cursor_btr_ahoge
                STYLE_SILENT_WITCH -> R.drawable.stmc_cursor_silent_witch
                else -> R.drawable.stmc_cursor_standard
            }
            if (tag == resId) return
            tag = resId
            cursorBitmap = BitmapFactory.decodeResource(resources, resId)
            invalidate()
        }

        fun setPointerPosition(x: Float, y: Float) {
            pointerX = x
            pointerY = y
            invalidate()
        }

        fun press() {
            pulseAnimator?.cancel()
            pulseProgress = 1f
            pressAnimator?.cancel()
            pressAnimator = ValueAnimator.ofFloat(pressProgress, 1f).apply {
                duration = 140L
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    pressProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun release() {
            pressAnimator?.cancel()
            pressAnimator = ValueAnimator.ofFloat(pressProgress, 0f).apply {
                duration = 180L
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    pressProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            pulse()
        }

        private fun pulse() {
            pulseAnimator?.cancel()
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 360L
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    pulseProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bitmap = cursorBitmap ?: return
            val cursorSize = dp(CURSOR_SIZE_DP)
            val hotspotX = cursorSize * HOTSPOT_X_RATIO
            val hotspotY = cursorSize * HOTSPOT_Y_RATIO

            if (pressProgress > 0f) {
                val radius = dp(8f) + dp(8f) * pressProgress
                pressPaint.alpha = (34 + pressProgress * 54).toInt().coerceIn(0, 88)
                canvas.drawCircle(pointerX, pointerY, radius, pressPaint)
            }

            if (pulseProgress < 1f) {
                val radius = dp(10f) + dp(14f) * pulseProgress
                glowPaint.alpha = ((1f - pulseProgress) * 92).toInt().coerceIn(0, 92)
                canvas.drawCircle(pointerX, pointerY, radius, glowPaint)
            }

            val left = pointerX - hotspotX
            val top = pointerY - hotspotY
            canvas.drawBitmap(
                bitmap,
                null,
                android.graphics.RectF(left, top, left + cursorSize, top + cursorSize),
                cursorPaint
            )
        }

        private fun dp(value: Float): Float {
            return value * resources.displayMetrics.density
        }
    }
}
