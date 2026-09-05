package com.sulat.ai.share

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfRenderer
import java.io.File
import java.io.FileInputStream

data class SaveResult(
    val success: Boolean,
    val error: String? = null,
    val savedUri: Uri? = null,
    val pdfFile: File? = null
)

object SaveHelper {

    private const val PDF_MIME_TYPE = "application/pdf"

    /**
     * Generate a real PDF and return it for saving.
     * Uses the frozen deterministic pipeline: LetterTemplateEngine → DocumentLayout → PdfRenderer.
     */
    fun generatePdf(
        context: Context,
        draft: LetterDraft,
        paperSize: PaperSize = PaperSize.A4
    ): SaveResult {
        val engine = LetterTemplateEngine()
        val layout = engine.buildLayout(draft, paperSize)

        val outputFile = File(context.cacheDir, "save_${draft.id}.pdf")

        val renderer = PdfRenderer()
        val result = renderer.renderPdf(layout, outputFile, paperSize)

        if (!result.success) {
            return SaveResult(
                success = false,
                error = result.error ?: "PDF generation failed"
            )
        }

        if (result.file == null) {
            return SaveResult(success = false, error = "PDF file was not created")
        }

        if (!PdfRenderer.isValidPdfFile(result.file)) {
            return SaveResult(success = false, error = "Generated PDF is invalid")
        }

        return SaveResult(
            success = true,
            savedUri = null,
            pdfFile = result.file
        )
    }

    /**
     * Save PDF bytes to a content URI using SAF.
     * Returns success/error result.
     */
    fun saveToUri(context: Context, pdfFile: File, uri: Uri): SaveResult {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return SaveResult(success = false, error = "PDF file is missing or empty")
        }

        if (!PdfRenderer.isValidPdfFile(pdfFile)) {
            return SaveResult(success = false, error = "PDF file is invalid")
        }

        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(pdfFile).use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            } ?: return SaveResult(success = false, error = "Could not open output stream")

            SaveResult(success = true, savedUri = uri, pdfFile = pdfFile)
        } catch (e: SecurityException) {
            SaveResult(success = false, error = "Permission denied to write file")
        } catch (e: Exception) {
            SaveResult(success = false, error = "Save failed: ${e.message}")
        }
    }

    /**
     * Build a safe filename for the letter PDF.
     */
    fun buildFilename(draft: LetterDraft): String {
        return ShareHelper.sanitizeFilename(draft)
    }

    /**
     * Create a unique filename with timestamp to avoid conflicts.
     */
    fun buildUniqueFilename(draft: LetterDraft): String {
        val base = buildFilename(draft)
        val timestamp = System.currentTimeMillis()
        val nameWithoutExt = base.removeSuffix(".pdf")
        return "${nameWithoutExt}_$timestamp.pdf"
    }
}
