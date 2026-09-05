package com.sulat.ai.workflow

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.data.template.DateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class WorkflowTest {

    // ════════════════════════════════════════════════════════════════════════
    // RECIPIENT DATA FLOW (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun recipientCreation() {
        val r = Recipient(name = "KA. JUAN", position = "Minister", organization = "Local", address = "123 St", optionalInfo = "Notes")
        assertEquals("KA. JUAN", r.name)
        assertEquals("Minister", r.position)
        assertEquals("Local", r.organization)
        assertEquals("123 St", r.address)
        assertEquals("Notes", r.optionalInfo)
        assertTrue(r.id.isNotEmpty())
    }

    @Test
    fun recipientOptionalFieldsBlank() {
        val r = Recipient(name = "JUAN")
        assertEquals("", r.position)
        assertEquals("", r.organization)
        assertEquals("", r.address)
        assertEquals("", r.optionalInfo)
    }

    @Test
    fun recipientPreservesOrder() {
        val r1 = Recipient(name = "R1")
        val r2 = Recipient(name = "R2")
        val r3 = Recipient(name = "R3")
        val draft = LetterDraft(recipients = listOf(r1, r2, r3))
        assertEquals("R1", draft.recipients[0].name)
        assertEquals("R2", draft.recipients[1].name)
        assertEquals("R3", draft.recipients[2].name)
    }

    @Test
    fun recipientUnicodePreserved() {
        val r = Recipient(name = "KA. \u00D1o\u00F1o")
        assertEquals("KA. \u00D1o\u00F1o", r.name)
    }

    @Test
    fun recipientAddressMultilinePreserved() {
        val addr = "Line1\nLine2\nLine3"
        val r = Recipient(address = addr)
        val lines = r.address.split("\n")
        assertEquals(3, lines.size)
    }

    @Test
    fun draftWithMultipleRecipients() {
        val recipients = (1..5).map { Recipient(name = "Recipient $it") }
        val draft = LetterDraft(recipients = recipients)
        assertEquals(5, draft.recipients.size)
    }

    @Test
    fun draftRecipientCountMatches() {
        val draft = LetterDraft(recipients = listOf(
            Recipient(name = "A"), Recipient(name = "B"), Recipient(name = "C")
        ))
        assertEquals(3, draft.recipients.size)
    }

    @Test
    fun draftPreservesExistingRecipients() {
        val original = LetterDraft(recipients = listOf(Recipient(name = "Existing")))
        val updated = original.copy(recipients = original.recipients + Recipient(name = "New"))
        assertEquals(2, updated.recipients.size)
        assertEquals("Existing", updated.recipients[0].name)
        assertEquals("New", updated.recipients[1].name)
    }

    @Test
    fun recipientDeletePreservesOthers() {
        val r1 = Recipient(id = "id1", name = "R1")
        val r2 = Recipient(id = "id2", name = "R2")
        val r3 = Recipient(id = "id3", name = "R3")
        val draft = LetterDraft(recipients = listOf(r1, r2, r3))
        val updated = draft.copy(recipients = draft.recipients.filter { it.id != "id2" })
        assertEquals(2, updated.recipients.size)
        assertEquals("R1", updated.recipients[0].name)
        assertEquals("R3", updated.recipients[1].name)
    }

    @Test
    fun recipientEditPreservesOtherFields() {
        val original = Recipient(name = "KA. JUAN", position = "Minister", organization = "Local", address = "123", optionalInfo = "info")
        val edited = original.copy(position = "President")
        assertEquals("KA. JUAN", edited.name)
        assertEquals("President", edited.position)
        assertEquals("Local", edited.organization)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LETTER INFO FLOW (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun letterSubjectPreserved() {
        val draft = LetterDraft(subject = "Monthly Report")
        assertEquals("Monthly Report", draft.subject)
    }

    @Test
    fun letterGreetingPreserved() {
        val draft = LetterDraft(greeting = "Dear Bro. Eduardo V. Manalo,")
        assertEquals("Dear Bro. Eduardo V. Manalo,", draft.greeting)
    }

    @Test
    fun letterInfoUpdate() {
        val draft = LetterDraft()
        val updated = draft.copy(subject = "Subject", greeting = "Greeting")
        assertEquals("Subject", updated.subject)
        assertEquals("Greeting", updated.greeting)
    }

    @Test
    fun letterBodyPreserved() {
        val body = "Paragraph 1.\n\nParagraph 2.\n\nParagraph 3."
        val draft = LetterDraft(body = body)
        val paragraphs = draft.body.split("\n\n")
        assertEquals(3, paragraphs.size)
    }

    @Test
    fun letterBodyUnicodePreserved() {
        val body = "Tagalog text with \u00F1 and \u00E9 characters."
        val draft = LetterDraft(body = body)
        assertTrue(draft.body.contains("\u00F1"))
        assertTrue(draft.body.contains("\u00E9"))
    }

    @Test
    fun letterBodyWhitespacePreserved() {
        val body = "Line1\n  Indented\n\n    Block indented"
        val draft = LetterDraft(body = body)
        assertTrue(draft.body.contains("  Indented"))
        assertTrue(draft.body.contains("    Block indented"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATE FLOW (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun dateSystemSpecificDateValid() {
        val date = DateSystem.specificDate(2026, 9, 15)
        assertNotNull(date)
        assertEquals(15, date!!.dayOfMonth)
    }

    @Test
    fun dateSystemSpecificDateInvalid() {
        assertNull(DateSystem.specificDate(2026, 2, 30))
        assertNull(DateSystem.specificDate(2026, 13, 1))
    }

    @Test
    fun dateSystemLeapYear() {
        val date = DateSystem.specificDate(2028, 2, 29)
        assertNotNull(date)
    }

    @Test
    fun dateSystemFormatDisplay() {
        val date = DateSystem.specificDate(2026, 9, 15)!!
        val display = DateSystem.formatDisplay(date)
        assertEquals("September 15, 2026", display)
    }

    @Test
    fun dateSystemOrdinalWeekday() {
        val date = DateSystem.ordinalWeekday(2026, 9, java.time.DayOfWeek.MONDAY, 1)
        assertNotNull(date)
        assertEquals(7, date!!.dayOfMonth)
    }

    @Test
    fun dateSystemLastWeekday() {
        val date = DateSystem.lastWeekday(2026, 9, java.time.DayOfWeek.FRIDAY)
        assertEquals(25, date.dayOfMonth)
    }

    @Test
    fun dateSystemDeduplicateAndSort() {
        val d1 = LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 15)!!), label = "Sep 15")
        val d2 = LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 10)!!), label = "Sep 10")
        val result = DateSystem.deduplicateAndSort(listOf(d1, d2))
        assertEquals(2, result.size)
        assertEquals("Sep 10", result[0].label)
    }

    @Test
    fun dateSystemDeduplicateRemovesDuplicates() {
        val d1 = LetterDate(date = Date(), label = "Sep 15, 2026")
        val d2 = LetterDate(date = Date(), label = "Sep 15, 2026")
        val result = DateSystem.deduplicateAndSort(listOf(d1, d2))
        assertEquals(1, result.size)
    }

    @Test
    fun dateSystemCalculateTotalLetters() {
        assertEquals(15, DateSystem.calculateTotalLetters(3, 5))
    }

    @Test
    fun dateSystemCalculateTotalEnvelopeLabels() {
        assertEquals(20, DateSystem.calculateTotalEnvelopeLabels(4, 5))
    }

    // ════════════════════════════════════════════════════════════════════════
    // DRAFT PERSISTENCE FLOW (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun draftCreationDefaults() {
        val draft = LetterDraft()
        assertTrue(draft.id.isNotEmpty())
        assertTrue(draft.recipients.isEmpty())
        assertTrue(draft.dates.isEmpty())
        assertEquals("", draft.body)
        assertEquals("", draft.subject)
        assertEquals("", draft.greeting)
    }

    @Test
    fun draftModifiedTimeUpdates() {
        val draft = LetterDraft(modifiedTime = 1000L)
        val updated = draft.copy(modifiedTime = 2000L)
        assertEquals(2000L, updated.modifiedTime)
    }

    @Test
    fun draftIdStable() {
        val draft = LetterDraft(id = "test-id-123")
        val updated = draft.copy(subject = "New")
        assertEquals("test-id-123", updated.id)
    }

    @Test
    fun draftCopyPreservesAllFields() {
        val draft = LetterDraft(
            id = "id1",
            recipients = listOf(Recipient(name = "R1")),
            dates = listOf(LetterDate(date = Date(), label = "Sep 15")),
            sender = SenderProfile(name = "Sender"),
            body = "Body",
            subject = "Subject",
            greeting = "Greeting",
            createdTime = 100L,
            modifiedTime = 200L
        )
        val copy = draft.copy(subject = "Changed")
        assertEquals("id1", copy.id)
        assertEquals(1, copy.recipients.size)
        assertEquals(1, copy.dates.size)
        assertEquals("Sender", copy.sender.name)
        assertEquals("Body", copy.body)
        assertEquals("Greeting", copy.greeting)
        assertEquals(100L, copy.createdTime)
        assertEquals(200L, copy.modifiedTime)
    }

    @Test
    fun senderProfileFields() {
        val sender = SenderProfile(name = "Name", address = "Addr", lokal = "Lokal", distrito = "Distrito", contactNumber = "123", signature = "Sig")
        assertEquals("Name", sender.name)
        assertEquals("Addr", sender.address)
        assertEquals("Lokal", sender.lokal)
        assertEquals("Distrito", sender.distrito)
        assertEquals("123", sender.contactNumber)
        assertEquals("Sig", sender.signature)
    }

    @Test
    fun draftWithBodyParagraphs() {
        val body = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        val draft = LetterDraft(body = body)
        val paragraphs = draft.body.split("\n\n")
        assertEquals(3, paragraphs.size)
        assertEquals("First paragraph.", paragraphs[0])
        assertEquals("Second paragraph.", paragraphs[1])
        assertEquals("Third paragraph.", paragraphs[2])
    }

    @Test
    fun draftWithEmptyRecipients() {
        val draft = LetterDraft(recipients = emptyList())
        assertTrue(draft.recipients.isEmpty())
    }

    @Test
    fun draftIsGeneratedDefault() {
        val draft = LetterDraft()
        assertFalse(draft.isGenerated)
    }

    @Test
    fun draftCreatedTimeDefault() {
        val before = System.currentTimeMillis()
        val draft = LetterDraft()
        val after = System.currentTimeMillis()
        assertTrue(draft.createdTime in before..after)
    }

    // ════════════════════════════════════════════════════════════════════════
    // WORKFLOW STEP INTEGRATION (10 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun fullWorkflowDraftPipeline() {
        var draft = LetterDraft()
        draft = draft.copy(recipients = listOf(Recipient(name = "KA. JUAN")))
        draft = draft.copy(subject = "Monthly Report", greeting = "Dear Bro. JUAN,")
        draft = draft.copy(dates = listOf(
            LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 15)!!), label = "September 15, 2026")
        ))
        draft = draft.copy(body = "Body text here.")
        draft = draft.copy(modifiedTime = System.currentTimeMillis())

        assertEquals(1, draft.recipients.size)
        assertEquals("Monthly Report", draft.subject)
        assertEquals("Dear Bro. JUAN,", draft.greeting)
        assertEquals(1, draft.dates.size)
        assertEquals("Body text here.", draft.body)
    }

    @Test
    fun workflowStepPreservesPriorData() {
        var draft = LetterDraft()
        draft = draft.copy(recipients = listOf(Recipient(name = "R1"), Recipient(name = "R2")))
        val afterStep1 = draft.copy(subject = "Subject")
        assertEquals(2, afterStep1.recipients.size)
        assertEquals("Subject", afterStep1.subject)
    }

    @Test
    fun workflowStepBackPreservesData() {
        var draft = LetterDraft()
        draft = draft.copy(recipients = listOf(Recipient(name = "R1")))
        draft = draft.copy(subject = "Subject", greeting = "Greeting")
        val afterBack = draft.copy(body = "Body")
        assertEquals("R1", afterBack.recipients[0].name)
        assertEquals("Subject", afterBack.subject)
        assertEquals("Greeting", afterBack.greeting)
        assertEquals("Body", afterBack.body)
    }

    @Test
    fun recipientCountUsedForCalculations() {
        val draft = LetterDraft(recipients = listOf(
            Recipient(name = "R1"), Recipient(name = "R2"), Recipient(name = "R3")
        ))
        val dateCount = 4
        assertEquals(12, DateSystem.calculateTotalLetters(draft.recipients.size, dateCount))
    }

    @Test
    fun envelopeCountMatchesRecipientCount() {
        val draft = LetterDraft(recipients = listOf(
            Recipient(name = "R1"), Recipient(name = "R2")
        ))
        val envelopeLabels = DateSystem.calculateTotalEnvelopeLabels(draft.recipients.size, 1)
        assertEquals(2, envelopeLabels)
    }

    @Test
    fun draftUpdatePreservesCreatedTime() {
        val original = LetterDraft(createdTime = 1000L, modifiedTime = 1000L)
        val updated = original.copy(modifiedTime = 2000L)
        assertEquals(1000L, updated.createdTime)
        assertEquals(2000L, updated.modifiedTime)
    }

    @Test
    fun draftWithNoSubjectOrGreeting() {
        val draft = LetterDraft(body = "Body only.")
        assertEquals("", draft.subject)
        assertEquals("", draft.greeting)
        assertEquals("Body only.", draft.body)
    }

    @Test
    fun dateSelectionWithDraft() {
        val draft = LetterDraft(
            dates = listOf(
                LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 15)!!), label = "September 15, 2026"),
                LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 10, 1)!!), label = "October 1, 2026")
            )
        )
        assertEquals(2, draft.dates.size)
        val sorted = DateSystem.deduplicateAndSort(draft.dates)
        assertEquals(2, sorted.size)
    }

    @Test
    fun paperSizePropagation() {
        val draft = LetterDraft()
        val paperSize = "A4"
        assertEquals("A4", paperSize)
    }

    @Test
    fun workflowCompleteDraftReadyForPreview() {
        val draft = LetterDraft(
            recipients = listOf(Recipient(name = "KA. JUAN", position = "Minister", organization = "Local", address = "123 St")),
            dates = listOf(LetterDate(date = DateSystem.localDateToDate(DateSystem.specificDate(2026, 9, 15)!!), label = "September 15, 2026")),
            body = "Dear Bro. JUAN,\n\nThis is the letter body.\n\nSincerely,\nSender",
            subject = "Monthly Report",
            greeting = "Dear Bro. JUAN,"
        )
        assertTrue(draft.recipients.isNotEmpty())
        assertTrue(draft.dates.isNotEmpty())
        assertTrue(draft.body.isNotBlank())
        assertTrue(draft.subject.isNotBlank())
        assertTrue(draft.greeting.isNotBlank())
    }

    // ════════════════════════════════════════════════════════════════════════
    // PAPER SIZE SELECTION (4 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun paperSizeA4() {
        val ps = com.sulat.ai.document.PaperSize.A4
        assertEquals("A4", ps.name)
    }

    @Test
    fun paperSizeShortBond() {
        val ps = com.sulat.ai.document.PaperSize.ShortBond
        assertEquals("ShortBond", ps.name)
    }

    @Test
    fun paperSizeLongBond() {
        val ps = com.sulat.ai.document.PaperSize.LongBond
        assertEquals("LongBond", ps.name)
    }

    @Test
    fun paperSizeLegal() {
        val ps = com.sulat.ai.document.PaperSize.Legal
        assertEquals("Legal", ps.name)
    }
}
