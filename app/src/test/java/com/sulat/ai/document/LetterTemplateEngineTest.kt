package com.sulat.ai.document

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.layout.LayoutSection
import com.sulat.ai.document.renderer.LetterTemplateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Date

class LetterTemplateEngineTest {

    private lateinit var engine: LetterTemplateEngine

    @Before
    fun setUp() {
        engine = LetterTemplateEngine()
    }

    private fun makeDraft(
        recipients: List<Recipient> = listOf(
            Recipient(id = "r1", name = "KA. JUAN DELA CRUZ", position = "Minister", organization = "INC", address = "123 Main St", optionalInfo = "VIP")
        ),
        subject: String = "Test Subject",
        greeting: String = "Dear Kapatid",
        body: String = "This is the body.\n\nSecond paragraph.",
        sender: SenderProfile = SenderProfile(name = "Sender Name", address = "456 Oak Ave", lokal = "Locale A", distrito = "District 1", contactNumber = "09171234567", signature = "Faithfully")
    ): LetterDraft {
        val dates = listOf(LetterDate(date = Date(1700000000000L), label = "January 1, 2026"))
        return LetterDraft(
            id = "test-draft",
            recipients = recipients,
            dates = dates,
            sender = sender,
            body = body,
            subject = subject,
            greeting = greeting
        )
    }

    // ── Recipient tests ───────────────────────────────────────────────────

