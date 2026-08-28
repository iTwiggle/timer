package com.itwiggle.randomchime

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class AccountabilityMirrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var mirrorState: MirrorState = MirrorState()
    private var marks: List<MirrorMark> = emptyList()
    private var animatedMarkId: String? = null
    private var transitionOutcome: String? = null
    private var transitionProgress = 1f
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null
    private var animationWasCancelled = false
    private val transitionQueue = ArrayDeque<MirrorTransition>()
    private var settledState: MirrorState = MirrorState()
    private var onTransitionConsumed: ((String) -> Unit)? = null
    private var onQueueDrained: (() -> Unit)? = null
    private var visibilityListener: ViewTreeObserver.OnScrollChangedListener? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val cursiveTypeface = Typeface.create("cursive", Typeface.ITALIC)
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private data class PlacedMark(
        val mark: MirrorMark,
        val slot: RectF,
        val rotation: Float
    )

    private fun dp(value: Float) = value * density

    fun setMirrorContent(
        state: MirrorState,
        recentMarks: List<MirrorMark>,
        transitions: List<MirrorTransition> = emptyList(),
        onTransitionConsumed: ((String) -> Unit)? = null,
        onQueueDrained: (() -> Unit)? = null
    ) {
        animator?.cancel()
        animator = null
        marks = recentMarks
        settledState = state
        this.onTransitionConsumed = onTransitionConsumed
        this.onQueueDrained = onQueueDrained

        transitionQueue.clear()
        transitions
            .filter { pending -> recentMarks.any { it.id == pending.markId } }
            .forEach { transitionQueue.addLast(it) }

        animatedMarkId = null
        transitionOutcome = null
        if (transitionQueue.isEmpty()) {
            mirrorState = state
            transitionProgress = 1f
            animationProgress = 1f
        } else {
            // Hold the pre-outcome state. Nothing animates until the mirror is
            // actually on screen, so a replay can never be missed.
            mirrorState = transitionQueue.first().from
            transitionProgress = 0f
            animationProgress = 0f
        }
        updateContentDescription()
        invalidate()
        maybeStartNextTransition()
    }

    private fun updateContentDescription() {
        val done = marks.count { it.isDone }
        val skipped = marks.size - done
        contentDescription =
            "Accountability Mirror. Clarity ${mirrorState.clarity} percent. " +
                "Integrity ${mirrorState.integrity} percent. $done completed marks and $skipped skipped marks visible."
    }

    private fun visibleFraction(): Float {
        if (!isShown || height <= 0) return 0f
        val rect = Rect()
        if (!getGlobalVisibleRect(rect)) return 0f
        return (rect.height().toFloat() / height.toFloat()).coerceIn(0f, 1f)
    }

    private fun maybeStartNextTransition() {
        if (animator?.isRunning == true) return
        val next = transitionQueue.firstOrNull()
        if (next == null) {
            if (mirrorState != settledState) {
                mirrorState = settledState
                transitionProgress = 1f
                animationProgress = 1f
                updateContentDescription()
                invalidate()
            }
            return
        }
        if (visibleFraction() < 0.6f) return
        transitionQueue.removeFirst()
        startMirrorAnimation(next)
    }

    private fun startMirrorAnimation(transition: MirrorTransition) {
        animator?.cancel()
        val mark = marks.firstOrNull { it.id == transition.markId }
        if (mark == null) {
            onTransitionConsumed?.invoke(transition.markId)
            maybeStartNextTransition()
            return
        }
        val from = transition.from
        val to = transition.to
        val outcome = transition.outcome
        animatedMarkId = transition.markId
        transitionOutcome = outcome
        mirrorState = from
        transitionProgress = 0f
        animationProgress = 0f
        animationWasCancelled = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (outcome) {
                "done" -> 1_650L
                "snoozed" -> 1_350L
                "skipped" -> 950L
                else -> if (mark.isDone) 1_400L else 1_150L
            }
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                transitionProgress = progress
                mirrorState = interpolateState(from, to, progress)
                animationProgress = when (outcome) {
                    "done" -> ((progress - 0.18f) / 0.62f).coerceIn(0f, 1f)
                    "snoozed" -> ((progress - 0.42f) / 0.48f).coerceIn(0f, 1f)
                    "skipped" -> ((progress - 0.14f) / 0.50f).coerceIn(0f, 1f)
                    else -> progress
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    post {
                        performHapticFeedback(
                            if (outcome == "skipped") HapticFeedbackConstants.LONG_PRESS
                            else HapticFeedbackConstants.CLOCK_TICK
                        )
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    animationWasCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    // Settle on the destination state either way: a cancelled
                    // animation must never strand the mirror mid-interpolation.
                    mirrorState = to
                    transitionProgress = 1f
                    animationProgress = 1f
                    transitionOutcome = null
                    animatedMarkId = null
                    updateContentDescription()
                    invalidate()
                    if (animationWasCancelled) return
                    onTransitionConsumed?.invoke(transition.markId)
                    if (transitionQueue.isEmpty()) {
                        onQueueDrained?.invoke()
                    } else {
                        post { maybeStartNextTransition() }
                    }
                }
            })
            start()
        }
    }

    private fun interpolateState(from: MirrorState, to: MirrorState, progress: Float) = MirrorState(
        clarity = lerp(from.clarity, to.clarity, progress),
        integrity = lerp(from.integrity, to.integrity, progress),
        doneStreak = if (progress < 1f) from.doneStreak else to.doneStreak,
        snoozeStreak = if (progress < 1f) from.snoozeStreak else to.snoozeStreak
    )

    private fun lerp(from: Int, to: Int, progress: Float) =
        (from + (to - from) * progress).toInt().coerceIn(0, 100)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val listener = ViewTreeObserver.OnScrollChangedListener { maybeStartNextTransition() }
        visibilityListener = listener
        viewTreeObserver.addOnScrollChangedListener(listener)
        maybeStartNextTransition()
    }

    override fun onDetachedFromWindow() {
        visibilityListener?.let { viewTreeObserver.removeOnScrollChangedListener(it) }
        visibilityListener = null
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) maybeStartNextTransition()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maybeStartNextTransition()
    }

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
        val inner = RectF(
            outer.left + dp(12f),
            outer.top + dp(12f),
            outer.right - dp(12f),
            outer.bottom - dp(12f)
        )

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(38, 34, 30)
        canvas.drawRoundRect(outer, dp(18f), dp(18f), paint)

        paint.color = Color.rgb(193, 203, 199)
        canvas.drawRoundRect(inner, dp(10f), dp(10f), paint)

        val clip = Path().apply {
            addRoundRect(inner, dp(10f), dp(10f), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        drawReflection(canvas, inner)
        drawMirrorSurface(canvas, inner)
        drawStructuralDamage(canvas, inner)
        drawOutcomeMotion(canvas, inner)
        canvas.restore()

        linePaint.color = Color.argb(145, 255, 255, 255)
        linePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(inner, dp(10f), dp(10f), linePaint)
    }

    private fun drawOutcomeMotion(canvas: Canvas, bounds: RectF) {
        val outcome = transitionOutcome ?: return
        if (transitionProgress >= 1f) return
        when (outcome) {
            "done" -> {
                val pulse = sin(Math.PI * transitionProgress).toFloat().coerceAtLeast(0f)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = dp(2.2f)
                linePaint.color = Color.argb((pulse * 105).toInt(), 225, 247, 239)
                val radius = bounds.width() * (0.12f + transitionProgress * 0.58f)
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, linePaint)
            }
            "snoozed" -> {
                val edgeY = bounds.top + bounds.height() * transitionProgress
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(28, 225, 233, 232)
                canvas.drawRect(bounds.left, bounds.top, bounds.right, edgeY, paint)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = dp(4f)
                linePaint.color = Color.argb(75, 246, 250, 249)
                canvas.drawLine(bounds.left, edgeY, bounds.right, edgeY, linePaint)
            }
            "skipped" -> {
                val pulse = (1f - transitionProgress).coerceIn(0f, 1f)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((pulse * 85).toInt(), 74, 18, 25)
                canvas.drawRect(bounds, paint)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = dp(2.4f)
                linePaint.color = Color.argb((pulse * 180).toInt(), 88, 28, 34)
                val reach = bounds.width() * min(0.34f, transitionProgress * 0.52f)
                canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.centerX() - reach, bounds.top, linePaint)
                canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.centerX() + reach, bounds.bottom, linePaint)
            }
        }
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
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 20, 14, 12)
        }

        val headTop = bounds.top + bounds.height() * 0.12f
        val head = RectF(cx - dp(31f), headTop, cx + dp(31f), headTop + dp(68f))
        canvas.drawOval(head, skin)
        canvas.drawOval(
            RectF(head.left - dp(5f), head.centerY() - dp(8f), head.left + dp(5f), head.centerY() + dp(10f)),
            skin
        )
        canvas.drawOval(
            RectF(head.right - dp(5f), head.centerY() - dp(8f), head.right + dp(5f), head.centerY() + dp(10f)),
            skin
        )

        paint.color = Color.rgb(26, 20, 18)
        canvas.drawArc(
            RectF(head.left, head.top - dp(4f), head.right, head.bottom - dp(18f)),
            185f,
            170f,
            true,
            paint
        )

        val neck = RectF(cx - dp(18f), head.bottom - dp(5f), cx + dp(18f), head.bottom + dp(32f))
        canvas.drawRoundRect(neck, dp(8f), dp(8f), skin)

        val torsoTop = neck.bottom - dp(4f)
        val torsoBottom = baseY
        val torso = Path().apply {
            moveTo(cx - dp(19f), torsoTop)
            cubicTo(
                cx - dp(58f),
                torsoTop + dp(2f),
                cx - dp(86f),
                torsoTop + dp(34f),
                cx - dp(72f),
                torsoTop + dp(78f)
            )
            lineTo(cx - dp(50f), torsoBottom)
            lineTo(cx + dp(50f), torsoBottom)
            lineTo(cx + dp(72f), torsoTop + dp(78f))
            cubicTo(
                cx + dp(86f),
                torsoTop + dp(34f),
                cx + dp(58f),
                torsoTop + dp(2f),
                cx + dp(19f),
                torsoTop
            )
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
        canvas.drawArc(
            RectF(cx - dp(27f), torsoTop + dp(82f), cx - dp(2f), torsoTop + dp(116f)),
            250f,
            130f,
            false,
            highlight
        )
        canvas.drawArc(
            RectF(cx + dp(2f), torsoTop + dp(82f), cx + dp(27f), torsoTop + dp(116f)),
            160f,
            130f,
            false,
            highlight
        )

        linePaint.color = Color.argb(90, 19, 14, 12)
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(
            head.centerX() - dp(9f),
            head.centerY() + dp(12f),
            head.centerX() + dp(9f),
            head.centerY() + dp(12f),
            linePaint
        )
        canvas.drawLine(
            head.centerX() - dp(13f),
            head.centerY() - dp(5f),
            head.centerX() - dp(5f),
            head.centerY() - dp(5f),
            linePaint
        )
        canvas.drawLine(
            head.centerX() + dp(5f),
            head.centerY() - dp(5f),
            head.centerX() + dp(13f),
            head.centerY() - dp(5f),
            linePaint
        )

        canvas.restore()
    }

    private fun drawMirrorSurface(canvas: Canvas, bounds: RectF) {
        val placements = layoutMarks(bounds)
        val fog = (100 - mirrorState.clarity) / 100f
        val fingerMedium = mirrorState.clarity < 72

        if (fog > 0.01f) {
            val layer = canvas.saveLayer(bounds, null)
            drawFogLayer(canvas, bounds, fog)
            if (fingerMedium) drawMarks(canvas, placements, Medium.FINGER)
            canvas.restoreToCount(layer)
        }

        if (fingerMedium) {
            drawFingerRims(canvas, placements, fog)
        } else {
            drawMarks(canvas, placements, Medium.LIPSTICK)
        }
    }

    private fun drawFogLayer(canvas: Canvas, bounds: RectF, fog: Float) {
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
            canvas.drawLine(
                x,
                top,
                x - dp(3f),
                top + bounds.height() * (0.16f + (index % 2) * 0.08f),
                linePaint
            )
            canvas.drawCircle(x, top, dp(3f + (index % 2)), paint)
        }
    }

    private enum class Medium { FINGER, LIPSTICK }

    private fun drawMarks(canvas: Canvas, placements: List<PlacedMark>, medium: Medium) {
        placements.forEach { placed ->
            val progress =
                if (placed.mark.id == animatedMarkId) animationProgress.coerceIn(0f, 1f) else 1f
            canvas.save()
            canvas.rotate(placed.rotation, placed.slot.centerX(), placed.slot.centerY())
            if (placed.mark.isDone) {
                drawCheck(canvas, placed.slot, progress, medium)
            } else {
                drawSkipped(canvas, placed.slot, progress, medium)
            }
            canvas.restore()
        }
    }

    private fun drawCheck(canvas: Canvas, slot: RectF, progress: Float, medium: Medium) {
        val width = min(slot.width() * 0.58f, dp(52f))
        val height = min(slot.height() * 0.52f, dp(38f))
        val left = slot.centerX() - width / 2f
        val top = slot.centerY() - height / 2f
        val path = Path().apply {
            moveTo(left, top + height * 0.52f)
            lineTo(left + width * 0.31f, top + height)
            cubicTo(
                left + width * 0.48f,
                top + height * 0.74f,
                left + width * 0.72f,
                top + height * 0.28f,
                left + width,
                top
            )
        }
        val segment = Path()
        val measure = PathMeasure(path, false)
        measure.getSegment(0f, measure.length * progress, segment, true)

        val targetPaint = if (medium == Medium.FINGER) clearPaint else linePaint
        targetPaint.style = Paint.Style.STROKE
        targetPaint.strokeWidth = if (medium == Medium.FINGER) dp(7.5f) else dp(3.4f)
        if (medium == Medium.LIPSTICK) {
            targetPaint.color = Color.argb(220, 137, 30, 62)
        }
        canvas.drawPath(segment, targetPaint)
    }

    private fun drawSkipped(canvas: Canvas, slot: RectF, progress: Float, medium: Medium) {
        val targetPaint = if (medium == Medium.FINGER) clearPaint else paint
        targetPaint.style = Paint.Style.FILL
        targetPaint.typeface = cursiveTypeface
        targetPaint.textSize = skippedTextSize(slot, targetPaint)
        if (medium == Medium.LIPSTICK) {
            targetPaint.color = Color.argb(215, 137, 30, 62)
        }

        val metrics = targetPaint.fontMetrics
        val baseline = slot.centerY() - (metrics.ascent + metrics.descent) / 2f
        val textWidth = targetPaint.measureText("skipped")
        val left = slot.centerX() - textWidth / 2f

        canvas.save()
        canvas.clipRect(
            slot.left,
            slot.top,
            slot.left + slot.width() * progress.coerceIn(0f, 1f),
            slot.bottom
        )
        canvas.drawText("skipped", left, baseline, targetPaint)
        canvas.restore()
    }

    private fun skippedTextSize(slot: RectF, targetPaint: Paint): Float {
        val preferred = min(dp(27f), slot.height() * 0.46f)
        targetPaint.textSize = preferred
        val width = targetPaint.measureText("skipped").coerceAtLeast(1f)
        return preferred * min(1f, slot.width() * 0.9f / width)
    }

    private fun drawFingerRims(canvas: Canvas, placements: List<PlacedMark>, fog: Float) {
        if (fog <= 0.02f) return
        placements.forEach { placed ->
            val progress =
                if (placed.mark.id == animatedMarkId) animationProgress.coerceIn(0f, 1f) else 1f
            canvas.save()
            canvas.rotate(placed.rotation, placed.slot.centerX(), placed.slot.centerY())
            if (placed.mark.isDone) {
                val width = min(placed.slot.width() * 0.58f, dp(52f))
                val height = min(placed.slot.height() * 0.52f, dp(38f))
                val left = placed.slot.centerX() - width / 2f
                val top = placed.slot.centerY() - height / 2f
                val path = Path().apply {
                    moveTo(left, top + height * 0.52f)
                    lineTo(left + width * 0.31f, top + height)
                    cubicTo(
                        left + width * 0.48f,
                        top + height * 0.74f,
                        left + width * 0.72f,
                        top + height * 0.28f,
                        left + width,
                        top
                    )
                }
                val segment = Path()
                val measure = PathMeasure(path, false)
                measure.getSegment(0f, measure.length * progress, segment, true)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = dp(1.1f)
                linePaint.color = Color.argb((40 + fog * 55).toInt(), 255, 255, 255)
                canvas.drawPath(segment, linePaint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1f)
                paint.typeface = cursiveTypeface
                paint.textSize = skippedTextSize(placed.slot, paint)
                paint.color = Color.argb((36 + fog * 48).toInt(), 255, 255, 255)
                val metrics = paint.fontMetrics
                val baseline = placed.slot.centerY() - (metrics.ascent + metrics.descent) / 2f
                val textWidth = paint.measureText("skipped")
                val left = placed.slot.centerX() - textWidth / 2f
                canvas.save()
                canvas.clipRect(
                    placed.slot.left,
                    placed.slot.top,
                    placed.slot.left + placed.slot.width() * progress,
                    placed.slot.bottom
                )
                canvas.drawText("skipped", left, baseline, paint)
                canvas.restore()
            }
            canvas.restore()
        }
    }

    private fun layoutMarks(bounds: RectF): List<PlacedMark> {
        if (marks.isEmpty()) return emptyList()

        val targetArea = dp(104f) * dp(72f)
        val capacity = ((bounds.width() * bounds.height()) / targetArea)
            .toInt()
            .coerceIn(5, 14)
        val visible = marks.take(capacity)
        val columns = if (capacity <= 6 || bounds.width() < dp(235f)) 2 else 3
        val rows = ceil(capacity / columns.toFloat()).toInt()
        val cellWidth = bounds.width() / columns
        val cellHeight = bounds.height() / rows
        val slots = mutableListOf<RectF>()

        repeat(rows) { row ->
            repeat(columns) { column ->
                if (slots.size >= capacity) return@repeat
                slots += RectF(
                    bounds.left + column * cellWidth + dp(5f),
                    bounds.top + row * cellHeight + dp(5f),
                    bounds.left + (column + 1) * cellWidth - dp(5f),
                    bounds.top + (row + 1) * cellHeight - dp(5f)
                )
            }
        }

        val used = BooleanArray(slots.size)
        val placed = mutableListOf<PlacedMark>()
        visible.reversed().forEach { mark ->
            val seed = mark.id.hashCode() and Int.MAX_VALUE
            var slotIndex = seed % slots.size
            while (used[slotIndex]) slotIndex = (slotIndex + 1) % slots.size
            used[slotIndex] = true

            val source = slots[slotIndex]
            val offsetX = (((seed / 17) % 13) - 6) / 100f * source.width()
            val offsetY = (((seed / 31) % 11) - 5) / 100f * source.height()
            val slot = RectF(source).apply { offset(offsetX, offsetY) }
            val rotation = (((seed / 47) % 17) - 8).toFloat()
            placed += PlacedMark(mark, slot, rotation)
        }
        return placed
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
        spots.take((1 + damage / 24).coerceAtMost(spots.size)).forEach {
            canvas.drawOval(it, paint)
        }

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
