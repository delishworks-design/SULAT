package com.sulat.ai.document.envelope

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.DeterministicTextMeasurer
import com.sulat.ai.document.renderer.PdfTextStyle
import com.sulat.ai.document.renderer.TextMeasurer
import com.sulat.ai.document.renderer.TextWrapUtils
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

        // Render address — preserve explicit line breaks, no trim
        if (r.address.isNotEmpty()) {
            val wrappedLines = TextWrapUtils.wrapMultiline(
                r.address, styles.addressStyle, layout.labelMaxWidthPt, textMeasurer
            )
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
     * Uses the shared [TextWrapUtils] — TextMeasurer-based, no heuristics.
     */
    private fun wrapAddress(text: String, style: PdfTextStyle, maxWidthPt: Double): List<String> {
        if (text.isEmpty()) return emptyList()
        return TextWrapUtils.wrapTextWidthAware(text, style, maxWidthPt, textMeasurer)
    }
}
