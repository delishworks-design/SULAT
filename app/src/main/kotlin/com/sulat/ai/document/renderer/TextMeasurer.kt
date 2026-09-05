package com.sulat.ai.document.renderer

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Abstraction for measuring text width at a given font size and weight.
 * The calculator uses this for width-aware wrapping instead of character-count estimation.
 * The Android implementation uses Paint.measureText() for accuracy.
 * The deterministic test implementation uses a fixed average width for unit-test reproducibility.
 */
interface TextMeasurer {
    /**
     * Returns the width of [text] in points when rendered at [fontSizePt]pt.
     */
    fun measureTextWidth(text: String, fontSizePt: Double, isBold: Boolean): Double

    /**
     * Returns the maximum number of characters of [role]-styled text that fit in [widthPt]pt.
     * This is only used as a fallback for extremely long unbreakable tokens
     * when character-level splitting is needed.
     */
    fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int
}

/**
 * Deterministic text measurer for unit tests.
 * Uses a fixed average character width ratio (0.55 × fontSize) which is
 * consistent with monospaced-like estimation. All measurements are
 * deterministic — same input always produces same output.
 */
class DeterministicTextMeasurer : TextMeasurer {

    private val charWidthRatio = 0.55

    override fun measureTextWidth(text: String, fontSizePt: Double, isBold: Boolean): Double {
        val effectiveRatio = if (isBold) charWidthRatio * 1.05 else charWidthRatio
        return text.length * fontSizePt * effectiveRatio
    }

    override fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int {
        val style = role.style
        val charWidth = style.fontSizePt * charWidthRatio * (if (style.isBold) 1.05 else 1.0)
        return (widthPt / charWidth).toInt().coerceAtLeast(1)
    }
}

/**
 * Android Paint-based text measurer for actual PDF rendering.
 * Uses Paint.measureText() for accurate glyph-level width measurement.
 * This measurer is only used in the renderer layer, not in domain logic.
 */
class AndroidPdfTextMeasurer : TextMeasurer {

    private val paint = Paint().apply {
        isAntiAlias = true
    }

    override fun measureTextWidth(text: String, fontSizePt: Double, isBold: Boolean): Double {
        paint.textSize = fontSizePt.toFloat()
        paint.typeface = if (isBold) {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        } else {
            Typeface.DEFAULT
        }
        return paint.measureText(text).toDouble()
    }

    override fun estimateMaxCharsForWidth(text: String, role: PdfTextRole, widthPt: Double): Int {
        val style = role.style
        var accWidth = 0.0
        for (i in text.indices) {
            val charWidth = measureTextWidth(text.substring(i, i + 1), style.fontSizePt, style.isBold)
            accWidth += charWidth
            if (accWidth > widthPt) return i.coerceAtLeast(1)
        }
        return text.length
    }
}
