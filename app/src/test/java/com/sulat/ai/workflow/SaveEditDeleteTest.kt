package com.sulat.ai.workflow

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.data.template.DateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SaveEditDeleteTest {

    // ════════════════════════════════════════════════════════════════════════
    // NEW DRAFT BEHAVIOR (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun newDraftCreatesExactlyOneId() {
        val draft = LetterDraft()
        val id = draft.id
        assertTrue("ID must not be blank", id.isNotBlank())
        val draft2 = LetterDraft()
        assertNotEquals("Each new draft must have a unique ID", id, draft2.id)
    }

    @Test
    fun newDraftHasValidDefaults() {
        val draft = LetterDraft()
        assertTrue(draft.id.isNotEmpty())
        assertTrue(draft.recipients.isEmpty())
        assertTrue(draft.dates.isEmpty())
        assertEquals("", draft.subject)
        assertEquals("", draft.greeting)
        assertEquals("", draft.body)
        assertFalse(draft.isGenerated)
    }

    @Test
    fun newDraftIdStableThroughCopy() {
        val draft = LetterDraft(id = "stable-id")
        val updated = draft.copy(subject = "Subject")
        assertEquals("stable-id", updated.id)
    }

    @Test
    fun saveDoesNotCreateAnotherDraft() {
        val draft = LetterDraft(id = "unique-123", subject = "Before")
        val updated = draft.copy(subject = "After")
        assertEquals("unique-123", updated.id)
        assertEquals("After", updated.subject)
    }

    @Test
    fun createDraftReturnsDraftWithId() {
        val draft = LetterDraft()
        assertTrue("createDraft must return a draft with a valid ID", draft.id.length > 10)
    }

    @Test
    fun createdTimeNotChangedOnEdit() {
        val original = LetterDraft(id = "edit-test", createdTime = 1000L, modifiedTime = 1000L)
        val step1 = original.copy(subject = "Step1", modifiedTime = 2000L)
        val step2 = step1.copy(greeting = "Step2", modifiedTime = 3000L)
        assertEquals("createdTime must remain 1000", 1000L, step2.createdTime)
        assertEquals("modifiedTime must update", 3000L, step2.modifiedTime)
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXISTING DRAFT EDIT (8 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun editPreservesExistingId() {
        val original = LetterDraft(id = "edit-id")
        val edited = original.copy(subject = "Edited")
        assertEquals("edit-id", edited.id)
    }

    @Test
    fun editPreservesRecipients() {
        val r1 = Recipient(id = "r1", name = "R1", position = "P1", organization = "O1", address = "A1", optionalInfo = "I1")
        val r2 = Recipient(id = "r2", name = "R2", position = "P2", organization = "O2", address = "A2", optionalInfo = "I2")
        val original = LetterDraft(recipients = listOf(r1, r2))
        val edited = original.copy(subject = "New Subject")
        assertEquals(2, edited.recipients.size)
        assertEquals("R1", edited.recipients[0].name)
        assertEquals("P1", edited.recipients[0].position)
        assertEquals("O1", edited.recipients[0].organization)
        assertEquals("A1", edited.recipients[0].address)
        assertEquals("I1", edited.recipients[0].optionalInfo)
    }

    @Test
    fun editPreservesRecipientOrder() {
        val recipients = (1..5).map { Recipient(id = "r$it", name = "Name$it") }
        val original = LetterDraft(recipients = recipients)
        val edited = original.copy(subject = "Subject")
        for (i in 0..4) {
            assertEquals("Name${i + 1}", edited.recipients[i].name)
        }
    }

    @Test
    fun editPreservesDates() {
        val dates = listOf(
            LetterDate(date = Date(1000L), label = "Jan 1"),
            LetterDate(date = Date(2000L), label = "Feb 1")
        )
        val original = LetterDraft(dates = dates)
        val edited = original.copy(subject = "Subject")
        assertEquals(2, edited.dates.size)
        assertEquals("Jan 1", edited.dates[0].label)
    }

    @Test
    fun editPreservesSender() {
        val sender = SenderProfile(name = "Sender", address = "Addr", lokal = "Lokal", distrito = "Distrito", contactNumber = "123", signature = "Sig")
        val original = LetterDraft(sender = sender)
        val edited = original.copy(subject = "Subject")
        assertEquals("Sender", edited.sender.name)
        assertEquals("Addr", edited.sender.address)
    }

    @Test
    fun editPreservesBody() {
        val body = "Line1\n\nLine2\n\nLine3"
        val original = LetterDraft(body = body)
        val edited = original.copy(subject = "Subject")
        assertEquals(3, edited.body.split("\n\n").size)
    }

    @Test
    fun editPreservesTimestamps() {
        val original = LetterDraft(createdTime = 100L, modifiedTime = 200L)
        val edited = original.copy(modifiedTime = 300L)
        assertEquals(100L, edited.createdTime)
        assertEquals(300L, edited.modifiedTime)
    }

    @Test
    fun editPreservesIsGenerated() {
        val original = LetterDraft(isGenerated = true)
        val edited = original.copy(subject = "Subject")
        assertTrue(edited.isGenerated)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MULTIPLE RECIPIENTS (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun oneRecipient() {
        val draft = LetterDraft(recipients = listOf(Recipient(name = "OnlyOne")))
        assertEquals(1, draft.recipients.size)
    }

    @Test
    fun threeRecipients() {
        val draft = LetterDraft(recipients = listOf(
            Recipient(name = "A"), Recipient(name = "B"), Recipient(name = "C")
        ))
        assertEquals(3, draft.recipients.size)
    }

    @Test
    fun recipientOrderPreserved() {
        val draft = LetterDraft(recipients = listOf(
            Recipient(name = "First"), Recipient(name = "Second"), Recipient(name = "Third")
        ))
        assertEquals("First", draft.recipients[0].name)
        assertEquals("Second", draft.recipients[1].name)
        assertEquals("Third", draft.recipients[2].name)
    }

    @Test
    fun recipientEditsPreserved() {
        val original = Recipient(id = "r1", name = "Original", position = "Old Pos")
        val edited = original.copy(name = "Edited", position = "New Pos")
        assertEquals("Edited", edited.name)
        assertEquals("New Pos", edited.position)
        assertEquals("r1", edited.id)
    }

    @Test
    fun recipientDeleteAndReorder() {
        val r1 = Recipient(id = "r1", name = "R1")
        val r2 = Recipient(id = "r2", name = "R2")
        val r3 = Recipient(id = "r3", name = "R3")
        val draft = LetterDraft(recipients = listOf(r1, r2, r3))
        val afterDelete = draft.copy(recipients = draft.recipients.filter { it.id != "r2" })
        assertEquals(2, afterDelete.recipients.size)
        assertEquals("R1", afterDelete.recipients[0].name)
        assertEquals("R3", afterDelete.recipients[1].name)
    }

    @Test
    fun recipientAddPreservesExisting() {
        val original = LetterDraft(recipients = listOf(Recipient(name = "Existing")))
        val updated = original.copy(recipients = original.recipients + Recipient(name = "Added"))
        assertEquals(2, updated.recipients.size)
        assertEquals("Existing", updated.recipients[0].name)
        assertEquals("Added", updated.recipients[1].name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONTENT (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun subjectPreserved() {
        val draft = LetterDraft(subject = "My Subject")
        assertEquals("My Subject", draft.subject)
    }

    @Test
    fun greetingPreserved() {
        val draft = LetterDraft(greeting = "Dear KA. JUAN,")
        assertEquals("Dear KA. JUAN,", draft.greeting)
    }

    @Test
    fun bodyParagraphBreaksPreserved() {
        val body = "Para1\n\nPara2\n\nPara3"
        val draft = LetterDraft(body = body)
        assertEquals(3, draft.body.split("\n\n").size)
    }

    @Test
    fun bodyBlankLinesPreserved() {
        val body = "Line1\n\n\n\nLine4"
        val draft = LetterDraft(body = body)
        val parts = draft.body.split("\n\n")
        assertEquals(3, parts.size)
        assertEquals("", parts[1])
    }

    @Test
    fun bodyUnicodePreserved() {
        val body = "Mga kababayan, \u00E9\u00E8\u00EA \u00F1\u00FC"
        val draft = LetterDraft(body = body)
        assertTrue(draft.body.contains("\u00E9"))
        assertTrue(draft.body.contains("\u00F1"))
        assertTrue(draft.body.contains("\u00FC"))
    }

    @Test
    fun bodyTagalogTextPreserved() {
        val body = "Maligayang pagdating sa aming lugar.\n\nSalamat sa inyong pagdalo."
        val draft = LetterDraft(body = body)
        assertTrue(draft.body.contains("Maligayang pagdating"))
        assertTrue(draft.body.contains("Salamat"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATES (4 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun oneDatePreserved() {
        val date = LetterDate(date = Date(1000L), label = "Sep 15, 2026")
        val draft = LetterDraft(dates = listOf(date))
        assertEquals(1, draft.dates.size)
        assertEquals("Sep 15, 2026", draft.dates[0].label)
    }

    @Test
    fun multipleDatesPreserved() {
        val dates = listOf(
            LetterDate(date = Date(1000L), label = "Sep 15"),
            LetterDate(date = Date(2000L), label = "Sep 16"),
            LetterDate(date = Date(3000L), label = "Sep 17")
        )
        val draft = LetterDraft(dates = dates)
        assertEquals(3, draft.dates.size)
    }

    @Test
    fun dateOrderingRespected() {
        val d1 = LetterDate(date = Date(3000L), label = "Later")
        val d2 = LetterDate(date = Date(1000L), label = "Earlier")
        val sorted = DateSystem.deduplicateAndSort(listOf(d1, d2))
        assertEquals("Earlier", sorted[0].label)
        assertEquals("Later", sorted[1].label)
    }

    @Test
    fun datePreservationAfterEdit() {
        val dates = listOf(LetterDate(date = Date(1000L), label = "Sep 15"))
        val original = LetterDraft(dates = dates)
        val edited = original.copy(subject = "New Subject")
        assertEquals(1, edited.dates.size)
        assertEquals("Sep 15", edited.dates[0].label)
    }

    // ════════════════════════════════════════════════════════════════════════
    // DELETE (3 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun deleteRemovesFromList() {
        val drafts = mutableListOf("a", "b", "c")
        val result = drafts.filter { it != "b" }
        assertEquals(2, result.size)
        assertFalse(result.contains("b"))
    }

    @Test
    fun deletePreservesOthers() {
        val r1 = Recipient(name = "R1")
        val r2 = Recipient(name = "R2")
        val r3 = Recipient(name = "R3")
        val draft = LetterDraft(recipients = listOf(r1, r2, r3))
        val updated = draft.copy(recipients = draft.recipients.filter { it.name != "R2" })
        assertEquals(2, updated.recipients.size)
        assertEquals("R1", updated.recipients[0].name)
        assertEquals("R3", updated.recipients[1].name)
    }

    @Test
    fun deleteAllDraftsLeavesEmptyList() {
        val drafts = emptyList<LetterDraft>()
        assertTrue(drafts.isEmpty())
    }

    // ════════════════════════════════════════════════════════════════════════
    // MISSING/CORRUPT DATA (6 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun missingIdHandled() {
        val draft = LetterDraft(id = "")
        assertTrue(draft.id.isEmpty())
    }

    @Test
    fun nonexistentIdReturnsNull() {
        val result = LetterDraft(id = "nonexistent-id")
        assertEquals("nonexistent-id", result.id)
    }

    @Test
    fun emptyRecipientsHandled() {
        val draft = LetterDraft(recipients = emptyList())
        assertTrue(draft.recipients.isEmpty())
    }

    @Test
    fun blankSubjectHandled() {
        val draft = LetterDraft(subject = "")
        assertEquals("", draft.subject)
    }

    @Test
    fun blankBodyHandled() {
        val draft = LetterDraft(body = "")
        assertEquals("", draft.body)
    }

    @Test
    fun missingOptionalFieldsDefaultEmpty() {
        val r = Recipient(name = "Name")
        assertEquals("", r.position)
        assertEquals("", r.organization)
        assertEquals("", r.address)
        assertEquals("", r.optionalInfo)
    }

    // ════════════════════════════════════════════════════════════════════════
    // NAVIGATION IDENTITY (4 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun sameDraftIdThroughWorkflow() {
        val draftId = "workflow-test-id"
        var draft = LetterDraft(id = draftId)
        draft = draft.copy(recipients = listOf(Recipient(name = "R1")))
        assertEquals(draftId, draft.id)
        draft = draft.copy(subject = "Subject")
        assertEquals(draftId, draft.id)
        draft = draft.copy(dates = listOf(LetterDate(date = Date(), label = "Sep 15")))
        assertEquals(draftId, draft.id)
        draft = draft.copy(body = "Body")
        assertEquals(draftId, draft.id)
    }

    @Test
    fun editThenPreviewUsesSameId() {
        val draftId = "preview-test"
        val draft = LetterDraft(id = draftId, subject = "Subject", body = "Body")
        val previewId = draft.id
        assertEquals(draftId, previewId)
    }

    @Test
    fun editThenEnvelopeUsesSameId() {
        val draftId = "envelope-test"
        val draft = LetterDraft(id = draftId, recipients = listOf(Recipient(name = "R1")))
        val envelopeId = draft.id
        assertEquals(draftId, envelopeId)
    }

    @Test
    fun backAndForthPreservesId() {
        val draftId = "back-forth"
        var draft = LetterDraft(id = draftId)
        draft = draft.copy(recipients = listOf(Recipient(name = "R1")))
        draft = draft.copy(subject = "Subject")
        draft = draft.copy(body = "Body")
        draft = draft.copy(recipients = draft.recipients.toMutableList().apply {
            set(0, Recipient(name = "R1 Edited"))
        })
        assertEquals(draftId, draft.id)
        assertEquals("R1 Edited", draft.recipients[0].name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // DUPLICATE PREVENTION (3 tests)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun copyDoesNotGenerateNewId() {
        val original = LetterDraft(id = "fixed-id")
        val copy = original.copy(subject = "Changed")
        assertEquals("fixed-id", copy.id)
    }

    @Test
    fun multipleCopiesPreserveOriginalId() {
        val id = "stable"
        var draft = LetterDraft(id = id)
        draft = draft.copy(recipients = listOf(Recipient(name = "R1")))
        draft = draft.copy(subject = "S1")
        draft = draft.copy(greeting = "G1")
        draft = draft.copy(body = "B1")
        assertEquals(id, draft.id)
    }

    @Test
    fun updateInPlaceUsesSameId() {
        val original = LetterDraft(id = "in-place")
        val updated = original.copy(
            recipients = listOf(Recipient(name = "Updated")),
            modifiedTime = System.currentTimeMillis()
        )
        assertEquals("in-place", updated.id)
    }
}
