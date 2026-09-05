package com.sulat.ai.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

data class ShareResult(
    val success: Boolean,
    val error: String? = null,
    val pdfFile: File? = null
)

object ShareHelper {

    private const val PDF_MIME_TYPE = "application/pdf"
    private const val MAX_FILENAME_LENGTH = 100
    private const val CACHE_SUBDIR = "shared"
    const val FILE_PROVIDER_AUTHORITY = "com.sulat.ai.fileprovider"

    /**
     * Resolve the FileProvider authority from the application context.
     * Falls back to the hardcoded constant if context is unavailable.
     */
    fun resolveAuthority(context: Context): String {
        return "${context.applicationContext.packageName}.fileprovider"
    }

    /**
     * Get the expected share directory inside the app cache.
     */
    fun getShareDirectory(context: Context): File {
        return File(context.cacheDir, CACHE_SUBDIR)
    }

    /**
     * Validate that a file is inside the allowed share directory.
     * Pure JVM — no Android dependency. Returns null if valid, or an error message.
     */
    fun validateShareDirectory(file: File, shareDirectory: File): String? {
        val shareDirCanonical = shareDirectory.canonicalFile
        val fileCanonical = file.canonicalFile

        if (!fileCanonical.path.startsWith(shareDirCanonical.path + File.separator) &&
            fileCanonical != shareDirCanonical) {
            return "PDF file is not in the allowed share directory"
        }
        return null
    }

    /**
     * Generate a real PDF and share it via Android's native share sheet.
     * Uses the frozen deterministic pipeline: LetterTemplateEngine → DocumentLayout → PdfRenderer.
     */
    fun generateAndShare(
        context: Context,
        draft: LetterDraft,
        paperSize: PaperSize = PaperSize.A4
    ): ShareResult {
        val engine = LetterTemplateEngine()
        val layout = engine.buildLayout(draft, paperSize)

        val shareDir = getShareDirectory(context)
        shareDir.mkdirs()
        val outputFile = File(shareDir, sanitizeFilename(draft))

        val renderer = PdfRenderer()
        val result = renderer.renderPdf(layout, outputFile, paperSize)

        if (!result.success) {
            return ShareResult(
                success = false,
                error = result.error ?: "PDF generation failed"
            )
        }

        if (result.file == null) {
            return ShareResult(success = false, error = "PDF file was not created")
        }

        val validation = validatePdfFile(result.file)
        if (validation != null) {
            return ShareResult(success = false, error = validation)
        }

        val dirCheck = validateShareDirectory(result.file, shareDir)
        if (dirCheck != null) {
            return ShareResult(success = false, error = dirCheck)
        }

        return sharePdf(context, result.file)
    }

    /**
     * Share an existing PDF file through Android's native share sheet.
     * Validates the file, obtains a content URI via FileProvider, and launches ACTION_SEND.
     */
    fun sharePdf(context: Context, pdfFile: File): ShareResult {
        val validation = validatePdfFile(pdfFile)
        if (validation != null) {
            return ShareResult(success = false, error = validation)
        }

        return try {
            val authority = resolveAuthority(context)
            val contentUri = FileProvider.getUriForFile(
                context,
                authority,
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(shareIntent, "Share letter via")
            )

            ShareResult(success = true, pdfFile = pdfFile)
        } catch (e: IllegalArgumentException) {
            ShareResult(success = false, error = "FileProvider cannot serve this file")
        } catch (e: Exception) {
            ShareResult(success = false, error = "Share failed: ${e.message}")
        }
    }

    /**
     * Validate a PDF file for sharing. Returns null if valid, or an error message.
     */
    fun validatePdfFile(file: File): String? {
        if (!file.exists()) {
            return "PDF file does not exist"
        }
        if (!file.isFile) {
            return "Path is not a regular file"
        }
        if (file.length() == 0L) {
            return "PDF file is empty"
        }
        if (!PdfRenderer.isValidPdfFile(file)) {
            return "File is not a valid PDF"
        }
        return null
    }

    /**
     * Generate a safe, human-readable filename for a letter PDF.
     * Sanitizes all dangerous characters and enforces length limits.
     */
    fun sanitizeFilename(draft: LetterDraft): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(Date(draft.createdTime))

        val rawName = if (draft.subject.isNotEmpty()) {
            draft.subject
        } else if (draft.recipients.isNotEmpty()) {
            draft.recipients.first().name
        } else {
            "Letter"
        }

        val sanitized = rawName
            .replace(Regex("[/\\\\:*?\"<>|.]"), "")
            .replace(Regex("[\\x00-\\x1f]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val baseName = if (sanitized.isEmpty()) "Letter" else sanitized

        val full = "Sulat-$baseName-$dateStr"
        val truncated = if (full.length > MAX_FILENAME_LENGTH) {
            full.substring(0, MAX_FILENAME_LENGTH)
        } else {
            full
        }

        return "$truncated.pdf"
    }

    /**
     * The PDF MIME type constant.
     */
    fun pdfMimeType(): String = PDF_MIME_TYPE

    /**
     * The FileProvider authority constant.
     */
    fun fileProviderAuthority(): String = FILE_PROVIDER_AUTHORITY
}
