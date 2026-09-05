package com.sulat.ai.data.persistence

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class PersistenceManagerCrudTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        PersistenceManager.clearDrafts(context)
    }

    @After
    fun tearDown() {
        PersistenceManager.clearDrafts(context)
    }

    private fun makeDraft(id: String = "draft-a", body: String = "Body A"): LetterDraft {
        return LetterDraft(
            id = id,
            recipients = listOf(
                Recipient(id = "r1", name = "Bro. Juan", position = "Minister", organization = "INC")
            ),
            dates = listOf(
                LetterDate(date = Date(1700000000000L), label = "January 1, 2026")
            ),
            sender = SenderProfile(name = "Sender"),
            body = body,
            subject = "Subject A",
            greeting = "Dear A"
        )
    }

    // TEST A: CREATE
    @Test
    fun testCreateSingleDraft() {
        val draft = makeDraft(id = "create-test")
        PersistenceManager.saveDraft(context, draft)

        val drafts = PersistenceManager.loadDrafts(context)
        assertEquals(1, drafts.size)
        assertEquals("create-test", drafts[0].id)
        assertEquals("Body A", drafts[0].body)
    }

    // TEST B: MULTIPLE
    @Test
    fun testSaveMultipleDrafts() {
        val a = makeDraft(id = "draft-a", body = "Body A")
        val b = makeDraft(id = "draft-b", body = "Body B")
        PersistenceManager.saveDraft(context, a)
        PersistenceManager.saveDraft(context, b)

        val drafts = PersistenceManager.loadDrafts(context)
        assertEquals(2, drafts.size)
        assertTrue(drafts.any { it.id == "draft-a" })
        assertTrue(drafts.any { it.id == "draft-b" })
    }

    // TEST C: UPDATE
    @Test
    fun testUpdateDraftReplacesExisting() {
        val original = makeDraft(id = "update-test", body = "Original body")
        PersistenceManager.saveDraft(context, original)

        val updated = makeDraft(id = "update-test", body = "Updated body")
        PersistenceManager.saveDraft(context, updated)

        val drafts = PersistenceManager.loadDrafts(context)
        assertEquals(1, drafts.size)
        assertEquals("Updated body", drafts[0].body)
    }

    // TEST D: GET
    @Test
    fun testGetDraftById() {
        val draft = makeDraft(id = "get-test")
        PersistenceManager.saveDraft(context, draft)

        val found = PersistenceManager.getDraft(context, "get-test")
        assertNotNull(found)
        assertEquals("get-test", found!!.id)
        assertEquals("Body A", found.body)
    }

    @Test
    fun testGetNonexistentDraftReturnsNull() {
        val found = PersistenceManager.getDraft(context, "nonexistent")
        assertNull(found)
    }

    // TEST E: DELETE
    @Test
    fun testDeleteDraft() {
        val draft = makeDraft(id = "delete-test")
        PersistenceManager.saveDraft(context, draft)

        PersistenceManager.deleteDraft(context, "delete-test")

        val drafts = PersistenceManager.loadDrafts(context)
        assertTrue(drafts.none { it.id == "delete-test" })
    }

    @Test
    fun testDeleteOnlyRemovesTargetDraft() {
        val a = makeDraft(id = "keep-this", body = "Keep")
        val b = makeDraft(id = "delete-this", body = "Delete")
        PersistenceManager.saveDraft(context, a)
        PersistenceManager.saveDraft(context, b)

        PersistenceManager.deleteDraft(context, "delete-this")

        val drafts = PersistenceManager.loadDrafts(context)
        assertEquals(1, drafts.size)
        assertEquals("keep-this", drafts[0].id)
    }

    // TEST F: CLEAR
    @Test
    fun testClearDrafts() {
        PersistenceManager.saveDraft(context, makeDraft(id = "a"))
        PersistenceManager.saveDraft(context, makeDraft(id = "b"))

        PersistenceManager.clearDrafts(context)

        val drafts = PersistenceManager.loadDrafts(context)
        assertTrue(drafts.isEmpty())
    }

    // TEST G: Multiple recipients survive save/load
    @Test
    fun testMultipleRecipientsSurviveRoundTrip() {
        val draft = LetterDraft(
            id = "multi-recipient",
            recipients = listOf(
                Recipient(id = "r1", name = "Bro. Juan", position = "Minister", organization = "INC"),
                Recipient(id = "r2", name = "Bro. Pedro", position = "Deacon", organization = "INC"),
                Recipient(id = "r3", name = "Bro. Jose", position = "Secretary", organization = "INC")
            ),
            dates = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")),
            sender = SenderProfile(name = "Sender"),
            body = "Hello"
        )
        PersistenceManager.saveDraft(context, draft)

        val loaded = PersistenceManager.getDraft(context, "multi-recipient")
        assertNotNull(loaded)
        assertEquals(3, loaded!!.recipients.size)
        assertEquals("Bro. Juan", loaded.recipients[0].name)
        assertEquals("Bro. Pedro", loaded.recipients[1].name)
        assertEquals("Bro. Jose", loaded.recipients[2].name)
    }

    // TEST H: Multiple dates survive save/load
    @Test
    fun testMultipleDatesSurviveRoundTrip() {
        val draft = LetterDraft(
            id = "multi-date",
            recipients = listOf(Recipient(name = "Test")),
            dates = listOf(
                LetterDate(date = Date(1700000000000L), label = "January 1, 2026"),
                LetterDate(date = Date(1700100000000L), label = "January 2, 2026"),
                LetterDate(date = Date(1700200000000L), label = "January 3, 2026")
            ),
            sender = SenderProfile(name = "Sender"),
            body = "Hello"
        )
        PersistenceManager.saveDraft(context, draft)

        val loaded = PersistenceManager.getDraft(context, "multi-date")
        assertNotNull(loaded)
        assertEquals(3, loaded!!.dates.size)
        assertEquals("January 1, 2026", loaded.dates[0].label)
        assertEquals("January 2, 2026", loaded.dates[1].label)
        assertEquals("January 3, 2026", loaded.dates[2].label)
    }

    // ══════════════════════════════════════════════════════════════════════
    // FIX11A REGRESSION: createDraft crash path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun testCreateDraftDoesNotCrash() {
        val draft = PersistenceManager.createDraft(context)
        assertNotNull("Draft must not be null", draft)
        assertTrue("Draft ID must not be blank", draft.id.isNotBlank())
    }

    @Test
    fun testCreateDraftPersistsToFile() {
        val draft = PersistenceManager.createDraft(context)

        val dataFile = java.io.File(context.filesDir, "sulat_data.json")
        assertTrue("Data file must exist after createDraft", dataFile.exists())
        assertTrue("Data file must be non-empty after createDraft", dataFile.length() > 0)

        val json = dataFile.readText(Charsets.UTF_8)
        assertTrue("File must contain valid JSON", json.contains("\"drafts\""))
        assertTrue("File must contain the draft ID", json.contains(draft.id))
    }

    @Test
    fun testCreateDraftRetrievableByGetDraft() {
        val draft = PersistenceManager.createDraft(context)

        val found = PersistenceManager.getDraft(context, draft.id)
        assertNotNull("getDraft must find the created draft", found)
        assertEquals("Draft ID must match", draft.id, found!!.id)
        assertEquals("Draft createdTime must match", draft.createdTime, found.createdTime)
    }

    @Test
    fun testCreateDraftRetrievableByLoadDrafts() {
        val draft = PersistenceManager.createDraft(context)

        val all = PersistenceManager.loadDrafts(context)
        assertTrue("loadDrafts must contain the created draft", all.any { it.id == draft.id })
    }

    @Test
    fun testCreateDraftThreeTimesAllPersist() {
        val d1 = PersistenceManager.createDraft(context)
        val d2 = PersistenceManager.createDraft(context)
        val d3 = PersistenceManager.createDraft(context)

        assertNotEquals("d1 and d2 must have different IDs", d1.id, d2.id)
        assertNotEquals("d2 and d3 must have different IDs", d2.id, d3.id)
        assertNotEquals("d1 and d3 must have different IDs", d1.id, d3.id)

        val all = PersistenceManager.loadDrafts(context)
        assertEquals("All 3 drafts must persist", 3, all.size)
        assertTrue("d1 must be present", all.any { it.id == d1.id })
        assertTrue("d2 must be present", all.any { it.id == d2.id })
        assertTrue("d3 must be present", all.any { it.id == d3.id })

        val f1 = PersistenceManager.getDraft(context, d1.id)
        val f2 = PersistenceManager.getDraft(context, d2.id)
        val f3 = PersistenceManager.getDraft(context, d3.id)
        assertNotNull("d1 retrievable", f1)
        assertNotNull("d2 retrievable", f2)
        assertNotNull("d3 retrievable", f3)
    }

    @Test
    fun testCreateDraftAfterClearDrafts() {
        PersistenceManager.createDraft(context)
        PersistenceManager.createDraft(context)
        PersistenceManager.clearDrafts(context)

        val afterClear = PersistenceManager.loadDrafts(context)
        assertTrue("Store must be empty after clear", afterClear.isEmpty())

        val draft = PersistenceManager.createDraft(context)
        assertNotNull("createDraft after clear must succeed", draft)
        assertTrue("New draft ID must not be blank", draft.id.isNotBlank())

        val found = PersistenceManager.getDraft(context, draft.id)
        assertNotNull("New draft must be retrievable", found)
    }
}
