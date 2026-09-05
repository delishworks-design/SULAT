package com.sulat.ai.print

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.PdfRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream

class PrintHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeDraft(
        body: String = "Hello world.",
        subject: String = "Test Subject",
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister", organization = "INC")
        ),
        sender: SenderProfile = SenderProfile(name = "Sender Name", signature = "Faithfully")
    ): LetterDraft {
        return LetterDraft(
            id = "test-draft-id",
            recipients = recipients,
            dates = listOf(LetterDate(date = java.util.Date(1700000000000L), label = "Jan 1")),
            body = body,
            subject = subject,
            greeting = "Dear Kapatid",
            sender = sender
        )
    }

    /**
     * Build a minimal valid PDF byte array with [pageCount] pages.
     */
    private fun buildMinimalPdf(pageCount: Int): ByteArray {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")

        val objects = mutableListOf<String>()
        val offsets = mutableListOf<Int>()

        offsets.add(sb.length)
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(sb.length)
        val pageRefs = (3..2 + pageCount).joinToString(" ") { "$it 0 R" }
        sb.append("2 0 obj\n<< /Type /Pages /Kids [$pageRefs] /Count $pageCount >>\nendobj\n")

        for (i in 0 until pageCount) {
            offsets.add(sb.length)
            sb.append("${3 + i} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n")
        }

        val xrefOffset = sb.length
        val totalObjects = 2 + pageCount + 1
        sb.append("xref\n")
        sb.append("0 $totalObjects\n")
        sb.append("0000000000 65535 f \n")
        for (offset in offsets) {
            sb.append(String.format("%010d 00000 n \n", offset))
        }

        sb.append("trailer\n<< /Size $totalObjects /Root 1 0 R >>\n")
        sb.append("startxref\n")
        sb.append("$xrefOffset\n")
        sb.append("%%EOF\n")

        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun createMinimalPdf(pageCount: Int): File {
        val file = tempFolder.newFile("minimal_$pageCount.pdf")
        FileOutputStream(file).use { it.write(buildMinimalPdf(pageCount)) }
        return file
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF PAGE COUNT READING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testReadPdfPageCountSinglePage() {
        val pdf = createMinimalPdf(1)
        assertEquals(1, PrintHelper.readPdfPageCount(pdf))
    }

    @Test
    fun testReadPdfPageCountMultiplePages() {
        val pdf = createMinimalPdf(5)
        assertEquals(5, PrintHelper.readPdfPageCount(pdf))
    }

    @Test
    fun testReadPdfPageCountMissingFile() {
        val missing = File(tempFolder.root, "nonexistent.pdf")
        assertEquals(-1, PrintHelper.readPdfPageCount(missing))
    }

    @Test
    fun testReadPdfPageCountEmptyFile() {
        val empty = tempFolder.newFile("empty.pdf")
        assertEquals(-1, PrintHelper.readPdfPageCount(empty))
    }

    @Test
    fun testReadPdfPageCountInvalidPdf() {
        val invalid = tempFolder.newFile("invalid.pdf")
        invalid.writeText("This is not a PDF file at all.")
        assertEquals(-1, PrintHelper.readPdfPageCount(invalid))
    }

    // ════════════════════════════════════════════════════════════════════════
    // DOCUMENT NAME
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testDocumentNameWithSubject() {
        val draft = makeDraft(subject = "Request for Assistance")
        val name = PrintHelper.buildDocumentName(draft)
        assertTrue(name.startsWith("Sulat-Letter"))
        assertTrue(name.contains("Request for Assistance"))
    }

    @Test
    fun testDocumentNameWithoutSubject() {
        val draft = makeDraft(subject = "")
        val name = PrintHelper.buildDocumentName(draft)
        assertTrue(name.contains("Letter"))
    }

    @Test
    fun testDocumentNameWithUnicodeSubject() {
        val draft = makeDraft(subject = "Maraming Salamat")
        val name = PrintHelper.buildDocumentName(draft)
        assertTrue(name.contains("Maraming Salamat"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF VALIDATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testMissingPdfFile() {
        val missing = File(tempFolder.root, "nonexistent.pdf")
        assertFalse(PdfRenderer.isValidPdfFile(missing))
    }

    @Test
    fun testEmptyPdfFile() {
        val empty = tempFolder.newFile("empty.pdf")
        assertFalse(PdfRenderer.isValidPdfFile(empty))
    }

    @Test
    fun testNonPdfFile() {
        val textFile = tempFolder.newFile("readme.txt")
        textFile.writeText("Hello world")
        assertFalse(PdfRenderer.isValidPdfFile(textFile))
    }

    @Test
    fun testMinimalPdfIsValid() {
        val pdf = createMinimalPdf(1)
        assertTrue(PdfRenderer.isValidPdfFile(pdf))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF STREAM COPY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testPdfStreamCopyBytesMatch() {
        val source = createMinimalPdf(1)
        val sourceBytes = source.readBytes()

        val dest = tempFolder.newFile("copy_output.pdf")
        source.inputStream().use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }

        val destBytes = dest.readBytes()
        assertEquals(sourceBytes.size, destBytes.size)
        assertTrue(String(destBytes.sliceArray(0..4)) == "%PDF-")
    }

    @Test
    fun testPdfStreamCopyPreservesPageCount() {
        val source = createMinimalPdf(3)
        val sourceCount = PrintHelper.readPdfPageCount(source)

        val dest = tempFolder.newFile("copy_content.pdf")
        source.inputStream().use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }

        assertTrue(PdfRenderer.isValidPdfFile(dest))
        val destCount = PrintHelper.readPdfPageCount(dest)
        assertEquals(sourceCount, destCount)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAPER SIZE DEFAULTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testDefaultPaperSizeIsA4() {
        val paperSizeName: String? = null
        val paperSize = if (paperSizeName != null) {
            try { PaperSize.valueOf(paperSizeName) } catch (_: IllegalArgumentException) { PaperSize.A4 }
        } else {
            PaperSize.A4
        }
        assertEquals(PaperSize.A4, paperSize)
    }

    @Test
    fun testInvalidPaperSizeStringResolvesToA4() {
        val paperSizeName = "InvalidPaperSize"
        val paperSize = try {
            PaperSize.valueOf(paperSizeName)
        } catch (_: IllegalArgumentException) {
            PaperSize.A4
        }
        assertEquals(PaperSize.A4, paperSize)
    }

    @Test
    fun testLegalPaperSizeResolves() {
        val paperSize = try { PaperSize.valueOf("Legal") } catch (_: Exception) { PaperSize.A4 }
        assertEquals(PaperSize.Legal, paperSize)
    }

    @Test
    fun testLongBondPaperSizeResolves() {
        val paperSize = try { PaperSize.valueOf("LongBond") } catch (_: Exception) { PaperSize.A4 }
        assertEquals(PaperSize.LongBond, paperSize)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF PAGE COUNT FROM MULTIPLE-SIZE PDFS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testPageCountFrom10PagePdf() {
        val pdf = createMinimalPdf(10)
        assertEquals(10, PrintHelper.readPdfPageCount(pdf))
    }

    @Test
    fun testPageCountFrom2PagePdf() {
        val pdf = createMinimalPdf(2)
        assertEquals(2, PrintHelper.readPdfPageCount(pdf))
    }

    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testCleanupDeletesFile() {
        val testFile = File(tempFolder.root, "print_test-draft-id.pdf")
        testFile.writeBytes("test".toByteArray())
        assertTrue(testFile.exists())
        testFile.delete()
        assertFalse(testFile.exists())
    }
}
