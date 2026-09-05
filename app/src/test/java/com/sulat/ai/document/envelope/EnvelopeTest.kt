package com.sulat.ai.document.envelope

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.RecipientNameHierarchy
import com.sulat.ai.document.renderer.PdfTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class EnvelopeTest {

    private fun makeRecipient(
        name: String = "KA. JUAN DELA CRUZ",
        position: String = "Minister",
        organization: String = "Local Congregation of Manila",
        address: String = "123 Rizal Avenue, Manila",
        optionalInfo: String = ""
    ): Recipient {
        return Recipient(
            id = "r1",
            name = name,
            position = position,
            organization = organization,
            address = address,
            optionalInfo = optionalInfo
        )
    }

    private fun makeDraft(
        recipients: List<Recipient> = listOf(makeRecipient())
    ): LetterDraft {
        return LetterDraft(
            id = "test-draft-id",
            recipients = recipients,
            dates = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")),
            body = "Hello world.",
            subject = "Test Subject",
            greeting = "Dear Kapatid",
            sender = SenderProfile(name = "Sender Name", signature = "Faithfully")
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECIPIENT DATA (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun envelopeDataFromSingleRecipient() {
        val recipient = makeRecipient()
        val data = EnvelopeData.fromRecipient(recipient)
        assertNotNull(data)
        assertEquals("KA. JUAN DELA CRUZ", data!!.recipient.name)
        assertEquals("KA.", data.nameHierarchy.prefix)
        assertEquals("JUAN DELA CRUZ", data.nameHierarchy.mainName)
    }

    @Test
    fun envelopeDataFromDraftWithMultipleRecipients() {
        val r1 = makeRecipient(name = "KA. JUAN DELA CRUZ")
        val r2 = makeRecipient(name = "BRO. PEDRO SANTOS", position = "Secretary")
        val r3 = makeRecipient(name = "SIS. MARIA GARCIA")
        val draft = makeDraft(recipients = listOf(r1, r2, r3))
        val envelopeDataList = EnvelopeData.fromDraft(draft)
        assertEquals(3, envelopeDataList.size)
        assertEquals("KA.", envelopeDataList[0].nameHierarchy.prefix)
        assertEquals("BRO.", envelopeDataList[1].nameHierarchy.prefix)
        assertEquals("SIS.", envelopeDataList[2].nameHierarchy.prefix)
    }

    @Test
    fun envelopeDataPreservesRecipientOrder() {
        val names = listOf("First Person", "Second Person", "Third Person")
        val recipients = names.map { makeRecipient(name = it) }
        val draft = makeDraft(recipients = recipients)
        val envelopeDataList = EnvelopeData.fromDraft(draft)
        assertEquals(3, envelopeDataList.size)
        for (i in 0 until 3) {
            assertEquals(names[i], envelopeDataList[i].recipient.name)
        }
    }

    @Test
    fun envelopeDataZeroRecipientsRejected() {
        val draft = makeDraft(recipients = emptyList())
        val envelopeDataList = EnvelopeData.fromDraft(draft)
        assertTrue("Zero recipients must produce empty list", envelopeDataList.isEmpty())
    }

    @Test
    fun envelopeDataMissingNameHandled() {
        val recipient = makeRecipient(name = "")
        val data = EnvelopeData.fromRecipient(recipient)
        assertNull("Empty name must return null", data)
    }

    @Test
    fun envelopeDataOptionalFieldsOmittedWhenEmpty() {
        val recipient = makeRecipient(optionalInfo = "")
        val data = EnvelopeData.fromRecipient(recipient)!!
        assertEquals("", data.recipient.optionalInfo)
    }

    // ════════════════════════════════════════════════════════════════════════
    // NAME HIERARCHY (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun nameHierarchyKaPrefix() {
        val h = EnvelopeData.parseNameHierarchy("KA. JUAN DELA CRUZ")
        assertEquals("KA.", h.prefix)
        assertEquals("JUAN DELA CRUZ", h.mainName)
    }

    @Test
    fun nameHierarchyBroPrefix() {
        val h = EnvelopeData.parseNameHierarchy("BRO. PEDRO SANTOS")
        assertEquals("BRO.", h.prefix)
        assertEquals("PEDRO SANTOS", h.mainName)
    }

    @Test
    fun nameHierarchySisPrefix() {
        val h = EnvelopeData.parseNameHierarchy("SIS. MARIA GARCIA")
        assertEquals("SIS.", h.prefix)
        assertEquals("MARIA GARCIA", h.mainName)
    }

    @Test
    fun nameHierarchyOrdinaryName() {
        val h = EnvelopeData.parseNameHierarchy("JUAN DELA CRUZ")
        assertEquals("", h.prefix)
        assertEquals("JUAN DELA CRUZ", h.mainName)
    }

    @Test
    fun nameHierarchyNoArtificialSplitting() {
        val h = EnvelopeData.parseNameHierarchy("Angel de la Cruz")
        assertEquals("", h.prefix)
        assertEquals("Angel de la Cruz", h.mainName)
    }

    @Test
    fun nameHierarchyEmptyName() {
        val h = EnvelopeData.parseNameHierarchy("")
        assertEquals("", h.prefix)
        assertEquals("", h.mainName)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ADDRESS HANDLING (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun addressShortAddress() {
        val r = makeRecipient(address = "123 Rizal Ave, Manila")
        val data = EnvelopeData.fromRecipient(r)!!
        assertEquals("123 Rizal Ave, Manila", data.recipient.address)
    }

    @Test
    fun addressMultilinePreservesLineBreaks() {
        val r = makeRecipient(address = "123 Rizal Ave\nBarangay 123\nManila, Philippines")
        val data = EnvelopeData.fromRecipient(r)!!
        assertTrue("Must preserve newline characters", data.recipient.address.contains("\n"))
        val lines = data.recipient.address.split("\n")
        assertEquals(3, lines.size)
    }

    @Test
    fun addressLongAddress() {
        val longAddr = "A".repeat(500)
        val r = makeRecipient(address = longAddr)
        val data = EnvelopeData.fromRecipient(r)!!
        assertEquals(500, data.recipient.address.length)
    }

    @Test
    fun addressUnicodeSafe() {
        val r = makeRecipient(address = "123 \u00D1o\u00F1o Street, S\u00E3o Paulo, \u00DCberlingen")
        val data = EnvelopeData.fromRecipient(r)!!
        assertTrue(data.recipient.address.contains("\u00D1"))
        assertTrue(data.recipient.address.contains("\u00E3"))
        assertTrue(data.recipient.address.contains("\u00DC"))
    }

    @Test
    fun addressLongUnbreakableToken() {
        val r = makeRecipient(address = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ")
        val data = EnvelopeData.fromRecipient(r)!!
        assertEquals(52, data.recipient.address.length)
    }

    @Test
    fun addressExplicitLineBreaksPreserved() {
        val r = makeRecipient(address = "Line1\n\nLine3")
        val data = EnvelopeData.fromRecipient(r)!!
        val segments = data.recipient.address.split("\n")
        assertEquals(3, segments.size)
        assertEquals("", segments[1])
    }

    // ════════════════════════════════════════════════════════════════════════
    // LAYOUT (5 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun layoutDeterministicForSameInput() {
        val l1 = EnvelopeLayout.create(PaperSize.A4)
        val l2 = EnvelopeLayout.create(PaperSize.A4)
        assertEquals(l1, l2)
    }

    @Test
    fun layoutCorrectPageDimensionsA4() {
        val layout = EnvelopeLayout.create(PaperSize.A4)
        assertEquals(595.276, layout.page.widthPt, 0.001)
        assertEquals(841.89, layout.page.heightPt, 0.001)
    }

    @Test
    fun layoutCorrectPageDimensionsShortBond() {
        val layout = EnvelopeLayout.create(PaperSize.ShortBond)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(792.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun layoutLabelWithinPageBounds() {
        val layout = EnvelopeLayout.create(PaperSize.A4)
        assertTrue("Label X must be within page", layout.labelOriginXPt < layout.page.widthPt)
        assertTrue("Label Y must be within page", layout.labelOriginYPt < layout.page.heightPt)
        assertTrue("Label width must be positive", layout.labelMaxWidthPt > 0)
    }

    @Test
    fun layoutStylesHaveCorrectDefaults() {
        val styles = EnvelopeStyles()
        assertEquals(12.0, styles.prefixStyle.fontSizePt, 0.001)
        assertTrue(styles.prefixStyle.isBold)
        assertEquals(14.0, styles.nameStyle.fontSizePt, 0.001)
        assertTrue(styles.nameStyle.isBold)
        assertEquals(11.0, styles.positionStyle.fontSizePt, 0.001)
        assertFalse(styles.positionStyle.isBold)
        assertEquals(10.0, styles.addressStyle.fontSizePt, 0.001)
        assertFalse(styles.addressStyle.isBold)
        assertTrue(styles.optionalStyle.isItalic)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FOUR PAPER SIZES (4 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun layoutA4Dimensions() {
        val layout = EnvelopeLayout.create(PaperSize.A4)
        assertEquals(595.276, layout.page.widthPt, 0.001)
        assertEquals(841.89, layout.page.heightPt, 0.001)
    }

    @Test
    fun layoutShortBondDimensions() {
        val layout = EnvelopeLayout.create(PaperSize.ShortBond)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(792.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun layoutLongBondDimensions() {
        val layout = EnvelopeLayout.create(PaperSize.LongBond)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(936.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun layoutLegalDimensions() {
        val layout = EnvelopeLayout.create(PaperSize.Legal)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(1008.0, layout.page.heightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ENVELOPE RENDERER — LIMITATIONS (0 tests)
    // ════════════════════════════════════════════════════════════════════════
    //
    // LIMITATION: EnvelopeRenderer uses android.graphics.Canvas and
    // android.graphics.pdf.PdfDocument, which require the Android framework.
    // The following behaviors CANNOT be validated by JVM unit tests and
    // require on-device instrumentation tests:
    //
    // - PDF file generation (PdfDocument.writeTo)
    // - Actual Canvas drawing of text
    // - PDF page count matching recipient count
    // - PDF page dimensions matching paper size
    // - Valid PDF header (%PDF-)
    // - Non-empty PDF output
    // - Recipient text content in the rendered PDF
    //
    // Code inspection confirms:
    //   EnvelopeRenderer.renderEnvelopePdf() uses PdfDocument API
    //   One PdfDocument.Page per EnvelopeData recipient
    //   PdfDocument.PageInfo.Builder(width, height, pageNumber) matches paper size
    //   canvas.drawText() renders each text element at correct position
    //   typefaceForStyle() from PdfTypography ensures consistent rendering

    // ════════════════════════════════════════════════════════════════════════
    // FILENAME (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun filenameSafeBasic() {
        val filename = EnvelopeFilename.generate("JUAN DELA CRUZ")
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Must start with Sulat-Envelope-", filename.startsWith("Sulat-Envelope-"))
        assertTrue("Must contain recipient name", filename.contains("JUAN DELA CRUZ"))
    }

    @Test
    fun filenameDangerousCharsRemoved() {
        val filename = EnvelopeFilename.generate("Name/With\\Colon:Stars*Question?")
        assertFalse(filename.contains("/"))
        assertFalse(filename.contains("\\"))
        assertFalse(filename.contains(":"))
        assertFalse(filename.contains("*"))
        assertFalse(filename.contains("?"))
        assertTrue(filename.endsWith(".pdf"))
    }

    @Test
    fun filenameLongNameTruncated() {
        val longName = "A".repeat(200)
        val filename = EnvelopeFilename.generate(longName)
        assertTrue("Must end with .pdf", filename.endsWith(".pdf"))
        assertTrue("Length must be reasonable", filename.length <= 110)
    }

    @Test
    fun filenameAlwaysEndsWithPdf() {
        val names = listOf("Normal", "", "Name.pdf.txt", "A".repeat(300))
        for (name in names) {
            val filename = EnvelopeFilename.generate(name)
            assertTrue("Must end with .pdf: $filename", filename.endsWith(".pdf"))
        }
    }

    @Test
    fun filenameEmptyNameUsesFallback() {
        val filename = EnvelopeFilename.generate("")
        assertTrue("Must use fallback", filename.contains("Recipient"))
        assertTrue(filename.endsWith(".pdf"))
    }

    @Test
    fun filenameContainsDate() {
        val filename = EnvelopeFilename.generate("Test", date = 1700000000000L)
        assertTrue("Must contain date", filename.contains("2023"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECIPIENT NAME HIERARCHY — EXTENDED (2 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun nameHierarchyMinPrefix() {
        val h = EnvelopeData.parseNameHierarchy("MIN. PASTOR SANTOS")
        assertEquals("MIN.", h.prefix)
        assertEquals("PASTOR SANTOS", h.mainName)
    }

    @Test
    fun nameHierarchyCaseInsensitive() {
        val h = EnvelopeData.parseNameHierarchy("ka. juan dela cruz")
        assertEquals("KA.", h.prefix)
        assertEquals("juan dela cruz", h.mainName)
    }
}
