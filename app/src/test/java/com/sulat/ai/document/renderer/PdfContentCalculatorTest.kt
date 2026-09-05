package com.sulat.ai.document.renderer

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.layout.LayoutSection
import com.sulat.ai.document.layout.LayoutValidation
import com.sulat.ai.document.layout.PageGeometry
import com.sulat.ai.document.layout.Paragraph
import com.sulat.ai.document.layout.RecipientEntry
import com.sulat.ai.document.layout.RecipientNameHierarchy
import com.sulat.ai.document.renderer.LetterTemplateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfContentCalculatorTest {

    private lateinit var engine: LetterTemplateEngine

    @Before
    fun setUp() {
        engine = LetterTemplateEngine()
    }

    private fun makeLayout(
        body: String = "Hello world.",
        subject: String = "Test Subject",
        greeting: String = "Dear Kapatid",
        recipients: List<Recipient> = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister", organization = "INC")),
        sender: SenderProfile = SenderProfile(name = "Sender Name", signature = "Faithfully"),
        paperSize: PaperSize = PaperSize.A4
    ): DocumentLayout {
        val draft = LetterDraft(
            id = "test",
            recipients = recipients,
            dates = listOf(LetterDate(date = java.util.Date(1700000000000L), label = "Jan 1")),
            body = body,
            subject = subject,
            greeting = greeting,
            sender = sender
        )
        return engine.buildLayout(draft, paperSize)
    }

    // ── Basic rendering ───────────────────────────────────────────────────

    @Test
    fun testOnePageLetter() {
        val layout = makeLayout()
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        assertEquals(1, plan.totalPages)
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testDateRendering() {
        val layout = makeLayout()
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("2023") || allText.contains("2026") || allText.contains("January") || allText.contains("November"))
    }

    @Test
    fun testRecipientRendering() {
        val layout = makeLayout()
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("JUAN DELA CRUZ"))
        assertTrue(allText.contains("Minister"))
        assertTrue(allText.contains("INC"))
    }

    @Test
    fun testMultipleRecipients() {
        val recipients = listOf(
            Recipient(id = "r1", name = "Bro. Juan", position = "Minister"),
            Recipient(id = "r2", name = "Bro. Pedro", position = "Deacon"),
            Recipient(id = "r3", name = "Bro. Jose", position = "Secretary")
        )
        val layout = makeLayout(recipients = recipients)
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("JUAN"))
        assertTrue(allText.contains("PEDRO"))
        assertTrue(allText.contains("JOSE"))
    }

    @Test
    fun testSubjectRendering() {
        val layout = makeLayout(subject = "My Custom Subject")
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Re: My Custom Subject"))
    }

    @Test
    fun testGreetingRendering() {
        val layout = makeLayout(greeting = "Dear Kapatid")
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Dear Kapatid"))
    }

    @Test
    fun testBodyRendering() {
        val layout = makeLayout(body = "This is the body text.")
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("This is the body text."))
    }

    @Test
    fun testClosingRendering() {
        val layout = makeLayout(sender = SenderProfile(name = "Sender Name", signature = "Faithfully"))
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Faithfully"))
        assertTrue(allText.contains("Sender Name"))
    }

    // ── Data source integrity ─────────────────────────────────────────────

    @Test
    fun testChangingSubjectChangesContent() {
        val l1 = PdfContentCalculator(makeLayout(subject = "Subject A")).plan()
        val l2 = PdfContentCalculator(makeLayout(subject = "Subject B")).plan()
        val t1 = l1.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        val t2 = l2.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(t1.contains("Subject A"))
        assertTrue(t2.contains("Subject B"))
        assertFalse(t1.contains("Subject B"))
    }

    @Test
    fun testChangingGreetingChangesContent() {
        val l1 = PdfContentCalculator(makeLayout(greeting = "Dear A")).plan()
        val l2 = PdfContentCalculator(makeLayout(greeting = "Dear B")).plan()
        val t1 = l1.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        val t2 = l2.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(t1.contains("Dear A"))
        assertTrue(t2.contains("Dear B"))
    }

    @Test
    fun testChangingRecipientChangesContent() {
        val l1 = PdfContentCalculator(makeLayout(recipients = listOf(Recipient(name = "Alice")))).plan()
        val l2 = PdfContentCalculator(makeLayout(recipients = listOf(Recipient(name = "Bob")))).plan()
        val t1 = l1.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        val t2 = l2.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(t1.contains("Alice"))
        assertTrue(t2.contains("Bob"))
    }

    @Test
    fun testChangingBodyChangesContent() {
        val l1 = PdfContentCalculator(makeLayout(body = "Body A")).plan()
        val l2 = PdfContentCalculator(makeLayout(body = "Body B")).plan()
        val t1 = l1.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        val t2 = l2.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(t1.contains("Body A"))
        assertTrue(t2.contains("Body B"))
    }

    @Test
    fun testNoHardcodedSampleSubject() {
        val layout = makeLayout(subject = "")
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertFalse(allText.contains("Re: Liham"))
    }

    @Test
    fun testNoHardcodedSampleGreeting() {
        val layout = makeLayout(greeting = "")
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertFalse(allText.contains("Pinakamamahal"))
    }

    @Test
    fun testNoHardcodedKapatid() {
        val layout = makeLayout(recipients = listOf(Recipient(name = "TEST")))
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertFalse(allText.contains("Kapatid na"))
    }

    // ── Paper sizes ───────────────────────────────────────────────────────

    @Test
    fun testA4PageDimensions() {
        val layout = makeLayout(paperSize = PaperSize.A4)
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        assertEquals(595, layout.page.widthPt.toInt())
        assertEquals(841, layout.page.heightPt.toInt())
    }

    @Test
    fun testLegalPageDimensions() {
        val layout = makeLayout(paperSize = PaperSize.Legal)
        assertEquals(612, layout.page.widthPt.toInt())
        assertEquals(1008, layout.page.heightPt.toInt())
    }

    @Test
    fun testLongBondPageDimensions() {
        val layout = makeLayout(paperSize = PaperSize.LongBond)
        assertEquals(612, layout.page.widthPt.toInt())
        assertEquals(936, layout.page.heightPt.toInt())
    }

    @Test
    fun testLegalDiffersFromLongBond() {
        val legal = makeLayout(paperSize = PaperSize.Legal)
        val longBond = makeLayout(paperSize = PaperSize.LongBond)
        assertTrue(legal.page.heightPt != longBond.page.heightPt)
    }

    @Test
    fun testPageDimensionsDeterministic() {
        val l1 = PdfContentCalculator(makeLayout()).plan()
        val l2 = PdfContentCalculator(makeLayout()).plan()
        assertEquals(l1.totalPages, l2.totalPages)
    }

    // ── Pagination ────────────────────────────────────────────────────────

    @Test
    fun testShortBodyOnePage() {
        val layout = makeLayout(body = "Short body.")
        val plan = PdfContentCalculator(layout).plan()
        assertEquals(1, plan.totalPages)
    }

    @Test
    fun testLongBodyMultiplePages() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: This is a test sentence for pagination." }
        val layout = makeLayout(body = longBody)
        val plan = PdfContentCalculator(layout).plan()
        assertTrue(plan.totalPages > 1)
    }

    @Test
    fun testContentAfterPageBreak() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: Testing content preservation." }
        val layout = makeLayout(body = longBody)
        val plan = PdfContentCalculator(layout).plan()
        val lastPageText = plan.pages.last().lines.map { it.text }.joinToString(" ")
        assertTrue(lastPageText.isNotEmpty())
    }

    @Test
    fun testClosingAfterBody() {
        val layout = makeLayout(body = "Body text.")
        val plan = PdfContentCalculator(layout).plan()
        val allLines = plan.pages.flatMap { it.lines }
        val bodyIdx = allLines.indexOfFirst { it.text.contains("Body text") }
        val closingIdx = allLines.indexOfFirst { it.text.contains("Faithfully") }
        assertTrue(bodyIdx >= 0)
        assertTrue(closingIdx > bodyIdx)
    }

    // ── Empty states ──────────────────────────────────────────────────────

    @Test
    fun testZeroRecipientsDoesNotCrash() {
        val layout = makeLayout(recipients = emptyList())
        val plan = PdfContentCalculator(layout).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testEmptySubjectHandled() {
        val layout = makeLayout(subject = "")
        val plan = PdfContentCalculator(layout).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertFalse(allText.contains("Re:"))
    }

    @Test
    fun testEmptyGreetingHandled() {
        val layout = makeLayout(greeting = "")
        val plan = PdfContentCalculator(layout).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testEmptyBodyHandled() {
        val layout = makeLayout(body = "")
        val plan = PdfContentCalculator(layout).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testMissingSenderHandled() {
        val layout = makeLayout(sender = SenderProfile())
        val plan = PdfContentCalculator(layout).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    // ── Unicode ───────────────────────────────────────────────────────────

    @Test
    fun testFilipinoCharacters() {
        val layout = makeLayout(body = "Maraming salamat po sa inyong pagkakataon.")
        val plan = PdfContentCalculator(layout).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Maraming salamat po"))
    }

    @Test
    fun testAccentedCharacters() {
        val layout = makeLayout(body = "Café résumé naïve")
        val plan = PdfContentCalculator(layout).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Café"))
    }

    @Test
    fun testPunctuationQuotes() {
        val layout = makeLayout(body = "He said \"hello\" and she said 'bye'.")
        val plan = PdfContentCalculator(layout).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("\"hello\""))
    }

    // ── Determinism ───────────────────────────────────────────────────────

    @Test
    fun testSameInputSamePageCount() {
        val l1 = PdfContentCalculator(makeLayout()).plan()
        val l2 = PdfContentCalculator(makeLayout()).plan()
        assertEquals(l1.totalPages, l2.totalPages)
    }

    @Test
    fun testSameInputSameGeometry() {
        val l1 = PdfContentCalculator(makeLayout()).plan()
        val l2 = PdfContentCalculator(makeLayout()).plan()
        assertEquals(l1.pages.size, l2.pages.size)
        assertEquals(l1.pages[0].lines.size, l2.pages[0].lines.size)
    }

    // ── Line wrapping ─────────────────────────────────────────────────────

    @Test
    fun testLongLineWraps() {
        val longText = "This is a very long line that should wrap because it exceeds the available width on the page and needs to be broken into multiple visual lines."
        val layout = makeLayout(body = longText)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        assertTrue(bodyLines.size > 1)
    }

    @Test
    fun testShortLineDoesNotWrap() {
        val layout = makeLayout(body = "Short.")
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        assertEquals(1, bodyLines.size)
    }

    // ── Typography roles ──────────────────────────────────────────────────

    @Test
    fun testRecipientPrefixRoleDiffers() {
        assertTrue(PdfTextRole.RECIPIENT_PREFIX.style.isBold)
        assertTrue(PdfTextRole.RECIPIENT_NAME.style.isBold)
        assertFalse(PdfTextRole.RECIPIENT_POSITION.style.isBold)
    }

    @Test
    fun testSubjectIsBold() {
        assertTrue(PdfTextRole.SUBJECT.style.isBold)
    }

    @Test
    fun testBodyIsNotBold() {
        assertFalse(PdfTextRole.BODY.style.isBold)
    }

    // ── PDF header check ──────────────────────────────────────────────────

    @Test
    fun testIsValidPdfFile_rejectsNonexistent() {
        assertFalse(PdfRenderer.isValidPdfFile(java.io.File("/nonexistent/file.pdf")))
    }

    @Test
    fun testIsValidPdfFile_rejectsEmpty() {
        val tmp = java.io.File.createTempFile("empty", ".pdf")
        tmp.deleteOnExit()
        assertFalse(PdfRenderer.isValidPdfFile(tmp))
    }

    @Test
    fun testIsValidPdfFile_rejectsPlainText() {
        val tmp = java.io.File.createTempFile("plain", ".txt")
        tmp.writeText("This is not a PDF")
        tmp.deleteOnExit()
        assertFalse(PdfRenderer.isValidPdfFile(tmp))
    }

    @Test
    fun testIsValidPdfFile_acceptsValidHeader() {
        val tmp = java.io.File.createTempFile("valid", ".pdf")
        tmp.writeBytes("%PDF-1.4 fake content".toByteArray())
        tmp.deleteOnExit()
        assertTrue(PdfRenderer.isValidPdfFile(tmp))
    }
}
