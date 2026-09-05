package com.sulat.ai.document.renderer

import android.graphics.Paint

/**
 * Abstraction for measuring text width.
 * The calculator uses this for width-aware wrapping instead of character-count estimation.
 * The Android implementation uses Paint.measureText() for accuracy.
 * The deterministic test implementation uses a fixed average width for unit-test reproducibility.
 */
interface TextMeasurer {
    /**
     * Returns the width of [text] in points when rendered with [style].
     * The implementation must configure measurement characteristics identically
     * to how the renderer would draw the same text (typeface, size, bold/italic).
     */
    fun measureTextWidth(text: String, style: PdfTextStyle): Double

    /**
     * Returns the maximum number of characters of text styled with [role]'s style
     * that fit in [widthPt]pt.
     * Used as a starting point for character-level splitting of long unbreakable tokens.
     */
    fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int
}

/**
 * Deterministic text measurer for unit tests.
 * Uses a fixed average character width ratio (0.55 × fontSize) which is
 * consistent with monospaced-like estimation. All measurements are
 * deterministic — same input always produces same output.
 * Handles bold (1.05×) and italic (1.03×) width variations.
 */
class DeterministicTextMeasurer : TextMeasurer {

    private val charWidthRatio = 0.55

    override fun measureTextWidth(text: String, style: PdfTextStyle): Double {
        var ratio = charWidthRatio
        if (style.isBold) ratio *= 1.05
        if (style.isItalic) ratio *= 1.03
        return text.length * style.fontSizePt * ratio
    }

    override fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int {
        val style = role.style
        var ratio = charWidthRatio
        if (style.isBold) ratio *= 1.05
        if (style.isItalic) ratio *= 1.03
        val charWidth = style.fontSizePt * ratio
        return (widthPt / charWidth).toInt().coerceAtLeast(1)
    }
}

/**
 * Android Paint-based text measurer for actual PDF rendering.
 * Uses Paint.measureText() for accurate glyph-level width measurement.
 * Configures Paint.typeface identically to PdfRenderer.renderPageContent()
 * via the shared [typefaceForStyle] function.
 * This measurer is only used in the renderer layer, not in domain logic.
 */
class AndroidPdfTextMeasurer : TextMeasurer {

    private val paint = Paint().apply {
        isAntiAlias = true
    }

    override fun measureTextWidth(text: String, style: PdfTextStyle): Double {
        paint.textSize = style.fontSizePt.toFloat()
        paint.typeface = typefaceForStyle(style)
        return paint.measureText(text).toDouble()
    }

    override fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int {
        val style = role.style
        var accWidth = 0.0
        for (i in text.indices) {
            val charWidth = measureTextWidth(text.substring(i, i + 1), style)
            accWidth += charWidth
            if (accWidth > widthPt) return i.coerceAtLeast(1)
        }
        return text.length
    }
}
