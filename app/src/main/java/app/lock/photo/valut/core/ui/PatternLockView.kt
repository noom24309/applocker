package app.lock.photo.valut.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import app.lock.photo.valut.R
import kotlin.math.hypot

/**
 * Reusable 3x3 pattern lock grid. Emits the connected node indices (0..8) via
 * [onPatternComplete]. Pure UI: it knows nothing about hashing or storage.
 */
class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Called when the user lifts their finger with at least one node selected. */
    var onPatternComplete: ((List<Int>) -> Unit)? = null

    private val selected = mutableListOf<Int>()
    private val nodeCx = FloatArray(GRID * GRID)
    private val nodeCy = FloatArray(GRID * GRID)

    private var currentX = 0f
    private var currentY = 0f
    private var tracking = false
    private var errorState = false
    private var inputEnabled = true

    // White nodes/line on a purple (primary) panel — matches the reference design.
    private val panelColor = ContextCompat.getColor(context, R.color.white)
    private val nodeColor = ContextCompat.getColor(context, R.color.primary)
    private val errorColor = ContextCompat.getColor(context, R.color.accent_red)
    private val panelRadius = dp(24f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = panelColor
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var nodeRadius = 0f
    private var hitRadius = 0f

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        if (!enabled) reset()
    }

    /** Clears the current drawing and any error state. */
    fun reset() {
        selected.clear()
        tracking = false
        errorState = false
        invalidate()
    }

    /** Flags the last entry as wrong (nodes/line turn red) until the next touch. */
    fun showError() {
        errorState = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h).toFloat()
        val cell = size / GRID
        nodeRadius = cell * 0.16f
        hitRadius = cell * 0.40f
        val offsetX = (w - size) / 2f
        val offsetY = (h - size) / 2f
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val index = row * GRID + col
                nodeCx[index] = offsetX + cell * col + cell / 2f
                nodeCy[index] = offsetY + cell * row + cell / 2f
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                tracking = true
                currentX = event.x
                currentY = event.y
                addNodeUnder(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                currentX = event.x
                currentY = event.y
                addNodeUnder(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (tracking) {
                tracking = false
                if (selected.isNotEmpty()) onPatternComplete?.invoke(selected.toList())
                invalidate()
            }
        }
        return true
    }

    private fun addNodeUnder(x: Float, y: Float) {
        for (index in nodeCx.indices) {
            if (index in selected) continue
            if (hypot(x - nodeCx[index], y - nodeCy[index]) <= hitRadius) {
                addWithMidpoint(index)
                break
            }
        }
    }

    /** Adds [index], auto-including the node in between on straight lines (standard behaviour). */
    private fun addWithMidpoint(index: Int) {
        val last = selected.lastOrNull()
        if (last != null) {
            val mid = midpointIndex(last, index)
            if (mid != -1 && mid !in selected) selected.add(mid)
        }
        selected.add(index)
    }

    private fun midpointIndex(a: Int, b: Int): Int {
        val ar = a / GRID; val ac = a % GRID
        val br = b / GRID; val bc = b % GRID
        val mr = (ar + br); val mc = (ac + bc)
        if (mr % 2 != 0 || mc % 2 != 0) return -1
        return (mr / 2) * GRID + (mc / 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Purple panel background.
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(), panelRadius, panelRadius, bgPaint
        )

        val activeColor = if (errorState) errorColor else nodeColor

        // Lines between selected nodes.
        linePaint.color = activeColor
        for (i in 0 until selected.size - 1) {
            val a = selected[i]; val b = selected[i + 1]
            canvas.drawLine(nodeCx[a], nodeCy[a], nodeCx[b], nodeCy[b], linePaint)
        }
        if (tracking && selected.isNotEmpty()) {
            val a = selected.last()
            canvas.drawLine(nodeCx[a], nodeCy[a], currentX, currentY, linePaint)
        }

        // Nodes: idle = small solid dot; selected = filled centre inside a ring.
        for (index in nodeCx.indices) {
            val isOn = index in selected
            if (isOn) {
                ringPaint.color = activeColor
                canvas.drawCircle(nodeCx[index], nodeCy[index], nodeRadius * 1.7f, ringPaint)
                nodePaint.color = activeColor
                canvas.drawCircle(nodeCx[index], nodeCy[index], nodeRadius * 0.8f, nodePaint)
            } else {
                nodePaint.color = nodeColor
                canvas.drawCircle(nodeCx[index], nodeCy[index], nodeRadius * 0.5f, nodePaint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val GRID = 3
    }
}
