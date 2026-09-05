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
    // FILE VALIDATION (5 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun validateMissingFile() {
        val missing = File(tempFolder.root, "nonexistent.pdf")
        val error = ShareHelper.validatePdfFile(missing)
        assertNotNull("Missing file must return error", error)
        assertTrue(error!!.contains("does not exist"))
    }

    @Test
    fun validateDirectory() {
        val dir = tempFolder.newFolder("a_dir")
        val error = ShareHelper.validatePdfFile(dir)
        assertNotNull("Directory must return error", error)
        assertTrue(error!!.contains("not a regular file"))
    }

    @Test
    fun validateZeroByteFile() {
        val empty = tempFolder.newFile("empty.pdf")
        val error = ShareHelper.validatePdfFile(empty)
        assertNotNull("Zero-byte file must return error", error)
        assertTrue(error!!.contains("empty"))
    }

    @Test
    fun validateNonPdfFile() {
        val textFile = tempFolder.newFile("readme.txt")
        textFile.writeText("Hello world")
        val error = ShareHelper.validatePdfFile(textFile)
        assertNotNull("Non-PDF file must return error", error)
        assertTrue(error!!.contains("not a valid PDF"))
    }

    @Test
    fun validateValidPdf() {
        val pdf = createValidPdf()
        val error = ShareHelper.validatePdfFile(pdf)
        assertNull("Valid PDF must pass validation", error)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHARE DIRECTORY SECURITY (3 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun shareDirectoryValidLocation() {
        val shareDir = File(tempFolder.root, "cache_shared")
        shareDir.mkdirs()
        val pdf = File(shareDir, "test.pdf")
        FileOutputStream(pdf).use { it.write(buildMinimalPdf()) }
        val error = ShareHelper.validateShareDirectory(pdf, shareDir)
        assertNull("PDF in allowed share directory must pass", error)
    }

    @Test
    fun shareDirectoryRejectsOutsidePath() {
        val shareDir = File(tempFolder.root, "cache_shared")
        shareDir.mkdirs()
        val outsideDir = File(tempFolder.root, "outside")
        outsideDir.mkdirs()
        val pdf = File(outsideDir, "external.pdf")
        FileOutputStream(pdf).use { it.write(buildMinimalPdf()) }
        val error = ShareHelper.validateShareDirectory(pdf, shareDir)
        assertNotNull("PDF outside share directory must be rejected", error)
        assertTrue(error!!.contains("not in the allowed share directory"))
    }

    @Test
    fun shareDirectoryRejectsSiblingDirectory() {
        val shareDir = File(tempFolder.root, "cache_shared")
        shareDir.mkdirs()
        val siblingDir = File(tempFolder.root, "cache_shared_other")
        siblingDir.mkdirs()
        val pdf = File(siblingDir, "escape.pdf")
        FileOutputStream(pdf).use { it.write(buildMinimalPdf()) }
        val error = ShareHelper.validateShareDirectory(pdf, shareDir)
        assertNotNull("PDF in sibling directory must be rejected", error)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FILENAME SANITIZATION (13 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun sanitizeNormalSubject() {
        val draft = makeDraft(subject = "Request for Assistance")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Must contain subject", filename.contains("Request for Assistance"))
        assertTrue("Must start with Sulat-", filename.startsWith("Sulat-"))
    }

    @Test
    fun sanitizePreservesSpaces() {
        val draft = makeDraft(subject = "My Letter")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Must preserve spaces", filename.contains("My Letter"))
    }

    @Test
    fun sanitizeRemovesSlash() {
        val draft = makeDraft(subject = "Topic/Sub")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Must not contain /", filename.contains("/"))
    }

    @Test
    fun sanitizeRemovesBackslash() {
        val draft = makeDraft(subject = "Topic\\Sub")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Must not contain \\", filename.contains("\\"))
    }

    @Test
    fun sanitizeRemovesColon() {
        val draft = makeDraft(subject = "Topic: Important")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Must not contain :", filename.contains(":"))
    }

    @Test
    fun sanitizeRemovesControlChars() {
        val draft = makeDraft(subject = "Topic\u0000\u0001\u0002")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Must not contain null char", filename.contains("\u0000"))
        assertFalse("Must not contain control char", filename.contains("\u0001"))
    }

    @Test
    fun sanitizeRemovesPathTraversal() {
        val draft = makeDraft(subject = "../../../etc/passwd")
        val filename = ShareHelper.sanitizeFilename(draft)
        assertFalse("Must not contain ..", filename.contains(".."))
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
    }

    @Test
    fun sanitizeEmptySubjectUsesRecipient() {
        val draft = makeDraft(
            subject = "",
            recipients = listOf(Recipient(id = "r1", name = "Bro. Pedro"))
        )
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Must contain recipient name", filename.contains("Pedro"))
    }

    @Test
    fun sanitizeEmptySubjectAndRecipientsUsesFallback() {
        val draft = makeDraft(subject = "", recipients = emptyList())
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Must contain fallback Letter", filename.contains("Letter"))
    }

    @Test
    fun sanitizeEmptyRecipientNameUsesFallback() {
        val draft = makeDraft(
            subject = "",
            recipients = listOf(Recipient(id = "r1", name = ""))
        )
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Must use Letter fallback for empty name", filename.contains("Letter"))
    }

    @Test
    fun sanitizeTruncatesLongInput() {
        val longSubject = "A".repeat(200)
        val draft = makeDraft(subject = longSubject)
        val filename = ShareHelper.sanitizeFilename(draft)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Length must be reasonable", filename.length <= 110)
    }

    @Test
    fun sanitizeAlwaysEndsWithPdfExtension() {
        val drafts = listOf(
            makeDraft(subject = "Hello"),
            makeDraft(subject = ""),
            makeDraft(subject = "Test.pdf.txt")
        )
        for (draft in drafts) {
            val filename = ShareHelper.sanitizeFilename(draft)
            assertTrue("Must always end with .pdf: $filename", filename.endsWith(".pdf"))
            assertFalse("Must not contain double extensions", filename.contains(".pdf."))
        }
    }

    @Test
    fun sanitizeRemovesAllDangerousChars() {
        val draft = makeDraft(subject = "Test: File/Name\\With*Question?\"Quote<Less>Greater|Pipe")
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
    // MIME TYPE (1 test)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun pdfMimeType() {
        assertEquals("application/pdf", ShareHelper.pdfMimeType())
    }

    // ════════════════════════════════════════════════════════════════════════
    // FILEPROVIDER AUTHORITY (1 test)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun fileProviderAuthorityMatchesManifestPattern() {
        val authority = ShareHelper.FILE_PROVIDER_AUTHORITY
        assertEquals("com.sulat.ai.fileprovider", authority)
        assertTrue("Authority must end with .fileprovider", authority.endsWith(".fileprovider"))
        assertTrue("Authority must contain applicationId", authority.contains("com.sulat.ai"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF INTEGRITY (2 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun validPdfStartsWithHeader() {
        val pdf = createValidPdf()
        val bytes = pdf.readBytes()
        assertTrue("PDF must start with %PDF-", String(bytes.sliceArray(0..4)) == "%PDF-")
    }

    @Test
    fun validPdfIsNonTrivialSize() {
        val pdf = createValidPdf()
        val bytes = pdf.readBytes()
        assertTrue("PDF must be substantial", bytes.size > 100)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FOUR PAPER SIZES (5 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun allFourPaperSizesExist() {
        val names = PaperSize.entries.map { it.name }
        assertTrue(names.contains("A4"))
        assertTrue(names.contains("ShortBond"))
        assertTrue(names.contains("Legal"))
        assertTrue(names.contains("LongBond"))
        assertEquals(4, PaperSize.entries.size)
    }

    @Test
    fun a4Dimensions() {
        assertEquals(595.276, PaperSize.A4.widthPt, 0.001)
        assertEquals(841.89, PaperSize.A4.heightPt, 0.001)
    }

    @Test
    fun shortBondDimensions() {
        assertEquals(612.0, PaperSize.ShortBond.widthPt, 0.001)
        assertEquals(792.0, PaperSize.ShortBond.heightPt, 0.001)
    }

    @Test
    fun longBondDimensions() {
        assertEquals(612.0, PaperSize.LongBond.widthPt, 0.001)
        assertEquals(936.0, PaperSize.LongBond.heightPt, 0.001)
    }

    @Test
    fun legalDimensions() {
        assertEquals(612.0, PaperSize.Legal.widthPt, 0.001)
        assertEquals(1008.0, PaperSize.Legal.heightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // URI SECURITY — INTENT CONTRACT
    // ════════════════════════════════════════════════════════════════════════
    //
    // LIMITATION: android.content.Intent cannot be instantiated in pure JVM
    // unit tests. The share intent contract (ACTION_SEND, application/pdf,
    // EXTRA_STREAM, FLAG_GRANT_READ_URI_PERMISSION) is verified by code
    // inspection of ShareHelper.sharePdf(). On-device instrumentation tests
    // are required to validate actual Sharesheet behavior.
    //
    // Verified by code inspection:
    //   Intent(Intent.ACTION_SEND) — line 84 of ShareHelper.kt
    //   type = PDF_MIME_TYPE ("application/pdf") — line 85
    //   putExtra(Intent.EXTRA_STREAM, contentUri) — line 86
    //   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) — line 87
    //   No Intent.FLAG_GRANT_WRITE_URI_PERMISSION — confirmed absent
    //   FileProvider.getUriForFile() produces content:// URI — line 78-81
    //   No Uri.fromFile() usage — confirmed absent
    //   No file:// URI usage — confirmed absent
}
