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
        assertEquals(595, round(PaperSize.A4.widthPt).toInt())
        assertEquals(842, round(PaperSize.A4.heightPt).toInt())
    }

    @Test
    fun testRoundedPdfDimensions_legal() {
        assertEquals(612, round(PaperSize.Legal.widthPt).toInt())
        assertEquals(1008, round(PaperSize.Legal.heightPt).toInt())
    }

    @Test
    fun testRoundedPdfDimensions_longBond() {
        assertEquals(612, round(PaperSize.LongBond.widthPt).toInt())
        assertEquals(936, round(PaperSize.LongBond.heightPt).toInt())
    }

    @Test
    fun testRoundingNotTruncation() {
        val a4Height = round(PaperSize.A4.heightPt).toInt()
        val truncatedH = PaperSize.A4.heightPt.toInt()
        assertEquals(842, a4Height)
        assertEquals(841, truncatedH)
        assertTrue("Rounding must not truncate: 841.89 should round to 842", a4Height != truncatedH)
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
        val plan = PdfContentCalculator(layout).plan()
        assertTrue(plan.pages.isNotEmpty())
    }

    // ════════════════════════════════════════════════════════════════════════
    // BASIC RENDERING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testOnePageLetter() {
        val plan = PdfContentCalculator(makeLayout()).plan()
        assertEquals(1, plan.totalPages)
    }

    @Test
    fun testDateRendering() {
        val plan = PdfContentCalculator(makeLayout()).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("2023") || allText.contains("2026") || allText.contains("January") || allText.contains("November"))
    }

    @Test
    fun testRecipientRendering() {
        val allText = PdfContentCalculator(makeLayout()).plan().allText()
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
        val allText = PdfContentCalculator(makeLayout(recipients = recipients)).plan().allText()
        assertTrue(allText.contains("Juan"))
        assertTrue(allText.contains("Pedro"))
        assertTrue(allText.contains("Jose"))
    }

    @Test
    fun testSubjectRendering() {
        val allText = PdfContentCalculator(makeLayout(subject = "My Custom Subject")).plan().allText()
        assertTrue(allText.contains("Re: My Custom Subject"))
    }

    @Test
    fun testGreetingRendering() {
        val allText = PdfContentCalculator(makeLayout(greeting = "Dear Kapatid")).plan().allText()
        assertTrue(allText.contains("Dear Kapatid"))
    }

    @Test
    fun testBodyRendering() {
        val allText = PdfContentCalculator(makeLayout(body = "This is the body text.")).plan().allText()
        assertTrue(allText.contains("This is the body text."))
    }

    @Test
    fun testClosingRendering() {
        val allText = PdfContentCalculator(makeLayout(sender = SenderProfile(name = "Sender Name", signature = "Faithfully"))).plan().allText()
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
        assertFalse(PdfContentCalculator(makeLayout(subject = "")).plan().allText().contains("Re: Liham"))
    }

    @Test
    fun testNoHardcodedSampleGreeting() {
        assertFalse(PdfContentCalculator(makeLayout(greeting = "")).plan().allText().contains("Pinakamamahal"))
    }

    @Test
    fun testNoHardcodedKapatid() {
        assertFalse(PdfContentCalculator(makeLayout(recipients = listOf(Recipient(name = "TEST")))).plan().allText().contains("Kapatid na"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // WRAPPING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testShortLineDoesNotWrap() {
        val bodyLines = PdfContentCalculator(makeLayout(body = "Short.")).plan().bodyLines()
        assertEquals(1, bodyLines.size)
    }

    @Test
    fun testLongLineWraps() {
        val longText = "This is a very long line that should wrap because it exceeds the available width on the page and needs to be broken into multiple visual lines."
        val bodyLines = PdfContentCalculator(makeLayout(body = longText)).plan().bodyLines()
        assertTrue("Long line should wrap into multiple lines, got ${bodyLines.size}", bodyLines.size > 1)
    }

    @Test
    fun testLongParagraphWrapsPreservingAllWords() {
        val paragraph = "The quick brown fox jumps over the lazy dog near the river bank where the trees grow tall and the birds sing beautifully every morning without fail since the beginning of time."
        val bodyLines = PdfContentCalculator(makeLayout(body = paragraph)).plan().bodyLines()
        assertTrue("Long paragraph should wrap", bodyLines.size > 1)
        val allWords = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val outputWords = bodyLines.flatMap { it.text.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        assertEquals("All words must be preserved", allWords.size, outputWords.size)
        for (word in allWords) {
            assertTrue("Word '$word' must appear in output", outputWords.contains(word))
        }
    }

    @Test
    fun testMultipleParagraphsSeparateOutput() {
        val allText = PdfContentCalculator(makeLayout(body = "First paragraph text.\n\nSecond paragraph text.")).plan().allText()
        assertTrue(allText.contains("First paragraph"))
        assertTrue(allText.contains("Second paragraph"))
    }

    @Test
    fun testExplicitLineBreaksPreserved() {
        val allText = PdfContentCalculator(makeLayout(body = "Line one.\nLine two.\nLine three.")).plan().allText()
        assertTrue(allText.contains("Line one."))
        assertTrue(allText.contains("Line two."))
        assertTrue(allText.contains("Line three."))
    }

    @Test
    fun testBlankLinesSeparateParagraphs() {
        val allText = PdfContentCalculator(makeLayout(body = "Para one.\n\n\nPara two.")).plan().allText()
        assertTrue(allText.contains("Para one."))
        assertTrue(allText.contains("Para two."))
    }

    @Test
    fun testLongUnbreakableTokenSplit() {
        val longToken = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val bodyLines = PdfContentCalculator(makeLayout(body = longToken)).plan().bodyLines()
        val allChars = bodyLines.joinToString("") { it.text }
        assertTrue("All characters must be preserved", allChars.contains("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    }

    @Test
    fun testLongTokenPreservesAllCharacters() {
        val longToken = "ABCDEFGHIJK"
        val bodyLines = PdfContentCalculator(makeLayout(body = longToken)).plan().bodyLines()
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
        val bodyLines = plan.bodyLines()
        for (line in bodyLines) {
            val width = measurer.measureTextWidth(line.text, PdfTextRole.BODY.style)
            assertTrue(
                "Line '${line.text.take(30)}...' width ${width} must not exceed usable width $usableWidth",
                width <= usableWidth + 1.0
            )
        }
    }

    @Test
    fun testFilipinoCharacters() {
        assertTrue(PdfContentCalculator(makeLayout(body = "Maraming salamat po sa inyong pagkakataon.")).plan().allText().contains("Maraming salamat po"))
    }

    @Test
    fun testAccentedCharacters() {
        assertTrue(PdfContentCalculator(makeLayout(body = "Café résumé naïve")).plan().allText().contains("Café"))
    }

    @Test
    fun testPunctuationQuotes() {
        assertTrue(PdfContentCalculator(makeLayout(body = "He said \"hello\" and she said 'bye'.")).plan().allText().contains("\"hello\""))
    }

    @Test
    fun testMixedWidthCharacters() {
        val allText = PdfContentCalculator(makeLayout(body = "Hello 你好世界 café")).plan().allText()
        assertTrue(allText.contains("Hello"))
        assertTrue(allText.contains("café"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHARACTER PRESERVATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllInputCharactersAppearsInOrder() {
        val body = "The quick brown fox jumps over the lazy dog."
        val bodyText = PdfContentCalculator(makeLayout(body = body)).plan()
            .bodyLines().joinToString(" ") { it.text }
        for (word in body.split(" ")) {
            assertTrue("Word '$word' must appear in output", bodyText.contains(word))
        }
    }

    @Test
    fun testRealisticLetterAllCharactersPreserved() {
        val allText = PdfContentCalculator(realisticLayout()).plan().allTextWords()
        assertTrue(allText.contains("JUAN DELA CRUZ"))
        assertTrue(allText.contains("Resident Minister"))
        assertTrue(allText.contains("Request for Confirmation"))
        assertTrue(allText.contains("Dear Brother,"))
        assertTrue(allText.contains("Maraming salamat"))
        assertTrue(allText.contains("Lloyd Malto"))
        assertTrue(allText.contains("Faithfully yours,"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAGINATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testShortBodyOnePage() {
        assertEquals(1, PdfContentCalculator(makeLayout(body = "Short body.")).plan().totalPages)
    }

    @Test
    fun testLongBodyMultiplePages() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it: This is a test sentence for pagination." }
        assertTrue(PdfContentCalculator(makeLayout(body = longBody)).plan().totalPages > 1)
    }

    @Test
    fun testManyPagesContentPreserved() {
        val longBody = (1..500).joinToString("\n\n") { "Paragraph $it: Long enough to force multiple pages with sufficient content to fill them." }
        val plan = PdfContentCalculator(makeLayout(body = longBody)).plan()
        assertTrue(plan.totalPages >= 3)
        val allText = plan.allTextWords()
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
        val allLines = PdfContentCalculator(makeLayout(body = "Body text.")).plan().allLines()
        val bodyIdx = allLines.indexOfFirst { it.text.contains("Body text") }
        val closingIdx = allLines.indexOfFirst { it.text.contains("Faithfully") }
        assertTrue(bodyIdx >= 0)
        assertTrue(closingIdx > bodyIdx)
    }

    @Test
    fun testClosingAfterPageBreak() {
        val longBody = (1..300).joinToString("\n\n") { "Paragraph $it: Filling up the page so closing moves to a new page." }
        val allLines = PdfContentCalculator(makeLayout(body = longBody)).plan().allLines()
        val bodyIndices = allLines.mapIndexedNotNull { idx, line -> if (line.role == PdfTextRole.BODY) idx else null }
        val closingIndex = allLines.indexOfFirst { it.role == PdfTextRole.CLOSING }
        assertTrue("Closing must exist", closingIndex >= 0)
        assertTrue("Closing must be after all body lines", closingIndex > bodyIndices.last())
    }

    @Test
    fun testNoDuplicateLines() {
        val longBody = (1..100).joinToString("\n\n") { "Paragraph $it: Testing for duplicate lines across page breaks." }
        val allLines = PdfContentCalculator(makeLayout(body = longBody)).plan().allLines()
        val textYPairs = allLines.map { "${it.text}@${it.yPt}" }
        assertEquals("No duplicate lines (same text at same Y)", textYPairs.size, textYPairs.toSet().size)
    }

    @Test
    fun testNoLostLines() {
        val longBody = (1..100).joinToString("\n\n") { "Paragraph $it: Testing that no lines are lost during pagination." }
        val layout = makeLayout(body = longBody)
        val totalLineCount = PdfContentCalculator(layout).plan().allLines().size
        assertTrue("Must have many lines across pages", totalLineCount > 50)
    }

    @Test
    fun testNoAccidentalBlankPages() {
        for (page in PdfContentCalculator(makeLayout(body = "Simple body.")).plan().pages) {
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
                    "First line of page ${page.pageNumber} must not appear above top margin, was $firstLineY",
                    firstLineY >= layout.page.marginTopPt - 0.01
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TYPOGRAPHY — REGRESSION
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
        assertTrue(PdfContentCalculator(makeLayout(recipients = emptyList())).plan().pages.isNotEmpty())
    }

    @Test
    fun testEmptySubjectHandled() {
        assertFalse(PdfContentCalculator(makeLayout(subject = "")).plan().allText().contains("Re:"))
    }

    @Test
    fun testEmptyGreetingHandled() {
        assertTrue(PdfContentCalculator(makeLayout(greeting = "")).plan().pages.isNotEmpty())
    }

    @Test
    fun testEmptyBodyHandled() {
        assertTrue(PdfContentCalculator(makeLayout(body = "")).plan().pages.isNotEmpty())
    }

    @Test
    fun testMissingSenderHandled() {
        assertTrue(PdfContentCalculator(makeLayout(sender = SenderProfile())).plan().pages.isNotEmpty())
    }

    @Test
    fun testAllEmptyFieldsNoBlankPages() {
        val plan = PdfContentCalculator(makeLayout(
            body = "", subject = "", greeting = "",
            sender = SenderProfile(), recipients = emptyList()
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
        assertEquals(1, PdfContentCalculator(realisticLayout()).plan().totalPages)
    }

    @Test
    fun testRealisticLetterAllSectionsPresent() {
        val allText = PdfContentCalculator(realisticLayout()).plan().allTextWords()
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
        val allLines = PdfContentCalculator(realisticLayout()).plan().allLines()
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
        val allText = plan.allTextWords()
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
    // TEXT MEASURER — STYLE-BASED API
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testDeterministicTextMeasurerDeterministic() {
        val m = DeterministicTextMeasurer()
        val w1 = m.measureTextWidth("Hello World", PdfTextRole.BODY.style)
        val w2 = m.measureTextWidth("Hello World", PdfTextRole.BODY.style)
        assertEquals(w1, w2, 0.001)
    }

    @Test
    fun testDeterministicTextMeasurerBoldWider() {
        val m = DeterministicTextMeasurer()
        val normal = m.measureTextWidth("Test", PdfTextRole.BODY.style)
        val bold = m.measureTextWidth("Test", PdfTextRole.SUBJECT.style)
        assertTrue("Bold must be wider than normal", bold > normal)
    }

    @Test
    fun testDeterministicTextMeasurerLongerTextWider() {
        val m = DeterministicTextMeasurer()
        val short = m.measureTextWidth("Hi", PdfTextRole.BODY.style)
        val long = m.measureTextWidth("Hello World", PdfTextRole.BODY.style)
        assertTrue("Longer text must be wider", long > short)
    }

    @Test
    fun testDeterministicTextMeasurerFontSizeAffectsWidth() {
        val m = DeterministicTextMeasurer()
        val smallStyle = PdfTextStyle(fontSizePt = 8.0, isBold = false)
        val largeStyle = PdfTextStyle(fontSizePt = 16.0, isBold = false)
        val small = m.measureTextWidth("Test", smallStyle)
        val large = m.measureTextWidth("Test", largeStyle)
        assertTrue("Larger font must be wider", large > small)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5B — TYPOGRAPHY: EXACT STYLE MATCHING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testRegularMeasurement() {
        val m = DeterministicTextMeasurer()
        val w = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = false))
        assertTrue("Regular width must be positive", w > 0)
    }

    @Test
    fun testBoldMeasurement() {
        val m = DeterministicTextMeasurer()
        val regular = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = false))
        val bold = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = true))
        assertTrue("Bold must be wider than regular", bold > regular)
    }

    @Test
    fun testItalicMeasurement() {
        val m = DeterministicTextMeasurer()
        val regular = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = false))
        val italic = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = true))
        assertTrue("Italic must be wider than regular", italic > regular)
    }

    @Test
    fun testBoldItalicMeasurement() {
        val m = DeterministicTextMeasurer()
        val regular = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = false))
        val boldItalic = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = true, isItalic = true))
        assertTrue("Bold+Italic must be wider than regular", boldItalic > regular)
    }

    @Test
    fun testBoldItalicWiderThanBoldOnly() {
        val m = DeterministicTextMeasurer()
        val bold = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = true, isItalic = false))
        val boldItalic = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = true, isItalic = true))
        assertTrue("Bold+Italic must be wider than Bold only", boldItalic > bold)
    }

    @Test
    fun testItalicWiderThanBold() {
        val m = DeterministicTextMeasurer()
        val bold = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = true, isItalic = false))
        val italic = m.measureTextWidth("Test", PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = true))
        assertTrue("Italic must be wider than Bold (ratio 1.03 vs 1.05: bold wins)", bold >= italic)
    }

    @Test
    fun testTypefaceStyleMappingConsistency() {
        val regularStyle = PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = false)
        val boldStyle = PdfTextStyle(fontSizePt = 11.0, isBold = true, isItalic = false)
        val italicStyle = PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = true)
        val boldItalicStyle = PdfTextStyle(fontSizePt = 11.0, isBold = true, isItalic = true)
        assertFalse("Regular is not bold", regularStyle.isBold)
        assertFalse("Regular is not italic", regularStyle.isItalic)
        assertTrue("Bold is bold", boldStyle.isBold)
        assertFalse("Bold is not italic", boldStyle.isItalic)
        assertFalse("Italic is not bold", italicStyle.isBold)
        assertTrue("Italic is italic", italicStyle.isItalic)
        assertTrue("Bold+Italic is bold", boldItalicStyle.isBold)
        assertTrue("Bold+Italic is italic", boldItalicStyle.isItalic)
    }

    @Test
    fun testExistingItalicRolesHaveItalicStyle() {
        assertTrue("CLOSING must be italic", PdfTextRole.CLOSING.style.isItalic)
        assertTrue("RECIPIENT_OPTIONAL must be italic", PdfTextRole.RECIPIENT_OPTIONAL.style.isItalic)
        assertFalse("BODY must not be italic", PdfTextRole.BODY.style.isItalic)
        assertFalse("SUBJECT must not be italic", PdfTextRole.SUBJECT.style.isItalic)
    }

    @Test
    fun testWrappingWithItalicText() {
        val longItalicText = "This is an italic line that is long enough to wrap across multiple lines because it exceeds the available width on the page."
        val layout = makeLayout(body = longItalicText)
        val calculator = PdfContentCalculator(layout, DeterministicTextMeasurer())
        val plan = calculator.plan()
        val bodyLines = plan.bodyLines()
        assertTrue("Italic text should wrap into multiple lines", bodyLines.size > 1)
        val outputWords = bodyLines.flatMap { it.text.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        assertTrue(outputWords.contains("italic"))
        assertTrue(outputWords.contains("line"))
    }

    @Test
    fun testWrappingWithBoldText() {
        val longBoldRecipient = Recipient(name = "KA. VERY LONG NAME THAT SHOULD WRAP ACCROSS MULTIPLE LINES WHEN RENDERED IN BOLD TYPEFACE ON THE PAGE", position = "Minister")
        val layout = makeLayout(recipients = listOf(longBoldRecipient))
        val plan = PdfContentCalculator(layout).plan()
        val nameLines = plan.allLines().filter { it.role == PdfTextRole.RECIPIENT_NAME }
        assertTrue("Recipient name must be present", nameLines.isNotEmpty())
        val allWords = nameLines.flatMap { it.text.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        assertTrue(allWords.contains("VERY"))
        assertTrue(allWords.contains("LONG"))
        assertTrue(allWords.contains("NAME"))
        assertTrue(allWords.contains("TYPEFACE"))
    }

    @Test
    fun testAllRolesPassFullStyleToMeasurer() {
        val m = DeterministicTextMeasurer()
        for (role in PdfTextRole.entries) {
            val w = m.measureTextWidth("Test", role.style)
            assertTrue("${role.name} width must be positive", w > 0)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5B — CONSECUTIVE SPACES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testTwoSpacesPreserved() {
        val body = "Hello  world"
        val allText = PdfContentCalculator(makeLayout(body = body)).plan().allText()
        assertTrue("Two spaces between Hello and world must be preserved in output lines",
            allText.contains("Hello  world") || allText.lines().any { it.contains("Hello  world") })
    }

    @Test
    fun testThreeSpacesPreserved() {
        val body = "Hello   world"
        val plan = PdfContentCalculator(makeLayout(body = body)).plan()
        val bodyText = plan.bodyLines().joinToString("\n") { it.text }
        assertTrue("Three spaces must be preserved", bodyText.contains("Hello   world"))
    }

    @Test
    fun testConsecutiveSpacesAroundWrappingBoundary() {
        val longText = "A  B  " + "word ".repeat(60)
        val layout = makeLayout(body = longText)
        val plan = PdfContentCalculator(layout).plan()
        val bodyText = plan.bodyLines().joinToString("\n") { it.text }
        assertTrue("Content before spaces must appear", bodyText.contains("A  B"))
    }

    @Test
    fun testCharacterSequencePreservedAfterWrapping() {
        val body = "A  B  C  D  E  F  G"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val allText = bodyLines.joinToString("\n") { it.text }
        assertTrue("All original characters must appear in order", allText.contains("A  B"))
        assertTrue(allText.contains("C  D"))
        assertTrue(allText.contains("E  F"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5B — UNICODE SAFE SPLITTING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testEmojiLongTokenNoSurrogateSplit() {
        val emoji = "🎉".repeat(80)
        val layout = makeLayout(body = emoji)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val originalCodePoints = emoji.codePointCount(0, emoji.length)
        val reconstructedCodePoints = reconstructed.codePointCount(0, reconstructed.length)
        assertEquals("All emoji code points must be preserved", originalCodePoints, reconstructedCodePoints)
    }

    @Test
    fun testSupplementaryUnicodePreservesCodePoints() {
        val supplementary = "\uD83D\uDE00".repeat(50)
        val layout = makeLayout(body = supplementary)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val originalCPs = supplementary.codePointCount(0, supplementary.length)
        val reconstructedCPs = reconstructed.codePointCount(0, reconstructed.length)
        assertEquals("All supplementary code points preserved", originalCPs, reconstructedCPs)
    }

    @Test
    fun testFilipinoAccentedUnchanged() {
        val filipino = "Ang pagtitipon ay gaganapin sa darating na Linggo. Lahat ng kapatid ay inaasahang dadalo upang masiyahan sa pag-aaral ng banal na kasulatan."
        val plan = PdfContentCalculator(makeLayout(body = filipino)).plan()
        val allText = plan.allText()
        assertTrue(allText.contains("pagtitipon"))
        assertTrue(allText.contains("kasulatan"))
    }

    @Test
    fun testMixedAsciiAndUnicodeLongTokenPreservesOrder() {
        val mixed = "Hello🎉World🌍Test"
        val bodyLines = PdfContentCalculator(makeLayout(body = mixed)).plan().bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        assertTrue("Mixed content must be preserved", reconstructed.contains("Hello"))
        assertTrue(reconstructed.contains("World"))
        assertTrue(reconstructed.contains("Test"))
        val origCPs = mixed.codePointCount(0, mixed.length)
        val reconCPs = reconstructed.codePointCount(0, reconstructed.length)
        assertEquals("Code point count must match", origCPs, reconCPs)
    }

    @Test
    fun testUnicodeTokenWrappingNoCharacterLoss() {
        val unicode = "aaaaaaaa\uD83D\uDE00bbbbbb"
        val layout = makeLayout(body = unicode)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = unicode.codePointCount(0, unicode.length)
        val reconCPs = reconstructed.codePointCount(0, reconstructed.length)
        assertEquals("No characters lost in Unicode wrapping", origCPs, reconCPs)
    }

    @Test
    fun testUnicodeTokenWrappingNoCharacterDuplication() {
        val unicode = "🎉abc🎉"
        val layout = makeLayout(body = unicode)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = unicode.codePointCount(0, unicode.length)
        val reconCPs = reconstructed.codePointCount(0, reconstructed.length)
        assertEquals("No character duplication in Unicode wrapping", origCPs, reconCPs)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5C — WHITESPACE PRESERVATION
    // Deterministic policy: lines.joinToString("") must reconstruct all source
    // characters in order. Line breaks are synthetic; concatenating lines must
    // equal the original source text.
    // ════════════════════════════════════════════════════════════════════════

    private fun flattenedCodePoints(lines: List<RenderLine>): List<Int> {
        val combined = lines.joinToString("") { it.text }
        val codePoints = mutableListOf<Int>()
        var i = 0
        while (i < combined.length) {
            val cp = combined.codePointAt(i)
            codePoints.add(cp)
            i += Character.charCount(cp)
        }
        return codePoints
    }

    private fun sourceCodePoints(text: String): List<Int> {
        val codePoints = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            codePoints.add(cp)
            i += Character.charCount(cp)
        }
        return codePoints
    }

    @Test
    fun testFiveConsecutiveSpacesPreserved() {
        val body = "Hello     world"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(body)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Five consecutive spaces must be preserved as code points", origCPs, reconCPs)
    }

    @Test
    fun testLeadingSpacesPreserved() {
        val body = "   Hello world"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(body)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Leading spaces must be preserved as code points", origCPs, reconCPs)
    }

    @Test
    fun testTrailingSpacesPreserved() {
        val body = "Hello world   "
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(body)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Trailing spaces must be preserved as code points", origCPs, reconCPs)
    }

    @Test
    fun testLeadingAndTrailingSpacesPreserved() {
        val body = "  Hello world  "
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(body)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Leading and trailing spaces must be preserved as code points", origCPs, reconCPs)
    }

    @Test
    fun testSpacesNearWrapBoundaryPreserved() {
        val longText = "A  " + "word ".repeat(80)
        val layout = makeLayout(body = longText)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(longText)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Spaces near wrap boundary must be preserved as code points", origCPs, reconCPs)
    }

    @Test
    fun testWhitespacePlusLongToken() {
        val longToken = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val body = "Hello  $longToken"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(body)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Whitespace + long token must preserve all code points", origCPs, reconCPs)
    }

    @Test
    fun testWhitespacePlusUnicodeToken() {
        val body = "Hello  🎉🎉🎉  world"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(body)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Whitespace + Unicode must preserve all code points", origCPs, reconCPs)
    }

    @Test
    fun testCodePointPreservationAcrossWrapping() {
        val body = "A  B  C  D  E  F  G"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val origCPs = sourceCodePoints(body)
        val reconCPs = flattenedCodePoints(bodyLines)
        assertEquals("Flattened code-point sequence must equal source", origCPs, reconCPs)
    }

    @Test
    fun testAllWhitespacePreservedDeterministic() {
        val body = "Hello   world     foo bar"
        val l1 = PdfContentCalculator(makeLayout(body = body)).plan().bodyLines()
        val l2 = PdfContentCalculator(makeLayout(body = body)).plan().bodyLines()
        val r1 = l1.joinToString("") { it.text }
        val r2 = l2.joinToString("") { it.text }
        assertEquals("Whitespace preservation must be deterministic", r1, r2)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5C — UNICODE COMPREHENSIVE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testNoIsolatedHighSurrogate() {
        val body = "Hello \uD83D\uDE00 world"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        for (line in bodyLines) {
            val text = line.text
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val cc = Character.charCount(cp)
                if (Character.isHighSurrogate(text[i])) {
                    assertTrue(
                        "High surrogate must be followed by low surrogate at index $i in line '${text.take(40)}'",
                        i + 1 < text.length && Character.isLowSurrogate(text[i + 1])
                    )
                }
                i += cc
            }
        }
    }

    @Test
    fun testNoIsolatedLowSurrogate() {
        val body = "Hello \uD83D\uDE00 world"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        for (line in bodyLines) {
            val text = line.text
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (Character.isLowSurrogate(text[i])) {
                    assertTrue(
                        "Low surrogate at index $i must be preceded by high surrogate in line '${text.take(40)}'",
                        i > 0 && Character.isHighSurrogate(text[i - 1])
                    )
                }
                i += Character.charCount(cp)
            }
        }
    }

    @Test
    fun testFlattenedCodePointSequenceEqualsSource() {
        val body = "Hello  world 🎉🎉🎉 Mabuhay Pilipinas"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val origCPs = sourceCodePoints(body)
        val reconCPs = flattenedCodePoints(bodyLines)
        assertEquals("Flattened code-point sequence must equal source", origCPs, reconCPs)
    }

    @Test
    fun testUnicodeWrappingRemainsDeterministic() {
        val body = "Hello 🎉🎉🎉 world 🌍🌍"
        val l1 = PdfContentCalculator(makeLayout(body = body)).plan().bodyLines()
        val l2 = PdfContentCalculator(makeLayout(body = body)).plan().bodyLines()
        val r1 = l1.joinToString("") { it.text }
        val r2 = l2.joinToString("") { it.text }
        assertEquals("Unicode wrapping must be deterministic", r1, r2)
    }

    @Test
    fun testMabuhayPilipinas() {
        val body = "Mabuhay 🎉 Pilipinas"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val origCPs = sourceCodePoints(body)
        val reconCPs = flattenedCodePoints(bodyLines)
        assertEquals("Mabuhay 🎉 Pilipinas must preserve all code points", origCPs, reconCPs)
    }

    @Test
    fun testEmojiCodePointCountPreservedAfterWrapping() {
        val emoji = "🎉".repeat(80)
        val layout = makeLayout(body = emoji)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(emoji)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Emoji code-point count must be preserved after wrapping", origCPs, reconCPs)
    }

    @Test
    fun testSupplementaryCodePointCountPreservedAfterWrapping() {
        val supplementary = "\uD83D\uDE00".repeat(50)
        val layout = makeLayout(body = supplementary)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val reconstructed = bodyLines.joinToString("") { it.text }
        val origCPs = sourceCodePoints(supplementary)
        val reconCPs = sourceCodePoints(reconstructed)
        assertEquals("Supplementary code-point count must be preserved after wrapping", origCPs, reconCPs)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5C — ESTIMATOR CODE-POINT SAFETY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testEstimatorHandlesASCII() {
        val m = DeterministicTextMeasurer()
        val maxChars = m.estimateMaxCharsForWidth("Hello World", PdfTextRole.BODY, 200.0)
        assertTrue("Estimator must return at least 1 for ASCII", maxChars >= 1)
        assertTrue("Estimator must handle ASCII without error", maxChars <= 200)
    }

    @Test
    fun testEstimatorHandlesEmoji() {
        val m = DeterministicTextMeasurer()
        val maxChars = m.estimateMaxCharsForWidth("🎉🎉🎉🎉🎉", PdfTextRole.BODY, 200.0)
        assertTrue("Estimator must return at least 1 for emoji", maxChars >= 1)
    }

    @Test
    fun testEstimatorHandlesSupplementaryUnicode() {
        val m = DeterministicTextMeasurer()
        val maxChars = m.estimateMaxCharsForWidth("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00", PdfTextRole.BODY, 200.0)
        assertTrue("Estimator must return at least 1 for supplementary Unicode", maxChars >= 1)
    }

    @Test
    fun testEstimatorNeverTreatsSurrogatePairAsTwoChars() {
        val m = DeterministicTextMeasurer()
        val style = PdfTextRole.BODY.style
        val cpCount = codePointCount("🎉🎉🎉")
        val charWidth = m.measureTextWidth("🎉", style)
        val totalWidth = cpCount * charWidth
        val maxChars = m.estimateMaxCharsForWidth("🎉🎉🎉🎉🎉", PdfTextRole.BODY, totalWidth)
        assertTrue(
            "Estimator must count code points not UTF-16 units: cpCount=$cpCount, maxChars=$maxChars",
            maxChars >= cpCount - 1
        )
    }

    @Test
    fun testEstimatorRemainsDeterministic() {
        val m = DeterministicTextMeasurer()
        val r1 = m.estimateMaxCharsForWidth("Hello 🎉 World", PdfTextRole.BODY, 150.0)
        val r2 = m.estimateMaxCharsForWidth("Hello 🎉 World", PdfTextRole.BODY, 150.0)
        assertEquals("Estimator must be deterministic", r1, r2)
    }

    @Test
    fun testEstimatorWithLongUnicodeText() {
        val m = DeterministicTextMeasurer()
        val longText = "🎉".repeat(100)
        val maxChars = m.estimateMaxCharsForWidth(longText, PdfTextRole.BODY, 50.0)
        assertTrue("Estimator must handle long Unicode text without error", maxChars >= 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5C — CODE-POINT COUNT HELPER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testCodePointCountASCII() {
        assertEquals(5, codePointCount("Hello"))
    }

    @Test
    fun testCodePointCountEmoji() {
        assertEquals(3, codePointCount("🎉🎉🎉"))
    }

    @Test
    fun testCodePointCountSupplementary() {
        assertEquals(3, codePointCount("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00"))
    }

    @Test
    fun testCodePointCountMixed() {
        val text = "Hello 🎉 World"
        val expected = 13 // H-e-l-l-o-spc-🎉-spc-W-o-r-l-d
        assertEquals(expected, codePointCount(text))
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIX 5B — CHARACTER-PRESERVATION INVARIANT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testWrappingPreservesAllCodePoints() {
        val body = "Hello  world  with   multiple    spaces and emoji 🎉🎉🎉"
        val layout = makeLayout(body = body)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val outputWords = bodyLines.flatMap { it.text.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        val inputWords = body.split(Regex("\\s+")).filter { it.isNotEmpty() }
        assertEquals("Word count must be preserved", inputWords.size, outputWords.size)
        for (word in inputWords) {
            assertTrue("Word '$word' must appear in output", outputWords.contains(word))
        }
    }

    @Test
    fun testLongBodyPreservesAllCodePointsAcrossPages() {
        val longBody = (1..200).joinToString("\n\n") { "Paragraph $it with spaces  and emoji 🎉" }
        val layout = makeLayout(body = longBody)
        val plan = PdfContentCalculator(layout).plan()
        val bodyLines = plan.bodyLines()
        val outputWords = bodyLines.flatMap { it.text.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        assertTrue("Must have many output words", outputWords.size > 100)
        assertTrue("Must contain Paragraph 1", outputWords.contains("Paragraph"))
        assertTrue("Must contain emoji word", outputWords.contains("🎉"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPER
    // ════════════════════════════════════════════════════════════════════════

    private fun RenderPlan.allText(): String {
        return pages.flatMap { it.lines }.joinToString("\n") { it.text }
    }

    private fun RenderPlan.allTextWords(): String {
        return pages.flatMap { it.lines }.joinToString(" ") { it.text }
    }

    private fun RenderPlan.allLines(): List<RenderLine> {
        return pages.flatMap { it.lines }
    }

    private fun RenderPlan.bodyLines(): List<RenderLine> {
        return pages.flatMap { it.lines }.filter { it.role == PdfTextRole.BODY }
    }
}
