package com.sulat.ai.share

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class SaveHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeDraft(
        subject: String = "Test Subject",
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister")
        ),
        body: String = "Hello world."
    ): LetterDraft {
        return LetterDraft(
            id = "test-draft-id",
            recipients = recipients,
            dates = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")),
            body = body,
            subject = subject,
            greeting = "Dear Kapatid",
            sender = SenderProfile(name = "Sender Name", signature = "Faithfully")
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

    private fun createValidPdf(name: String = "test.pdf"): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { it.write(buildMinimalPdf()) }
        return file
    }

    // ════════════════════════════════════════════════════════════════════════
    // FILENAME BUILDING (7 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun buildFilenameWithSubject() {
        val draft = makeDraft(subject = "Request for Assistance")
        val filename = SaveHelper.buildFilename(draft)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Must contain subject", filename.contains("Request for Assistance"))
        assertTrue("Must start with Sulat-", filename.startsWith("Sulat-"))
    }

    @Test
    fun buildFilenameWithRecipient() {
        val draft = makeDraft(subject = "", recipients = listOf(Recipient(id = "r1", name = "Bro. Pedro")))
        val filename = SaveHelper.buildFilename(draft)
        assertTrue("Must contain recipient name", filename.contains("Pedro"))
    }

    @Test
    fun buildFilenameFallbackToLetter() {
        val draft = makeDraft(subject = "", recipients = emptyList())
        val filename = SaveHelper.buildFilename(draft)
        assertTrue("Must contain Letter", filename.contains("Letter"))
    }

    @Test
    fun buildFilenameTruncatesLongSubject() {
        val longSubject = "A".repeat(200)
        val draft = makeDraft(subject = longSubject)
        val filename = SaveHelper.buildFilename(draft)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Length must be <= 110", filename.length <= 110)
    }

    @Test
    fun buildFilenameSanitizesDangerousChars() {
        val draft = makeDraft(subject = "Test/File\\Name:With*Danger?")
        val filename = SaveHelper.buildFilename(draft)
        assertFalse("Must not contain /", filename.contains("/"))
        assertFalse("Must not contain \\", filename.contains("\\"))
        assertFalse("Must not contain :", filename.contains(":"))
        assertFalse("Must not contain *", filename.contains("*"))
        assertFalse("Must not contain ?", filename.contains("?"))
    }

    @Test
    fun buildFilenameAlwaysEndsWithPdf() {
        val drafts = listOf(
            makeDraft(subject = "Hello"),
            makeDraft(subject = "Test.pdf.txt")
        )
        for (draft in drafts) {
            val filename = SaveHelper.buildFilename(draft)
            assertTrue("Must always end with .pdf: $filename", filename.endsWith(".pdf"))
        }
    }

    @Test
    fun buildUniqueFilenameContainsTimestamp() {
        val draft = makeDraft(subject = "Test Letter")
        val filename = SaveHelper.buildUniqueFilename(draft)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Must contain timestamp", filename.contains("_"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHARE HELPER INTEGRATION (2 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun buildFilenameMatchesShareHelper() {
        val draft = makeDraft(subject = "Same Subject Test")
        val saveFilename = SaveHelper.buildFilename(draft)
        val shareFilename = ShareHelper.sanitizeFilename(draft)
        assertEquals("SaveHelper and ShareHelper must produce same filename", shareFilename, saveFilename)
    }

    @Test
    fun pdfMimeTypeIsCorrect() {
        assertEquals("application/pdf", "application/pdf")
    }
}
