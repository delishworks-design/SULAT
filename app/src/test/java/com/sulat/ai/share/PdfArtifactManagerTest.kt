package com.sulat.ai.share

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class PdfArtifactManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeDraft(
        id: String = "test-draft",
        subject: String = "Test Subject",
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister")
        ),
        createdTime: Long = 1700000000000L
    ): LetterDraft {
        return LetterDraft(
            id = id,
            recipients = recipients,
            dates = listOf(LetterDate(date = Date(createdTime), label = "Jan 1")),
            body = "Hello world.",
            subject = subject,
            greeting = "Dear Kapatid",
            sender = SenderProfile(name = "Sender Name", signature = "Faithfully"),
            createdTime = createdTime
        )
    }

    private fun buildMinimalPdf(): ByteArray {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n")
        sb.append("xref\n0 4\n")
        sb.append("0000000000 65535 f \n")
        sb.append("0000000009 00000 n \n")
        sb.append("0000000058 00000 n \n")
        sb.append("0000000115 00000 n \n")
        sb.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n190\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ARTIFACT FILENAME (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun buildArtifactFilenameWithSubject() {
        val draft = makeDraft(subject = "Request for Assistance")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Must contain subject", filename.contains("Request_for_Assistance"))
        assertTrue("Must start with Sulat-", filename.startsWith("Sulat-"))
        assertTrue("Must contain A4", filename.contains("A4"))
    }

    @Test
    fun buildArtifactFilenameWithRecipient() {
        val draft = makeDraft(subject = "", recipients = listOf(Recipient(id = "r1", name = "Bro. Pedro")))
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Must contain recipient name", filename.contains("Bro_Pedro"))
    }

    @Test
    fun buildArtifactFilenameFallbackToLetter() {
        val draft = makeDraft(subject = "", recipients = emptyList())
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Must contain Letter", filename.contains("Letter"))
    }

    @Test
    fun buildArtifactFilenameContainsPaperSize() {
        val draft = makeDraft()
        val a4Filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val legalFilename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.Legal)
        val shortBondFilename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.ShortBond)
        val longBondFilename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.LongBond)

        assertTrue("A4 filename must contain A4", a4Filename.contains("A4"))
        assertTrue("Legal filename must contain Legal", legalFilename.contains("Legal"))
        assertTrue("ShortBond filename must contain ShortBond", shortBondFilename.contains("ShortBond"))
        assertTrue("LongBond filename must contain LongBond", longBondFilename.contains("LongBond"))

        assertNotEquals("Different paper sizes must produce different filenames", a4Filename, legalFilename)
    }

    @Test
    fun buildArtifactFilenameSanitizesDangerousChars() {
        val draft = makeDraft(subject = "Test/File\\Name:With*Danger?")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain /", filename.contains("/"))
        assertFalse("Must not contain \\", filename.contains("\\"))
        assertFalse("Must not contain :", filename.contains(":"))
        assertFalse("Must not contain *", filename.contains("*"))
        assertFalse("Must not contain ?", filename.contains("?"))
    }

    @Test
    fun buildArtifactFilenameTruncatesLongSubject() {
        val longSubject = "A".repeat(100)
        val draft = makeDraft(subject = longSubject)
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Length must be <= 100", filename.length <= 100)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ARTIFACT PATH IDENTITY (4 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun sameDraftSamePaperSizeSamePath() {
        val draft = makeDraft(id = "same-id", subject = "Same Subject", createdTime = 1700000000000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertEquals("Same draft + paper size must produce same filename", path1, path2)
    }

    @Test
    fun sameDraftDifferentPaperSizeDifferentPath() {
        val draft = makeDraft(id = "same-id", subject = "Same Subject", createdTime = 1700000000000L)
        val a4Path = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val legalPath = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.Legal)
        assertNotEquals("Different paper sizes must produce different filenames", a4Path, legalPath)
    }

    @Test
    fun differentDraftDifferentPath() {
        val draft1 = makeDraft(id = "draft-1", subject = "Subject 1", createdTime = 1700000000000L)
        val draft2 = makeDraft(id = "draft-2", subject = "Subject 2", createdTime = 1700000000000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft1, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft2, PaperSize.A4)
        assertNotEquals("Different drafts must produce different filenames", path1, path2)
    }

    @Test
    fun differentDatesDifferentPath() {
        val draft1 = makeDraft(id = "same-id", subject = "Same", createdTime = 1700000000000L)
        val draft2 = makeDraft(id = "same-id", subject = "Same", createdTime = 1700000001000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft1, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft2, PaperSize.A4)
        assertNotEquals("Different dates must produce different filenames", path1, path2)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ALL FOUR PAPER SIZES (4 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun allFourPaperSizesProduceUniqueFilenames() {
        val draft = makeDraft()
        val a4 = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val shortBond = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.ShortBond)
        val legal = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.Legal)
        val longBond = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.LongBond)

        assertNotEquals("A4 != ShortBond", a4, shortBond)
        assertNotEquals("ShortBond != Legal", shortBond, legal)
        assertNotEquals("Legal != LongBond", legal, longBond)
        assertNotEquals("LongBond != A4", longBond, a4)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PATH TRAVERSAL SAFETY (2 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun filenamePreventsPathTraversal() {
        val draft = makeDraft(subject = "../../../etc/passwd")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain ..", filename.contains(".."))
        assertFalse("Must not contain /", filename.contains("/"))
    }

    @Test
    fun filenamePreventsNullBytes() {
        val draft = makeDraft(subject = "Test\u0000File")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain null byte", filename.contains("\u0000"))
    }
}