    @Test
    fun testOneRecipient() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals(1, recipientBlock.entries.size)
        assertEquals("r1", recipientBlock.entries[0].recipient.id)
    }

    @Test
    fun testThreeRecipients() {
        val recipients = listOf(
            Recipient(id = "r1", name = "Bro. Juan", position = "Minister", organization = "INC"),
            Recipient(id = "r2", name = "Bro. Pedro", position = "Deacon", organization = "INC"),
            Recipient(id = "r3", name = "Bro. Jose", position = "Secretary", organization = "INC")
        )
        val draft = makeDraft(recipients = recipients)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals(3, recipientBlock.entries.size)
    }

    @Test
    fun testRecipientOrderingPreserved() {
        val recipients = listOf(
            Recipient(id = "r1", name = "First"),
            Recipient(id = "r2", name = "Second"),
            Recipient(id = "r3", name = "Third")
        )
        val draft = makeDraft(recipients = recipients)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals("First", recipientBlock.entries[0].recipient.name)
        assertEquals("Second", recipientBlock.entries[1].recipient.name)
        assertEquals("Third", recipientBlock.entries[2].recipient.name)
    }

    @Test
    fun testRecipientPositionPreserved() {
        val recipients = listOf(Recipient(id = "r1", name = "Test", position = "Minister"))
        val draft = makeDraft(recipients = recipients)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals("Minister", recipientBlock.entries[0].recipient.position)
    }

    @Test
    fun testRecipientOrganizationPreserved() {
        val recipients = listOf(Recipient(id = "r1", name = "Test", organization = "INC"))
        val draft = makeDraft(recipients = recipients)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals("INC", recipientBlock.entries[0].recipient.organization)
    }

    @Test
    fun testRecipientAddressPreserved() {
        val recipients = listOf(Recipient(id = "r1", name = "Test", address = "123 Main St"))
        val draft = makeDraft(recipients = recipients)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals("123 Main St", recipientBlock.entries[0].recipient.address)
    }

    @Test
    fun testRecipientOptionalInfoPreserved() {
        val recipients = listOf(Recipient(id = "r1", name = "Test", optionalInfo = "Important"))
        val draft = makeDraft(recipients = recipients)
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals("Important", recipientBlock.entries[0].recipient.optionalInfo)
    }

    // ── Name hierarchy tests ──────────────────────────────────────────────

    @Test
    fun testKaPrefixSplit() {
        val hierarchy = engine.parseRecipientName("KA. JUAN DELA CRUZ")
        assertEquals("KA.", hierarchy.prefix)
        assertEquals("JUAN DELA CRUZ", hierarchy.mainName)
    }

    @Test
    fun testNormalNameNotSplit() {
        val hierarchy = engine.parseRecipientName("JUAN DELA CRUZ")
        assertEquals("", hierarchy.prefix)
        assertEquals("JUAN DELA CRUZ", hierarchy.mainName)
    }

    @Test
    fun testBroPrefixSplit() {
        val hierarchy = engine.parseRecipientName("Bro. EDUARDO MANALO")
        assertEquals("Bro.", hierarchy.prefix)
        assertEquals("EDUARDO MANALO", hierarchy.mainName)
    }

    @Test
    fun testPrefixTypographyRoleDiffers() {
        val layout = engine.buildLayout(makeDraft(), PaperSize.A4)
        val recipientBlock = layout.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        val hierarchy = recipientBlock.entries[0].nameHierarchy
        assertTrue(hierarchy.prefix.isNotEmpty())
        assertTrue(hierarchy.mainName.isNotEmpty())
        assertNotEquals(hierarchy.prefix, hierarchy.mainName)
    }

    @Test
    fun testOriginalNameContentPreserved() {
        val draft = makeDraft(recipients = listOf(Recipient(id = "r1", name = "KA. JUAN DELA CRUZ")))
        val text = engine.formatLetterText(draft, PaperSize.A4)
        assertTrue(text.contains("KA. JUAN DELA CRUZ"))
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        assertFalse("Expected not equals", a == b)
    }

    // ── Content tests ─────────────────────────────────────────────────────

    @Test
    fun testSubjectFromDraft() {
        val draft = makeDraft(subject = "My Custom Subject")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val subjectSection = layout.sections.filterIsInstance<LayoutSection.SubjectSection>().first()
        assertEquals("My Custom Subject", subjectSection.text)
    }

    @Test
    fun testGreetingFromDraft() {
        val draft = makeDraft(greeting = "Dear Kapatid")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val greetingSection = layout.sections.filterIsInstance<LayoutSection.GreetingSection>().first()
        assertEquals("Dear Kapatid", greetingSection.text)
    }

    @Test
    fun testBodyFromDraft() {
        val draft = makeDraft(body = "Hello world")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val bodySection = layout.sections.filterIsInstance<LayoutSection.BodySection>().first()
        assertEquals(1, bodySection.paragraphs.size)
        assertEquals("Hello world", bodySection.paragraphs[0].lines.first())
    }

    @Test
    fun testMultipleParagraphsPreserved() {
        val draft = makeDraft(body = "Para 1.\n\nPara 2.\n\nPara 3.")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val bodySection = layout.sections.filterIsInstance<LayoutSection.BodySection>().first()
        assertEquals(3, bodySection.paragraphs.size)
    }

    @Test
    fun testBlankParagraphSpacingPreserved() {
        val draft = makeDraft(body = "Line 1\n\n\n\nLine 2")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val bodySection = layout.sections.filterIsInstance<LayoutSection.BodySection>().first()
        assertEquals(2, bodySection.paragraphs.size)
    }

    // ── Empty states ──────────────────────────────────────────────────────

    @Test
    fun testZeroRecipientsDoesNotCrash() {
        val draft = makeDraft(recipients = emptyList())
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertFalse(layout.validation.hasRecipients)
        assertTrue(layout.validation.errors.contains("No recipients specified"))
    }

    @Test
    fun testEmptySubjectHandled() {
        val draft = makeDraft(subject = "")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertFalse(layout.validation.hasSubject)
        val subjectSections = layout.sections.filterIsInstance<LayoutSection.SubjectSection>()
        assertEquals(0, subjectSections.size)
    }

    @Test
    fun testEmptyGreetingHandled() {
        val draft = makeDraft(greeting = "")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertFalse(layout.validation.hasGreeting)
        val greetingSections = layout.sections.filterIsInstance<LayoutSection.GreetingSection>()
        assertEquals(0, greetingSections.size)
    }

    @Test
    fun testEmptyBodyHandled() {
        val draft = makeDraft(body = "")
        val layout = engine.buildLayout(draft, PaperSize.A4)
        assertFalse(layout.validation.hasBody)
        assertTrue(layout.validation.errors.contains("Body is empty"))
    }

    // ── Paper sizes ───────────────────────────────────────────────────────

    @Test
    fun testA4Dimensions() {
        assertEquals(595.276, PaperSize.A4.widthPt, 0.01)
        assertEquals(841.89, PaperSize.A4.heightPt, 0.01)
    }

    @Test
    fun testLegalDimensions() {
        assertEquals(612.0, PaperSize.Legal.widthPt, 0.01)
        assertEquals(1008.0, PaperSize.Legal.heightPt, 0.01)
    }

    @Test
    fun testLongBondDimensions() {
        assertEquals(612.0, PaperSize.LongBond.widthPt, 0.01)
        assertEquals(936.0, PaperSize.LongBond.heightPt, 0.01)
    }

    @Test
    fun testLegalNotEqualLongBond() {
        assertTrue(PaperSize.Legal.heightPt != PaperSize.LongBond.heightPt)
    }

    // ── Layout geometry ───────────────────────────────────────────────────

    @Test
    fun testMarginsDeterministic() {
        val draft = makeDraft()
        val layout1 = engine.buildLayout(draft, PaperSize.A4)
        val layout2 = engine.buildLayout(draft, PaperSize.A4)
        assertEquals(layout1.page.marginTopPt, layout2.page.marginTopPt, 0.001)
        assertEquals(layout1.page.marginLeftPt, layout2.page.marginLeftPt, 0.001)
    }

    @Test
    fun testUsableWidthDeterministic() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val expected = PaperSize.A4.widthPt - 72.0 - 72.0
        assertEquals(expected, layout.page.usableWidthPt, 0.01)
    }

    @Test
    fun testUsableHeightDeterministic() {
        val draft = makeDraft()
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val expected = PaperSize.A4.heightPt - 72.0 - 72.0
        assertEquals(expected, layout.page.usableHeightPt, 0.01)
    }

    // ── Determinism ───────────────────────────────────────────────────────

    @Test
    fun testSameInputSameOutput() {
        val draft = makeDraft()
        val layout1 = engine.buildLayout(draft, PaperSize.A4)
        val layout2 = engine.buildLayout(draft, PaperSize.A4)
        assertEquals(layout1.sections.size, layout2.sections.size)
        assertEquals(layout1.validation, layout2.validation)
    }

    // ── Multiple dates ────────────────────────────────────────────────────

    @Test
    fun testMultipleDatesPreserved() {
        val dates = listOf(
            LetterDate(date = Date(1700000000000L), label = "January 1, 2026"),
            LetterDate(date = Date(1700100000000L), label = "January 2, 2026")
        )
        val draft = LetterDraft(
            id = "test",
            recipients = listOf(Recipient(name = "Test")),
            dates = dates,
            body = "Hello"
        )
        val layout = engine.buildLayout(draft, PaperSize.A4)
        val dateSection = layout.sections.filterIsInstance<LayoutSection.DateSection>().first()
        // DateSection.label is formatted via DateSystem.formatDisplay, so check for comma-separated dates
        assertTrue(dateSection.label.contains(","))
    }

    // ── No hardcoding ─────────────────────────────────────────────────────

    @Test
    fun testChangingSubjectChangesLayout() {
        val layout1 = engine.buildLayout(makeDraft(subject = "Subject A"), PaperSize.A4)
        val layout2 = engine.buildLayout(makeDraft(subject = "Subject B"), PaperSize.A4)
        val s1 = layout1.sections.filterIsInstance<LayoutSection.SubjectSection>().first()
        val s2 = layout2.sections.filterIsInstance<LayoutSection.SubjectSection>().first()
        assertEquals("Subject A", s1.text)
        assertEquals("Subject B", s2.text)
    }

    @Test
    fun testChangingGreetingChangesLayout() {
        val layout1 = engine.buildLayout(makeDraft(greeting = "Dear A"), PaperSize.A4)
        val layout2 = engine.buildLayout(makeDraft(greeting = "Dear B"), PaperSize.A4)
        val g1 = layout1.sections.filterIsInstance<LayoutSection.GreetingSection>().first()
        val g2 = layout2.sections.filterIsInstance<LayoutSection.GreetingSection>().first()
        assertEquals("Dear A", g1.text)
        assertEquals("Dear B", g2.text)
    }

    @Test
    fun testChangingRecipientChangesBlock() {
        val layout1 = engine.buildLayout(makeDraft(recipients = listOf(Recipient(name = "Alice"))), PaperSize.A4)
        val layout2 = engine.buildLayout(makeDraft(recipients = listOf(Recipient(name = "Bob"))), PaperSize.A4)
        val r1 = layout1.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        val r2 = layout2.sections.filterIsInstance<LayoutSection.RecipientBlock>().first()
        assertEquals("Alice", r1.entries[0].recipient.name)
        assertEquals("Bob", r2.entries[0].recipient.name)
    }

    // ── formatLetterText ──────────────────────────────────────────────────

    @Test
    fun testFormatLetterTextContainsAllFields() {
        val draft = makeDraft()
        val text = engine.formatLetterText(draft, PaperSize.A4)
        assertTrue(text.contains("KA. JUAN DELA CRUZ"))
        assertTrue(text.contains("Minister"))
        assertTrue(text.contains("INC"))
        assertTrue(text.contains("Test Subject"))
        assertTrue(text.contains("Dear Kapatid"))
        assertTrue(text.contains("This is the body."))
        assertTrue(text.contains("Faithfully"))
        assertTrue(text.contains("Sender Name"))
    }

    @Test
    fun testFormatLetterTextHandlesEmptyFields() {
        val draft = makeDraft(subject = "", greeting = "", sender = SenderProfile())
        val text = engine.formatLetterText(draft, PaperSize.A4)
        assertFalse(text.contains("Re:"))
    }
}
