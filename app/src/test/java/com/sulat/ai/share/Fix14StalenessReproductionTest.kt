package com.sulat.ai.share

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Date

/**
 * FIX14 TEMPORARY REPRODUCTION TEST.
 *
 * Reproduces the FIX13 HIGH-severity defect:
 * "User edits an existing draft, Preview shows the new content, but Save/Share/Print
 *  may reuse an older cached PDF artifact because artifact validity currently checks
 *  file validity/path identity but not whether the PDF represents the current draft
 *  content."
 *
 * This is a JVM-only reproduction that does NOT touch Android Context. It exercises
 *  the same canonical-path identity contract that the production `isValidArtifact`
 *  relies on, and demonstrates the exact precondition for staleness:
 *
 *   1. After generation, the canonical path is determined by
 *      (draftId, sanitizedSubject, paperSize.name) only.
 *   2. Editing draft.body, recipients, sender, dates, greeting, modifiedTime does
 *      NOT change the canonical path (because none of those fields feed the filename).
 *   3. Therefore a cached file at that path is reused even when its bytes reflect
 *      old content.
 *
 * The test is purely investigative — it asserts the structural precondition and the
 * byte-level staleness, not a fix outcome.
 *
 * NOTE: This file is INTENDED TO BE DELETED before committing the investigation.
 */
