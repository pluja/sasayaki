package com.sasayaki.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator

class BubbleView(context: Context) : View(context) {
    companion object {
        const val SIZE_DP = 56
        private const val RECORD_BUTTON_DP = 74
        private const val RECORD_GAP_DP = 2
    }

    private val density = resources.displayMetrics.density
    private val sizePx = (SIZE_DP * density).toInt()
    private val recordButtonPx = (RECORD_BUTTON_DP * density).toInt()
    private val recordGapPx = (RECORD_GAP_DP * density).toInt()
    private val recordWidthPx = recordButtonPx * 4 + recordGapPx * 3
    private val errorWidthPx = recordButtonPx * 2 + recordGapPx
    private val recordHeightPx = recordButtonPx
    private val radius = 20f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 226, 238)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var state: ServiceState = ServiceState.Idle
    private var audioLevel: Float = 0f
    private var pulseScale: Float = 1f
    private var arcAngle: Float = 0f
    private var activeProfileName: String = "AUTO"
    private var elapsedSeconds: Long = 0
    private var recordingPaused: Boolean = false
    private val roundedClipPath = Path()

    private val pulseAnimator = ValueAnimator.ofFloat(1f, 1.18f).apply {
        duration = 600
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { pulseScale = it.animatedValue as Float; invalidate() }
    }

    private val arcAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { arcAngle = it.animatedValue as Float; invalidate() }
    }

    init {
        minimumWidth = recordWidthPx
        minimumHeight = recordHeightPx
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = when (state) {
            is ServiceState.Recording -> recordWidthPx
            is ServiceState.Error -> errorWidthPx
            else -> sizePx
        }
        val desiredHeight = if (state is ServiceState.Recording || state is ServiceState.Error) recordHeightPx else sizePx
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (state is ServiceState.Recording) {
            drawRecordingControls(canvas)
            return
        }
        if (state is ServiceState.Error) {
            drawErrorControls(canvas)
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = sizePx / 2f * 0.72f
        when (state) {
            is ServiceState.Idle -> {
                fillPaint.color = Color.argb(190, 32, 34, 36)
                canvas.drawCircle(cx, cy, baseRadius, fillPaint)
                fillPaint.color = Color.rgb(225, 226, 238)
                canvas.drawCircle(cx, cy, baseRadius * 0.32f, fillPaint)
            }
            is ServiceState.Transcribing,
            is ServiceState.PostProcessing -> {
                fillPaint.color = Color.argb(220, 68, 72, 92)
                canvas.drawCircle(cx, cy, baseRadius, fillPaint)
                strokePaint.color = Color.rgb(225, 226, 238)
                val rect = RectF(cx - baseRadius * 0.52f, cy - baseRadius * 0.52f, cx + baseRadius * 0.52f, cy + baseRadius * 0.52f)
                canvas.drawArc(rect, arcAngle, 90f, false, strokePaint)
            }
            is ServiceState.Injecting -> {
                fillPaint.color = Color.argb(220, 45, 130, 80)
                canvas.drawCircle(cx, cy, baseRadius, fillPaint)
            }
            is ServiceState.Error -> Unit
            is ServiceState.Recording -> Unit
        }
    }

    fun updateState(newState: ServiceState) {
        val heightChanged = (state is ServiceState.Recording) != (newState is ServiceState.Recording)
        state = newState
        recordingPaused = (newState as? ServiceState.Recording)?.paused == true
        when (newState) {
            is ServiceState.Recording -> {
                if (!pulseAnimator.isRunning) pulseAnimator.start()
                arcAnimator.cancel()
            }
            is ServiceState.Transcribing,
            is ServiceState.PostProcessing -> {
                pulseAnimator.cancel()
                if (!arcAnimator.isRunning) arcAnimator.start()
            }
            else -> {
                pulseAnimator.cancel()
                arcAnimator.cancel()
                pulseScale = 1f
            }
        }
        if (heightChanged) requestLayout()
        invalidate()
    }

    fun updateAudioLevel(level: Float) {
        audioLevel = level
        if (state is ServiceState.Recording) invalidate()
    }

    fun updateRecordingElapsed(seconds: Long) {
        elapsedSeconds = seconds
        if (state is ServiceState.Recording) invalidate()
    }

    fun updateActiveProfileName(name: String) {
        activeProfileName = name.uppercase().take(12)
        invalidate()
    }

    fun cleanup() {
        pulseAnimator.cancel()
        arcAnimator.cancel()
    }

    fun collapsedWidthPx(): Int = when (state) {
        is ServiceState.Recording -> recordWidthPx
        is ServiceState.Error -> errorWidthPx
        else -> sizePx
    }

    fun collapsedHeightPx(): Int = if (state is ServiceState.Recording || state is ServiceState.Error) recordHeightPx else sizePx

    fun recordingWidthPx(): Int = recordWidthPx

    fun expandedHeightPx(): Int = recordHeightPx

    fun profileSegmentCenterXPx(): Float = segmentCenterX(2)

    fun isCancelHit(x: Float, y: Float): Boolean = state is ServiceState.Recording && segmentFor(x, y) == 1

    fun isProfileHit(x: Float, y: Float): Boolean = state is ServiceState.Recording && segmentFor(x, y) == 2

    fun isPauseHit(x: Float, y: Float): Boolean = state is ServiceState.Recording && segmentFor(x, y) == 3

    fun isErrorRetryHit(x: Float, y: Float): Boolean = state is ServiceState.Error && errorSegmentFor(x, y) == 0

    fun isErrorCancelHit(x: Float, y: Float): Boolean = state is ServiceState.Error && errorSegmentFor(x, y) == 1

    private fun drawRecordingControls(canvas: Canvas) {
        val outer = RectF(0f, 0f, recordWidthPx.toFloat(), recordHeightPx.toFloat())
        fillPaint.color = Color.rgb(27, 28, 31)
        canvas.drawRoundRect(outer, radius, radius, fillPaint)

        roundedClipPath.reset()
        roundedClipPath.addRoundRect(outer, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(roundedClipPath)

        activePaint.color = Color.rgb(74, 78, 98)
        canvas.drawRect(segmentRect(2), activePaint)

        strokePaint.color = Color.rgb(73, 74, 82)
        strokePaint.strokeWidth = 1.5f * density
        for (index in 1 until 4) {
            val x = index * recordButtonPx + (index - 0.5f) * recordGapPx
            canvas.drawLine(x, 0f, x, recordHeightPx.toFloat(), strokePaint)
        }
        canvas.restore()

        strokePaint.color = Color.rgb(88, 89, 100)
        strokePaint.strokeWidth = 2.5f * density
        canvas.drawRoundRect(outer, radius, radius, strokePaint)

        drawStopSegment(canvas, 0)
        drawCancelSegment(canvas, 1)
        drawProfileSegment(canvas, 2)
        drawPauseSegment(canvas, 3)
    }

    private fun drawErrorControls(canvas: Canvas) {
        val outer = RectF(0f, 0f, errorWidthPx.toFloat(), recordHeightPx.toFloat())
        fillPaint.color = Color.rgb(96, 31, 36)
        canvas.drawRoundRect(outer, radius, radius, fillPaint)

        roundedClipPath.reset()
        roundedClipPath.addRoundRect(outer, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(roundedClipPath)
        strokePaint.color = Color.rgb(125, 54, 60)
        strokePaint.strokeWidth = 1.5f * density
        val dividerX = recordButtonPx + recordGapPx / 2f
        canvas.drawLine(dividerX, 0f, dividerX, recordHeightPx.toFloat(), strokePaint)
        canvas.restore()

        strokePaint.color = Color.rgb(150, 70, 76)
        strokePaint.strokeWidth = 2.5f * density
        canvas.drawRoundRect(outer, radius, radius, strokePaint)

        drawRetrySegment(canvas, 0)
        drawErrorCancelSegment(canvas, 1)
    }

    private fun drawRetrySegment(canvas: Canvas, index: Int) {
        val cx = errorSegmentCenterX(index)
        val cy = recordHeightPx / 2f
        strokePaint.color = Color.rgb(246, 225, 226)
        strokePaint.strokeWidth = 3f * density
        val arcRadius = 15f * density
        val rect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
        canvas.drawArc(rect, 45f, 280f, false, strokePaint)

        fillPaint.color = Color.rgb(246, 225, 226)
        val arrow = Path().apply {
            moveTo(cx + 9f * density, cy - 19f * density)
            lineTo(cx + 19f * density, cy - 16f * density)
            lineTo(cx + 12f * density, cy - 8f * density)
            close()
        }
        canvas.drawPath(arrow, fillPaint)
    }

    private fun drawErrorCancelSegment(canvas: Canvas, index: Int) {
        val cx = errorSegmentCenterX(index)
        val cy = recordHeightPx / 2f
        strokePaint.color = Color.rgb(246, 225, 226)
        strokePaint.strokeWidth = 3.5f * density
        val arm = 15f * density
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, strokePaint)
        canvas.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, strokePaint)
    }

    private fun drawStopSegment(canvas: Canvas, index: Int) {
        val cx = segmentCenterX(index)
        val cy = recordHeightPx * 0.34f
        strokePaint.color = Color.rgb(205, 208, 232)
        strokePaint.strokeWidth = 3f * density
        val half = 8f * density * pulseScale * (1f + audioLevel * 0.1f)
        canvas.drawRoundRect(RectF(cx - half, cy - half, cx + half, cy + half), 3f * density, 3f * density, strokePaint)
        drawCenteredText(canvas, formatElapsed(elapsedSeconds), cx, recordHeightPx * 0.68f, 14f * density)
    }

    private fun drawCancelSegment(canvas: Canvas, index: Int) {
        val cx = segmentCenterX(index)
        val cy = recordHeightPx / 2f
        strokePaint.color = Color.rgb(205, 208, 232)
        strokePaint.strokeWidth = 3f * density
        canvas.drawCircle(cx, cy, 13f * density, strokePaint)
        canvas.drawLine(cx - 9f * density, cy + 9f * density, cx + 9f * density, cy - 9f * density, strokePaint)
    }

    private fun drawProfileSegment(canvas: Canvas, index: Int) {
        drawCenteredText(canvas, activeProfileName, segmentCenterX(index), recordHeightPx / 2f, 12f * density)
    }

    private fun drawPauseSegment(canvas: Canvas, index: Int) {
        val cx = segmentCenterX(index)
        val cy = recordHeightPx / 2f
        fillPaint.color = Color.rgb(225, 226, 238)
        if (recordingPaused) {
            val halfHeight = 18f * density
            val halfWidth = 14f * density
            val playPath = Path().apply {
                moveTo(cx - halfWidth / 2f, cy - halfHeight)
                lineTo(cx - halfWidth / 2f, cy + halfHeight)
                lineTo(cx + halfWidth, cy)
                close()
            }
            canvas.drawPath(playPath, fillPaint)
            return
        }
        val width = 6f * density
        val height = 28f * density
        canvas.drawRoundRect(RectF(cx - 11f * density, cy - height / 2, cx - 11f * density + width, cy + height / 2), 3f * density, 3f * density, fillPaint)
        canvas.drawRoundRect(RectF(cx + 5f * density, cy - height / 2, cx + 5f * density + width, cy + height / 2), 3f * density, 3f * density, fillPaint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
        textPaint.textSize = size
        textPaint.color = Color.rgb(225, 226, 238)
        val baseline = y - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun segmentCenterX(index: Int): Float {
        return index * (recordButtonPx + recordGapPx) + recordButtonPx / 2f
    }

    private fun segmentRect(index: Int): RectF {
        val left = index * (recordButtonPx + recordGapPx).toFloat()
        return RectF(left, 0f, left + recordButtonPx, recordHeightPx.toFloat())
    }

    private fun segmentFor(x: Float, y: Float): Int {
        if (y < 0f || y > recordHeightPx) return -1
        val segment = (x / (recordButtonPx + recordGapPx)).toInt()
        return if (segment in 0..3) segment else -1
    }

    private fun errorSegmentCenterX(index: Int): Float {
        return index * (recordButtonPx + recordGapPx) + recordButtonPx / 2f
    }

    private fun errorSegmentFor(x: Float, y: Float): Int {
        if (y < 0f || y > recordHeightPx) return -1
        val segment = (x / (recordButtonPx + recordGapPx)).toInt()
        return if (segment in 0..1) segment else -1
    }

    private fun formatElapsed(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(minutes, secs)
    }
}
