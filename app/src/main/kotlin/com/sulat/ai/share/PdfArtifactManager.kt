package com.sulat.ai.share

import android.content.Context
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfRenderer
import java.io.File
import java.security.MessageDigest

data class ArtifactResult(
    val success: Boolean,
    val artifact: File? = null,
    val error: String? = null
)

object PdfArtifactManager {

    private const val CACHE_SUBDIR = "shared"
    private const val SIDECAR_EXTENSION = ".fp"

    /**
     * Build a deterministic internal cache filename for the canonical artifact.
     * Includes draft ID and paper size to ensure uniqueness.
     * File is placed in cacheDir/shared/ for FileProvider access.
     */
    fun buildArtifactFilename(draft: LetterDraft, paperSize: PaperSize): String {
        val safeSubject = sanitizeForFilename(draft.subject.ifEmpty {
            draft.recipients.firstOrNull()?.name ?: "Letter"
        })
        val safeDraftId = sanitizeForFilename(draft.id)
        val paperSizeSuffix = paperSize.name
        return "Sulat-${safeDraftId}-${safeSubject}-${paperSizeSuffix}.pdf"
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
     * Single-generation invariant: PDF is rendered at most once per (draft content,
     * draft identity, paperSize). Validity is verified against a content fingerprint
     * sidecar file written next to the PDF; legacy PDFs without a sidecar are
     * treated as invalid and regenerated.
     */
    fun ensurePdfArtifact(
        context: Context,
        draft: LetterDraft,
        paperSize: PaperSize
    ): ArtifactResult {
        val artifactFile = getArtifactPath(context, draft, paperSize)

        if (isValidArtifact(context, artifactFile, draft, paperSize)) {
            return ArtifactResult(success = true, artifact = artifactFile)
        }

        // Any invalid or stale state: drop both the PDF and any orphan sidecar
        // before regenerating.
        deleteArtifactAndSidecar(artifactFile)

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

        // Write the content fingerprint sidecar. If this fails, remove the PDF
        // and report failure — we never leave an artifact without a sidecar.
        val sidecarFile = sidecarFor(artifactFile)
        try {
            sidecarFile.writeText(computeContentFingerprint(draft, paperSize))
        } catch (e: Exception) {
            deleteArtifactAndSidecar(artifactFile)
            return ArtifactResult(
                success = false,
                error = "Failed to write artifact fingerprint sidecar: ${e.message}"
            )
        }

        return ArtifactResult(success = true, artifact = result.file)
    }

    /**
     * Check if an artifact file is valid for the given draft and paper size.
     * Uses the provided context to calculate the expected path, and verifies the
     * content fingerprint sidecar matches the supplied draft.
     */
    fun isValidArtifact(context: Context, artifact: File, draft: LetterDraft, paperSize: PaperSize): Boolean {
        if (!artifact.exists() || !artifact.isFile || artifact.length() == 0L) {
            return false
        }

        if (!PdfRenderer.isValidPdfFile(artifact)) {
            return false
        }

        val expectedPath = getArtifactPath(context, draft, paperSize)
        if (artifact.canonicalPath != expectedPath.canonicalPath) {
            return false
        }

        val sidecarFile = sidecarFor(artifact)
        val stored = readFingerprint(sidecarFile) ?: return false
        val expected = computeContentFingerprint(draft, paperSize)
        return stored == expected
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
     * Delete the canonical artifact (and its fingerprint sidecar, if any) for a
     * draft+paperSize if it exists.
     */
    fun deleteArtifact(context: Context, draft: LetterDraft, paperSize: PaperSize): Boolean {
        val artifact = getArtifactPath(context, draft, paperSize)
        return deleteArtifactAndSidecar(artifact)
    }

    /**
     * Compute a deterministic SHA-256 content fingerprint over the exact set of
     * fields consumed by LetterTemplateEngine.buildLayout + the paper size.
     *
     * Render-driving inputs (verified against LetterTemplateEngine.kt and
     * PdfContentCalculator.kt):
     *   - paperSize.widthPt, paperSize.heightPt
     *   - draft.dates[*].date.time, in iteration order
     *   - draft.recipients[*].name, .position, .organization, .address, .optionalInfo
     *     (rendered in iteration order; prefix is derived from name)
     *   - draft.subject, draft.greeting, draft.body
     *   - draft.sender.signature, .name, .address, .lokal, .distrito, .contactNumber
     *
     * NOT included (verified not consumed by buildLayout / PdfContentCalculator):
     *   - draft.id (used only by the canonical filename)
     *   - draft.createdTime, draft.modifiedTime, draft.isGenerated
     *   - recipient.id (not read by render code)
     *   - date.label (not read by buildLayout — only date.time is consumed)
     */
    fun computeContentFingerprint(draft: LetterDraft, paperSize: PaperSize): String {
        val md = MessageDigest.getInstance("SHA-256")

        fun absorb(label: String, value: String) {
            md.update(label.toByteArray(Charsets.UTF_8))
            md.update(0)
            md.update(value.toByteArray(Charsets.UTF_8))
            md.update(0)
        }

        fun absorbLong(label: String, value: Long) {
            absorb(label, value.toString())
        }

        fun absorbDouble(label: String, value: Double) {
            absorb(label, value.toString())
        }

        // Paper size geometry — drives layout and pagination.
        absorbDouble("paper.widthPt", paperSize.widthPt)
        absorbDouble("paper.heightPt", paperSize.heightPt)

        // Dates — only the millisecond instant is read (formatDisplay is then
        // applied, but only the instant is the upstream input).
        absorb("dates.count", draft.dates.size.toString())
        for ((index, letterDate) in draft.dates.withIndex()) {
            absorbLong("dates[$index].time", letterDate.date.time)
        }

        // Recipients — id is not rendered, so we exclude it.
        absorb("recipients.count", draft.recipients.size.toString())
        for ((index, recipient) in draft.recipients.withIndex()) {
            absorb("recipients[$index].name", recipient.name)
            absorb("recipients[$index].position", recipient.position)
            absorb("recipients[$index].organization", recipient.organization)
            absorb("recipients[$index].address", recipient.address)
            absorb("recipients[$index].optionalInfo", recipient.optionalInfo)
        }

        // Free-text fields.
        absorb("subject", draft.subject)
        absorb("greeting", draft.greeting)
        absorb("body", draft.body)

        // Sender — all six fields are read by renderClosing.
        absorb("sender.signature", draft.sender.signature)
        absorb("sender.name", draft.sender.name)
        absorb("sender.address", draft.sender.address)
        absorb("sender.lokal", draft.sender.lokal)
        absorb("sender.distrito", draft.sender.distrito)
        absorb("sender.contactNumber", draft.sender.contactNumber)

        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute the sidecar path for a given artifact file. The sidecar lives in
     * the same directory as the PDF and uses a `.fp` extension.
     */
    fun sidecarFor(artifact: File): File {
        return File(artifact.parentFile, artifact.name + SIDECAR_EXTENSION)
    }

    private fun readFingerprint(sidecar: File): String? {
        if (!sidecar.exists() || !sidecar.isFile) return null
        return try {
            sidecar.readText(Charsets.UTF_8).trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteArtifactAndSidecar(artifact: File): Boolean {
        val sidecar = sidecarFor(artifact)
        val pdfDeleted = if (artifact.exists()) artifact.delete() else true
        val sidecarDeleted = if (sidecar.exists()) sidecar.delete() else true
        return pdfDeleted && sidecarDeleted
    }

    private fun sanitizeForFilename(input: String): String {
        return input
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .replace(".", "")
            .replace(Regex("[\\x00-\\x1f]"), "")
            .replace(Regex("\\s+"), "_")
            .trim()
            .take(50)
            .ifEmpty { "Letter" }
    }
}
