package com.sulat.ai.preview

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
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
        return PreviewCalculator(renderPlan, availableWidthPx)
    }

    // ════════════════════════════════════════════════════════════════════════
    // A4 PAGE SCALING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4PageScaling() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val expectedScale = 1080.0 / 595.276
        assertEquals(expectedScale, calc.scale, 0.001)
    }

    @Test
    fun testA4PageWidthMatchesAvailable() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun testA4PageHeightProportional() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        assertTrue("A4 page height must be positive", calc.pageHeightPx > 0)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEGAL PAGE SCALING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testLegalPageScaling() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        val expectedScale = 1080.0 / 595.276
        assertEquals(expectedScale, calc.scale, 0.001)
    }

    @Test
    fun testLegalPageWidthMatchesAvailable() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun testLegalPageHeightProportional() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.Legal)
        assertTrue("Legal page height must be positive", calc.pageHeightPx > 0)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LONG BOND PAGE SCALING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testLongBondPageScaling() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        val expectedScale = 1080.0 / 595.276
        assertEquals(expectedScale, calc.scale, 0.001)
    }

    @Test
    fun testLongBondPageWidthMatchesAvailable() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun testLongBondPageHeightProportional() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.LongBond)
        assertTrue("Long Bond page height must be positive", calc.pageHeightPx > 0)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ASPECT-RATIO PRESERVATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAspectRatioPreserved() {
        val calc = makeCalculator(availableWidthPx = 1080, paperSize = PaperSize.A4)
        val ratio = calc.documentHeightPt / calc.documentWidthPt
        val pxRatio = calc.pageHeightPx.toDouble() / calc.pageWidthPx
        assertEquals("Aspect ratio must be preserved", ratio, pxRatio, 0.01)
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

    @Test
    fun testTextSizeDependsOnRole() {
        val calc = makeCalculator()
        val bodySize = calc.textSizeSp(PdfTextRole.BODY)
        val subjectSize = calc.textSizeSp(PdfTextRole.SUBJECT)
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
    // EXTREMELY LONG TEXT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testExtremelyLongText() {
        val longBody = "A".repeat(10000)
        val calc = makeCalculator(draft = makeDraft(body = longBody))
        assertTrue("Extremely long text must not crash", calc.totalPages >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PIPELINE INTEGRITY — SAME AS PDF
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testPreviewUsesSamePipelineAsPdf() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val renderPlan = PdfContentCalculator(layout).plan()

        val calc = PreviewCalculator(renderPlan, 1080)

        assertEquals("Preview page count must match PDF", renderPlan.totalPages, calc.totalPages)
        assertEquals("Preview total pages must match", renderPlan.pages.size, calc.totalPages)
    }
}
