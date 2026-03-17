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
    }

    private val sizePx = (SIZE_DP * resources.displayMetrics.density).toInt()

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
        minimumHeight = sizePx
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = width / 2f * 0.7f

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
}
