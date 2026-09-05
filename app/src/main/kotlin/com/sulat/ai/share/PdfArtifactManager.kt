package com.sulat.ai.share

import android.content.Context
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfRenderer
import java.io.File

data class ArtifactResult(
    val success: Boolean,
    val artifact: File? = null,
    val error: String? = null
)

object PdfArtifactManager {

    private const val CACHE_SUBDIR = "shared"

    /**
     * Build a deterministic internal cache filename for the canonical artifact.
     * Includes draft ID and paper size to ensure uniqueness.
     * File is placed in cacheDir/shared/ for FileProvider access.
     */
    fun buildArtifactFilename(draft: LetterDraft, paperSize: PaperSize): String {
        val safeSubject = sanitizeForFilename(draft.subject.ifEmpty {
            draft.recipients.firstOrNull()?.name ?: "Letter"
        })
        val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date(draft.createdTime))
        val paperSizeSuffix = paperSize.name
        return "Sulat-${safeSubject}-${dateStr}-${paperSizeSuffix}.pdf"
    }

    /**
     * Get the canonical artifact file path for a draft+paperSize combination.
     * This path is deterministic and can be reconstructed after Activity recreation.
     */
    fun getArtifactPath(context: Context, draft: LetterDraft, paperSize: PaperSize): File {
        val shareDir = File(context.cacheDir, CACHE_SUBDIR)
        val filename = buildArtifactFilename(draft, paperSize)
        return File(shareDir, filename)
    }

    /**
     * Ensure the canonical PDF artifact exists and is valid.
     * If valid artifact exists, return it without regenerating.
     * If invalid or missing, generate a new one.
     *
     * Single-generation invariant: PDF is rendered at most once per draft+paperSize.
     */
    fun ensurePdfArtifact(
        context: Context,
        draft: LetterDraft,
        paperSize: PaperSize
    ): ArtifactResult {
        val artifactFile = getArtifactPath(context, draft, paperSize)

        if (isValidArtifact(artifactFile, draft, paperSize)) {
            return ArtifactResult(success = true, artifact = artifactFile)
        }

        if (artifactFile.exists()) {
            artifactFile.delete()
        }

        val engine = LetterTemplateEngine()
        val layout = engine.buildLayout(draft, paperSize)

        artifactFile.parentFile?.mkdirs()

        val renderer = PdfRenderer()
        val result = renderer.renderPdf(layout, artifactFile, paperSize)

        if (!result.success) {
            return ArtifactResult(
                success = false,
                error = result.error ?: "PDF generation failed"
            )
        }

        if (result.file == null || !result.file.exists() || result.file.length() == 0L) {
            return ArtifactResult(success = false, error = "PDF file was not created or is empty")
        }

        if (!PdfRenderer.isValidPdfFile(result.file)) {
            return ArtifactResult(success = false, error = "Generated PDF is invalid")
        }

        val shareDir = File(context.cacheDir, CACHE_SUBDIR)
        if (!result.file.canonicalPath.startsWith(shareDir.canonicalPath + File.separator)) {
            return ArtifactResult(success = false, error = "PDF is not in share directory")
        }

        return ArtifactResult(success = true, artifact = result.file)
    }

    /**
     * Check if an artifact file is valid for the given draft and paper size.
     */
    fun isValidArtifact(artifact: File, draft: LetterDraft, paperSize: PaperSize): Boolean {
        if (!artifact.exists() || !artifact.isFile || artifact.length() == 0L) {
            return false
        }

        if (!PdfRenderer.isValidPdfFile(artifact)) {
            return false
        }

        val expectedPath = getArtifactPath(android.app.Application(), draft, paperSize)
        if (artifact.canonicalPath != expectedPath.canonicalPath) {
            return false
        }

        return true
    }

    /**
     * Validate artifact is inside the share directory and is a valid PDF.
     */
    fun validateArtifact(file: File, context: Context): String? {
        if (!file.exists()) {
            return "Artifact file does not exist"
        }
        if (!file.isFile) {
            return "Artifact is not a regular file"
        }
        if (file.length() == 0L) {
            return "Artifact file is empty"
        }
        if (!PdfRenderer.isValidPdfFile(file)) {
            return "Artifact is not a valid PDF"
        }

        val shareDir = File(context.cacheDir, CACHE_SUBDIR)
        val validation = ShareHelper.validateShareDirectory(file, shareDir)
        if (validation != null) {
            return validation
        }

        return null
    }

    /**
     * Delete the canonical artifact for a draft+paperSize if it exists.
     */
    fun deleteArtifact(context: Context, draft: LetterDraft, paperSize: PaperSize): Boolean {
        val artifact = getArtifactPath(context, draft, paperSize)
        return if (artifact.exists()) artifact.delete() else true
    }

    private fun sanitizeForFilename(input: String): String {
        return input
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .replace(Regex("[\\x00-\\x1f]"), "")
            .replace(Regex("\\s+"), "_")
            .trim()
            .take(50)
            .ifEmpty { "Letter" }
    }
}
