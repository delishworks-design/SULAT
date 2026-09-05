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
     * Returns the maximum number of Unicode code points of text styled with [role]'s style
     * that fit in [widthPt]pt.
     * Used as a starting point for character-level splitting of long unbreakable tokens.
     * Must iterate over code points, not UTF-16 code units.
     */
    fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int
}

/**
 * Count the number of Unicode code points in [text].
 * Uses code-point iteration to avoid counting surrogate pairs as two characters.
 */
fun codePointCount(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        count++
        i += Character.charCount(cp)
    }
    return count
}

/**
 * Deterministic text measurer for unit tests.
 * Uses a fixed average character width ratio (0.55 × fontSize) which is
 * consistent with monospaced-like estimation. All measurements are
 * deterministic — same input always produces same output.
 * Handles bold (1.05×) and italic (1.03×) width variations.
 * Counts Unicode code points, not UTF-16 code units.
 */
class DeterministicTextMeasurer : TextMeasurer {

    private val charWidthRatio = 0.55

    override fun measureTextWidth(text: String, style: PdfTextStyle): Double {
        var ratio = charWidthRatio
        if (style.isBold) ratio *= 1.05
        if (style.isItalic) ratio *= 1.03
        val cpCount = codePointCount(text)
        return cpCount * style.fontSizePt * ratio
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
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val charStr = text.substring(i, i + charCount)
            val charWidth = measureTextWidth(charStr, style)
            accWidth += charWidth
            if (accWidth > widthPt) {
                val codePointsBefore = codePointCount(text.substring(0, i))
                return codePointsBefore.coerceAtLeast(1)
            }
            i += charCount
        }
        return codePointCount(text)
    }
}
