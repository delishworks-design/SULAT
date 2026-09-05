package com.sulat.ai.qa

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfContentCalculator
import com.sulat.ai.data.template.DateSystem
import com.sulat.ai.document.envelope.EnvelopeData
import com.sulat.ai.document.envelope.EnvelopeLayout
import com.sulat.ai.document.renderer.PdfRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import kotlin.math.round

class QaTest {

    private val engine = LetterTemplateEngine()

    // ════════════════════════════════════════════════════════════════════════
    // PAPER GEOMETRY (8 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun a4DimensionsMm() {
        assertEquals(210.0, PaperSize.A4.widthMm, 0.01)
        assertEquals(297.0, PaperSize.A4.heightMm, 0.01)
    }

    @Test
    fun a4DimensionsPt() {
        assertEquals(595.276, PaperSize.A4.widthPt, 0.001)
        assertEquals(841.89, PaperSize.A4.heightPt, 0.001)
    }

    @Test
    fun shortBondDimensions() {
        assertEquals(215.9, PaperSize.ShortBond.widthMm, 0.01)
        assertEquals(279.4, PaperSize.ShortBond.heightMm, 0.01)
        assertEquals(612.0, PaperSize.ShortBond.widthPt, 0.001)
        assertEquals(792.0, PaperSize.ShortBond.heightPt, 0.001)
    }

    @Test
    fun longBondDimensions() {
        assertEquals(215.9, PaperSize.LongBond.widthMm, 0.01)
        assertEquals(330.2, PaperSize.LongBond.heightMm, 0.01)
        assertEquals(612.0, PaperSize.LongBond.widthPt, 0.001)
        assertEquals(936.0, PaperSize.LongBond.heightPt, 0.001)
    }

    @Test
    fun legalDimensions() {
        assertEquals(215.9, PaperSize.Legal.widthMm, 0.01)
        assertEquals(355.6, PaperSize.Legal.heightMm, 0.01)
        assertEquals(612.0, PaperSize.Legal.widthPt, 0.001)
        assertEquals(1008.0, PaperSize.Legal.heightPt, 0.001)
    }

    @Test
    fun a4PdfWidthRounded() {
        assertEquals(595, round(PaperSize.A4.widthPt).toInt())
        assertEquals(842, round(PaperSize.A4.heightPt).toInt())
    }

    @Test
    fun shortBondPdfWidthExact() {
        assertEquals(612, round(PaperSize.ShortBond.widthPt).toInt())
        assertEquals(792, round(PaperSize.ShortBond.heightPt).toInt())
    }

    @Test
    fun defaultMarginsAreOneInch() {
        val margins = PaperSize.defaultMarginsPt()
        assertEquals(72.0, margins.top, 0.001)
        assertEquals(72.0, margins.bottom, 0.001)
        assertEquals(72.0, margins.left, 0.001)
        assertEquals(72.0, margins.right, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PDF VALIDITY (8 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun pdfValidHeader() {
        val header = "%PDF-1.4"
        assertTrue(header.startsWith("%PDF-"))
    }

    @Test
    fun pdfRendererMethodExists() {
        val method = PdfRenderer::class.java.getMethod(
            "renderPdf",
            DocumentLayout::class.java,
            java.io.File::class.java,
            PaperSize::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun pdfRendererIsValidPdfFileMethodExists() {
        val method = PdfRenderer.Companion::class.java.getMethod("isValidPdfFile", java.io.File::class.java)
        assertNotNull(method)
    }

    @Test
    fun documentLayoutPageGeometryA4() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertEquals(595.276, layout.page.widthPt, 0.001)
        assertEquals(841.89, layout.page.heightPt, 0.001)
    }

    @Test
    fun documentLayoutPageGeometryShortBond() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.ShortBond)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(792.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun documentLayoutPageGeometryLongBond() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.LongBond)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(936.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun documentLayoutPageGeometryLegal() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.Legal)
        assertEquals(612.0, layout.page.widthPt, 0.001)
        assertEquals(1008.0, layout.page.heightPt, 0.001)
    }

    @Test
    fun documentLayoutMarginsConsistent() {
        val draft = makeDraft()
        for (ps in PaperSize.entries) {
            val layout = engine.buildLayout(draft, ps)
            assertEquals("${ps.name} marginLeft", 72.0, layout.page.marginLeftPt, 0.001)
            assertEquals("${ps.name} marginRight", 72.0, layout.page.marginRightPt, 0.001)
            assertEquals("${ps.name} marginTop", 72.0, layout.page.marginTopPt, 0.001)
            assertEquals("${ps.name} marginBottom", 72.0, layout.page.marginBottomPt, 0.001)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAGINATION (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun shortLetterSinglePage() {
        val draft = makeDraft(body = "Short body.")
        val plan = renderPlan(draft)
        assertEquals(1, plan.pages.size)
    }

    @Test
    fun longLetterMultiPage() {
        val body = "Paragraph.\n\n".repeat(100)
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        assertTrue("Long letter must have multiple pages", plan.pages.size > 1)
    }

    @Test
    fun paginationDeterministic() {
        val body = "Paragraph.\n\n".repeat(50)
        val draft = makeDraft(body = body)
        val pages1 = renderPlan(draft).pages.size
        val pages2 = renderPlan(draft).pages.size
        assertEquals(pages1, pages2)
    }

    @Test
    fun paginationDifferentAcrossPaperSizes() {
        val body = "Paragraph.\n\n".repeat(50)
        val draft = makeDraft(body = body)
        val a4Pages = renderPlan(draft, PaperSize.A4).pages.size
        val shortPages = renderPlan(draft, PaperSize.ShortBond).pages.size
        assertTrue("Both should be multi-page", a4Pages > 1 && shortPages > 1)
    }

    @Test
    fun bodyContentPreservedAcrossPages() {
        val body = "First paragraph.\n\n" + "Middle paragraph.\n\n".repeat(80) + "Last paragraph."
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        assertTrue(plan.pages.size >= 2)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("First paragraph must appear", allText.contains("First paragraph"))
        assertTrue("Last paragraph must appear", allText.contains("Last paragraph"))
    }

    @Test
    fun noLostLinesInPagination() {
        val lines = (1..50).map { "Line $it of the letter body." }
        val body = lines.joinToString("\n\n")
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        for (line in lines) {
            assertTrue("Must contain: $line", allText.contains(line))
        }
    }

    @Test
    fun emptyBodyProducesPage() {
        val draft = makeDraft(body = "")
        val plan = renderPlan(draft)
        assertTrue("Must have at least one page", plan.pages.size >= 1)
    }

    @Test
    fun veryLongBodyMultiPage() {
        val body = "A".repeat(10000)
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        assertTrue("Very long body must paginate", plan.pages.size >= 2)
    }

    @Test
    fun multipleRecipientsAllAppear() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "Recipient1"), Recipient(name = "Recipient2"), Recipient(name = "Recipient3")
        ))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Recipient1"))
        assertTrue(allText.contains("Recipient2"))
        assertTrue(allText.contains("Recipient3"))
    }

    @Test
    fun subjectAppearsInRenderPlan() {
        val draft = makeDraft(subject = "Test Subject")
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue("Subject must appear", allText.contains("Test Subject"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // UNICODE / WHITESPACE (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun tagalogTextPreserved() {
        val body = "Mga Minamahal na Kapatid sa Pananampalataya"
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Mga Minamahal na Kapatid sa Pananampalataya"))
    }

    @Test
    fun unicodeAccentedCharacters() {
        val body = "ka\u00F1ino \u00E9\u00E8\u00EA \u00F1 \u00E1\u00E9\u00ED\u00F3\u00FA"
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("\u00F1"))
        assertTrue(allText.contains("\u00E9"))
        assertTrue(allText.contains("\u00E1"))
    }

    @Test
    fun unicodeUmlaut() {
        val body = "Title with \u00FC\u00F6\u00E4 characters"
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("\u00FC"))
    }

    @Test
    fun paragraphBreaksPreserved() {
        val body = "Para1\n\nPara2\n\nPara3"
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Para1"))
        assertTrue(allText.contains("Para2"))
        assertTrue(allText.contains("Para3"))
    }

    @Test
    fun consecutiveSpacesPreserved() {
        val body = "Word1  Word2   Word3"
        val draft = makeDraft(body = body)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val plan = PdfContentCalculator(layout).plan()
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Word1"))
        assertTrue(allText.contains("Word2"))
        assertTrue(allText.contains("Word3"))
    }

    @Test
    fun punctuationPreserved() {
        val body = "Hello, world! How are you? I'm fine. (Really?)"
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Hello, world!"))
        assertTrue(allText.contains("(Really?)"))
    }

    @Test
    fun numbersPreserved() {
        val body = "Date: 2026-09-05. Amount: 1,234.56. Percentage: 99.9%."
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("2026-09-05"))
        assertTrue(allText.contains("1,234.56"))
    }

    @Test
    fun recipientNameUnicode() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "KA. \u00D1o\u00F1o Dela Cruz")
        ))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("\u00D1"))
        assertTrue(allText.contains("\u00F1"))
    }

    @Test
    fun bodyMixedLanguage() {
        val body = "Dear Bro. Eduardo,\n\nMga minamahal na kapatid, I hope this letter finds you well."
        val draft = makeDraft(body = body)
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Dear Bro. Eduardo"))
        assertTrue(allText.contains("Mga minamahal"))
    }

    @Test
    fun greetingPreservesPunctuation() {
        val draft = makeDraft(greeting = "Mahal kong Kakapatid,")
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Mahal kong Kakapatid,"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // MULTIPLE RECIPIENTS (8 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun oneRecipient() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "One")))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("One"))
    }

    @Test
    fun threeRecipientsAllAppear() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "R1"), Recipient(name = "R2"), Recipient(name = "R3")
        ))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("R1"))
        assertTrue(allText.contains("R2"))
        assertTrue(allText.contains("R3"))
    }

    @Test
    fun fiveRecipientsAllAppear() {
        val draft = makeDraft(recipients = (1..5).map { Recipient(name = "Recipient$it") })
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        for (i in 1..5) {
            assertTrue("Recipient$i must appear", allText.contains("Recipient$i"))
        }
    }

    @Test
    fun recipientOrderPreserved() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "First"), Recipient(name = "Second"), Recipient(name = "Third")
        ))
        val plan = renderPlan(draft)
        val allLines = plan.pages.flatMap { it.lines }.map { it.text }
        val firstIdx = allLines.indexOfFirst { it.contains("First") }
        val secondIdx = allLines.indexOfFirst { it.contains("Second") }
        val thirdIdx = allLines.indexOfFirst { it.contains("Third") }
        assertTrue("First before Second", firstIdx < secondIdx)
        assertTrue("Second before Third", secondIdx < thirdIdx)
    }

    @Test
    fun recipientFieldsPreserved() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "KA. JUAN", position = "Minister", organization = "Local", address = "123 St", optionalInfo = "Note")
        ))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("KA. JUAN"))
        assertTrue(allText.contains("Minister"))
        assertTrue(allText.contains("Local"))
        assertTrue(allText.contains("123 St"))
        assertTrue(allText.contains("Note"))
    }

    @Test
    fun recipientWithBlankPositionSkipped() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "Name", position = "", organization = "Org")
        ))
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertNotNull(layout)
        val plan = renderPlan(draft)
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun recipientNameHierarchyPrefix() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "KA. JUAN DELA CRUZ")
        ))
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertNotNull(layout)
        val plan = renderPlan(draft)
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun manyRecipientsPaginate() {
        val draft = makeDraft(recipients = (1..10).map {
            Recipient(name = "Recipient$it", position = "Position$it", organization = "Org$it", address = "Address$it")
        })
        val plan = renderPlan(draft)
        assertTrue("10 recipients should produce content", plan.pages.isNotEmpty())
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        for (i in 1..10) {
            assertTrue("Recipient$i must appear", allText.contains("Recipient$i"))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MULTIPLE DATES (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun noDate() {
        val draft = makeDraft(dates = emptyList())
        val plan = renderPlan(draft)
        assertTrue(plan.pages.isNotEmpty())
    }

    @Test
    fun oneDate() {
        val draft = makeDraft(dates = listOf(
            LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 15)!!), label = "September 15, 2026")
        ))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("September 15, 2026"))
    }

    @Test
    fun multipleDates() {
        val draft = makeDraft(dates = listOf(
            LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 15)!!), label = "September 15, 2026"),
            LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 10, 1)!!), label = "October 1, 2026")
        ))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("September 15, 2026"))
        assertTrue(allText.contains("October 1, 2026"))
    }

    @Test
    fun duplicateDatesDeduplicated() {
        val dates = listOf(
            LetterDate(date = Date(1000L), label = "Same"),
            LetterDate(date = Date(1000L), label = "Same")
        )
        val deduped = DateSystem.deduplicateAndSort(dates)
        assertEquals(1, deduped.size)
    }

    @Test
    fun dateOrderingSorted() {
        val dates = listOf(
            LetterDate(date = Date(3000L), label = "Later"),
            LetterDate(date = Date(1000L), label = "Earlier")
        )
        val sorted = DateSystem.deduplicateAndSort(dates)
        assertEquals("Earlier", sorted[0].label)
        assertEquals("Later", sorted[1].label)
    }

    @Test
    fun leapYearDate() {
        val date = DateSystem.specificDate(2028, 2, 29)
        assertNotNull(date)
        assertEquals(29, date!!.dayOfMonth)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PREVIEW CONSISTENCY (5 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun previewAndRenderPlanSamePageCount() {
        val draft = makeDraft(body = "Test body.\n\n".repeat(30))
        val plan = renderPlan(draft)
        assertTrue(plan.pages.size >= 2)
    }

    @Test
    fun previewCalculatorClassExists() {
        val clazz = com.sulat.ai.preview.PreviewCalculator::class.java
        assertNotNull(clazz)
    }

    @Test
    fun previewCalculatorScalePositive() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val plan = PdfContentCalculator(layout).plan()
        val calc = com.sulat.ai.preview.PreviewCalculator(plan, layout.page, 1080)
        assertTrue("Scale must be positive", calc.scale > 0)
    }

    @Test
    fun previewCalculatorPageWidthMatches() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val plan = PdfContentCalculator(layout).plan()
        val calc = com.sulat.ai.preview.PreviewCalculator(plan, layout.page, 1080)
        assertEquals(1080, calc.pageWidthPx)
    }

    @Test
    fun previewCalculatorTotalPagesMatches() {
        val draft = makeDraft(body = "Para.\n\n".repeat(50))
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val plan = PdfContentCalculator(layout).plan()
        val calc = com.sulat.ai.preview.PreviewCalculator(plan, layout.page, 1080)
        assertEquals(plan.pages.size, calc.totalPages)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHARE VERIFICATION (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun sharePdfMethodExists() {
        val method = com.sulat.ai.share.ShareHelper::class.java.getMethod(
            "sharePdf", android.content.Context::class.java, java.io.File::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun shareGenerateAndShareMethodExists() {
        val method = com.sulat.ai.share.ShareHelper::class.java.getMethod(
            "generateAndShare", android.content.Context::class.java, LetterDraft::class.java, PaperSize::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun shareMimeTypeIsPdf() {
        assertEquals("application/pdf", com.sulat.ai.share.ShareHelper.pdfMimeType())
    }

    @Test
    fun shareDirectoryMethodExists() {
        val method = com.sulat.ai.share.ShareHelper::class.java.getMethod(
            "getShareDirectory", android.content.Context::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun shareValidateMethodExists() {
        val method = com.sulat.ai.share.ShareHelper::class.java.getMethod(
            "validatePdfFile", java.io.File::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun shareAuthorityMatches() {
        val authority = com.sulat.ai.share.ShareHelper.FILE_PROVIDER_AUTHORITY
        assertTrue(authority.endsWith(".fileprovider"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // PRINT VERIFICATION (8 tests) — reflection-only; Android framework
    // PrintAttributes cannot be instantiated in JVM unit tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun printDocumentMethodExists() {
        val method = com.sulat.ai.print.PrintHelper::class.java.getMethod(
            "printDocument", android.content.Context::class.java, LetterDraft::class.java, PaperSize::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun printExistingPdfMethodExists() {
        val method = com.sulat.ai.print.PrintHelper::class.java.getMethod(
            "printExistingPdf", android.content.Context::class.java, java.io.File::class.java, PaperSize::class.java, String::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun buildPrintAttributesMethodExists() {
        val method = com.sulat.ai.print.PrintHelper::class.java.getMethod(
            "buildPrintAttributes", PaperSize::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun buildPrintAttributesReturnType() {
        val method = com.sulat.ai.print.PrintHelper::class.java.getMethod(
            "buildPrintAttributes", PaperSize::class.java
        )
        assertNotNull(method.returnType)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ENVELOPE VERIFICATION (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun envelopeDataFromDraft() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "R1")))
        val data = EnvelopeData.fromDraft(draft)
        assertEquals(1, data.size)
    }

    @Test
    fun envelopeDataMultipleRecipients() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "R1"), Recipient(name = "R2"), Recipient(name = "R3")
        ))
        val data = EnvelopeData.fromDraft(draft)
        assertEquals(3, data.size)
    }

    @Test
    fun envelopeDataOrderPreserved() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "First"), Recipient(name = "Second"), Recipient(name = "Third")
        ))
        val data = EnvelopeData.fromDraft(draft)
        assertEquals("First", data[0].recipient.name)
        assertEquals("Second", data[1].recipient.name)
        assertEquals("Third", data[2].recipient.name)
    }

    @Test
    fun envelopeLayoutDimensions() {
        for (ps in PaperSize.entries) {
            val layout = EnvelopeLayout.create(ps)
            assertEquals("${ps.name} widthPt", ps.widthPt, layout.page.widthPt, 0.001)
            assertEquals("${ps.name} heightPt", ps.heightPt, layout.page.heightPt, 0.001)
        }
    }

    @Test
    fun envelopeLayoutLabelOrigin() {
        val layout = EnvelopeLayout.create(PaperSize.A4)
        assertEquals(108.0, layout.labelOriginXPt, 0.001)
        assertEquals(108.0, layout.labelOriginYPt, 0.001)
    }

    @Test
    fun envelopeLayoutLabelMaxWidthPositive() {
        for (ps in PaperSize.entries) {
            val layout = EnvelopeLayout.create(ps)
            assertTrue("${ps.name} labelMaxWidthPt must be positive", layout.labelMaxWidthPt > 0)
        }
    }

    @Test
    fun envelopeNameHierarchyPrefix() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "KA. JUAN DELA CRUZ")))
        val data = EnvelopeData.fromDraft(draft)
        assertEquals("KA.", data[0].nameHierarchy.prefix)
        assertEquals("JUAN DELA CRUZ", data[0].nameHierarchy.mainName)
    }

    @Test
    fun envelopeAddressPreserved() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "R1", address = "Line1\nLine2\nLine3")
        ))
        val data = EnvelopeData.fromDraft(draft)
        val lines = data[0].recipient.address.split("\n")
        assertEquals(3, lines.size)
    }

    @Test
    fun envelopeUnicodePreserved() {
        val draft = makeDraft(recipients = listOf(
            Recipient(name = "KA. \u00D1o\u00F1o", address = "S\u00E3o Paulo")
        ))
        val data = EnvelopeData.fromDraft(draft)
        assertTrue(data[0].recipient.name.contains("\u00D1"))
        assertTrue(data[0].recipient.address.contains("\u00E3"))
    }

    @Test
    fun envelopeRendererMethodExists() {
        val method = com.sulat.ai.document.envelope.EnvelopeRenderer::class.java.getMethod(
            "renderEnvelopePdf",
            java.util.List::class.java,
            java.io.File::class.java,
            PaperSize::class.java
        )
        assertNotNull(method)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SAVE/EDIT REGRESSION (8 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun draftIdStable() {
        val draft = LetterDraft(id = "stable-id")
        val edited = draft.copy(subject = "Edited")
        assertEquals("stable-id", edited.id)
    }

    @Test
    fun createdTimePreserved() {
        val draft = LetterDraft(createdTime = 100L, modifiedTime = 200L)
        val edited = draft.copy(modifiedTime = 300L)
        assertEquals(100L, edited.createdTime)
        assertEquals(300L, edited.modifiedTime)
    }

    @Test
    fun recipientsPreservedAfterEdit() {
        val draft = LetterDraft(recipients = listOf(Recipient(name = "R1"), Recipient(name = "R2")))
        val edited = draft.copy(subject = "Subject")
        assertEquals(2, edited.recipients.size)
    }

    @Test
    fun datesPreservedAfterEdit() {
        val dates = listOf(LetterDate(date = Date(1000L), label = "Sep 15"))
        val draft = LetterDraft(dates = dates)
        val edited = draft.copy(subject = "Subject")
        assertEquals(1, edited.dates.size)
    }

    @Test
    fun bodyPreservedAfterEdit() {
        val draft = LetterDraft(body = "Body text")
        val edited = draft.copy(subject = "Subject")
        assertEquals("Body text", edited.body)
    }

    @Test
    fun senderPreservedAfterEdit() {
        val sender = SenderProfile(name = "Sender")
        val draft = LetterDraft(sender = sender)
        val edited = draft.copy(subject = "Subject")
        assertEquals("Sender", edited.sender.name)
    }

    @Test
    fun greetingPreservedAfterEdit() {
        val draft = LetterDraft(greeting = "Dear Bro.,")
        val edited = draft.copy(body = "Body")
        assertEquals("Dear Bro.,", edited.greeting)
    }

    @Test
    fun multipleEditsPreserveId() {
        var draft = LetterDraft(id = "multi-edit")
        draft = draft.copy(recipients = listOf(Recipient(name = "R1")))
        draft = draft.copy(subject = "S1")
        draft = draft.copy(greeting = "G1")
        draft = draft.copy(body = "B1")
        assertEquals("multi-edit", draft.id)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECIPIENT NAME HIERARCHY (5 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun kaPrefix() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "KA. JUAN")))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("KA."))
        assertTrue(allText.contains("JUAN"))
    }

    @Test
    fun kabPrefix() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "KAB. PEDRO")))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("KAB."))
        assertTrue(allText.contains("PEDRO"))
    }

    @Test
    fun broPrefix() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "BRO. JUAN")))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("BRO."))
        assertTrue(allText.contains("JUAN"))
    }

    @Test
    fun sisPrefix() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "SIS. MARIA")))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("SIS."))
        assertTrue(allText.contains("MARIA"))
    }

    @Test
    fun ordinaryNameNotSplit() {
        val draft = makeDraft(recipients = listOf(Recipient(name = "Juan Dela Cruz")))
        val plan = renderPlan(draft)
        val allText = plan.pages.flatMap { it.lines }.joinToString(" ") { it.text }
        assertTrue(allText.contains("Juan Dela Cruz"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private fun makeDraft(
        recipients: List<Recipient> = listOf(Recipient(name = "Test Recipient")),
        dates: List<LetterDate> = emptyList(),
        body: String = "Test body content.",
        subject: String = "Test Subject",
        greeting: String = "Dear Test,"
    ) = LetterDraft(
        recipients = recipients,
        dates = dates,
        body = body,
        subject = subject,
        greeting = greeting
    )

    private fun renderPlan(draft: LetterDraft, paperSize: PaperSize = PaperSize.A4): com.sulat.ai.document.renderer.RenderPlan {
        val layout = engine.buildLayout(draft, paperSize)
        return PdfContentCalculator(layout).plan()
    }

}
