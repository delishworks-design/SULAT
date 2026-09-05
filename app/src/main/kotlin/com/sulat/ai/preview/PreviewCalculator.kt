package com.sulat.ai.preview

import com.sulat.ai.document.renderer.PdfTextRole
import com.sulat.ai.document.renderer.RenderPlan

/**
 * Pure JVM preview calculator that computes scale and geometry from a [RenderPlan].
 * Contains no Android framework dependencies — fully unit-testable.
 * Preview and PDF share identical sections, typography, wrapping, and pagination.
 */
class PreviewCalculator(
    val renderPlan: RenderPlan,
    private val availableWidthPx: Int
) {
    val totalPages: Int get() = renderPlan.totalPages

    private val firstPage get() = renderPlan.pages.firstOrNull()

    private val contentHeightPt: Double
        get() {
            val page = firstPage ?: return 841.89
            val maxYPt = page.lines.maxOfOrNull { it.yPt } ?: return 841.89
            val lastLine = page.lines.last()
            val lineHeight = lastLine.role.style.fontSizePt * lastLine.role.style.lineSpacingMultiplier
            return maxYPt + lineHeight
        }

    val documentWidthPt: Double get() = 595.276

    val documentHeightPt: Double get() = contentHeightPt

    val scale: Double
        get() = availableWidthPx.toDouble() / documentWidthPt

    val pageWidthPx: Int get() = availableWidthPx

    val pageHeightPx: Int
        get() = (contentHeightPt * scale).toInt()

    fun textX(marginLeftPt: Double): Float {
        return (marginLeftPt * scale).toFloat()
    }

    fun textY(yPt: Double, marginTopPt: Double): Float {
        return ((marginTopPt + yPt) * scale).toFloat()
    }

    fun textSizeSp(role: PdfTextRole): Float {
        return (role.style.fontSizePt * scale * 1.33).toFloat()
    }

    fun lineSpacingPx(role: PdfTextRole): Float {
        return (role.style.fontSizePt * role.style.lineSpacingMultiplier * scale).toFloat()
    }

    fun isBold(role: PdfTextRole): Boolean = role.style.isBold

    fun isItalic(role: PdfTextRole): Boolean = role.style.isItalic
}
