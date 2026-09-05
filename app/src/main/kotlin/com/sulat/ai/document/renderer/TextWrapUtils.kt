package com.sulat.ai.document.renderer

/**
 * Reusable pure-JVM text wrapping utility.
 * Uses [TextMeasurer] for width-aware wrapping — no heuristics.
 * Same algorithm as [PdfContentCalculator] but publicly accessible.
 *
 * Deterministic whitespace policy:
 * - Source characters are never deleted during wrapping.
 * - Space tokens handle all inter-word spacing.
 * - When a space token fits on the current line, it is appended.
 * - When a space token does not fit, the current line is flushed
 *   and the space becomes leading content on the next line.
 * - Leading spaces at the start of the text are preserved on the first line.
 * - The invariant: lines.joinToString("") produces a string containing
 *   all source characters in their original order.
 *
 * Falls back to character-level splitting for tokens wider than [maxWidthPt].
 */
object TextWrapUtils {

    /**
     * A whitespace-aware token: represents either a word or a run of spaces.
     */
    private data class Token(val text: String, val isSpace: Boolean)

    /**
     * Tokenize [text] into words and space runs, preserving consecutive spaces.
     * "Hello  world" → [Token("Hello", false), Token("  ", true), Token("world", false)]
     */
    private fun tokenizePreservingSpaces(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            if (text[i] == ' ') {
                val start = i
                while (i < text.length && text[i] == ' ') i++
                tokens.add(Token(text.substring(start, i), true))
            } else {
                val start = i
                while (i < text.length && text[i] != ' ') i++
                tokens.add(Token(text.substring(start, i), false))
            }
        }
        return tokens
    }

    /**
     * Width-aware text wrapping that preserves consecutive spaces.
     *
     * @param text The text to wrap.
     * @param style The text style for measurement.
     * @param maxWidthPt The maximum width in points.
     * @param textMeasurer The text measurer for width calculations.
     * @return List of wrapped lines. All source characters are preserved in order.
     */
    fun wrapTextWidthAware(
        text: String,
        style: PdfTextStyle,
        maxWidthPt: Double,
        textMeasurer: TextMeasurer
    ): List<String> {
        if (text.isEmpty()) return listOf(text)

        val totalWidth = textMeasurer.measureTextWidth(text, style)
        if (totalWidth <= maxWidthPt) return listOf(text)

        val tokens = tokenizePreservingSpaces(text)
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        fun currentLineWidth(): Double {
            if (currentLine.isEmpty()) return 0.0
            return textMeasurer.measureTextWidth(currentLine.toString(), style)
        }

        fun flushLine() {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
            }
        }

        for (token in tokens) {
            if (token.isSpace) {
                val spaceWidth = textMeasurer.measureTextWidth(token.text, style)
                if (spaceWidth > maxWidthPt) {
                    flushLine()
                    val splitChunks = splitLongTokenUnicodeSafe(token.text, style, maxWidthPt, textMeasurer)
                    for (chunk in splitChunks) {
                        lines.add(chunk)
                    }
                } else if (currentLineWidth() + spaceWidth <= maxWidthPt) {
                    currentLine.append(token.text)
                } else {
                    flushLine()
                    currentLine.append(token.text)
                }
            } else {
                val tokenWidth = textMeasurer.measureTextWidth(token.text, style)
                if (tokenWidth > maxWidthPt) {
                    flushLine()
                    val splitChunks = splitLongTokenUnicodeSafe(token.text, style, maxWidthPt, textMeasurer)
                    for (chunk in splitChunks) {
                        lines.add(chunk)
                    }
                } else {
                    val candidate = if (currentLine.isEmpty()) {
                        token.text
                    } else {
                        currentLine.toString() + token.text
                    }
                    val candidateWidth = textMeasurer.measureTextWidth(candidate, style)
                    if (candidateWidth <= maxWidthPt) {
                        currentLine.append(token.text)
                    } else {
                        flushLine()
                        currentLine.append(token.text)
                    }
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    /**
     * Unicode-safe splitting of a long unbreakable token.
     * Uses code-point iteration to avoid splitting surrogate pairs.
     * All original characters are preserved in order.
     */
    private fun splitLongTokenUnicodeSafe(
        token: String,
        style: PdfTextStyle,
        maxWidthPt: Double,
        textMeasurer: TextMeasurer
    ): List<String> {
        if (token.isEmpty()) return emptyList()

        // Estimate max chars that fit using measured character width
        val sampleChar = token.substring(0, minOf(1, token.length))
        val charWidth = textMeasurer.measureTextWidth(sampleChar, style)
        val maxChars = if (charWidth > 0) (maxWidthPt / charWidth).toInt().coerceAtLeast(1) else token.length
        if (maxChars >= token.length) return listOf(token)

        val codePoints = mutableListOf<Int>()
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            codePoints.add(cp)
            i += Character.charCount(cp)
        }

        val result = mutableListOf<String>()
        var start = 0
        while (start < codePoints.size) {
            var end = (start + maxChars).coerceAtMost(codePoints.size)
            while (end > start + 1) {
                val chunk = buildString {
                    for (cp in codePoints.subList(start, end)) {
                        appendCodePoint(cp)
                    }
                }
                val chunkWidth = textMeasurer.measureTextWidth(chunk, style)
                if (chunkWidth <= maxWidthPt) break
                end--
            }
            if (start < codePoints.size) {
                val chunk = buildString {
                    for (cp in codePoints.subList(start, end)) {
                        appendCodePoint(cp)
                    }
                }
                result.add(chunk)
                start = end
            }
        }
        return result
    }

    /**
     * Wrap a multiline text, preserving explicit \n line breaks.
     * Each line segment is independently wrapped if too long.
     * Leading/trailing whitespace on each segment is preserved (no trim).
     * Empty segments (blank lines) are preserved.
     */
    fun wrapMultiline(
        text: String,
        style: PdfTextStyle,
        maxWidthPt: Double,
        textMeasurer: TextMeasurer
    ): List<String> {
        val segments = text.split("\n")
        val result = mutableListOf<String>()
        for (segment in segments) {
            if (segment.isEmpty()) {
                result.add("")
            } else {
                result.addAll(wrapTextWidthAware(segment, style, maxWidthPt, textMeasurer))
            }
        }
        return result
    }
}
