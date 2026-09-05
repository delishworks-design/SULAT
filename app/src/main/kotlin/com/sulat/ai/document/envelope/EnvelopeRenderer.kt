package com.sulat.ai.document.envelope

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.DeterministicTextMeasurer
import com.sulat.ai.document.renderer.PdfTextStyle
import com.sulat.ai.document.renderer.PdfTextRole
import com.sulat.ai.document.renderer.TextMeasurer
import com.sulat.ai.document.renderer.typefaceForStyle
import java.io.File
import java.io.FileOutputStream
import kotlin.math.round

data class EnvelopeRenderResult(
    val success: Boolean,
    val file: File? = null,
    val pageCount: Int = 0,
    val error: String? = null
)

class EnvelopeRenderer(
    private val textMeasurer: TextMeasurer = DeterministicTextMeasurer()
) {
    /**
     * Render envelope labels for all recipients in a draft.
     * Each recipient gets one PDF page.
     *
     * @param draft The letter draft containing recipients.
     * @param outputFile The output PDF file.
     * @param paperSize The paper/document size for the envelope template.
     * @return [EnvelopeRenderResult] with success status and file reference.
     */
    fun renderEnvelopePdf(
        draft: LetterDraft,
        outputFile: File,
        paperSize: PaperSize = PaperSize.A4
    ): EnvelopeRenderResult {
        val envelopeDataList = EnvelopeData.fromDraft(draft)
        if (envelopeDataList.isEmpty()) {
            return EnvelopeRenderResult(
                success = false,
                error = "No recipients found in draft"
            )
        }
        return renderEnvelopePdf(envelopeDataList, outputFile, paperSize)
    }

    /**
     * Render envelope labels for a list of recipients.
     * Each recipient gets one PDF page.
     *
     * @param recipients The list of envelope data (one per recipient).
     * @param outputFile The output PDF file.
     * @param paperSize The paper/document size for the envelope template.
     * @return [EnvelopeRenderResult] with success status and file reference.
     */
    fun renderEnvelopePdf(
        recipients: List<EnvelopeData>,
        outputFile: File,
        paperSize: PaperSize = PaperSize.A4
    ): EnvelopeRenderResult {
        if (recipients.isEmpty()) {
            return EnvelopeRenderResult(
                success = false,
                error = "No recipients provided"
            )
        }

        val layout = EnvelopeLayout.create(paperSize)
        val pdfWidth = round(layout.page.widthPt).toInt()
        val pdfHeight = round(layout.page.heightPt).toInt()
        val paint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }

        val pdfDocument = PdfDocument()
        try {
            for ((index, envelopeData) in recipients.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    pdfWidth,
                    pdfHeight,
                    index + 1
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                renderEnvelopeLabel(page.canvas, envelopeData, layout, paint)
                pdfDocument.finishPage(page)
            }

            outputFile.parentFile?.mkdirs()

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return EnvelopeRenderResult(
                    success = false,
                    error = "PDF file was not written or is empty"
                )
            }

            return EnvelopeRenderResult(
                success = true,
                file = outputFile,
                pageCount = recipients.size
            )
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return EnvelopeRenderResult(
                success = false,
                error = "Envelope rendering failed: ${e.message}"
            )
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Render a single envelope label on a canvas.
     */
    private fun renderEnvelopeLabel(
        canvas: Canvas,
        data: EnvelopeData,
        layout: EnvelopeLayout,
        paint: Paint
    ) {
        val h = data.nameHierarchy
        val r = data.recipient
        val styles = layout.styles
        var cursorY = layout.labelOriginYPt

        // Render prefix (slightly larger, bold)
        if (h.prefix.isNotEmpty()) {
            renderText(canvas, h.prefix, styles.prefixStyle, layout.labelOriginXPt, cursorY, paint)
            cursorY += lineSpacing(styles.prefixStyle)
        }

        // Render main name (bold, prominent)
        if (h.mainName.isNotEmpty()) {
            renderText(canvas, h.mainName, styles.nameStyle, layout.labelOriginXPt, cursorY, paint)
            cursorY += lineSpacing(styles.nameStyle)
        }

        // Render position
        if (r.position.isNotEmpty()) {
            val wrappedLines = wrapAddress(r.position, styles.positionStyle, layout.labelMaxWidthPt)
            for (line in wrappedLines) {
                renderText(canvas, line, styles.positionStyle, layout.labelOriginXPt, cursorY, paint)
                cursorY += lineSpacing(styles.positionStyle)
            }
        }

        // Render organization
        if (r.organization.isNotEmpty()) {
            val wrappedLines = wrapAddress(r.organization, styles.organizationStyle, layout.labelMaxWidthPt)
            for (line in wrappedLines) {
                renderText(canvas, line, styles.organizationStyle, layout.labelOriginXPt, cursorY, paint)
                cursorY += lineSpacing(styles.organizationStyle)
            }
        }

        // Render address — preserve explicit line breaks
        if (r.address.isNotEmpty()) {
            val wrappedLines = wrapMultilineAddress(r.address, styles.addressStyle, layout.labelMaxWidthPt)
            for (line in wrappedLines) {
                renderText(canvas, line, styles.addressStyle, layout.labelOriginXPt, cursorY, paint)
                cursorY += lineSpacing(styles.addressStyle)
            }
        }

        // Render optional info
        if (r.optionalInfo.isNotEmpty()) {
            val wrappedLines = wrapAddress(r.optionalInfo, styles.optionalStyle, layout.labelMaxWidthPt)
            for (line in wrappedLines) {
                renderText(canvas, line, styles.optionalStyle, layout.labelOriginXPt, cursorY, paint)
                cursorY += lineSpacing(styles.optionalStyle)
            }
        }
    }

    /**
     * Render a single line of text on the canvas.
     */
    private fun renderText(
        canvas: Canvas,
        text: String,
        style: PdfTextStyle,
        xPt: Double,
        yPt: Double,
        paint: Paint
    ) {
        paint.textSize = style.fontSizePt.toFloat()
        paint.typeface = typefaceForStyle(style)
        canvas.drawText(text, xPt.toFloat(), (yPt + style.fontSizePt).toFloat(), paint)
    }

    /**
     * Calculate the line spacing (advance) for a text style.
     */
    private fun lineSpacing(style: PdfTextStyle): Double {
        return style.fontSizePt * style.lineSpacingMultiplier
    }

    /**
     * Wrap a single-line address to fit within maxWidthPt.
     * Uses the same token-based approach as PdfContentCalculator.
     */
    private fun wrapAddress(text: String, style: PdfTextStyle, maxWidthPt: Double): List<String> {
        if (text.isEmpty()) return emptyList()
        val totalWidth = textMeasurer.measureTextWidth(text, style)
        if (totalWidth <= maxWidthPt) return listOf(text)
        return wrapTextWidthAware(text, style, maxWidthPt)
    }

    /**
     * Wrap a multiline address, preserving explicit line breaks.
     * Each line segment is independently wrapped if too long.
     */
    private fun wrapMultilineAddress(text: String, style: PdfTextStyle, maxWidthPt: Double): List<String> {
        val segments = text.split("\n")
        val result = mutableListOf<String>()
        for (segment in segments) {
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) {
                result.add("")
            } else {
                result.addAll(wrapAddress(trimmed, style, maxWidthPt))
            }
        }
        return result
    }

    /**
     * Width-aware text wrapping.
     * Tokenizes text into words and space runs, wrapping at word boundaries.
     * Falls back to character-level splitting for long unbreakable tokens.
     */
    private fun wrapTextWidthAware(text: String, style: PdfTextStyle, maxWidthPt: Double): List<String> {
        if (text.isEmpty()) return listOf(text)

        data class Token(val text: String, val isSpace: Boolean)

        fun tokenize(s: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i < s.length) {
                if (s[i] == ' ') {
                    val start = i
                    while (i < s.length && s[i] == ' ') i++
                    tokens.add(Token(s.substring(start, i), true))
                } else {
                    val start = i
                    while (i < s.length && s[i] != ' ') i++
                    tokens.add(Token(s.substring(start, i), false))
                }
            }
            return tokens
        }

        val tokens = tokenize(text)
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
                if (currentLineWidth() + spaceWidth <= maxWidthPt) {
                    currentLine.append(token.text)
                } else {
                    flushLine()
                    currentLine.append(token.text)
                }
            } else {
                val tokenWidth = textMeasurer.measureTextWidth(token.text, style)
                if (tokenWidth > maxWidthPt) {
                    flushLine()
                    val splitChunks = splitLongToken(token.text, style, maxWidthPt)
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
     */
    private fun splitLongToken(token: String, style: PdfTextStyle, maxWidthPt: Double): List<String> {
        if (token.isEmpty()) return emptyList()
        val charWidth = style.fontSizePt * 0.55 * if (style.isBold) 1.05 else 1.0
        val maxChars = (maxWidthPt / charWidth).toInt().coerceAtLeast(1)
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
}
