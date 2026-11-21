package com.scancode.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

internal enum class FrameShape(
    @get:StringRes val label: Int,
    val widthFraction: Float,
    val heightToWidth: Float,
) {
    SQUARE(R.string.frame_square, 0.73f, 1f),
    WIDE(R.string.frame_wide, 0.87f, 0.54f),
    LARGE(R.string.frame_full, 0.90f, 1f),
}

class ScanOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val bounds = RectF()
    private val corners = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = ContextCompat.getColor(context, R.color.lime)
    private var footerHeight = 0
    private var headerBottom = 0

    internal var shape = FrameShape.SQUARE
        set(value) {
            field = value
            updateBounds()
            invalidate()
        }

    internal var live = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    internal fun setReservedSpace(headerBottom: Int, footerHeight: Int) {
        this.headerBottom = headerBottom
        this.footerHeight = footerHeight
        updateBounds()
        invalidate()
    }

    internal fun containsCenter(box: Rect) =
        bounds.contains(box.exactCenterX(), box.exactCenterY())

    internal fun distanceFromCenter(box: Rect): Float {
        val dx = box.exactCenterX() - bounds.centerX()
        val dy = box.exactCenterY() - bounds.centerY()
        return dx * dx + dy * dy
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateBounds()

    private fun updateBounds() {
        val top = headerBottom + dp(10)
        val bottom = height - footerHeight - dp(8)
        val available = (bottom - top).coerceAtLeast(0f)
        val frameWidth = minOf(width * shape.widthFraction, dp(440), available / shape.heightToWidth)
        val frameHeight = frameWidth * shape.heightToWidth
        val x = (width - frameWidth) / 2
        val y = top + (available - frameHeight) / 2
        bounds.set(x, y, x + frameWidth, y + frameHeight)
        val length = minOf(dp(23), frameHeight / 4)
        val radius = length / 2
        val (l, t, r, b) = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        corners.rewind()
        corners.moveTo(l, t + length)
        corners.lineTo(l, t + radius)
        corners.quadTo(l, t, l + radius, t)
        corners.lineTo(l + length, t)
        corners.moveTo(r - length, t)
        corners.lineTo(r - radius, t)
        corners.quadTo(r, t, r, t + radius)
        corners.lineTo(r, t + length)
        corners.moveTo(r, b - length)
        corners.lineTo(r, b - radius)
        corners.quadTo(r, b, r - radius, b)
        corners.lineTo(r - length, b)
        corners.moveTo(l + length, b)
        corners.lineTo(l + radius, b)
        corners.quadTo(l, b, l, b - radius)
        corners.lineTo(l, b - length)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!live) {
            paint.style = Paint.Style.FILL
            paint.color = 0x545D759D
            val step = dp(16)
            var y = step
            while (y < height) {
                var x = step
                while (x < width) {
                    canvas.drawCircle(x, y, dp(0.8f), paint)
                    x += step
                }
                y += step
            }
        }
        if (bounds.isEmpty) return

        paint.style = Paint.Style.FILL
        paint.color = if (live) 0x66111B30 else 0x38111B30
        canvas.drawRect(0f, 0f, width.toFloat(), bounds.top, paint)
        canvas.drawRect(0f, bounds.bottom, width.toFloat(), height.toFloat(), paint)
        canvas.drawRect(0f, bounds.top, bounds.left, bounds.bottom, paint)
        canvas.drawRect(bounds.right, bounds.top, width.toFloat(), bounds.bottom, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(1)
        paint.color = 0x706E819E
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint)
        if (live) {
            paint.strokeWidth = dp(5)
            paint.color = Color.argb(125, 17, 27, 48)
            canvas.drawPath(corners, paint)
        }
        paint.strokeWidth = dp(2.4f)
        paint.color = accent
        canvas.drawPath(corners, paint)

        if (!live) {
            paint.color = 0xA4DCF184.toInt()
            paint.strokeWidth = dp(1.3f)
            val centerY = bounds.centerY()
            canvas.drawLine(bounds.left + dp(14), centerY, bounds.right - dp(14), centerY, paint)
        }
    }

    private fun dp(value: Number) = value.toFloat() * resources.displayMetrics.density
}
