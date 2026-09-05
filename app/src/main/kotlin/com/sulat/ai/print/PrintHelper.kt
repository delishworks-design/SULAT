package com.sulat.ai.print

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfRenderer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class PrintResult(
    val success: Boolean,
    val error: String? = null,
    val pdfFile: File? = null
)

object PrintHelper {

    private const val PRINT_JOB_PREFIX = "Sulat-Letter"

    /**
     * Generate a real PDF file from a LetterDraft using the frozen deterministic pipeline.
     * Returns a [PrintResult] with the generated PDF file or an error.
     */
    fun generatePrintPdf(
        context: Context,
        draft: LetterDraft,
        paperSize: PaperSize = PaperSize.A4
    ): PrintResult {
        val engine = LetterTemplateEngine()
        val layout = engine.buildLayout(draft, paperSize)

        val outputFile = File(context.cacheDir, "print_${draft.id}.pdf")

        val renderer = PdfRenderer()
        val result = renderer.renderPdf(layout, outputFile, paperSize)

        if (!result.success) {
            return PrintResult(
                success = false,
                error = result.error ?: "PDF generation failed"
            )
        }

        if (result.file == null || !PdfRenderer.isValidPdfFile(result.file)) {
            return PrintResult(
                success = false,
                error = "Generated PDF is invalid"
            )
        }

        return PrintResult(
            success = true,
            pdfFile = result.file
        )
    }

    /**
     * Launch the Android Print Framework with the generated PDF.
     * The PDF is produced by the frozen deterministic pipeline, not drawn manually.
     */
    fun printDocument(
        context: Context,
        draft: LetterDraft,
        paperSize: PaperSize = PaperSize.A4
    ): PrintResult {
        val printResult = generatePrintPdf(context, draft, paperSize)
        if (!printResult.success) {
            return printResult
        }

        val pdfFile = printResult.pdfFile!!
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "$PRINT_JOB_PREFIX-${draft.id.take(8)}"

        val adapter = PdfPrintDocumentAdapter(pdfFile, jobName)
        val attributes = buildPrintAttributes(paperSize)
        printManager.print(jobName, adapter, attributes)

        return printResult
    }

    /**
     * Build [PrintAttributes] for a given [PaperSize].
     * Maps canonical paper sizes to Android print media sizes.
     */
    fun buildPrintAttributes(paperSize: PaperSize): PrintAttributes {
        val mediaSize = when (paperSize) {
            PaperSize.A4 -> PrintAttributes.MediaSize(
                "ISO_A4", "A4",
                8268, 11693  // 210mm × 297mm in mils (1/1000 inch)
            )
            PaperSize.ShortBond -> PrintAttributes.MediaSize(
                "NA_LETTER", "Letter",
                8500, 11000  // 8.5in × 11in in mils
            )
            PaperSize.Legal -> PrintAttributes.MediaSize(
                "NA_LEGAL", "Legal",
                8500, 14000  // 8.5in × 14in in mils
            )
            PaperSize.LongBond -> PrintAttributes.MediaSize(
                "LONG_BOND", "Long Bond",
                8500, 13000  // 8.5in × 13in in mils
            )
        }

        return PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setResolution(
                PrintAttributes.Resolution("pdf", "PDF", 300, 300)
            )
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    }

    /**
     * Read the page count from a PDF file by parsing the PDF trailer.
     * Falls back to counting page objects if trailer parsing fails.
     * Returns -1 if the PDF cannot be read.
     */
    fun readPdfPageCount(pdfFile: File): Int {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return -1

        return try {
            val content = pdfFile.readBytes()
            val contentStr = String(content, Charsets.US_ASCII)

            // Try trailer-based page count first (/N in cross-reference trailer)
            val trailerRegex = Regex("""(\d+)\s+\d+\s+obj\s*<<[^>]*\/Type\s*\/Catalog[^>]*>>""")
            val catalogMatch = trailerRegex.find(contentStr)

            if (catalogMatch != null) {
                val catalogObjNum = catalogMatch.groupValues[1]
                val pagesRegex = Regex(
                    """$catalogObjNum\s+\d+\s+obj[^<]*<<[^>]*\/Type\s*\/Pages[^>]*\/Count\s+(\d+)"""
                )
                val pagesMatch = pagesRegex.find(contentStr)
                if (pagesMatch != null) {
                    return pagesMatch.groupValues[1].toIntOrNull() ?: -1
                }
            }

            // Fallback: count /Type /Page entries (not /Pages)
            val pageCount = Regex("""/Type\s*/Page(?!s)""").findAll(contentStr).count()
            if (pageCount > 0) pageCount else -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Build a human-readable print document name.
     */
    fun buildDocumentName(draft: LetterDraft): String {
        val subject = if (draft.subject.isNotEmpty()) draft.subject else "Letter"
        return "$PRINT_JOB_PREFIX - $subject"
    }

    /**
     * Clean up temporary print PDF files.
     */
    fun cleanupPrintPdf(context: Context, draftId: String) {
        File(context.cacheDir, "print_${draftId}.pdf").delete()
    }
}

/**
 * A [PrintDocumentAdapter] that reads an existing PDF file and streams it
 * to the print destination. Does NOT recreate or re-render the letter.
 *
 * This adapter consumes the PDF produced by [PdfRenderer] via the frozen
 * deterministic pipeline. The onWrite() method copies PDF bytes directly
 * without modification.
 */
class PdfPrintDocumentAdapter(
    private val pdfFile: File,
    private val jobName: String
) : PrintDocumentAdapter() {

    private var pageCount: Int = 0

    init {
        pageCount = PrintHelper.readPdfPageCount(pdfFile)
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        if (!pdfFile.exists() || pageCount <= 0) {
            callback?.onLayoutFailed("PDF file is missing or has no pages")
            return
        }

        val info = PrintDocumentInfo.Builder(jobName)
            .setPageCount(pageCount)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            closeQuietly(destination)
            return
        }

        if (!pdfFile.exists()) {
            callback?.onWriteFailed("PDF file not found")
            closeQuietly(destination)
            return
        }

        try {
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            closeQuietly(destination)
                            return
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback?.onWriteFailed("Write failed: ${e.message}")
        } catch (e: Exception) {
            callback?.onWriteFailed("Unexpected error: ${e.message}")
        } finally {
            closeQuietly(destination)
        }
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor) {
        try {
            pfd.close()
        } catch (_: IOException) { }
    }
}
