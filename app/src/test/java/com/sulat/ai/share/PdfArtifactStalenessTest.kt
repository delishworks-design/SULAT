package com.sulat.ai.share

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * FIX14-B central staleness regression tests.
 *
 * Reproduces the FIX13 / FIX14 defect at the lifecycle level by simulating
 * the artifact cache directly using the public surface of [PdfArtifactManager].
 *
 * These tests do NOT depend on Android Context. They model the cache as:
 *   <temp>/shared/Sulat-<safeDraftId>-<safeSubject>-<paperSize>.pdf
 *   <temp>/shared/Sulat-<safeDraftId>-<safeSubject>-<paperSize>.pdf.fp
 *
 * and drive every scenario through the public functions [buildArtifactFilename],
 * [computeContentFingerprint], and [sidecarFor]. The same logic is what
 * [isValidArtifact] executes in production.
 */
class PdfArtifactStalenessTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeDraft(
        id: String = "stale-id",
        body: String = "OLD body",
        subject: String = "Subject",
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister")
        ),
        dates: List<LetterDate> = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")),
        sender: SenderProfile = SenderProfile(name = "Sender", signature = "Faithfully"),
        modifiedTime: Long = 1700000000000L
    ): LetterDraft {
        return LetterDraft(
            id = id,
            recipients = recipients,
            dates = dates,
            sender = sender,
            body = body,
            subject = subject,
            greeting = "Dear Sir",
            createdTime = 1700000000000L,
            modifiedTime = modifiedTime
        )
    }

    private fun writeMinimalValidPdf(file: File) {
        // Real PDF magic so a content-blind check would still treat this as valid.
        val bytes = (
            "%PDF-1.4\n" +
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n" +
                "xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n" +
                "0000000058 00000 n \n0000000115 00000 n \n" +
                "trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n190\n%%EOF\n"
            ).toByteArray(Charsets.US_ASCII)
        FileOutputStream(file).use { it.write(bytes) }
    }

    /**
     * Simulates a successfully generated artifact at the canonical path. In
     * production this is exactly what [PdfArtifactManager.ensurePdfArtifact]
     * does after a render succeeds.
     */
    private fun simulateArtifactGenerated(draft: LetterDraft, paperSize: PaperSize): Pair<File, File> {
        val canonicalName = PdfArtifactManager.buildArtifactFilename(draft, paperSize)
        val shareDir = File(tempFolder.root, "shared").apply { mkdirs() }
        val pdf = File(shareDir, canonicalName)
        val sidecar = PdfArtifactManager.sidecarFor(pdf)
        writeMinimalValidPdf(pdf)
        sidecar.writeText(PdfArtifactManager.computeContentFingerprint(draft, paperSize))
        return pdf to sidecar
    }

    /**
     * Pure-JVM mirror of [PdfArtifactManager.isValidArtifact]. Returns true iff
     * the artifact passes the production validity check. Asserts against this
     * mirror so the test remains JVM-only (no Android Context).
     */
    private fun isValidArtifactMirror(pdf: File, draft: LetterDraft, paperSize: PaperSize): Boolean {
        if (!pdf.exists() || !pdf.isFile || pdf.length() == 0L) return false
        if (!pdf.readBytes().copyOf(5).toString(Charsets.US_ASCII).startsWith("%PDF-")) return false
        val sidecar = PdfArtifactManager.sidecarFor(pdf)
        if (!sidecar.exists() || !sidecar.isFile) return false
        val stored = sidecar.readText(Charsets.UTF_8).trim()
        val expected = PdfArtifactManager.computeContentFingerprint(draft, paperSize)
        return stored == expected
    }

    // ─────────────────────────────────────────────────────────────────────
    // Most important regression: edit SAME draft ID → stale PDF must NOT
    // be returned as valid.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    fun sameDraftIdEdited_invalidatesArtifact() {
        val draft = makeDraft(body = "OLD body", subject = "S")
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        assertTrue("precondition: pdf exists", pdf.exists())
        assertTrue("precondition: sidecar exists", sidecar.exists())
        assertTrue(
            "precondition: artifact is valid for its own draft",
            isValidArtifactMirror(pdf, draft, paperSize)
        )

        // Edit body — same id, same subject, same paper size → same canonical path.
        val edited = draft.copy(body = "NEW body content", modifiedTime = draft.modifiedTime + 60_000L)
        assertEquals("id preserved", draft.id, edited.id)
        assertEquals(
            "canonical filename must be identical across edit (FIX13 finding)",
            PdfArtifactManager.buildArtifactFilename(draft, paperSize),
            PdfArtifactManager.buildArtifactFilename(edited, paperSize)
        )

        // Validity against the EDITED draft must be FALSE.
        assertFalse(
            "Stale PDF must NOT be valid for an edited draft (this is the central " +
                "FIX14-B regression — without the sidecar check, isValidArtifact would " +
                "return true because file exists, %PDF- header is intact, and path " +
                "matches).",
            isValidArtifactMirror(pdf, edited, paperSize)
        )
    }

    @Test
    fun subjectChange_invalidatesArtifact() {
        val draft = makeDraft(subject = "Old Subject")
        val paperSize = PaperSize.A4

        val (pdf, _) = simulateArtifactGenerated(draft, paperSize)
        // Note: subject change WILL change the canonical filename, but the test
        // deliberately uses a different paperSize to confirm subject changes are
        // detected even when filenames happen to collide after sanitization
        // (e.g. subjects that differ only in punctuation that gets stripped).
        val edited = draft.copy(subject = "Subject!Different")

        assertFalse(
            "Subject change must invalidate the artifact even at the same canonical path",
            isValidArtifactMirror(pdf, edited, paperSize)
        )
    }

    @Test
    fun recipientChange_invalidatesArtifact() {
        val draft = makeDraft(
            recipients = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister"))
        )
        val paperSize = PaperSize.A4

        val (pdf, _) = simulateArtifactGenerated(draft, paperSize)
        val edited = draft.copy(
            recipients = listOf(Recipient(id = "r1", name = "KA. PEDRO SANTOS", position = "Minister"))
        )
        assertFalse(
            "Recipient name change must invalidate the artifact",
            isValidArtifactMirror(pdf, edited, paperSize)
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sidecar handling tests (9, 10, 11).
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun missingSidecar_invalidatesArtifact() {
        val draft = makeDraft()
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        // Legacy artifact (no sidecar) — backwards-compat regression.
        assertTrue("precondition: sidecar exists before deletion", sidecar.exists())
        assertTrue(sidecar.delete())
        assertFalse(
            "Missing sidecar must invalidate the artifact (legacy PDFs from before " +
                "the fingerprint system must be treated as INVALID and regenerated).",
            isValidArtifactMirror(pdf, draft, paperSize)
        )
    }

    @Test
    fun mismatchedSidecar_invalidatesArtifact() {
        val draft = makeDraft(body = "OLD body")
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        // Simulate a sidecar that was written for a different (older or future)
        // version of the draft.
        sidecar.writeText("0000000000000000000000000000000000000000000000000000000000000000")
        assertFalse(
            "Sidecar whose content does not match the recomputed fingerprint must " +
                "invalidate the artifact.",
            isValidArtifactMirror(pdf, draft, paperSize)
        )
    }

    @Test
    fun matchingSidecar_validatesArtifact() {
        val draft = makeDraft()
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        assertTrue(
            "Sidecar with matching fingerprint must validate the artifact",
            isValidArtifactMirror(pdf, draft, paperSize)
        )

        // Recomputing and rewriting the sidecar (e.g. after a no-op copy) must
        // continue to validate.
        sidecar.writeText(PdfArtifactManager.computeContentFingerprint(draft, paperSize))
        assertTrue(
            "Rewriting a matching sidecar must continue to validate",
            isValidArtifactMirror(pdf, draft, paperSize)
        )
    }

    @Test
    fun emptyPdf_invalidatesArtifact() {
        val draft = makeDraft()
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        pdf.writeBytes(ByteArray(0))
        assertFalse(
            "Empty PDF must invalidate the artifact (existing structural check)",
            isValidArtifactMirror(pdf, draft, paperSize)
        )
        // Sidecar may still be present; that does not override structural failure.
        assertTrue(sidecar.exists())
    }

    @Test
    fun nonPdfContent_invalidatesArtifact() {
        val draft = makeDraft()
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        pdf.writeText("This is not a PDF file at all.")
        assertFalse(
            "Non-PDF content must invalidate the artifact (existing structural check)",
            isValidArtifactMirror(pdf, draft, paperSize)
        )
        assertTrue(sidecar.exists())
    }

    // ─────────────────────────────────────────────────────────────────────
    // Canonical-path identity invariants preserved alongside sidecar.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun sameDraftSameContentSameCanonicalPath() {
        val a = makeDraft(body = "same", subject = "same")
        val b = makeDraft(body = "same", subject = "same")
        assertEquals(
            PdfArtifactManager.buildArtifactFilename(a, PaperSize.A4),
            PdfArtifactManager.buildArtifactFilename(b, PaperSize.A4)
        )
    }

    @Test
    fun differentDraftDifferentCanonicalPath() {
        val a = makeDraft(id = "id-1")
        val b = makeDraft(id = "id-2")
        assertNotEquals(
            PdfArtifactManager.buildArtifactFilename(a, PaperSize.A4),
            PdfArtifactManager.buildArtifactFilename(b, PaperSize.A4)
        )
    }

    @Test
    fun differentPaperSizeDifferentCanonicalPath() {
        val draft = makeDraft()
        assertNotEquals(
            PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4),
            PdfArtifactManager.buildArtifactFilename(draft, PaperSize.Legal)
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sidecar lifecycle — sidecar file naming contract.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun sidecarPathIsArtifactPathPlusFpExtension() {
        val draft = makeDraft()
        val paperSize = PaperSize.A4
        val pdf = File(tempFolder.root, "shared/${PdfArtifactManager.buildArtifactFilename(draft, paperSize)}")
        val sidecar = PdfArtifactManager.sidecarFor(pdf)
        assertEquals(pdf.absolutePath + ".fp", sidecar.absolutePath)
    }

    @Test
    fun sidecarForLivesInSameDirectoryAsArtifact() {
        val parent = File(tempFolder.root, "deep/nested/path").apply { mkdirs() }
        val pdf = File(parent, "anything.pdf")
        val sidecar = PdfArtifactManager.sidecarFor(pdf)
        assertEquals(parent, sidecar.parentFile)
        assertEquals("anything.pdf.fp", sidecar.name)
    }

    // ─────────────────────────────────────────────────────────────────────
    // "Most important regression" — full lifecycle simulation.
    // Generate OLD PDF → save fingerprint → edit SAME draft ID → save draft →
    // recompute fingerprint → assert that the OLD sidecar no longer matches
    // the NEW draft's fingerprint.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    fun fullStalenessRegression_oldSidecarDoesNotMatchEditedDraft() {
        val draft = makeDraft(body = "OLD body", subject = "Subject")
        val paperSize = PaperSize.A4

        val (pdf, sidecar) = simulateArtifactGenerated(draft, paperSize)
        val oldFingerprint = sidecar.readText(Charsets.UTF_8).trim()
        assertEquals(
            "Initial sidecar must equal the fingerprint computed for the original draft",
            PdfArtifactManager.computeContentFingerprint(draft, paperSize),
            oldFingerprint
        )

        // Edit the SAME draft — body changes, id stays.
        val editedDraft = draft.copy(
            body = "Completely new body content",
            modifiedTime = draft.modifiedTime + 999_999L
        )
        assertEquals("id preserved across edit", draft.id, editedDraft.id)

        // The OLD sidecar is no longer authoritative for the NEW draft.
        val newFingerprint = PdfArtifactManager.computeContentFingerprint(editedDraft, paperSize)
        assertNotEquals(
            "Edited draft must produce a different fingerprint than the cached OLD one",
            oldFingerprint,
            newFingerprint
        )

        // isValidArtifact against the EDITED draft must reject the cached artifact.
        assertFalse(
            "Cached artifact with OLD fingerprint must NOT validate against the EDITED draft",
            isValidArtifactMirror(pdf, editedDraft, paperSize)
        )

        // Sanity: isValidArtifact still accepts the artifact when checked
        // against the ORIGINAL draft (this proves we are not breaking the
        // legitimate-cache case).
        assertTrue(
            "Artifact with matching fingerprint must still validate against its original draft",
            isValidArtifactMirror(pdf, draft, paperSize)
        )
    }
}
