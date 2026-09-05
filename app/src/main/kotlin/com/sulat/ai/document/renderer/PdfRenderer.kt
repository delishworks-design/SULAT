package com.sulat.ai.document.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import java.io.File
import java.io.FileOutputStream
import kotlin.math.round

data class PdfRenderResult(
    val success: Boolean,
    val file: File? = null,
    val pageCount: Int = 0,
    val error: String? = null
)

class PdfRenderer {

    /**
     * Render a [DocumentLayout] to a real PDF file.
     * Page geometry is derived exclusively from [layout.page] (Issue 1).
     * Integer PDF dimensions are produced by rounding, not truncating (Issue 2).
     * The [paperSize] parameter is accepted only for validation; it must not override layout geometry.
     * If [paperSize] is null, no validation occurs (layout geometry is trusted unconditionally).
     */
    fun renderPdf(
        layout: DocumentLayout,
        outputFile: File,
        paperSize: PaperSize? = null
    ): PdfRenderResult {
        if (paperSize != null) {
            val widthMatch = kotlin.math.abs(paperSize.widthPt - layout.page.widthPt) < 0.01
            val heightMatch = kotlin.math.abs(paperSize.heightPt - layout.page.heightPt) < 0.01
            if (!widthMatch || !heightMatch) {
                return PdfRenderResult(
                    success = false,
                    error = "PaperSize ${paperSize.name} (${paperSize.widthPt}x${paperSize.heightPt}pt) " +
                        "does not match layout geometry (${layout.page.widthPt}x${layout.page.heightPt}pt). " +
                        "DocumentLayout is the page geometry source of truth."
                )
            }
        }

        val measurer = AndroidPdfTextMeasurer()
        val calculator = PdfContentCalculator(layout, measurer)
        val plan = calculator.plan()

        val pdfWidth = round(layout.page.widthPt).toInt()
        val pdfHeight = round(layout.page.heightPt).toInt()

        val pdfDocument = PdfDocument()
        try {
            for (renderPage in plan.pages) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    pdfWidth,
                    pdfHeight,
                    renderPage.pageNumber
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                renderPageContent(page.canvas, renderPage, layout)
                pdfDocument.finishPage(page)
            }

            outputFile.parentFile?.mkdirs()

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return PdfRenderResult(
                    success = false,
                    error = "PDF file was not written or is empty"
                )
            }

            return PdfRenderResult(
                success = true,
                file = outputFile,
                pageCount = plan.totalPages
            )
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return PdfRenderResult(
                success = false,
                error = "PDF rendering failed: ${e.message}"
            )
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Backward-compatible overload: accepts PaperSize as positional parameter.
     */
    fun renderPdf(
        layout: DocumentLayout,
        paperSize: PaperSize,
        outputFile: File
    ): PdfRenderResult {
        return renderPdf(layout, outputFile, paperSize)
    }

    private fun renderPageContent(canvas: Canvas, renderPage: RenderPage, layout: DocumentLayout) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }

        for (line in renderPage.lines) {
            val style = line.role.style
            paint.textSize = style.fontSizePt.toFloat()
            paint.isFakeBoldText = style.isBold

            paint.typeface = when {
                style.isItalic && style.isBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                style.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                style.isBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else -> Typeface.DEFAULT
            }

            canvas.drawText(
                line.text,
                layout.page.marginLeftPt.toFloat(),
                (line.yPt + style.fontSizePt).toFloat(),
                paint
            )
        }
    }

    fun renderLetterToPdf(
        draft: LetterDraft,
        outputFile: File,
        paperSize: PaperSize = PaperSize.A4
    ): PdfRenderResult {
        val engine = LetterTemplateEngine()
        val layout = engine.buildLayout(draft, paperSize)
        return renderPdf(layout, outputFile, paperSize)
    }

    companion object {
        fun isValidPdfFile(file: File): Boolean {
            if (!file.exists() || file.length() == 0L) return false
            val header = ByteArray(5)
            file.inputStream().use { it.read(header) }
            return String(header) == "%PDF-"
        }

        /**
         * Deterministic conversion from layout point dimensions to integer PDF dimensions.
         * Uses rounding rather than truncation to preserve print accuracy.
         */
        fun roundPdfDimension(ptValue: Double): Int = round(ptValue).toInt()
    }
}
