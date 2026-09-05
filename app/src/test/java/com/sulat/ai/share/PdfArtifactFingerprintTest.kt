package com.sulat.ai.share

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * FIX14-B permanent regression tests for the content-fingerprint contract.
 *
 * The fingerprint MUST cover every render-driving input consumed by
 * LetterTemplateEngine.buildLayout + PaperSize. Any change to a render-driving
 * field must yield a different fingerprint; any non-rendering field must not.
 *
 * These tests do NOT touch Android Context. They exercise the public
 * [PdfArtifactManager.computeContentFingerprint] function directly.
 */
class PdfArtifactFingerprintTest {

    private fun makeDraft(
        id: String = "fp-id",
        body: String = "Body.",
        subject: String = "Subject",
        greeting: String = "Dear Sir",
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister")
        ),
        dates: List<LetterDate> = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")),
        sender: SenderProfile = SenderProfile(
            name = "Sender",
            address = "Address",
            lokal = "Lokal",
            distrito = "Distrito",
            contactNumber = "0917",
            signature = "Faithfully"
        ),
        createdTime: Long = 1700000000000L,
        modifiedTime: Long = 1700000000000L,
        isGenerated: Boolean = false
    ): LetterDraft {
        return LetterDraft(
            id = id,
            recipients = recipients,
            dates = dates,
            sender = sender,
            body = body,
            subject = subject,
            greeting = greeting,
            createdTime = createdTime,
            modifiedTime = modifiedTime,
            isGenerated = isGenerated
        )
    }

    // 1. Same content → same fingerprint
    @Test
    fun sameContentSameFingerprint() {
        val a = makeDraft()
        val b = makeDraft()
        assertEquals(
            "Identical content must yield identical fingerprints",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 2. Body change → different fingerprint
    @Test
    fun bodyChangeDifferentFingerprint() {
        val a = makeDraft(body = "OLD body")
        val b = makeDraft(body = "NEW body")
        assertNotEquals(
            "Body change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 3. Subject change → different fingerprint
    @Test
    fun subjectChangeDifferentFingerprint() {
        val a = makeDraft(subject = "Old Subject")
        val b = makeDraft(subject = "New Subject")
        assertNotEquals(
            "Subject change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 4. Recipient change → different fingerprint
    @Test
    fun recipientNameChangeDifferentFingerprint() {
        val a = makeDraft(recipients = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ")))
        val b = makeDraft(recipients = listOf(Recipient(id = "r1", name = "KA. PEDRO SANTOS")))
        assertNotEquals(
            "Recipient name change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun recipientFieldChangeDifferentFingerprint() {
        val a = makeDraft(recipients = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister")))
        val b = makeDraft(recipients = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Deacon")))
        assertNotEquals(
            "Recipient position change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun recipientOrderChangeDifferentFingerprint() {
        val r1 = Recipient(id = "r1", name = "KA. JUAN")
        val r2 = Recipient(id = "r2", name = "KA. PEDRO")
        val a = makeDraft(recipients = listOf(r1, r2))
        val b = makeDraft(recipients = listOf(r2, r1))
        assertNotEquals(
            "Recipient order change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 5. Sender change → different fingerprint
    @Test
    fun senderNameChangeDifferentFingerprint() {
        val a = makeDraft(sender = SenderProfile(name = "Sender One"))
        val b = makeDraft(sender = SenderProfile(name = "Sender Two"))
        assertNotEquals(
            "Sender name change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun senderFieldChangeDifferentFingerprint() {
        val a = makeDraft(sender = SenderProfile(signature = "Faithfully"))
        val b = makeDraft(sender = SenderProfile(signature = "Yours truly"))
        assertNotEquals(
            "Sender signature change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 6. Date change → different fingerprint
    @Test
    fun dateTimeChangeDifferentFingerprint() {
        val a = makeDraft(dates = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")))
        val b = makeDraft(dates = listOf(LetterDate(date = Date(1700000001000L), label = "Jan 1")))
        assertNotEquals(
            "Date millisecond change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun dateOrderChangeDifferentFingerprint() {
        val d1 = LetterDate(date = Date(1700000000000L), label = "Jan 1")
        val d2 = LetterDate(date = Date(1700100000000L), label = "Jan 2")
        val a = makeDraft(dates = listOf(d1, d2))
        val b = makeDraft(dates = listOf(d2, d1))
        assertNotEquals(
            "Date order change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun dateLabelChangeAloneDoesNotChangeFingerprint() {
        // The layout consumes `date.time` only (DateSystem.formatDisplay takes a
        // LocalDate). The `label` field is preserved on disk but not rendered.
        // Changing only the label must NOT change the fingerprint.
        val a = makeDraft(dates = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")))
        val b = makeDraft(dates = listOf(LetterDate(date = Date(1700000000000L), label = "January 1")))
        assertEquals(
            "Date label alone must NOT change the fingerprint (label is not consumed by buildLayout)",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 7. Greeting change → different fingerprint
    @Test
    fun greetingChangeDifferentFingerprint() {
        val a = makeDraft(greeting = "Dear Sir")
        val b = makeDraft(greeting = "Dear Madam")
        assertNotEquals(
            "Greeting change must produce a different fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 8. Boolean/flag change — isGenerated is NOT a render input (verified
    // against LetterTemplateEngine.kt — the flag is not read by buildLayout).
    // createdTime/modifiedTime are also not render inputs. These should NOT
    // change the fingerprint.
    @Test
    fun nonRenderingFlagChangesDoNotChangeFingerprint() {
        val a = makeDraft(isGenerated = false, createdTime = 1L, modifiedTime = 2L)
        val b = makeDraft(isGenerated = true, createdTime = 999L, modifiedTime = 888L)
        assertEquals(
            "isGenerated / createdTime / modifiedTime must NOT affect fingerprint (verified non-render inputs)",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun draftIdAloneDoesNotChangeFingerprint() {
        // draft.id is part of the canonical filename but NOT a render input.
        val a = makeDraft(id = "id-a")
        val b = makeDraft(id = "id-b")
        assertEquals(
            "draft.id alone must NOT change the fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    @Test
    fun recipientIdAloneDoesNotChangeFingerprint() {
        // recipient.id is not read by PdfContentCalculator.renderRecipient.
        val a = makeDraft(recipients = listOf(Recipient(id = "rid-a", name = "KA. JUAN")))
        val b = makeDraft(recipients = listOf(Recipient(id = "rid-b", name = "KA. JUAN")))
        assertEquals(
            "recipient.id alone must NOT change the fingerprint",
            PdfArtifactManager.computeContentFingerprint(a, PaperSize.A4),
            PdfArtifactManager.computeContentFingerprint(b, PaperSize.A4)
        )
    }

    // 13/14/15: Canonical-path invariants preserved
    @Test
    fun sameDraftSameContentSamePaperSizeSameCanonicalPath() {
        val a = makeDraft()
        val b = makeDraft()
        assertEquals(
            "Same draft content + same paper size must produce same canonical path",
            PdfArtifactManager.buildArtifactFilename(a, PaperSize.A4),
            PdfArtifactManager.buildArtifactFilename(b, PaperSize.A4)
        )
    }

    @Test
    fun differentDraftDifferentCanonicalPath() {
        val a = makeDraft(id = "id-x")
        val b = makeDraft(id = "id-y")
        assertNotEquals(
            "Different draft ids must produce different canonical paths",
            PdfArtifactManager.buildArtifactFilename(a, PaperSize.A4),
            PdfArtifactManager.buildArtifactFilename(b, PaperSize.A4)
        )
    }

    @Test
    fun differentPaperSizeDifferentCanonicalPath() {
        val a = makeDraft()
        assertNotEquals(
            "Different paper size must produce different canonical path",
            PdfArtifactManager.buildArtifactFilename(a, PaperSize.A4),
            PdfArtifactManager.buildArtifactFilename(a, PaperSize.Legal)
        )
    }

    @Test
    fun allFourPaperSizesProduceDistinctFingerprints() {
        val draft = makeDraft()
        val fingerprints = PaperSize.entries.map {
            PdfArtifactManager.computeContentFingerprint(draft, it)
        }
        assertEquals(
            "All four paper sizes must produce distinct fingerprints (geometry is part of fingerprint)",
            4,
            fingerprints.toSet().size
        )
    }

    // Fingerprint is a 64-character hex string (SHA-256 / 4 bits per hex char / 32 bytes).
    @Test
    fun fingerprintIsSha256Hex() {
        val draft = makeDraft()
        val fp = PdfArtifactManager.computeContentFingerprint(draft, PaperSize.A4)
        assertEquals("Fingerprint must be 64 hex chars (SHA-256)", 64, fp.length)
        assertTrue("Fingerprint must be lowercase hex", fp.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
