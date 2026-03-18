package com.sasayaki.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class FanMenuItem(
    val label: String,
    val active: Boolean
)

class FanMenuView(
    context: Context,
    private val onItemTap: (index: Int) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val satelliteRadius = 18f * density
    private val fanRadius = 56f * density
    private val spreadAngleDeg = 45.0

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val scrimPaint = Paint().apply {
        color = Color.argb(1, 0, 0, 0)
        style = Paint.Style.FILL
    }

    var items: List<FanMenuItem> = emptyList()
        set(value) { field = value; invalidate() }

    var anchorX: Float = 0f
    var anchorY: Float = 0f
    var fanRight: Boolean = true

    private var progress: Float = 0f

    private val expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = OvershootInterpolator(1.5f)
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
    }

    fun expand() {
        expandAnimator.cancel()
        expandAnimator.setFloatValues(progress, 1f)
        expandAnimator.start()
    }

    fun collapse(onEnd: () -> Unit) {
        expandAnimator.cancel()
        val collapseAnimator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 120
            addUpdateListener { progress = animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { onEnd() }
            })
        }
        collapseAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (progress <= 0f || items.isEmpty()) return

        val positions = computePositions()
        for (i in items.indices) {
            val (cx, cy) = positions[i]
            val item = items[i]
            val currentRadius = satelliteRadius * progress

            bgPaint.color = if (item.active) {
                Color.argb(220, 60, 160, 90)
            } else {
                Color.argb(200, 100, 100, 100)
            }
            canvas.drawCircle(cx, cy, currentRadius, bgPaint)

            if (progress > 0.5f) {
                val textAlpha = ((progress - 0.5f) / 0.5f * 255).toInt().coerceIn(0, 255)
                textPaint.alpha = textAlpha
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(item.label, cx, textY, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tappedIndex = hitTest(event.x, event.y)
            if (tappedIndex >= 0) {
                onItemTap(tappedIndex)
            } else {
                onDismiss()
            }
            return true
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        if (progress < 0.3f) return -1
        val positions = computePositions()
        val hitRadius = satelliteRadius * 1.5f
        for (i in positions.indices) {
            val (cx, cy) = positions[i]
            val dx = x - cx
            val dy = y - cy
            if (sqrt(dx * dx + dy * dy) <= hitRadius) return i
        }
        return -1
    }

    private fun computePositions(): List<Pair<Float, Float>> {
        val baseAngle = if (fanRight) 0.0 else 180.0
        val startAngle = baseAngle - spreadAngleDeg
        val step = if (items.size > 1) {
            (spreadAngleDeg * 2) / (items.size - 1)
        } else 0.0

        return items.indices.map { i ->
            val angleDeg = startAngle + step * i
            val angleRad = Math.toRadians(angleDeg)
            val distance = fanRadius * progress
            val cx = anchorX + (cos(angleRad) * distance).toFloat()
            val cy = anchorY + (sin(angleRad) * distance).toFloat()
            cx to cy
        }
    }

    fun cleanup() {
        expandAnimator.cancel()
    }
}
