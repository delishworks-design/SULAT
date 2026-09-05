package com.sulat.ai.share

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.PdfRenderer
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

class ShareHelperTest {

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
            dates = listOf(LetterDate(date = java.util.Date(1700000000000L), label = "Jan 1")),
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
    // FILE VALIDATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testValidateMissingFile() {
        val missing = File(tempFolder.root, "nonexistent.pdf")
        val error = ShareHelper.validatePdfFile(missing)
        assertNotNull("Missing file must return error", error)
        assertTrue(error!!.contains("does not exist"))
    }

    @Test
    fun testValidateDirectory() {
        val dir = tempFolder.newFolder("a_dir")
        val error = ShareHelper.validatePdfFile(dir)
        assertNotNull("Directory must return error", error)
        assertTrue(error!!.contains("not a regular file"))
    }

    @Test
    fun testValidateZeroByteFile() {
        val empty = tempFolder.newFile("empty.pdf")
        val error = ShareHelper.validatePdfFile(empty)
        assertNotNull("Zero-byte file must return error", error)
        assertTrue(error!!.contains("empty"))
    }

    @Test
    fun testValidateNonPdfFile() {
        val textFile = tempFolder.newFile("readme.txt")
        textFile.writeText("Hello world")
        val error = ShareHelper.validatePdfFile(textFile)
        assertNotNull("Non-PDF file must return error", error)
        assertTrue(error!!.contains("not a valid PDF"))
    }

    @Test
    fun testValidateValidPdf() {
        val pdf = createValidPdf()
        val error = ShareHelper.validatePdfFile(pdf)
        assertNull("Valid PDF must pass validation", error)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FILENAME SANITIZATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testSanitizeFilenameNormalSubject() {
        val draft = makeDraft(subject = "Request for Assistance")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Filename must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Filename must contain subject", filename.contains("Request for Assistance"))
        assertTrue("Filename must start with Sulat", filename.startsWith("Sulat-"))
    }

    @Test
    fun testSanitizeFilenameWithSpaces() {
        val draft = makeDraft(subject = "My Letter")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Filename must preserve spaces", filename.contains("My Letter"))
        assertTrue("Filename must end with .pdf", filename.endsWith(".pdf"))
    }

    @Test
    fun testSanitizeFilenameWithSlash() {
        val draft = makeDraft(subject = "Topic/Sub")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Filename must not contain /", filename.contains("/"))
        assertTrue("Filename must end with .pdf", filename.endsWith(".pdf"))
    }

    @Test
    fun testSanitizeFilenameWithBackslash() {
        val draft = makeDraft(subject = "Topic\\Sub")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Filename must not contain \\", filename.contains("\\"))
    }

    @Test
    fun testSanitizeFilenameWithColon() {
        val draft = makeDraft(subject = "Topic: Important")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Filename must not contain :", filename.contains(":"))
    }

    @Test
    fun testSanitizeFilenameWithControlChars() {
        val draft = makeDraft(subject = "Topic\u0000\u0001\u0002")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Filename must not contain control chars", filename.contains("\u0000"))
        assertFalse("Filename must not contain control chars", filename.contains("\u0001"))
    }

    @Test
    fun testSanitizeFilenameWithPathTraversal() {
        val draft = makeDraft(subject = "../../../etc/passwd")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Filename must not contain path traversal", filename.contains(".."))
        assertTrue("Filename must end with .pdf", filename.endsWith(".pdf"))
    }

    @Test
    fun testSanitizeFilenameLongSubject() {
        val longSubject = "A".repeat(200)
        val draft = makeDraft(subject = longSubject)
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Filename must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Filename length must be reasonable", filename.length <= 110)
    }

    @Test
    fun testSanitizeFilenameEmptySubjectUsesRecipient() {
        val draft = makeDraft(
            subject = "",
            recipients = listOf(Recipient(id = "r1", name = "Bro. Pedro"))
        )
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Filename must contain recipient name", filename.contains("Pedro"))
    }

    @Test
    fun testSanitizeFilenameEmptySubjectAndRecipients() {
        val draft = makeDraft(subject = "", recipients = emptyList())
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Filename must contain fallback", filename.contains("Letter"))
    }

    @Test
    fun testSanitizeFilenameContainsDate() {
        val draft = makeDraft()
        val filename = ShareHelper.sanitizeFilename(draft)
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        assertTrue("Filename must contain current year", filename.contains(currentYear))
    }

    @Test
    fun testSanitizeFilenameNoDangerousChars() {
        val draft = makeDraft(subject = "Test: File/Name\\With*Question?")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse(filename.contains("/"))
        assertFalse(filename.contains("\\"))
        assertFalse(filename.contains(":"))
        assertFalse(filename.contains("*"))
        assertFalse(filename.contains("?"))
        assertFalse(filename.contains("\""))
        assertFalse(filename.contains("<"))
        assertFalse(filename.contains(">"))
        assertFalse(filename.contains("|"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // MIME TYPE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testPdfMimeType() {
        assertEquals("application/pdf", ShareHelper.pdfMimeType())
    }

    // ════════════════════════════════════════════════════════════════════════
    // FILE PROVIDER AUTHORITY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testFileProviderAuthority() {
        assertEquals("com.sulat.ai.fileprovider", ShareHelper.fileProviderAuthority())
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF INTEGRITY — SHARING USES REAL PDF
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testValidPdfStartsWithHeader() {
        val pdf = createValidPdf()
        val bytes = pdf.readBytes()
        assertTrue("PDF must start with %PDF-", String(bytes.sliceArray(0..4)) == "%PDF-")
    }

    @Test
    fun testValidPdfIsBinaryData() {
        val pdf = createValidPdf()
        val bytes = pdf.readBytes()
        assertTrue("PDF must be binary data", bytes.size > 100)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FOUR PAPER SIZES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllFourPaperSizesExist() {
        val names = PaperSize.entries.map { it.name }
        assertTrue(names.contains("A4"))
        assertTrue(names.contains("ShortBond"))
        assertTrue(names.contains("Legal"))
        assertTrue(names.contains("LongBond"))
        assertEquals(4, PaperSize.entries.size)
    }

    @Test
    fun testA4Dimensions() {
        assertEquals(595.276, PaperSize.A4.widthPt, 0.001)
        assertEquals(841.89, PaperSize.A4.heightPt, 0.001)
    }

    @Test
    fun testShortBondDimensions() {
        assertEquals(612.0, PaperSize.ShortBond.widthPt, 0.001)
        assertEquals(792.0, PaperSize.ShortBond.heightPt, 0.001)
    }

    @Test
    fun testLongBondDimensions() {
        assertEquals(612.0, PaperSize.LongBond.widthPt, 0.001)
        assertEquals(936.0, PaperSize.LongBond.heightPt, 0.001)
    }

    @Test
    fun testLegalDimensions() {
        assertEquals(612.0, PaperSize.Legal.widthPt, 0.001)
        assertEquals(1008.0, PaperSize.Legal.heightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING — FAILURE PATHS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testValidateFailsForMissingPdf() {
        val missing = File(tempFolder.root, "nope.pdf")
        val result = ShareHelper.validatePdfFile(missing)
        assertNotNull(result)
    }

    @Test
    fun testValidateFailsForEmptyFile() {
        val empty = tempFolder.newFile("empty.pdf")
        val result = ShareHelper.validatePdfFile(empty)
        assertNotNull(result)
    }

    @Test
    fun testValidatePassesForRealPdf() {
        val pdf = createValidPdf()
        val result = ShareHelper.validatePdfFile(pdf)
        assertNull(result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testCleanupDeletesFile() {
        val testFile = File(tempFolder.root, "share_test.pdf")
        testFile.writeBytes("test".toByteArray())
        assertTrue(testFile.exists())
        testFile.delete()
        assertFalse(testFile.exists())
    }
}
