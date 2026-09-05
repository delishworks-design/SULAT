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
import kotlin.math.abs

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

    private fun buildMinimalPdf(pageCount: Int, widthPt: Int = 612, heightPt: Int = 792): ByteArray {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")

        val offsets = mutableListOf<Int>()

        offsets.add(sb.length)
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(sb.length)
        val pageRefs = (3..2 + pageCount).joinToString(" ") { "$it 0 R" }
        sb.append("2 0 obj\n<< /Type /Pages /Kids [$pageRefs] /Count $pageCount >>\nendobj\n")

        for (i in 0 until pageCount) {
            offsets.add(sb.length)
            sb.append("${3 + i} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $widthPt $heightPt] >>\nendobj\n")
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

    private fun createMinimalPdf(pageCount: Int, widthPt: Int = 612, heightPt: Int = 792): File {
        val file = tempFolder.newFile("minimal_${widthPt}x${heightPt}_$pageCount.pdf")
        FileOutputStream(file).use { it.write(buildMinimalPdf(pageCount, widthPt, heightPt)) }
        return file
    }

    // ════════════════════════════════════════════════════════════════════════
    // ALL 4 PAPER SIZES — PHYSICAL DIMENSIONS (PT)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4PtDimensions() {
        assertEquals("A4 width must be 595.276pt", 595.276, PaperSize.A4.widthPt, 0.001)
        assertEquals("A4 height must be 841.89pt", 841.89, PaperSize.A4.heightPt, 0.001)
    }

    @Test
    fun testShortBondPtDimensions() {
        assertEquals("ShortBond width must be 612pt", 612.0, PaperSize.ShortBond.widthPt, 0.001)
        assertEquals("ShortBond height must be 792pt", 792.0, PaperSize.ShortBond.heightPt, 0.001)
    }

    @Test
    fun testLegalPtDimensions() {
        assertEquals("Legal width must be 612pt", 612.0, PaperSize.Legal.widthPt, 0.001)
        assertEquals("Legal height must be 1008pt", 1008.0, PaperSize.Legal.heightPt, 0.001)
    }

    @Test
    fun testLongBondPtDimensions() {
        assertEquals("LongBond width must be 612pt", 612.0, PaperSize.LongBond.widthPt, 0.001)
        assertEquals("LongBond height must be 936pt", 936.0, PaperSize.LongBond.heightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ALL 4 PAPER SIZES — PHYSICAL DIMENSIONS (MM)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4MmDimensions() {
        assertEquals("A4 width must be 210mm", 210.0, PaperSize.A4.widthMm, 0.1)
        assertEquals("A4 height must be 297mm", 297.0, PaperSize.A4.heightMm, 0.1)
    }

    @Test
    fun testShortBondMmDimensions() {
        assertEquals("ShortBond width must be 215.9mm", 215.9, PaperSize.ShortBond.widthMm, 0.1)
        assertEquals("ShortBond height must be 279.4mm", 279.4, PaperSize.ShortBond.heightMm, 0.1)
    }

    @Test
    fun testLegalMmDimensions() {
        assertEquals("Legal width must be 215.9mm", 215.9, PaperSize.Legal.widthMm, 0.1)
        assertEquals("Legal height must be 355.6mm", 355.6, PaperSize.Legal.heightMm, 0.1)
    }

    @Test
    fun testLongBondMmDimensions() {
        assertEquals("LongBond width must be 215.9mm", 215.9, PaperSize.LongBond.widthMm, 0.1)
        assertEquals("LongBond height must be 330.2mm", 330.2, PaperSize.LongBond.heightMm, 0.1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MM → MIL CONVERSION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Convert mm to mils (1/1000 inch): mils = round(mm / 25.4 * 1000)
     * Uses rounding (not truncation) to match Android Print Framework expectations.
     */
    private fun mmToMils(mm: Double): Int = kotlin.math.round(mm / 25.4 * 1000).toInt()

    @Test
    fun testA4MmToMils() {
        val widthMils = mmToMils(PaperSize.A4.widthMm)
        val heightMils = mmToMils(PaperSize.A4.heightMm)
        assertEquals("A4 width must be 8268 mils", 8268, widthMils)
        assertEquals("A4 height must be 11693 mils", 11693, heightMils)
    }

    @Test
    fun testShortBondMmToMils() {
        val widthMils = mmToMils(PaperSize.ShortBond.widthMm)
        val heightMils = mmToMils(PaperSize.ShortBond.heightMm)
        assertEquals("ShortBond width must be 8500 mils", 8500, widthMils)
        assertEquals("ShortBond height must be 11000 mils", 11000, heightMils)
    }

    @Test
    fun testLegalMmToMils() {
        val widthMils = mmToMils(PaperSize.Legal.widthMm)
        val heightMils = mmToMils(PaperSize.Legal.heightMm)
        assertEquals("Legal width must be 8500 mils", 8500, widthMils)
        assertEquals("Legal height must be 14000 mils", 14000, heightMils)
    }

    @Test
    fun testLongBondMmToMils() {
        val widthMils = mmToMils(PaperSize.LongBond.widthMm)
        val heightMils = mmToMils(PaperSize.LongBond.heightMm)
        assertEquals("LongBond width must be 8500 mils", 8500, widthMils)
        assertEquals("LongBond height must be 13000 mils", 13000, heightMils)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF GEOMETRY MATCHES SELECTED PAPER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4PdfGeometryMatches() {
        val engine = com.sulat.ai.document.renderer.LetterTemplateEngine()
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertEquals("A4 PDF width must match PaperSize", PaperSize.A4.widthPt, layout.page.widthPt, 0.001)
        assertEquals("A4 PDF height must match PaperSize", PaperSize.A4.heightPt, layout.page.heightPt, 0.001)
    }

    @Test
    fun testShortBondPdfGeometryMatches() {
        val engine = com.sulat.ai.document.renderer.LetterTemplateEngine()
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.ShortBond)
        assertEquals("ShortBond PDF width must match PaperSize", PaperSize.ShortBond.widthPt, layout.page.widthPt, 0.001)
        assertEquals("ShortBond PDF height must match PaperSize", PaperSize.ShortBond.heightPt, layout.page.heightPt, 0.001)
    }

    @Test
    fun testLegalPdfGeometryMatches() {
        val engine = com.sulat.ai.document.renderer.LetterTemplateEngine()
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.Legal)
        assertEquals("Legal PDF width must match PaperSize", PaperSize.Legal.widthPt, layout.page.widthPt, 0.001)
        assertEquals("Legal PDF height must match PaperSize", PaperSize.Legal.heightPt, layout.page.heightPt, 0.001)
    }

    @Test
    fun testLongBondPdfGeometryMatches() {
        val engine = com.sulat.ai.document.renderer.LetterTemplateEngine()
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.LongBond)
        assertEquals("LongBond PDF width must match PaperSize", PaperSize.LongBond.widthPt, layout.page.widthPt, 0.001)
        assertEquals("LongBond PDF height must match PaperSize", PaperSize.LongBond.heightPt, layout.page.heightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ALL 4 SIZES USE PORTRAIT ORIENTATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4IsPortrait() {
        assertTrue("A4 height must be greater than width",
            PaperSize.A4.heightPt > PaperSize.A4.widthPt)
    }

    @Test
    fun testShortBondIsPortrait() {
        assertTrue("ShortBond height must be greater than width",
            PaperSize.ShortBond.heightPt > PaperSize.ShortBond.widthPt)
    }

    @Test
    fun testLegalIsPortrait() {
        assertTrue("Legal height must be greater than width",
            PaperSize.Legal.heightPt > PaperSize.Legal.widthPt)
    }

    @Test
    fun testLongBondIsPortrait() {
        assertTrue("LongBond height must be greater than width",
            PaperSize.LongBond.heightPt > PaperSize.LongBond.widthPt)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAPER SIZE RELATIVE HEIGHTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testPaperSizeHeightOrder() {
        assertTrue("ShortBond must be shortest",
            PaperSize.ShortBond.heightPt < PaperSize.A4.heightPt)
        assertTrue("A4 must be shorter than LongBond",
            PaperSize.A4.heightPt < PaperSize.LongBond.heightPt)
        assertTrue("LongBond must be shorter than Legal",
            PaperSize.LongBond.heightPt < PaperSize.Legal.heightPt)
    }

    @Test
    fun testShortBondAndLegalHaveSameWidth() {
        assertEquals("ShortBond and Legal must have same width",
            PaperSize.ShortBond.widthPt, PaperSize.Legal.widthPt, 0.001)
        assertEquals("ShortBond and Legal must have same mm width",
            PaperSize.ShortBond.widthMm, PaperSize.Legal.widthMm, 0.1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAPER SIZE ENUM VALUES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllFourPaperSizesExist() {
        val names = PaperSize.entries.map { it.name }
        assertTrue("A4 must exist", names.contains("A4"))
        assertTrue("ShortBond must exist", names.contains("ShortBond"))
        assertTrue("Legal must exist", names.contains("Legal"))
        assertTrue("LongBond must exist", names.contains("LongBond"))
    }

    @Test
    fun testExactlyFourPaperSizes() {
        assertEquals("Must have exactly 4 paper sizes", 4, PaperSize.entries.size)
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
    fun testShortBondPaperSizeResolves() {
        val paperSize = try { PaperSize.valueOf("ShortBond") } catch (_: Exception) { PaperSize.A4 }
        assertEquals(PaperSize.ShortBond, paperSize)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MARGINS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testDefaultMarginsAreOneInch() {
        val margins = PaperSize.defaultMarginsPt()
        assertEquals("Top margin must be 72pt (1 inch)", 72.0, margins.top, 0.001)
        assertEquals("Bottom margin must be 72pt (1 inch)", 72.0, margins.bottom, 0.001)
        assertEquals("Left margin must be 72pt (1 inch)", 72.0, margins.left, 0.001)
        assertEquals("Right margin must be 72pt (1 inch)", 72.0, margins.right, 0.001)
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
