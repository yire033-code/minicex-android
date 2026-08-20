package com.example.minicex.ui.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Full-screen dim view with a transparent rectangular cutout
 * that highlights exactly the target view — no circles.
 */
class TutorialDimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000") // 80% dark
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A5B4FC")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        setShadowLayer(14f, 0f, 0f, Color.parseColor("#994F46E5"))
    }

    private var targetRect: RectF? = null
    private val cornerRadius = 22f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // required for PorterDuff.CLEAR
    }

    fun setTargetView(view: View, paddingDp: Int = 10) {
        val density = resources.displayMetrics.density
        val p = (paddingDp * density).toInt()
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        targetRect = RectF(
            (loc[0] - p).toFloat().coerceAtLeast(4f),
            (loc[1] - p).toFloat().coerceAtLeast(4f),
            (loc[0] + view.width + p).toFloat(),
            (loc[1] + view.height + p).toFloat()
        )
        invalidate()
    }

    fun clearTarget() {
        targetRect = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        targetRect?.let { rect ->
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, clearPaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        // Consumir todos los toques para que el usuario no pueda interactuar
        // con la UI subyacente durante el tutorial.
        return true
    }
}
