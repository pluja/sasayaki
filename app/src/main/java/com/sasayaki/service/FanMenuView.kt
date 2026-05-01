package com.sasayaki.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

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
    private val barHeight = 74f * density
    private val minSegmentWidth = 92f * density
    private val closeWidth = 74f * density
    private val cornerRadius = 24f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 231, 238)
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val scrimPaint = Paint().apply {
        color = Color.argb(1, 0, 0, 0)
        style = Paint.Style.FILL
    }

    var items: List<FanMenuItem> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var anchorX: Float = 0f
    var anchorY: Float = 0f
    var fanRight: Boolean = true

    private var progress: Float = 0f
    private var barRect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = OvershootInterpolator(1.2f)
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    fun expand() {
        animator.cancel()
        animator.setFloatValues(progress, 1f)
        animator.start()
    }

    fun collapse(onEnd: () -> Unit) {
        animator.cancel()
        ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 120
            addUpdateListener {
                progress = animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (items.isEmpty() || progress <= 0f) return

        computeBarRect()
        val scaled = RectF(
            barRect.centerX() + (barRect.left - barRect.centerX()) * progress,
            barRect.centerY() + (barRect.top - barRect.centerY()) * progress,
            barRect.centerX() + (barRect.right - barRect.centerX()) * progress,
            barRect.centerY() + (barRect.bottom - barRect.centerY()) * progress
        )

        fillPaint.color = Color.rgb(30, 31, 34)
        canvas.drawRoundRect(scaled, cornerRadius, cornerRadius, fillPaint)
        strokePaint.color = Color.rgb(95, 96, 106)
        canvas.drawRoundRect(scaled, cornerRadius, cornerRadius, strokePaint)
        if (progress < 0.75f) return

        val segmentWidth = (barRect.width() - closeWidth) / items.size
        items.forEachIndexed { index, item ->
            val left = barRect.left + segmentWidth * index
            val rect = RectF(left, barRect.top, left + segmentWidth, barRect.bottom)
            if (item.active) {
                fillPaint.color = Color.rgb(74, 78, 98)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            }
            strokePaint.color = Color.rgb(68, 69, 76)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.bottom, strokePaint)
            drawCenteredText(canvas, item.label.uppercase(), rect.centerX(), rect.centerY())
        }
        drawClose(canvas, RectF(barRect.right - closeWidth, barRect.top, barRect.right, barRect.bottom))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = hitTest(event.x, event.y)
            when {
                hit in items.indices -> onItemTap(hit)
                hit == items.size -> onDismiss()
                else -> onDismiss()
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        computeBarRect()
        if (!barRect.contains(x, y)) return -1
        val segmentWidth = (barRect.width() - closeWidth) / items.size
        val relativeX = x - barRect.left
        if (relativeX >= segmentWidth * items.size) return items.size
        return (relativeX / segmentWidth).toInt().coerceIn(0, items.lastIndex)
    }

    private fun computeBarRect() {
        val desiredWidth = (items.size * minSegmentWidth + closeWidth).coerceAtMost(width - 32f * density)
        val margin = 16f * density
        val maxLeft = (width - desiredWidth - margin).coerceAtLeast(margin)
        val left = (anchorX - desiredWidth / 2f).coerceIn(margin, maxLeft)
        val top = (anchorY - barHeight - 18f * density).coerceAtLeast(36f * density)
        barRect = RectF(left, top, left + desiredWidth, top + barHeight)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float) {
        val baseline = y - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun drawClose(canvas: Canvas, rect: RectF) {
        strokePaint.color = Color.rgb(230, 231, 238)
        strokePaint.strokeWidth = 3f * density
        val arm = 13f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, strokePaint)
        canvas.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, strokePaint)
        strokePaint.strokeWidth = 2f * density
    }

    fun cleanup() {
        animator.cancel()
    }
}
