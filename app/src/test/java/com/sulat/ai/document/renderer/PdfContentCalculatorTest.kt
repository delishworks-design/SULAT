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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.round

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

    private fun realisticLayout(): DocumentLayout {
        val draft = LetterDraft(
            id = "realistic",
            recipients = listOf(
                Recipient(
                    id = "r1",
                    name = "KA. JUAN DELA CRUZ",
                    position = "Resident Minister",
                    organization = "Local Congregation of Example",
                    address = "123 Example Street, Calamba, Laguna"
                )
            ),
            dates = listOf(LetterDate(date = java.util.Date(1700000000000L), label = "Jan 1")),
            subject = "Request for Confirmation of Activity",
            greeting = "Dear Brother,",
            body = "Peace be with you, brother.\n\n" +
                "This is a formal request regarding the upcoming activity " +
                "scheduled for next week. We would like to confirm the details " +
                "and ensure that all necessary arrangements are in place.\n\n" +
                "Maraming salamat po sa inyong pagkakataon. We look forward " +
                "to your kind response at your earliest convenience.",
            sender = SenderProfile(
                name = "Lloyd Malto",
                signature = "Faithfully yours,",
                address = "456 Sender Street"
            )
        )
        return engine.buildLayout(draft, PaperSize.A4)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAGE GEOMETRY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testA4PageDimensions() {
        val layout = makeLayout(paperSize = PaperSize.A4)
        assertEquals(595.276, layout.page.widthPt, 0.001)
        assertEquals(841.89, layout.page.heightPt, 0.001)
    }

    @Test
    fun testLegalPageDimensions() {
        val layout = makeLayout(paperSize = PaperSize.Legal)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(1008.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun testLongBondPageDimensions() {
        val layout = makeLayout(paperSize = PaperSize.LongBond)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(936.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun testLegalDiffersFromLongBond() {
        val legal = makeLayout(paperSize = PaperSize.Legal)
        val longBond = makeLayout(paperSize = PaperSize.LongBond)
        assertTrue(legal.page.heightPt != longBond.page.heightPt)
    }

    @Test
    fun testRoundedPdfDimensions_a4() {
        val a4Width = round(PaperSize.A4.widthPt).toInt()
        val a4Height = round(PaperSize.A4.heightPt).toInt()
        assertEquals(595, a4Width)
        assertEquals(842, a4Height)
    }

    @Test
    fun testRoundedPdfDimensions_legal() {
        val legalWidth = round(PaperSize.Legal.widthPt).toInt()
        val legalHeight = round(PaperSize.Legal.heightPt).toInt()
        assertEquals(612, legalWidth)
        assertEquals(1008, legalHeight)
    }

    @Test
    fun testRoundedPdfDimensions_longBond() {
        val longBondWidth = round(PaperSize.LongBond.widthPt).toInt()
        val longBondHeight = round(PaperSize.LongBond.heightPt).toInt()
        assertEquals(612, longBondWidth)
        assertEquals(936, longBondHeight)
    }

    @Test
    fun testRoundingNotTruncation() {
        val a4Width = round(PaperSize.A4.widthPt).toInt()
        val truncated = PaperSize.A4.widthPt.toInt()
        assertEquals(595, a4Width)
        assertEquals(595, truncated)
        val a4Height = round(PaperSize.A4.heightPt).toInt()
        val truncatedH = PaperSize.A4.heightPt.toInt()
        assertEquals(842, a4Height)
        assertEquals(841, truncatedH)
        assertTrue("Rounding must not truncate: 841.89 should round to 842, not 841", a4Height != truncatedH)
    }

    @Test
    fun testRoundPdfDimensionCompanionMethod() {
        assertEquals(595, PdfRenderer.roundPdfDimension(595.276))
        assertEquals(842, PdfRenderer.roundPdfDimension(841.89))
        assertEquals(612, PdfRenderer.roundPdfDimension(612.0))
        assertEquals(1008, PdfRenderer.roundPdfDimension(1008.0))
    }

    @Test
    fun testLayoutGeometryIsSourceOfTruth() {
        val layout = makeLayout(paperSize = PaperSize.A4)
        val calculator = PdfContentCalculator(layout)
        val plan = calculator.plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    // ════════════════════════════════════════════════════════════════════════
    // BASIC RENDERING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testOnePageLetter() {
        val layout = makeLayout()
        val plan = PdfContentCalculator(layout).plan()
        assertEquals(1, plan.totalPages)
    }

    @Test
    fun testDateRendering() {
        val plan = PdfContentCalculator(makeLayout()).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("2023") || allText.contains("2026") || allText.contains("January") || allText.contains("November"))
    }

    @Test
    fun testRecipientRendering() {
        val plan = PdfContentCalculator(makeLayout()).plan()
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
        val plan = PdfContentCalculator(makeLayout(recipients = recipients)).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Juan"))
        assertTrue(allText.contains("Pedro"))
        assertTrue(allText.contains("Jose"))
    }

    @Test
    fun testSubjectRendering() {
        val plan = PdfContentCalculator(makeLayout(subject = "My Custom Subject")).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Re: My Custom Subject"))
    }

    @Test
    fun testGreetingRendering() {
        val plan = PdfContentCalculator(makeLayout(greeting = "Dear Kapatid")).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Dear Kapatid"))
    }

    @Test
    fun testBodyRendering() {
        val plan = PdfContentCalculator(makeLayout(body = "This is the body text.")).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("This is the body text."))
    }

    @Test
    fun testClosingRendering() {
        val plan = PdfContentCalculator(makeLayout(sender = SenderProfile(name = "Sender Name", signature = "Faithfully"))).plan()
        val allText = plan.pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
        assertTrue(allText.contains("Faithfully"))
        assertTrue(allText.contains("Sender Name"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATA SOURCE INTEGRITY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testChangingSubjectChangesContent() {
        val t1 = PdfContentCalculator(makeLayout(subject = "Subject A")).plan().allText()
        val t2 = PdfContentCalculator(makeLayout(subject = "Subject B")).plan().allText()
        assertTrue(t1.contains("Subject A"))
        assertTrue(t2.contains("Subject B"))
        assertFalse(t1.contains("Subject B"))
    }

    @Test
    fun testChangingGreetingChangesContent() {
        val t1 = PdfContentCalculator(makeLayout(greeting = "Dear A")).plan().allText()
        val t2 = PdfContentCalculator(makeLayout(greeting = "Dear B")).plan().allText()
        assertTrue(t1.contains("Dear A"))
        assertTrue(t2.contains("Dear B"))
    }

    @Test
    fun testChangingRecipientChangesContent() {
        val t1 = PdfContentCalculator(makeLayout(recipients = listOf(Recipient(name = "Alice")))).plan().allText()
        val t2 = PdfContentCalculator(makeLayout(recipients = listOf(Recipient(name = "Bob")))).plan().allText()
        assertTrue(t1.contains("Alice"))
        assertTrue(t2.contains("Bob"))
    }

    @Test
    fun testChangingBodyChangesContent() {
        val t1 = PdfContentCalculator(makeLayout(body = "Body A")).plan().allText()
        val t2 = PdfContentCalculator(makeLayout(body = "Body B")).plan().allText()
        assertTrue(t1.contains("Body A"))
        assertTrue(t2.contains("Body B"))
    }

    @Test
    fun testNoHardcodedSampleSubject() {
        val allText = PdfContentCalculator(makeLayout(subject = "")).plan().allText()
        assertFalse(allText.contains("Re: Liham"))
    }

    @Test
    fun testNoHardcodedSampleGreeting() {
        val allText = PdfContentCalculator(makeLayout(greeting = "")).plan().allText()
        assertFalse(allText.contains("Pinakamamahal"))
    }

    @Test
    fun testNoHardcodedKapatid() {
        val allText = PdfContentCalculator(makeLayout(recipients = listOf(Recipient(name = "TEST")))).plan().allText()
        assertFalse(allText.contains("Kapatid na"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // WRAPPING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testShortLineDoesNotWrap() {
        val plan = PdfContentCalculator(makeLayout(body = "Short.")).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        assertEquals(1, bodyLines.size)
    }

    @Test
    fun testLongLineWraps() {
        val longText = "This is a very long line that should wrap because it exceeds the available width on the page and needs to be broken into multiple visual lines."
        val plan = PdfContentCalculator(makeLayout(body = longText)).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        assertTrue("Long line should wrap into multiple lines, got ${bodyLines.size}", bodyLines.size > 1)
    }

    @Test
    fun testLongParagraphWrapsPreservingAllWords() {
        val paragraph = "The quick brown fox jumps over the lazy dog near the river bank where the trees grow tall and the birds sing beautifully every morning without fail since the beginning of time."
        val plan = PdfContentCalculator(makeLayout(body = paragraph)).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        val reconstructed = bodyLines.joinToString(" ") { it.text }
        assertTrue("All words must be preserved in wrapped output", reconstructed.contains("quick brown fox"))
        assertTrue("All words must be preserved", reconstructed.contains("beautifully every morning"))
    }

    @Test
    fun testMultipleParagraphsSeparateOutput() {
        val body = "First paragraph text.\n\nSecond paragraph text."
        val plan = PdfContentCalculator(makeLayout(body = body)).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("First paragraph"))
        assertTrue(allText.contains("Second paragraph"))
    }

    @Test
    fun testExplicitLineBreaksPreserved() {
        val body = "Line one.\nLine two.\nLine three."
        val plan = PdfContentCalculator(makeLayout(body = body)).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("Line one."))
        assertTrue(allText.contains("Line two."))
        assertTrue(allText.contains("Line three."))
    }

    @Test
    fun testBlankLinesSeparateParagraphs() {
        val body = "Para one.\n\n\nPara two."
        val plan = PdfContentCalculator(makeLayout(body = body)).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("Para one."))
        assertTrue(allText.contains("Para two."))
    }

    @Test
    fun testLongUnbreakableTokenSplit() {
        val longToken = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val plan = PdfContentCalculator(makeLayout(body = longToken)).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        val allChars = bodyLines.joinToString("") { it.text }
        val allOriginalChars = longToken.filter { !it.isWhitespace() }
        assertEquals("All characters must be preserved", allOriginalChars.length, allChars.filter { it.isLetter() }.length)
    }

    @Test
    fun testLongTokenPreservesAllCharacters() {
        val longToken = "ABCDEFGHIJK"
        val plan = PdfContentCalculator(makeLayout(body = longToken)).plan()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        val reconstructed = bodyLines.joinToString("") { it.text }
        assertTrue("All original characters must appear in output", reconstructed.contains("ABCDEFGHIJK"))
    }

    @Test
    fun testLongUnbreakableTokenDoesNotExceedWidth() {
        val longToken = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val layout = makeLayout(body = longToken)
        val plan = PdfContentCalculator(layout).plan()
        val usableWidth = layout.page.usableWidthPt
        val measurer = DeterministicTextMeasurer()
        val bodyLines = plan.pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
        for (line in bodyLines) {
            val width = measurer.measureTextWidth(line.text, PdfTextRole.BODY.style.fontSizePt, PdfTextRole.BODY.style.isBold)
            assertTrue(
                "Line '${line.text.take(30)}...' width ${width} must not exceed usable width $usableWidth",
                width <= usableWidth + 1.0
            )
        }
    }

    @Test
    fun testFilipinoCharacters() {
        val plan = PdfContentCalculator(makeLayout(body = "Maraming salamat po sa inyong pagkakataon.")).plan()
        assertTrue(plan.allText().contains("Maraming salamat po"))
    }

    @Test
    fun testAccentedCharacters() {
        val plan = PdfContentCalculator(makeLayout(body = "Café résumé naïve")).plan()
        assertTrue(plan.allText().contains("Café"))
    }

    @Test
    fun testPunctuationQuotes() {
        val plan = PdfContentCalculator(makeLayout(body = "He said \"hello\" and she said 'bye'.")).plan()
        assertTrue(plan.allText().contains("\"hello\""))
    }

    @Test
    fun testMixedWidthCharacters() {
        val body = "Hello 你好世界 café"
        val plan = PdfContentCalculator(makeLayout(body = body)).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("Hello"))
        assertTrue(allText.contains("café"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHARACTER PRESERVATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllInputCharactersAppearsInOrder() {
        val body = "The quick brown fox jumps over the lazy dog."
        val plan = PdfContentCalculator(makeLayout(body = body)).plan()
        val bodyText = plan.pages.flatMap { it.lines }
            .filter { it.role == PdfTextRole.BODY }
            .joinToString(" ") { it.text }
        for (word in body.split(" ")) {
            assertTrue("Word '$word' must appear in output", bodyText.contains(word))
        }
    }

    @Test
    fun testRealisticLetterAllCharactersPreserved() {
        val layout = realisticLayout()
        val plan = PdfContentCalculator(layout).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("JUAN DELA CRUZ"))
        assertTrue(allText.contains("Resident Minister"))
        assertTrue(allText.contains("Request for Confirmation"))
        assertTrue(allText.contains("Dear Brother,"))
        assertTrue(allText.contains("Lloyd Malto"))
        assertTrue(allText.contains("Faithfully yours,"))
        assertTrue(allText.contains("Maraming salamat"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAGINATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testShortBodyOnePage() {
        val plan = PdfContentCalculator(makeLayout(body = "Short body.")).plan()
        assertEquals(1, plan.totalPages)
    }

    @Test
    fun testLongBodyMultiplePages() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: This is a test sentence for pagination." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        assertTrue(plan.totalPages > 1)
    }

    @Test
    fun testManyPagesContentPreserved() {
        val longBody = (1..500).joinToString("\n\n") { "Paragraph $it: Long enough to force multiple pages with sufficient content to fill them." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        assertTrue(plan.totalPages >= 3)
        val allText = plan.allText()
        assertTrue(allText.contains("Paragraph 1"))
        assertTrue(allText.contains("Paragraph 500"))
    }

    @Test
    fun testContentAfterPageBreak() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: Testing content preservation across pages." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        val lastPageText = plan.pages.last().lines.map { it.text }.joinToString(" ")
        assertTrue(lastPageText.isNotEmpty())
    }

    @Test
    fun testClosingAfterBody() {
        val plan = PdfContentCalculator(makeLayout(body = "Body text.")).plan()
        val allLines = plan.pages.flatMap { it.lines }
        val bodyIdx = allLines.indexOfFirst { it.text.contains("Body text") }
        val closingIdx = allLines.indexOfFirst { it.text.contains("Faithfully") }
        assertTrue(bodyIdx >= 0)
        assertTrue(closingIdx > bodyIdx)
    }

    @Test
    fun testClosingAfterPageBreak() {
        val longBody = (1..300).joinToString("\n\n") { "Paragraph $it: Filling up the page so closing moves to a new page." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        val allLines = plan.pages.flatMap { it.lines }
        val bodyIndices = allLines.mapIndexedNotNull { idx, line -> if (line.role == PdfTextRole.BODY) idx else null }
        val closingIndex = allLines.indexOfFirst { it.role == PdfTextRole.CLOSING }
        assertTrue("Closing must exist", closingIndex >= 0)
        assertTrue("Closing must be after all body lines", closingIndex > bodyIndices.last())
    }

    @Test
    fun testNoDuplicateLines() {
        val longBody = (1..100).joinToString("\n\n") { "Paragraph $it: Testing for duplicate lines across page breaks." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        val allLines = plan.pages.flatMap { it.lines }
        val textYPairs = allLines.map { "${it.text}@${it.yPt}" }
        assertEquals("No duplicate lines (same text at same Y)", textYPairs.size, textYPairs.toSet().size)
    }

    @Test
    fun testNoLostLines() {
        val longBody = (1..100).joinToString("\n\n") { "Paragraph $it: Testing that no lines are lost during pagination." }
        val layout = makeLayout(body = longBody)
        val plan = PdfContentCalculator(layout).plan()
        val totalLineCount = plan.pages.sumOf { it.lines.size }
        assertTrue("Must have many lines across pages", totalLineCount > 50)
    }

    @Test
    fun testNoAccidentalBlankPages() {
        val layout = makeLayout(body = "Simple body.")
        val plan = PdfContentCalculator(layout).plan()
        for (page in plan.pages) {
            assertTrue("Page ${page.pageNumber} must not be blank", page.lines.isNotEmpty())
        }
    }

    @Test
    fun testPageNumbersSequential() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: Checking sequential page numbers." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        for (i in plan.pages.indices) {
            assertEquals("Page number must be sequential", i + 1, plan.pages[i].pageNumber)
        }
    }

    @Test
    fun testContentNeverCrossesBottomMargin() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: Checking bottom margin." }
        val layout = makeLayout(body = longBody)
        val plan = PdfContentCalculator(layout).plan()
        val bottomLimit = layout.page.heightPt - layout.page.marginBottomPt
        for (page in plan.pages) {
            for (line in page.lines) {
                val lineHeight = line.role.style.fontSizePt * line.role.style.lineSpacingMultiplier
                assertTrue(
                    "Line at y=${line.yPt} with height $lineHeight must not cross bottom margin $bottomLimit",
                    line.yPt + lineHeight <= bottomLimit + 0.01
                )
            }
        }
    }

    @Test
    fun testNewPageStartsAtTopMargin() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: Testing page top margin." }
        val layout = makeLayout(body = longBody)
        val plan = PdfContentCalculator(layout).plan()
        for (page in plan.pages) {
            if (page.lines.isNotEmpty() && page.pageNumber > 1) {
                val firstLineY = page.lines.first().yPt
                assertTrue(
                    "First line of page ${page.pageNumber} must not appear above top margin (${layout.page.marginTopPt}pt), was $firstLineY",
                    firstLineY >= layout.page.marginTopPt - 0.01
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TYPOGRAPHY
    // ════════════════════════════════════════════════════════════════════════

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

    @Test
    fun testAllRolesHavePositiveFontSize() {
        for (role in PdfTextRole.entries) {
            assertTrue("${role.name} must have positive fontSizePt", role.style.fontSizePt > 0)
        }
    }

    @Test
    fun testAllRolesHavePositiveLineSpacing() {
        for (role in PdfTextRole.entries) {
            assertTrue("${role.name} must have lineSpacingMultiplier >= 1.0", role.style.lineSpacingMultiplier >= 1.0)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMPTY STATES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testZeroRecipientsDoesNotCrash() {
        val plan = PdfContentCalculator(makeLayout(recipients = emptyList())).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testEmptySubjectHandled() {
        val allText = PdfContentCalculator(makeLayout(subject = "")).plan().allText()
        assertFalse(allText.contains("Re:"))
    }

    @Test
    fun testEmptyGreetingHandled() {
        val plan = PdfContentCalculator(makeLayout(greeting = "")).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testEmptyBodyHandled() {
        val plan = PdfContentCalculator(makeLayout(body = "")).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testMissingSenderHandled() {
        val plan = PdfContentCalculator(makeLayout(sender = SenderProfile())).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun testAllEmptyFieldsNoBlankPages() {
        val plan = PdfContentCalculator(makeLayout(
            body = "",
            subject = "",
            greeting = "",
            sender = SenderProfile(),
            recipients = emptyList()
        )).plan()
        for (page in plan.pages) {
            assertTrue("No blank pages in empty state", page.lines.isNotEmpty())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DETERMINISM
    // ════════════════════════════════════════════════════════════════════════

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

    @Test
    fun testSameInputSameTextAndPositions() {
        val l1 = PdfContentCalculator(makeLayout()).plan()
        val l2 = PdfContentCalculator(makeLayout()).plan()
        for (i in l1.pages.indices) {
            assertEquals("Page ${i + 1} line count must match", l1.pages[i].lines.size, l2.pages[i].lines.size)
            for (j in l1.pages[i].lines.indices) {
                val a = l1.pages[i].lines[j]
                val b = l2.pages[i].lines[j]
                assertEquals("Line $j text must match", a.text, b.text)
                assertEquals("Line $j y-position must match", a.yPt, b.yPt, 0.001)
                assertEquals("Line $j role must match", a.role, b.role)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // REALISTIC LETTER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testRealisticLetterOnePage() {
        val plan = PdfContentCalculator(realisticLayout()).plan()
        assertEquals(1, plan.totalPages)
    }

    @Test
    fun testRealisticLetterAllSectionsPresent() {
        val plan = PdfContentCalculator(realisticLayout()).plan()
        val allText = plan.allText()
        assertTrue("Date must be present", allText.contains("2023") || allText.contains("2026") || allText.contains("January") || allText.contains("November"))
        assertTrue("Recipient must be present", allText.contains("JUAN DELA CRUZ"))
        assertTrue("Subject must be present", allText.contains("Request for Confirmation"))
        assertTrue("Greeting must be present", allText.contains("Dear Brother,"))
        assertTrue("Body must be present", allText.contains("Maraming salamat"))
        assertTrue("Sender must be present", allText.contains("Lloyd Malto"))
        assertTrue("Signature must be present", allText.contains("Faithfully yours"))
    }

    @Test
    fun testRealisticLetterSectionOrder() {
        val plan = PdfContentCalculator(realisticLayout()).plan()
        val allLines = plan.pages.flatMap { it.lines }
        val dateIdx = allLines.indexOfFirst { it.role == PdfTextRole.DATE }
        val recipientIdx = allLines.indexOfFirst { it.role == PdfTextRole.RECIPIENT_NAME }
        val subjectIdx = allLines.indexOfFirst { it.role == PdfTextRole.SUBJECT }
        val greetingIdx = allLines.indexOfFirst { it.role == PdfTextRole.GREETING }
        val bodyIdx = allLines.indexOfFirst { it.role == PdfTextRole.BODY }
        val closingIdx = allLines.indexOfFirst { it.role == PdfTextRole.CLOSING }

        assertTrue("Date before recipient", dateIdx < recipientIdx)
        assertTrue("Recipient before subject", recipientIdx < subjectIdx)
        assertTrue("Subject before greeting", subjectIdx < greetingIdx)
        assertTrue("Greeting before body", greetingIdx < bodyIdx)
        assertTrue("Body before closing", bodyIdx < closingIdx)
    }

    @Test
    fun testLongRealisticLetterMultiplePages() {
        val longBody = (1..50).joinToString("\n\n") { i ->
            when {
                i <= 10 -> "Peace be with you, brother. Paragraph $i discusses the upcoming activity scheduled for next week. We would like to confirm the details and ensure that all necessary arrangements are in place for the gathering of brethren."
                i <= 20 -> "Maraming salamat po sa inyong pagkakataon. We look forward to your kind response at your earliest convenience. Paragraph $i covers the financial report and the plans for the upcoming district conference that will be held at the chapel."
                i <= 30 -> "Ang pagtitipon ay gaganapin sa darating na Linggo. Lahat ng kapatid ay inaasahang dadalo upang masiyahan sa pag-aaral ng banal na kasulatan. Paragraph $i provides additional details about the spiritual food preparation."
                else -> "We pray for your guidance and continued service to the Lord. Paragraph $i concludes with our deepest gratitude and prayers for the continued progress of God's work in our congregation."
            }
        }
        val layout = makeLayout(
            body = longBody,
            greeting = "Dear Brother,",
            sender = SenderProfile(name = "Lloyd Malto", signature = "Faithfully yours,")
        )
        val plan = PdfContentCalculator(layout).plan()
        assertTrue("Long realistic letter must be multi-page", plan.totalPages > 1)
        val allText = plan.allText()
        assertTrue("Must contain first paragraph", allText.contains("Peace be with you"))
        assertTrue("Must contain last paragraph", allText.contains("concludes with our deepest"))
        assertTrue("Must contain sender", allText.contains("Lloyd Malto"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF HEADER CHECK
    // ════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════
    // TEXT MEASURER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testDeterministicTextMeasurerDeterministic() {
        val m = DeterministicTextMeasurer()
        val w1 = m.measureTextWidth("Hello World", 11.0, false)
        val w2 = m.measureTextWidth("Hello World", 11.0, false)
        assertEquals(w1, w2, 0.001)
    }

    @Test
    fun testDeterministicTextMeasurerBoldWider() {
        val m = DeterministicTextMeasurer()
        val normal = m.measureTextWidth("Test", 11.0, false)
        val bold = m.measureTextWidth("Test", 11.0, true)
        assertTrue("Bold must be wider than normal", bold > normal)
    }

    @Test
    fun testDeterministicTextMeasurerLongerTextWider() {
        val m = DeterministicTextMeasurer()
        val short = m.measureTextWidth("Hi", 11.0, false)
        val long = m.measureTextWidth("Hello World", 11.0, false)
        assertTrue("Longer text must be wider", long > short)
    }

    @Test
    fun testDeterministicTextMeasurerFontSizeAffectsWidth() {
        val m = DeterministicTextMeasurer()
        val small = m.measureTextWidth("Test", 8.0, false)
        val large = m.measureTextWidth("Test", 16.0, false)
        assertTrue("Larger font must be wider", large > small)
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPER
    // ════════════════════════════════════════════════════════════════════════

    private fun RenderPlan.allText(): String {
        return pages.flatMap { it.lines }.map { it.text }.joinToString(" ")
    }
}
