package com.itwiggle.randomchime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class AccountabilityMirrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var mirrorState: MirrorState = MirrorState()
        set(value) {
            field = value
            contentDescription = "Accountability Mirror. Clarity ${value.clarity} percent. Integrity ${value.integrity} percent."
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun dp(value: Float) = value * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(280f).toInt()
        val desiredHeight = dp(330f).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outer = RectF(dp(8f), dp(8f), width - dp(8f), height - dp(8f))
        val inner = RectF(outer.left + dp(12f), outer.top + dp(12f), outer.right - dp(12f), outer.bottom - dp(12f))

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(38, 34, 30)
        canvas.drawRoundRect(outer, dp(18f), dp(18f), paint)

        paint.color = Color.rgb(193, 203, 199)
        canvas.drawRoundRect(inner, dp(10f), dp(10f), paint)

        val clip = Path().apply { addRoundRect(inner, dp(10f), dp(10f), Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        drawReflection(canvas, inner)
        drawFog(canvas, inner)
        drawStructuralDamage(canvas, inner)
        canvas.restore()

        linePaint.color = Color.argb(145, 255, 255, 255)
        linePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(inner, dp(10f), dp(10f), linePaint)
    }

    private fun drawReflection(canvas: Canvas, bounds: RectF) {
        val damage = (100 - mirrorState.integrity) / 100f
        val cx = bounds.centerX()
        val baseY = bounds.bottom - dp(16f)

        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(cx, 0f)
        canvas.skew(damage * 0.055f, 0f)
        canvas.scale(1f + damage * 0.035f, 1f)
        canvas.translate(-cx, 0f)

        val skin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(74, 47, 36) }
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 151, 105, 79)
            style = Paint.Style.STROKE
            strokeWidth = dp(3f)
            strokeCap = Paint.Cap.ROUND
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(95, 20, 14, 12) }

        val headTop = bounds.top + bounds.height() * 0.12f
        val head = RectF(cx - dp(31f), headTop, cx + dp(31f), headTop + dp(68f))
        canvas.drawOval(head, skin)
        canvas.drawOval(RectF(head.left - dp(5f), head.centerY() - dp(8f), head.left + dp(5f), head.centerY() + dp(10f)), skin)
        canvas.drawOval(RectF(head.right - dp(5f), head.centerY() - dp(8f), head.right + dp(5f), head.centerY() + dp(10f)), skin)

        paint.color = Color.rgb(26, 20, 18)
        canvas.drawArc(RectF(head.left, head.top - dp(4f), head.right, head.bottom - dp(18f)), 185f, 170f, true, paint)

        val neck = RectF(cx - dp(18f), head.bottom - dp(5f), cx + dp(18f), head.bottom + dp(32f))
        canvas.drawRoundRect(neck, dp(8f), dp(8f), skin)

        val torsoTop = neck.bottom - dp(4f)
        val torsoBottom = baseY
        val torso = Path().apply {
            moveTo(cx - dp(19f), torsoTop)
            cubicTo(cx - dp(58f), torsoTop + dp(2f), cx - dp(86f), torsoTop + dp(34f), cx - dp(72f), torsoTop + dp(78f))
            lineTo(cx - dp(50f), torsoBottom)
            lineTo(cx + dp(50f), torsoBottom)
            lineTo(cx + dp(72f), torsoTop + dp(78f))
            cubicTo(cx + dp(86f), torsoTop + dp(34f), cx + dp(58f), torsoTop + dp(2f), cx + dp(19f), torsoTop)
            close()
        }
        canvas.drawPath(torso, skin)

        linePaint.color = skin.color
        linePaint.strokeWidth = dp(28f)
        canvas.drawLine(cx - dp(66f), torsoTop + dp(35f), cx - dp(92f), torsoBottom - dp(22f), linePaint)
        canvas.drawLine(cx + dp(66f), torsoTop + dp(35f), cx + dp(92f), torsoBottom - dp(22f), linePaint)

        canvas.drawOval(RectF(cx - dp(20f), torsoTop + dp(22f), cx, torsoTop + dp(62f)), shadow)
        canvas.drawOval(RectF(cx, torsoTop + dp(22f), cx + dp(20f), torsoTop + dp(62f)), shadow)

        canvas.drawLine(cx - dp(50f), torsoTop + dp(27f), cx - dp(12f), torsoTop + dp(49f), highlight)
        canvas.drawLine(cx + dp(50f), torsoTop + dp(27f), cx + dp(12f), torsoTop + dp(49f), highlight)
        canvas.drawLine(cx, torsoTop + dp(68f), cx, torsoBottom - dp(18f), highlight)
        canvas.drawArc(RectF(cx - dp(27f), torsoTop + dp(82f), cx - dp(2f), torsoTop + dp(116f)), 250f, 130f, false, highlight)
        canvas.drawArc(RectF(cx + dp(2f), torsoTop + dp(82f), cx + dp(27f), torsoTop + dp(116f)), 160f, 130f, false, highlight)

        linePaint.color = Color.argb(90, 19, 14, 12)
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(head.centerX() - dp(9f), head.centerY() + dp(12f), head.centerX() + dp(9f), head.centerY() + dp(12f), linePaint)
        canvas.drawLine(head.centerX() - dp(13f), head.centerY() - dp(5f), head.centerX() - dp(5f), head.centerY() - dp(5f), linePaint)
        canvas.drawLine(head.centerX() + dp(5f), head.centerY() - dp(5f), head.centerX() + dp(13f), head.centerY() - dp(5f), linePaint)

        canvas.restore()
    }

    private fun drawFog(canvas: Canvas, bounds: RectF) {
        val fog = (100 - mirrorState.clarity) / 100f
        if (fog <= 0.01f) return

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((35 + fog * 165).toInt(), 225, 233, 232)
        canvas.drawRect(bounds, paint)

        val cloudAlpha = (fog * 105).toInt()
        val clouds = listOf(
            floatArrayOf(0.15f, 0.18f, 0.25f),
            floatArrayOf(0.72f, 0.16f, 0.31f),
            floatArrayOf(0.38f, 0.47f, 0.34f),
            floatArrayOf(0.82f, 0.62f, 0.25f),
            floatArrayOf(0.22f, 0.79f, 0.32f),
            floatArrayOf(0.62f, 0.90f, 0.28f)
        )
        paint.color = Color.argb(cloudAlpha, 248, 251, 250)
        clouds.forEach { cloud ->
            canvas.drawCircle(
                bounds.left + bounds.width() * cloud[0],
                bounds.top + bounds.height() * cloud[1],
                bounds.width() * cloud[2],
                paint
            )
        }

        linePaint.color = Color.argb((fog * 150).toInt(), 255, 255, 255)
        linePaint.strokeWidth = dp(2f)
        val streakCount = max(2, (fog * 9).toInt())
        repeat(streakCount) { index ->
            val x = bounds.left + bounds.width() * ((index + 1f) / (streakCount + 1f))
            val top = bounds.top + bounds.height() * (0.06f + (index % 3) * 0.07f)
            canvas.drawLine(x, top, x - dp(3f), top + bounds.height() * (0.16f + (index % 2) * 0.08f), linePaint)
            canvas.drawCircle(x, top, dp(3f + (index % 2)), paint)
        }
    }

    private fun drawStructuralDamage(canvas: Canvas, bounds: RectF) {
        val damage = 100 - mirrorState.integrity
        if (damage <= 0) return

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((damage * 1.35f).toInt().coerceAtMost(150), 28, 28, 25)
        val spots = listOf(
            RectF(bounds.left - dp(14f), bounds.top + dp(12f), bounds.left + dp(38f), bounds.top + dp(82f)),
            RectF(bounds.right - dp(40f), bounds.top + dp(52f), bounds.right + dp(18f), bounds.top + dp(132f)),
            RectF(bounds.left - dp(20f), bounds.bottom - dp(92f), bounds.left + dp(52f), bounds.bottom + dp(10f)),
            RectF(bounds.right - dp(54f), bounds.bottom - dp(68f), bounds.right + dp(16f), bounds.bottom + dp(12f))
        )
        spots.take((1 + damage / 24).coerceAtMost(spots.size)).forEach { canvas.drawOval(it, paint) }

        if (damage < 10) return
        linePaint.color = Color.argb((95 + damage).coerceAtMost(210), 36, 39, 38)
        linePaint.strokeWidth = dp(1.1f)
        val crackCount = (1 + damage / 14).coerceAtMost(7)
        repeat(crackCount) { index ->
            val startX = if (index % 2 == 0) bounds.left else bounds.right
            val startY = bounds.top + bounds.height() * (0.13f + index * 0.105f)
            val direction = if (index % 2 == 0) 1f else -1f
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX + direction * bounds.width() * 0.13f, startY + bounds.height() * 0.05f)
                lineTo(startX + direction * bounds.width() * 0.21f, startY + bounds.height() * 0.015f)
                lineTo(startX + direction * bounds.width() * 0.31f, startY + bounds.height() * 0.09f)
            }
            canvas.drawPath(path, linePaint)
            if (damage > 38) {
                canvas.drawLine(
                    startX + direction * bounds.width() * 0.21f,
                    startY + bounds.height() * 0.015f,
                    startX + direction * bounds.width() * 0.26f,
                    startY - bounds.height() * 0.055f,
                    linePaint
                )
            }
        }

        if (damage > 65) {
            paint.color = Color.argb(((damage - 60) * 4).coerceAtMost(150), 16, 15, 14)
            val missing = Path().apply {
                moveTo(bounds.right - bounds.width() * 0.22f, bounds.bottom)
                lineTo(bounds.right, bounds.bottom - bounds.height() * 0.17f)
                lineTo(bounds.right, bounds.bottom)
                close()
            }
            canvas.drawPath(missing, paint)
        }
    }
}
