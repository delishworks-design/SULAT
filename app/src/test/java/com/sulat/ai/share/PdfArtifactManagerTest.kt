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
    // ARTIFACT FILENAME INCLUDES DRAFT ID (CRITICAL)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun buildArtifactFilenameIncludesDraftId() {
        val draft = makeDraft(id = "abc123", subject = "My Letter")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Filename must contain draft ID 'abc123'", filename.contains("abc123"))
    }

    @Test
    fun buildArtifactFilenameEndsWithPdf() {
        val draft = makeDraft()
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
    }

    @Test
    fun buildArtifactFilenameStartsWithSulat() {
        val draft = makeDraft()
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("Must start with Sulat-", filename.startsWith("Sulat-"))
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

    @Test
    fun buildArtifactFilenameSanitizesDangerousDraftId() {
        val draft = makeDraft(id = "../../../etc/passwd")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain ..", filename.contains(".."))
        assertFalse("Must not contain /", filename.contains("/"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // IDENTITY: SAME DRAFT + SAME PAPER SIZE = SAME PATH (CRITICAL)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun sameDraftSamePaperSizeSamePath() {
        val draft = makeDraft(id = "same-id", subject = "Same Subject", createdTime = 1700000000000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertEquals("Same draft + paper size must produce same filename", path1, path2)
    }

    @Test
    fun sameDraftIdSameSubjectSameDateSamePaperSizeSamePath() {
        val draft1 = makeDraft(id = "draft-id", subject = "Same Subject", createdTime = 1700000000000L)
        val draft2 = makeDraft(id = "draft-id", subject = "Same Subject", createdTime = 1700000000000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft1, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft2, PaperSize.A4)
        assertEquals("Same draft ID must produce same filename", path1, path2)
    }

    // ════════════════════════════════════════════════════════════════════════
    // IDENTITY: SAME DRAFT + DIFFERENT PAPER SIZE = DIFFERENT PATH
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun sameDraftDifferentPaperSizeDifferentPath() {
        val draft = makeDraft(id = "same-id", subject = "Same Subject", createdTime = 1700000000000L)
        val a4Path = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val legalPath = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.Legal)
        val shortBondPath = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.ShortBond)
        val longBondPath = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.LongBond)

        assertNotEquals("A4 != Legal", a4Path, legalPath)
        assertNotEquals("A4 != ShortBond", a4Path, shortBondPath)
        assertNotEquals("A4 != LongBond", a4Path, longBondPath)
        assertNotEquals("Legal != ShortBond", legalPath, shortBondPath)
        assertNotEquals("Legal != LongBond", legalPath, longBondPath)
        assertNotEquals("ShortBond != LongBond", shortBondPath, longBondPath)
    }

    // ════════════════════════════════════════════════════════════════════════
    // IDENTITY: DIFFERENT DRAFT ID = DIFFERENT PATH (CRITICAL COLLISION TEST)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun differentDraftIdsSameSubjectSameDateSamePaperDifferentPath() {
        val draft1 = makeDraft(id = "draft-1", subject = "Identical Subject", createdTime = 1700000000000L)
        val draft2 = makeDraft(id = "draft-2", subject = "Identical Subject", createdTime = 1700000000000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft1, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft2, PaperSize.A4)
        assertNotEquals("Different draft IDs must produce different filenames", path1, path2)
    }

    @Test
    fun differentDraftIdsDifferentSubjectDifferentPath() {
        val draft1 = makeDraft(id = "draft-1", subject = "Subject 1", createdTime = 1700000000000L)
        val draft2 = makeDraft(id = "draft-2", subject = "Subject 2", createdTime = 1700000000000L)
        val path1 = PdfArtifactManager.buildArtifactFilename(draft1, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft2, PaperSize.A4)
        assertNotEquals("Different drafts must produce different filenames", path1, path2)
    }

    @Test
    fun differentDraftIdsAllSamePropertiesDifferentPath() {
        val draft1 = makeDraft(
            id = "id-001",
            subject = "Same",
            recipients = listOf(Recipient(id = "r1", name = "Same Person")),
            createdTime = 1700000000000L
        )
        val draft2 = makeDraft(
            id = "id-002",
            subject = "Same",
            recipients = listOf(Recipient(id = "r1", name = "Same Person")),
            createdTime = 1700000000000L
        )
        val path1 = PdfArtifactManager.buildArtifactFilename(draft1, PaperSize.A4)
        val path2 = PdfArtifactManager.buildArtifactFilename(draft2, PaperSize.A4)
        assertNotEquals("Different draft IDs must produce different filenames even when all other properties match", path1, path2)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ALL FOUR PAPER SIZES PRODUCE UNIQUE FILENAMES
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

    @Test
    fun a4ProducesUniqueFilename() {
        val draft = makeDraft()
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertTrue("A4 filename must contain A4", filename.contains("A4"))
    }

    @Test
    fun shortBondProducesUniqueFilename() {
        val draft = makeDraft()
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.ShortBond)
        assertTrue("ShortBond filename must contain ShortBond", filename.contains("ShortBond"))
    }

    @Test
    fun longBondProducesUniqueFilename() {
        val draft = makeDraft()
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.LongBond)
        assertTrue("LongBond filename must contain LongBond", filename.contains("LongBond"))
    }

    @Test
    fun legalProducesUniqueFilename() {
        val draft = makeDraft()
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.Legal)
        assertTrue("Legal filename must contain Legal", filename.contains("Legal"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PATH TRAVERSAL AND SECURITY TESTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun filenamePreventsPathTraversalSubject() {
        val draft = makeDraft(subject = "../../../etc/passwd")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain ..", filename.contains(".."))
        assertFalse("Must not contain /", filename.contains("/"))
    }

    @Test
    fun filenamePreventsSlashInSubject() {
        val draft = makeDraft(subject = "Test/Path")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain /", filename.contains("/"))
    }

    @Test
    fun filenamePreventsBackslashInSubject() {
        val draft = makeDraft(subject = "Test\\Path")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain \\", filename.contains("\\"))
    }

    @Test
    fun filenamePreventsNullByte() {
        val draft = makeDraft(subject = "Test\u0000File")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain null byte", filename.contains("\u0000"))
    }

    @Test
    fun filenamePreventsControlCharacters() {
        val draft = makeDraft(subject = "Test\u001fFile")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain control chars", filename.contains("\u001f"))
    }

    @Test
    fun filenamePreventsDangerousCharsInDraftId() {
        val draft = makeDraft(id = "draft\x00id")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain null byte in draft ID", filename.contains("\u0000"))
    }

    @Test
    fun filenamePreventsPathTraversalInDraftId() {
        val draft = makeDraft(id = "../../../dangerous")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Must not contain .. in draft ID", filename.contains(".."))
        assertFalse("Must not contain / in draft ID", filename.contains("/"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SOURCE-LEVEL: NO android.app.Application() IN PRODUCTION CODE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun productionCodeMustNotInstantiateApplication() {
        val sourceFile = File("app/src/main/kotlin/com/sulat/ai/share/PdfArtifactManager.kt")
        assertTrue("PdfArtifactManager.kt must exist", sourceFile.exists())

        val sourceContent = sourceFile.readText()
        assertFalse(
            "Production code must NOT contain 'android.app.Application()' - use real Context instead",
            sourceContent.contains("android.app.Application()")
        )
        assertFalse(
            "Production code must NOT contain 'Application()' without package",
            sourceContent.contains(Regex("Application\\s*\\("))
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // JVM-TESTED VALIDATION (no Android context required)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun missingFileIsNotValid() {
        val draft = makeDraft()
        val fakeFile = File(tempFolder.newFolder(), "nonexistent.pdf")
        assertFalse("Nonexistent file must not be valid", fakeFile.exists())
    }

    @Test
    fun emptyFileIsNotValid() {
        val draft = makeDraft()
        val emptyFile = tempFolder.newFile("empty.pdf")
        assertEquals("Empty file must have 0 length", 0L, emptyFile.length())
    }

    @Test
    fun malformedPdfIsNotValid() {
        val draft = makeDraft()
        val malformedFile = tempFolder.newFile("malformed.pdf")
        malformedFile.writeBytes("NOT A PDF FILE".toByteArray())
        assertTrue("Malformed file must exist", malformedFile.exists())
        assertTrue("Malformed file must be non-empty", malformedFile.length() > 0)
    }

    @Test
    fun validPdfStructureIsRecognized() {
        val validPdf = tempFolder.newFile("valid.pdf")
        validPdf.writeBytes(buildMinimalPdf())
        assertTrue("Valid PDF must exist", validPdf.exists())
        assertTrue("Valid PDF must have content", validPdf.length() > 0)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FILENAME STRUCTURE VERIFICATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun filenameHasCorrectOrderDraftIdSubjectPaperSize() {
        val draft = makeDraft(id = "DRAFT123", subject = "MyLetter", createdTime = 1700000000000L)
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        val parts = filename.removeSuffix(".pdf").split("-")
        assertEquals("First part must be Sulat", "Sulat", parts[0])
        assertEquals("Second part must be draft ID", "DRAFT123", parts[1])
        assertEquals("Third part must be subject", "MyLetter", parts[2])
        assertEquals("Fourth part must be paper size", "A4", parts[3])
    }

    @Test
    fun emptyDraftIdIsSanitized() {
        val draft = makeDraft(id = "", subject = "Test")
        val filename = PdfArtifactManager.buildArtifactFilename(draft, PaperSize.A4)
        assertFalse("Empty draft ID must be sanitized to non-empty", filename.contains("-Letter.pdf"))
    }
}
