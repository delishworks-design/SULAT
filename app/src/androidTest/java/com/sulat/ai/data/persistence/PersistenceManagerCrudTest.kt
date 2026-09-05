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
}
