package app.lock.photo.valut.core.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView with pinch-to-zoom and double-tap zoom, backed by a matrix so large
 * images aren't re-decoded. Pans while zoomed; springs back to fit at 1x.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val values = FloatArray(9)
    private val mtx = Matrix()
    private var minScale = 1f
    private val maxScale = 5f
    private var fitScale = 1f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    /** Notifies the host (pager) whether the image is currently zoomed in. */
    var onZoomChanged: ((Boolean) -> Unit)? = null

    init {
        super.setScaleType(ScaleType.MATRIX)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { fitToScreen() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToScreen()
    }

    private fun fitToScreen() {
        val drawable = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        fitScale = min(width / dw, height / dh)
        minScale = fitScale
        mtx.reset()
        mtx.postScale(fitScale, fitScale)
        mtx.postTranslate((width - dw * fitScale) / 2f, (height - dh * fitScale) / 2f)
        imageMatrix = mtx
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (isZoomed()) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun currentScale(): Float {
        mtx.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun isZoomed(): Boolean = currentScale() > fitScale * 1.05f

    private fun applyAndFix() {
        fixTranslation()
        imageMatrix = mtx
        onZoomChanged?.invoke(isZoomed())
    }

    private fun fixTranslation() {
        val rect = currentImageRect() ?: return
        var dx = 0f
        var dy = 0f
        if (rect.width() <= width) {
            dx = (width - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) dx = -rect.left
            if (rect.right < width) dx = width - rect.right
        }
        if (rect.height() <= height) {
            dy = (height - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) dy = -rect.top
            if (rect.bottom < height) dy = height - rect.bottom
        }
        mtx.postTranslate(dx, dy)
    }

    private fun currentImageRect(): RectF? {
        val drawable = drawable ?: return null
        val rect = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        mtx.mapRect(rect)
        return rect
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val current = currentScale()
            var factor = detector.scaleFactor
            val projected = current * factor
            if (projected < minScale) factor = minScale / current
            if (projected > maxScale) factor = maxScale / current
            mtx.postScale(factor, factor, detector.focusX, detector.focusY)
            applyAndFix()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (isZoomed()) fitScale else fitScale * 3f
            val factor = target / currentScale()
            mtx.postScale(factor, factor, e.x, e.y)
            applyAndFix()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            if (isZoomed()) {
                mtx.postTranslate(-distanceX, -distanceY)
                applyAndFix()
            }
            return true
        }
    }
}
