package com.mexagent.app.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

/**
 * Full-screen transparent view drawn on top of everything.
 * Shows:
 *  - Animated pulsing rectangle around the current element
 *  - A small "bot dot" at the element center
 *  - A label chip with element name and status colour
 */
class HighlightOverlayView(context: Context) : View(context) {

    data class HighlightData(
        val x: Int, val y: Int, val width: Int, val height: Int,
        val label: String, val type: String, val status: String
    )

    private var current: HighlightData? = null
    private var animTick: Float = 0f

    // Paints
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        alpha = 200
    }

    private val rect = RectF()

    fun update(data: HighlightData?) {
        current = data
        animTick = 0f
        invalidate()
    }

    fun tick() {
        animTick += 0.12f
        if (current != null) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = current ?: return

        val statusColor = when (d.status) {
            "ACT"  -> Color.parseColor("#FF9800")  // orange
            "PASS" -> Color.parseColor("#4CAF50")  // green
            "FAIL" -> Color.parseColor("#F44336")  // red
            else   -> Color.parseColor("#2196F3")  // blue
        }

        val pulse = (0.55f + 0.45f * sin(animTick.toDouble())).toFloat()
        val alpha = (180 * pulse).toInt()

        // Element rectangle
        rect.set(
            d.x.toFloat(),
            d.y.toFloat(),
            (d.x + d.width).toFloat(),
            (d.y + d.height).toFloat()
        )

        // Fill with semi-transparent color
        fillPaint.color = statusColor
        fillPaint.alpha = (40 * pulse).toInt()
        canvas.drawRoundRect(rect, 8f, 8f, fillPaint)

        // Animated border
        borderPaint.color = statusColor
        borderPaint.alpha = alpha
        borderPaint.strokeWidth = 3f + 2f * pulse
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        // Corner accent lines
        val cornerLen = 20f
        borderPaint.alpha = 255
        borderPaint.strokeWidth = 4f
        // TL
        canvas.drawLine(rect.left, rect.top + cornerLen, rect.left, rect.top, borderPaint)
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, borderPaint)
        // TR
        canvas.drawLine(rect.right - cornerLen, rect.top, rect.right, rect.top, borderPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, borderPaint)
        // BL
        canvas.drawLine(rect.left, rect.bottom - cornerLen, rect.left, rect.bottom, borderPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, borderPaint)
        // BR
        canvas.drawLine(rect.right - cornerLen, rect.bottom, rect.right, rect.bottom, borderPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, borderPaint)

        // Bot dot at center
        val cx = d.x + d.width / 2f
        val cy = d.y + d.height / 2f
        val dotRadius = 10f + 4f * pulse

        dotPaint.color = statusColor
        dotPaint.alpha = 220
        canvas.drawCircle(cx, cy, dotRadius + 4f, dotPaint)

        dotPaint.color = Color.WHITE
        dotPaint.alpha = 255
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)

        // Label chip above the element
        val labelText = if (d.label.length > 28) d.label.take(25) + "…" else d.label
        val typeText  = "[${d.type.uppercase()}] ${d.status}"
        val textW     = maxOf(textPaint.measureText(labelText), subTextPaint.measureText(typeText))
        val chipW     = textW + 24f
        val chipH     = 56f
        val chipX     = cx - chipW / 2f
        val chipY     = rect.top - chipH - 8f

        labelBgPaint.color = Color.parseColor("#CC000000")
        canvas.drawRoundRect(chipX, chipY, chipX + chipW, chipY + chipH, 10f, 10f, labelBgPaint)

        // Status colour bar on left of chip
        labelBgPaint.color = statusColor
        canvas.drawRoundRect(chipX, chipY, chipX + 6f, chipY + chipH, 4f, 4f, labelBgPaint)

        canvas.drawText(labelText, chipX + 14f, chipY + 20f, textPaint)
        canvas.drawText(typeText,  chipX + 14f, chipY + 46f, subTextPaint)
    }
}
