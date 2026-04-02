package com.sasayaki.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

class BubbleView(context: Context) : View(context) {
    companion object {
        const val SIZE_DP = 56
        private const val CANCEL_SIZE_DP = 20
        private const val CANCEL_GAP_DP = 8
    }

    private val density = resources.displayMetrics.density
    private val sizePx = (SIZE_DP * density).toInt()
    private val cancelSizePx = CANCEL_SIZE_DP * density
    private val cancelGapPx = CANCEL_GAP_DP * density
    private val expandedHeightPx = (sizePx + cancelGapPx + cancelSizePx).toInt()

    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cancelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private var state: ServiceState = ServiceState.Idle
    private var audioLevel: Float = 0f
    private var pulseScale: Float = 1f
    private var arcAngle: Float = 0f

    private val pulseAnimator = ValueAnimator.ofFloat(1f, 1.3f).apply {
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
        minimumWidth = sizePx
        minimumHeight = expandedHeightPx
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (state is ServiceState.Recording) expandedHeightPx else sizePx
        setMeasuredDimension(
            resolveSize(sizePx, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = sizePx / 2f
        val cy = height - (sizePx / 2f)
        val baseRadius = sizePx / 2f * 0.7f

        when (state) {
            is ServiceState.Idle -> {
                basePaint.color = Color.argb(180, 100, 100, 100)
                canvas.drawCircle(cx, cy, baseRadius, basePaint)
                // Mic icon suggestion (simple circle)
                basePaint.color = Color.WHITE
                canvas.drawCircle(cx, cy, baseRadius * 0.35f, basePaint)
            }
            is ServiceState.Recording -> {
                val levelScale = 1f + audioLevel * 0.4f
                val radius = baseRadius * pulseScale * levelScale
                basePaint.color = Color.argb(200, 220, 50, 50)
                canvas.drawCircle(cx, cy, radius, basePaint)
                basePaint.color = Color.WHITE
                canvas.drawCircle(cx, cy, baseRadius * 0.3f, basePaint)
                drawCancelButton(canvas, cx)
            }
            is ServiceState.Transcribing -> {
                basePaint.color = Color.argb(180, 60, 120, 220)
                canvas.drawCircle(cx, cy, baseRadius, basePaint)
                arcPaint.color = Color.WHITE
                val rect = RectF(
                    cx - baseRadius * 0.5f, cy - baseRadius * 0.5f,
                    cx + baseRadius * 0.5f, cy + baseRadius * 0.5f
                )
                canvas.drawArc(rect, arcAngle, 90f, false, arcPaint)
            }
            is ServiceState.Injecting -> {
                basePaint.color = Color.argb(180, 50, 180, 80)
                canvas.drawCircle(cx, cy, baseRadius, basePaint)
            }
            is ServiceState.Error -> {
                basePaint.color = Color.argb(200, 220, 50, 50)
                canvas.drawCircle(cx, cy, baseRadius, basePaint)
            }
        }
    }

    fun updateState(newState: ServiceState) {
        val heightChanged = (state is ServiceState.Recording) != (newState is ServiceState.Recording)
        state = newState
        when (newState) {
            is ServiceState.Recording -> {
                if (!pulseAnimator.isRunning) pulseAnimator.start()
                arcAnimator.cancel()
            }
            is ServiceState.Transcribing -> {
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

    fun cleanup() {
        pulseAnimator.cancel()
        arcAnimator.cancel()
    }

    fun collapsedWidthPx(): Int = sizePx

    fun collapsedHeightPx(): Int = sizePx

    fun expandedHeightPx(): Int = expandedHeightPx

    fun isCancelHit(x: Float, y: Float): Boolean {
        if (state !is ServiceState.Recording) return false
        val centerX = sizePx / 2f
        val centerY = cancelSizePx / 2f
        val touchRadius = cancelSizePx * 0.75f
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= touchRadius * touchRadius
    }

    private fun drawCancelButton(canvas: Canvas, centerX: Float) {
        val radius = cancelSizePx / 2f
        val centerY = radius
        cancelPaint.color = Color.argb(235, 45, 45, 45)
        canvas.drawCircle(centerX, centerY, radius, cancelPaint)

        cancelStrokePaint.color = Color.WHITE
        val arm = radius * 0.45f
        canvas.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm, cancelStrokePaint)
        canvas.drawLine(centerX - arm, centerY + arm, centerX + arm, centerY - arm, cancelStrokePaint)
    }
}
