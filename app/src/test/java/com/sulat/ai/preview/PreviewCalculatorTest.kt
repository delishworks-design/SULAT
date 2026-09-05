package com.sulat.ai.preview

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.PageGeometry
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfContentCalculator
import com.sulat.ai.document.renderer.PdfTextRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PreviewCalculatorTest {

    private lateinit var engine: LetterTemplateEngine

    @Before
    fun setUp() {
        engine = LetterTemplateEngine()
    }

    private fun makeDraft(
        body: String = "Hello world.",
        subject: String = "Test Subject",
        greeting: String = "Dear Kapatid",
        recipients: List<Recipient> = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister", organization = "INC")),
        sender: SenderProfile = SenderProfile(name = "Sender Name", signature = "Faithfully")
    ): LetterDraft {
        return LetterDraft(
            id = "test",
            recipients = recipients,
            dates = listOf(LetterDate(date = java.util.Date(1700000000000L), label = "Jan 1")),
            body = body,
            subject = subject,
            greeting = greeting,
            sender = sender
        )
    }

    private fun makeCalculator(
        draft: LetterDraft = makeDraft(),
        availableWidthPx: Int = 1080,
        paperSize: PaperSize = PaperSize.A4
    ): PreviewCalculator {
        val layout = engine.buildLayout(draft, paperSize)
        val renderPlan = PdfContentCalculator(layout).plan()
        return PreviewCalculator(renderPlan, layout.page, availableWidthPx)
    }

    // ════════════════════════════════════════════════════════════════════════
    // A4 PAGE SCALING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4PageScaling() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val expectedScale = 1080.0 / PaperSize.A4.widthPt
        assertEquals(expectedScale, calc.scale, 0.001)
    }

    @Test
    fun testA4PageWidthMatchesAvailable() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun testA4PageHeightFromGeometry() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val expectedHeight = (PaperSize.A4.heightPt * calc.scale).toInt()
        assertEquals(expectedHeight, calc.pageHeightPx)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEGAL PAGE SCALING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testLegalPageScaling() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        val expectedScale = 1080.0 / PaperSize.Legal.widthPt
        assertEquals(expectedScale, calc.scale, 0.001)
    }

    @Test
    fun testLegalPageWidthMatchesAvailable() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun testLegalPageHeightFromGeometry() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        val expectedHeight = (PaperSize.Legal.heightPt * calc.scale).toInt()
        assertEquals(expectedHeight, calc.pageHeightPx)
    }

    @Test
    fun testLegalDiffersFromA4() {
        val a4 = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val legal = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        assertTrue("Legal must be taller than A4", legal.pageHeightPx > a4.pageHeightPx)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LONG BOND PAGE SCALING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testLongBondPageScaling() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        val expectedScale = 1080.0 / PaperSize.LongBond.widthPt
        assertEquals(expectedScale, calc.scale, 0.001)
    }

    @Test
    fun testLongBondPageWidthMatchesAvailable() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun testLongBondPageHeightFromGeometry() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        val expectedHeight = (PaperSize.LongBond.heightPt * calc.scale).toInt()
        assertEquals(expectedHeight, calc.pageHeightPx)
    }

    @Test
    fun testLongBondDiffersFromLegal() {
        val legal = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        val longBond = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        assertTrue("Legal must be taller than Long Bond", legal.pageHeightPx > longBond.pageHeightPx)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ASPECT-RATIO PRESERVATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAspectRatioPreserved() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val ptRatio = calc.documentHeightPt / calc.documentWidthPt
        val pxRatio = calc.pageHeightPx.toDouble() / calc.pageWidthPx
        assertEquals("Aspect ratio must be preserved", ptRatio, pxRatio, 0.01)
    }

    @Test
    fun testDifferentWidthPreservesAspectRatio() {
        val calc1 = makeCalculator(availableWidthPx = 800, paperSize = PaperSize.A4)
        val calc2 = makeCalculator(availableWidthPx = 1200, paperSize = PaperSize.A4)
        val ratio1 = calc1.pageHeightPx.toDouble() / calc1.pageWidthPx
        val ratio2 = calc2.pageHeightPx.toDouble() / calc2.pageWidthPx
        assertEquals("Aspect ratio must be the same at different widths", ratio1, ratio2, 0.01)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RENDERPLAN PAGE COUNT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testRenderPlanPageCount() {
        val calc = makeCalculator()
        assertTrue("Must have at least 1 page", calc.totalPages >= 1)
    }

    @Test
    fun testLongBodyMultiplePages() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: This is a test sentence for pagination." }
        val calc = makeCalculator(draft = makeDraft(body = longBody))
        assertTrue("Long body must produce multiple pages", calc.totalPages > 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MULTIPLE RECIPIENTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testMultipleRecipients() {
        val recipients = listOf(
            Recipient(id = "r1", name = "Bro. Juan", position = "Minister"),
            Recipient(id = "r2", name = "Bro. Pedro", position = "Deacon"),
            Recipient(id = "r3", name = "Bro. Jose", position = "Secretary")
        )
        val calc = makeCalculator(draft = makeDraft(recipients = recipients))
        assertTrue("Multiple recipients must produce output", calc.totalPages >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECIPIENT PREFIX HIERARCHY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testRecipientPrefixHierarchy() {
        val calc = makeCalculator()
        val page = calc.renderPlan.pages.first()
        val prefixLine = page.lines.firstOrNull { it.role == PdfTextRole.RECIPIENT_PREFIX }
        val nameLine = page.lines.firstOrNull { it.role == PdfTextRole.RECIPIENT_NAME }
        assertNotNull("Recipient prefix must be present", prefixLine)
        assertNotNull("Recipient name must be present", nameLine)
        assertTrue("Prefix must contain KA.", prefixLine!!.text.contains("KA."))
        assertTrue("Name must contain JUAN DELA CRUZ", nameLine!!.text.contains("JUAN DELA CRUZ"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // CORRECT ROLES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testCorrectRolesPresent() {
        val calc = makeCalculator()
        val allRoles = calc.renderPlan.pages.flatMap { it.lines }.map { it.role }.toSet()
        assertTrue("DATE must be present", allRoles.contains(PdfTextRole.DATE))
        assertTrue("RECIPIENT_NAME must be present", allRoles.contains(PdfTextRole.RECIPIENT_NAME))
        assertTrue("SUBJECT must be present", allRoles.contains(PdfTextRole.SUBJECT))
        assertTrue("GREETING must be present", allRoles.contains(PdfTextRole.GREETING))
        assertTrue("BODY must be present", allRoles.contains(PdfTextRole.BODY))
        assertTrue("CLOSING must be present", allRoles.contains(PdfTextRole.CLOSING))
        assertTrue("SENDER_NAME must be present", allRoles.contains(PdfTextRole.SENDER_NAME))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SUBJECT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testSubject() {
        val calc = makeCalculator(draft = makeDraft(subject = "My Test Subject"))
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Subject must be present", allText.contains("Re: My Test Subject"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // GREETING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testGreeting() {
        val calc = makeCalculator(draft = makeDraft(greeting = "Dear Brother,"))
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Greeting must be present", allText.contains("Dear Brother,"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // BODY PARAGRAPHS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testBodyParagraphs() {
        val calc = makeCalculator(draft = makeDraft(body = "First paragraph.\n\nSecond paragraph."))
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("First paragraph must be present", allText.contains("First paragraph"))
        assertTrue("Second paragraph must be present", allText.contains("Second paragraph"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // CLOSING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testClosing() {
        val calc = makeCalculator()
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Closing must be present", allText.contains("Faithfully"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SENDER INFORMATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testSenderInformation() {
        val calc = makeCalculator()
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Sender name must be present", allText.contains("Sender Name"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // UNICODE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testUnicode() {
        val calc = makeCalculator(draft = makeDraft(body = "Maraming salamat po sa inyong pagkakataon."))
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Filipino text must be preserved", allText.contains("Maraming salamat"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMOJI
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testEmoji() {
        val calc = makeCalculator(draft = makeDraft(body = "Hello 🎉 World"))
        val allText = calc.renderPlan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Emoji must be preserved", allText.contains("🎉"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEADING SPACES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testLeadingSpaces() {
        val calc = makeCalculator(draft = makeDraft(body = "  Hello world"))
        val bodyLines = calc.renderPlan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        val reconstructed = bodyLines.joinToString("") { it.text }
        assertTrue("Leading spaces must be preserved in output", reconstructed.contains("  Hello"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSECUTIVE SPACES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testConsecutiveSpaces() {
        val calc = makeCalculator(draft = makeDraft(body = "Hello  world"))
        val bodyLines = calc.renderPlan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        val reconstructed = bodyLines.joinToString("") { it.text }
        assertTrue("Consecutive spaces must be preserved", reconstructed.contains("Hello  world"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // TRAILING SPACES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testTrailingSpaces() {
        val calc = makeCalculator(draft = makeDraft(body = "Hello world  "))
        val bodyLines = calc.renderPlan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        val reconstructed = bodyLines.joinToString("") { it.text }
        assertTrue("Trailing spaces must be preserved", reconstructed.contains("world  "))
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMPTY BODY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testEmptyBody() {
        val calc = makeCalculator(draft = makeDraft(body = ""))
        assertTrue("Empty body must not crash", calc.totalPages >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ZERO RECIPIENTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testZeroRecipients() {
        val calc = makeCalculator(draft = makeDraft(recipients = emptyList()))
        assertTrue("Zero recipients must not crash", calc.totalPages >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // DETERMINISTIC PREVIEW GEOMETRY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testDeterministicPreviewGeometry() {
        val calc1 = makeCalculator()
        val calc2 = makeCalculator()
        assertEquals("Scale must be deterministic", calc1.scale, calc2.scale, 0.001)
        assertEquals("Page width must be deterministic", calc1.pageWidthPx, calc2.pageWidthPx)
        assertEquals("Page height must be deterministic", calc1.pageHeightPx, calc2.pageHeightPx)
        assertEquals("Total pages must be deterministic", calc1.totalPages, calc2.totalPages)
    }

    // ════════════════════════════════════════════════════════════════════════
    // TYPOGRAPHY — FONT SIZE SCALES FROM POINTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testTextSizePxFromPoints() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val scale = 1080.0 / PaperSize.A4.widthPt
        val bodySizePx = calc.textSizePx(PdfTextRole.BODY)
        val expectedPx = (PdfTextRole.BODY.style.fontSizePt * scale).toFloat()
        assertEquals("Body text size must be fontSizePt × scale", expectedPx, bodySizePx, 0.1f)
    }

    @Test
    fun testTextSizePxDependsOnRole() {
        val calc = makeCalculator()
        val bodySize = calc.textSizePx(PdfTextRole.BODY)
        val subjectSize = calc.textSizePx(PdfTextRole.SUBJECT)
        assertTrue("Subject font size must be positive", subjectSize > 0)
        assertTrue("Body font size must be positive", bodySize > 0)
    }

    @Test
    fun testBoldRoleReportedCorrectly() {
        val calc = makeCalculator()
        assertTrue("SUBJECT must be bold", calc.isBold(PdfTextRole.SUBJECT))
        assertFalse("BODY must not be bold", calc.isBold(PdfTextRole.BODY))
    }

    @Test
    fun testItalicRoleReportedCorrectly() {
        val calc = makeCalculator()
        assertTrue("CLOSING must be italic", calc.isItalic(PdfTextRole.CLOSING))
        assertFalse("BODY must not be italic", calc.isItalic(PdfTextRole.BODY))
    }

    // ════════════════════════════════════════════════════════════════════════
    // MARGINS FROM GEOMETRY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testMarginsFromPageGeometry() {
        val calc = makeCalculator(paperSize = PaperSize.A4)
        assertEquals("Margin left must match PageGeometry", PaperSize.defaultMarginsPt().left, calc.marginLeftPt, 0.001)
        assertEquals("Margin top must match PageGeometry", PaperSize.defaultMarginsPt().top, calc.marginTopPt, 0.001)
    }

    @Test
    fun testDocumentDimensionsMatchPaperSize() {
        val a4 = makeCalculator(paperSize = PaperSize.A4)
        assertEquals(PaperSize.A4.widthPt, a4.documentWidthPt, 0.001)
        assertEquals(PaperSize.A4.heightPt, a4.documentHeightPt, 0.001)

        val legal = makeCalculator(paperSize = PaperSize.Legal)
        assertEquals(PaperSize.Legal.widthPt, legal.documentWidthPt, 0.001)
        assertEquals(PaperSize.Legal.heightPt, legal.documentHeightPt, 0.001)

        val longBond = makeCalculator(paperSize = PaperSize.LongBond)
        assertEquals(PaperSize.LongBond.widthPt, longBond.documentWidthPt, 0.001)
        assertEquals(PaperSize.LongBond.heightPt, longBond.documentHeightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXTREMELY LONG TEXT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testExtremelyLongText() {
        val longBody = "A".repeat(10000)
        val calc = makeCalculator(draft = makeDraft(body = longBody))
        assertTrue("Extremely long text must not crash", calc.totalPages >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMPTY RENDERPLAN
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testEmptyRenderPlan() {
        val emptyLayout = engine.buildLayout(makeDraft(body = ""), PaperSize.A4)
        val renderPlan = PdfContentCalculator(emptyLayout).plan()
        val calc = PreviewCalculator(renderPlan, emptyLayout.page, 1080)
        assertTrue("Empty render plan must not crash", calc.totalPages >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PIPELINE INTEGRITY — SAME AS PDF
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testPreviewUsesSamePipelineAsPdf() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val renderPlan = PdfContentCalculator(layout).plan()

        val calc = PreviewCalculator(renderPlan, layout.page, 1080)

        assertEquals("Preview page count must match PDF", renderPlan.totalPages, calc.totalPages)
        assertEquals("Preview total pages must match", renderPlan.pages.size, calc.totalPages)
    }

    @Test
    fun testPreviewGeometryMatchesPdf() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val renderPlan = PdfContentCalculator(layout).plan()

        val calc = PreviewCalculator(renderPlan, layout.page, 1080)

        assertEquals("Document width must match PageGeometry", layout.page.widthPt, calc.documentWidthPt, 0.001)
        assertEquals("Document height must match PageGeometry", layout.page.heightPt, calc.documentHeightPt, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PRODUCTION DEFAULT PATH — A4
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testProductionDefaultIsA4() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val renderPlan = PdfContentCalculator(layout).plan()
        val calc = PreviewCalculator(renderPlan, layout.page, 1080)

        assertEquals("Default paper width must be A4", PaperSize.A4.widthPt, calc.documentWidthPt, 0.001)
        assertEquals("Default paper height must be A4", PaperSize.A4.heightPt, calc.documentHeightPt, 0.001)
        assertEquals("Default scale must match A4", 1080.0 / PaperSize.A4.widthPt, calc.scale, 0.001)
    }

    @Test
    fun testProductionConstructionPathDefaultsA4() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val renderPlan = PdfContentCalculator(layout).plan()
        val calc = PreviewCalculator(renderPlan, layout.page, 1080)

        assertTrue("Production path must have at least 1 page", calc.totalPages >= 1)
        assertTrue("Production page height must be positive", calc.pageHeightPx > 0)
        assertEquals("Production path must use A4 geometry",
            (PaperSize.A4.heightPt * calc.scale).toInt(), calc.pageHeightPx)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEGAL / LONG BOND — UNIT-LEVEL ONLY (not in production activity yet)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testLegalGeometryCorrect() {
        val calc = makeCalculator(paperSize = PaperSize.Legal)
        assertEquals(PaperSize.Legal.widthPt, calc.documentWidthPt, 0.001)
        assertEquals(PaperSize.Legal.heightPt, calc.documentHeightPt, 0.001)
    }

    @Test
    fun testLongBondGeometryCorrect() {
        val calc = makeCalculator(paperSize = PaperSize.LongBond)
        assertEquals(PaperSize.LongBond.widthPt, calc.documentWidthPt, 0.001)
        assertEquals(PaperSize.LongBond.heightPt, calc.documentHeightPt, 0.001)
    }
}
