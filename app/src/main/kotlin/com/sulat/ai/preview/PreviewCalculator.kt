package com.sulat.ai.preview

import com.sulat.ai.document.layout.PageGeometry
import com.sulat.ai.document.renderer.PdfTextRole
import com.sulat.ai.document.renderer.RenderPlan

/**
 * Pure JVM preview calculator that computes scale and geometry from a [RenderPlan]
 * and [PageGeometry] from the canonical DocumentLayout.
 *
 * Contains no Android framework dependencies — fully unit-testable.
 * Preview and PDF share identical sections, typography, wrapping, and pagination.
 *
 * Geometry source: DocumentLayout.page → PageGeometry.
 * No hardcoded paper dimensions. No hardcoded margins.
 */
class PreviewCalculator(
    val renderPlan: RenderPlan,
    val pageGeometry: PageGeometry,
    private val availableWidthPx: Int
) {
    val totalPages: Int get() = renderPlan.totalPages

    val documentWidthPt: Double get() = pageGeometry.widthPt

    val documentHeightPt: Double get() = pageGeometry.heightPt

    val scale: Double
        get() = availableWidthPx.toDouble() / documentWidthPt

    val pageWidthPx: Int get() = availableWidthPx

    val pageHeightPx: Int
        get() = (documentHeightPt * scale).toInt()

    val marginLeftPt: Double get() = pageGeometry.marginLeftPt

    val marginTopPt: Double get() = pageGeometry.marginTopPt

    fun textSizePx(role: PdfTextRole): Float {
        return (role.style.fontSizePt * scale).toFloat()
    }

    fun textX(): Float {
        return (marginLeftPt * scale).toFloat()
    }

    fun textY(yPt: Double): Float {
        return (yPt * scale).toFloat()
    }

    fun lineSpacingPx(role: PdfTextRole): Float {
        return (role.style.fontSizePt * role.style.lineSpacingMultiplier * scale).toFloat()
    }

    fun isBold(role: PdfTextRole): Boolean = role.style.isBold

    fun isItalic(role: PdfTextRole): Boolean = role.style.isItalic
}
