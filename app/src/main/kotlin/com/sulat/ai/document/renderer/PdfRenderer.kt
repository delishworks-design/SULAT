package com.sulat.ai.document.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import java.io.File
import java.io.FileOutputStream

data class PdfRenderResult(
    val success: Boolean,
    val file: File? = null,
    val pageCount: Int = 0,
    val error: String? = null
)

class PdfRenderer {

    fun renderPdf(
        layout: DocumentLayout,
        paperSize: PaperSize,
        outputFile: File
    ): PdfRenderResult {
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()

        val pdfDocument = PdfDocument()
        try {
            for (renderPage in plan.pages) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    paperSize.widthPt.toInt(),
                    paperSize.heightPt.toInt(),
                    renderPage.pageNumber
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                renderPageContent(canvas, renderPage, layout)

                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            return PdfRenderResult(
                success = true,
                file = outputFile,
                pageCount = plan.totalPages
            )
        } catch (e: Exception) {
            return PdfRenderResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        } finally {
            pdfDocument.close()
        }
    }

    private fun renderPageContent(canvas: Canvas, renderPage: RenderPage, layout: DocumentLayout) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }

        for (line in renderPage.lines) {
            val style = line.role.style
            paint.textSize = style.fontSizePt
            paint.isFakeBoldText = style.isBold

            if (style.isItalic) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            } else if (style.isBold) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                paint.typeface = Typeface.DEFAULT
            }

            canvas.drawText(
                line.text,
                layout.page.marginLeftPt.toFloat(),
                line.yPt.toFloat() + style.fontSizePt,
                paint
            )
        }
    }

    fun renderLetterToPdf(
        draft: LetterDraft,
        paperSize: PaperSize,
        outputFile: File
    ): PdfRenderResult {
        val engine = LetterTemplateEngine()
        val layout = engine.buildLayout(draft, paperSize)
        return renderPdf(layout, paperSize, outputFile)
    }

    companion object {
        fun isValidPdfFile(file: File): Boolean {
            if (!file.exists() || file.length() == 0L) return false
            val header = ByteArray(5)
            file.inputStream().use { it.read(header) }
            return String(header) == "%PDF-"
        }
    }
}
