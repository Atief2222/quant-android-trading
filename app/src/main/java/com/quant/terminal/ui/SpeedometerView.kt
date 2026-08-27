package com.quant.terminal.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var gaugeType: Int = 0 // 0 = CHOPPINESS (0..100), 1 = MPI (-5..+5)
    private var currentValue: Float = 50f
    private var labelText: String = "CHOPPINESS"

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38bdf8")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#f8fafc")
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94a3b8")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    fun setGaugeMode(type: Int, title: String) {
        this.gaugeType = type
        this.labelText = title
        invalidate()
    }

    fun setValue(value: Float) {
        this.currentValue = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.8f
        val radius = (w.coerceAtMost(h * 1.3f) / 2f) - 30f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1. Gambar Busur Warna
        if (gaugeType == 0) {
            // Choppiness: Hijau (Trend), Kuning (Moderate), Merah (Chop)
            arcPaint.color = Color.parseColor("#10b981")
            canvas.drawArc(arcRect, 180f, 72f, false, arcPaint)

            arcPaint.color = Color.parseColor("#f59e0b")
            canvas.drawArc(arcRect, 252f, 38f, false, arcPaint)

            arcPaint.color = Color.parseColor("#ef4444")
            canvas.drawArc(arcRect, 290f, 70f, false, arcPaint)
        } else {
            // MPI: Hijau (Gold Bullish), Kuning (Neutral), Merah (Gold Bearish)
            arcPaint.color = Color.parseColor("#10b981")
            canvas.drawArc(arcRect, 180f, 60f, false, arcPaint)

            arcPaint.color = Color.parseColor("#f59e0b")
            canvas.drawArc(arcRect, 240f, 60f, false, arcPaint)

            arcPaint.color = Color.parseColor("#ef4444")
            canvas.drawArc(arcRect, 300f, 60f, false, arcPaint)
        }

        // 2. Kalkulasi Sudut Jarum
        val angleDeg = if (gaugeType == 0) {
            val clamped = currentValue.coerceIn(0f, 100f)
            180f + (clamped / 100f * 180f)
        } else {
            val clamped = currentValue.coerceIn(-5f, 5f)
            180f + ((clamped + 5f) / 10f * 180f)
        }

        val angleRad = Math.toRadians(angleDeg.toDouble())
        val needleLength = radius * 0.75f
        val endX = (cx + needleLength * cos(angleRad)).toFloat()
        val endY = (cy + needleLength * sin(angleRad)).toFloat()

        // 3. Gambar Jarum & Titik Poros
        canvas.drawLine(cx, cy, endX, endY, needlePaint)
        needlePaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 10f, needlePaint)
        needlePaint.style = Paint.Style.STROKE

        // 4. Render Teks Nilai & Label
        val displayVal = if (gaugeType == 0) String.format("%.1f", currentValue) else String.format("%+.1f", currentValue)
        canvas.drawText(displayVal, cx, cy - 35f, textPaint)
        canvas.drawText(labelText, cx, cy + 30f, labelPaint)
    }
}
