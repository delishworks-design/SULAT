package com.sulat.ai.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.sulat.ai.document.renderer.typefaceForStyle

/**
 * Custom View that renders a single preview page using [PreviewCalculator].
 * Draws text lines at the exact coordinates computed by the deterministic pipeline.
 * Uses DocumentLayout.page geometry — no hardcoded margins or font conversions.
 */
class LetterPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var calculator: PreviewCalculator? = null
    private var pageIndex: Int = 0

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    fun setPreviewData(calculator: PreviewCalculator, pageIndex: Int) {
        this.calculator = calculator
        this.pageIndex = pageIndex
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val calc = calculator
        if (calc == null) {
            setMeasuredDimension(0, 0)
            return
        }
        setMeasuredDimension(calc.pageWidthPx, calc.pageHeightPx)
    }

    override fun onDraw(canvas: Canvas) {
        val calc = calculator ?: return
        val page = calc.renderPlan.pages.getOrNull(pageIndex) ?: return

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        for (line in page.lines) {
            val role = line.role
            val style = role.style

            textPaint.textSize = calc.textSizePx(role)
            textPaint.typeface = typefaceForStyle(style)

            val x = calc.textX()
            val y = calc.textY(line.yPt)

            canvas.drawText(line.text, x, y, textPaint)
        }
    }
}