class Fix14StalenessReproductionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeDraft(
        id: String = "draft-stale-14",
        body: String = "OLD body content",
        subject: String = "Subject",
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister")
        ),
        createdTime: Long = 1700000000000L,
        modifiedTime: Long = 1700000000000L
    ): LetterDraft {
        return LetterDraft(
            id = id,
            recipients = recipients,
            dates = listOf(LetterDate(date = Date(createdTime), label = "Jan 1")),
            body = body,
            subject = subject,
            greeting = "Dear Kapatid",
            sender = SenderProfile(name = "Sender", signature = "Faithfully"),
            createdTime = createdTime,
            modifiedTime = modifiedTime
        )
    }

    /** Writes a minimal PDF marker with a unique body string for byte-level assertion. */
    private fun writeMarkerPdf(file: File, marker: String) {
        // Real PDF magic header so PdfRenderer.isValidPdfFile would return true.
        val body = marker.toByteArray(Charsets.US_ASCII)
        val header = "%PDF-1.4\n".toByteArray(Charsets.US_ASCII)
        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(body)
        }
    }

    /** Computes the canonical artifact filename the production code would produce. */
    private fun canonicalFilename(draft: LetterDraft, paperSize: PaperSize): String {
        return PdfArtifactManager.buildArtifactFilename(draft, paperSize)
    }

    /**
     * REPRODUCTION STEP 1:
     * Confirm the canonical artifact identity contract is purely (id, subject, paperSize).
     */
    @Test
    fun step1_canonicalPathDependsOnlyOnIdSubjectPaperSize() {
        val d1 = makeDraft(body = "anything", subject = "Subj")
        val d2 = makeDraft(body = "different body", subject = "Subj")
        val d3 = makeDraft(body = "different body", subject = "Subj",
            recipients = listOf(Recipient(id = "r1", name = "Other", position = "P")))
        val d4 = d2.copy(modifiedTime = d2.modifiedTime + 99_999_999L)

        assertEquals(
            "same id+subject+paperSize must yield same canonical filename regardless of body",
            canonicalFilename(d1, PaperSize.A4),
            canonicalFilename(d2, PaperSize.A4)
        )
        assertEquals(
            "modifiedTime must NOT influence canonical filename",
            canonicalFilename(d2, PaperSize.A4),
            canonicalFilename(d4, PaperSize.A4)
        )
        assertEquals(
            "recipients must NOT influence canonical filename (subject present)",
            canonicalFilename(d2, PaperSize.A4),
            canonicalFilename(d3, PaperSize.A4)
        )

        val a4 = canonicalFilename(d1, PaperSize.A4)
        val legal = canonicalFilename(d1, PaperSize.Legal)
        assertNotEquals("different paper size must produce different filename", a4, legal)
    }

    /**
     * REPRODUCTION STEP 2:
     * Stage a PDF at the canonical path with an "OLD" marker, then simulate a draft
     * edit. Show that the path remains identical and the bytes still say "OLD".
     */
    @Test
    fun step2_cachedOldPdfSurvivesDraftEditAtCanonicalPath() {
        val originalDraft = makeDraft(body = "OLD body content")
        val paperSize = PaperSize.A4

        val shareDir = File(tempFolder.root, "shared")
        shareDir.mkdirs()
        val canonicalName = canonicalFilename(originalDraft, paperSize)
        val cachedFile = File(shareDir, canonicalName)

        writeMarkerPdf(cachedFile, marker = "OLD-PDF-MARKER")
        assertTrue("precondition: cached PDF must be installed", cachedFile.exists())

        // Simulate the production edit path: WriteLetterActivity.kt:69-73.
        val editedDraft = originalDraft.copy(
            body = "NEW body content (different)",
            modifiedTime = originalDraft.modifiedTime + 60_000L
        )

        val editedCanonicalName = canonicalFilename(editedDraft, paperSize)
        assertEquals(
            "FIX13 finding: id is preserved, subject is preserved, paperSize is preserved " +
                "→ canonical filename MUST be identical after edit. This is the precondition " +
                "for the staleness defect.",
            canonicalName,
            editedCanonicalName
        )

        val bytes = cachedFile.readBytes()
        val asString = String(bytes, Charsets.US_ASCII)
        assertTrue(
            "Bytes at the canonical path still reflect OLD content. " +
                "Any code that consumes the file at this path will see OLD content. " +
                "This is the defect — demonstrated at the byte level.",
            asString.contains("OLD-PDF-MARKER")
        )
        assertTrue(
            "Confirm the cached file still passes PdfRenderer.isValidPdfFile's header check " +
                "(starts with %PDF-). Without this, ensurePdfArtifact would invalidate for " +
                "structural reasons; WITH it, ensurePdfArtifact returns true and serves stale.",
            asString.startsWith("%PDF-")
        )
    }

    /**
     * REPRODUCTION STEP 3:
     * Document what a valid fingerprint-based invalidation would look like.
     * This does NOT modify production code — it just shows the deterministic contract
     * a content-hash approach would require.
     */
    @Test
    fun step3_contentFingerprintContractIsDeterministic() {
        val d1 = makeDraft(body = "X")
        val d2 = makeDraft(body = "X")
        val d3 = makeDraft(body = "Y")

        val f1 = contentFingerprint(d1)
        val f2 = contentFingerprint(d2)
        val f3 = contentFingerprint(d3)

        assertEquals("identical content yields identical fingerprint", f1, f2)
        assertNotEquals("different content yields different fingerprint", f1, f3)
    }

    /**
     * Helper: a stable content fingerprint over the fields that drive the PDF render.
     * This is a documentation-only helper used by the tests above to show what a
     * fingerprint approach would compute. It is NOT used by production code.
     */
    private fun contentFingerprint(draft: LetterDraft): String {
        val md = MessageDigest.getInstance("SHA-256")
        val parts = listOf(
            draft.id,
            draft.body,
            draft.subject,
            draft.greeting,
            draft.sender.name,
            draft.sender.address,
            draft.sender.lokal,
            draft.sender.distrito,
            draft.sender.contactNumber,
            draft.sender.signature,
            draft.isGenerated.toString(),
            draft.recipients.joinToString("|") { r ->
                listOf(r.id, r.name, r.position, r.organization, r.address, r.optionalInfo)
                    .joinToString(",")
            },
            draft.dates.joinToString("|") { d -> d.date.time.toString() + "," + d.label }
        )
        for (p in parts) {
            md.update(p.toByteArray(Charsets.UTF_8))
            md.update(0)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
