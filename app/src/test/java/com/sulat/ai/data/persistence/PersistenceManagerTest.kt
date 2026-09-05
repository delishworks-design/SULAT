package com.sulat.ai.data.persistence

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PersistenceManagerTest {

    private fun makeDraft(id: String = "test-id-1"): LetterDraft {
        return LetterDraft(
            id = id,
            recipients = listOf(
                Recipient(id = "r1", name = "Bro. Juan", position = "Minister", organization = "INC"),
                Recipient(id = "r2", name = "Bro. Pedro", position = "Deacon", organization = "INC")
            ),
            dates = listOf(
                LetterDate(date = Date(1700000000000L), label = "January 1, 2026"),
                LetterDate(date = Date(1700100000000L), label = "January 2, 2026")
            ),
            sender = SenderProfile(
                name = "Sender Name",
                address = "123 Main St",
                lokal = "Locale A",
                distrito = "District 1",
                contactNumber = "09171234567",
                signature = "Faithfully"
            ),
            body = "This is the letter body.",
            subject = "Test Subject",
            greeting = "Dear Kapatid",
            createdTime = 1700000000000L,
            modifiedTime = 1700100000000L,
            isGenerated = false
        )
    }

    @Test
    fun testDraftToJsonAndBack() {
        val original = makeDraft()
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.body, restored.body)
        assertEquals(original.subject, restored.subject)
        assertEquals(original.greeting, restored.greeting)
        assertEquals(original.createdTime, restored.createdTime)
        assertEquals(original.modifiedTime, restored.modifiedTime)
        assertEquals(original.isGenerated, restored.isGenerated)
    }

    @Test
    fun testRecipientsSurviveRoundTrip() {
        val original = makeDraft()
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals(2, restored.recipients.size)
        assertEquals("Bro. Juan", restored.recipients[0].name)
        assertEquals("Minister", restored.recipients[0].position)
        assertEquals("INC", restored.recipients[0].organization)
        assertEquals("r1", restored.recipients[0].id)
        assertEquals("Bro. Pedro", restored.recipients[1].name)
        assertEquals("r2", restored.recipients[1].id)
    }

    @Test
    fun testDatesSurviveRoundTrip() {
        val original = makeDraft()
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals(2, restored.dates.size)
        assertEquals("January 1, 2026", restored.dates[0].label)
        assertEquals("January 2, 2026", restored.dates[1].label)
    }

    @Test
    fun testSenderSurvivesRoundTrip() {
        val original = makeDraft()
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals("Sender Name", restored.sender.name)
        assertEquals("123 Main St", restored.sender.address)
        assertEquals("Locale A", restored.sender.lokal)
        assertEquals("District 1", restored.sender.distrito)
        assertEquals("09171234567", restored.sender.contactNumber)
        assertEquals("Faithfully", restored.sender.signature)
    }

    @Test
    fun testEmptyDraftRoundTrip() {
        val original = LetterDraft()
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals("", restored.body)
        assertEquals("", restored.subject)
        assertEquals(0, restored.recipients.size)
        assertEquals(0, restored.dates.size)
    }

    @Test
    fun testMalformedJsonDoesNotCrash() {
        val malformed = JSONObject()
        malformed.put("id", "bad")
        malformed.put("recipients", "not_an_array")
        malformed.put("dates", "not_an_array")
        malformed.put("sender", "not_an_object")

        val result = PersistenceManager.jsonToDraft(malformed)
        assertEquals("bad", result.id)
        assertEquals(0, result.recipients.size)
        assertEquals(0, result.dates.size)
    }

    @Test
    fun testDraftWithEmptyRecipientsList() {
        val original = LetterDraft(
            id = "empty-recipients",
            recipients = emptyList(),
            dates = listOf(LetterDate(date = Date(1700000000000L), label = "Jan 1")),
            body = "Hello"
        )
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals("empty-recipients", restored.id)
        assertEquals(0, restored.recipients.size)
        assertEquals(1, restored.dates.size)
        assertEquals("Hello", restored.body)
    }

    @Test
    fun testDraftWithEmptyDatesList() {
        val original = LetterDraft(
            id = "empty-dates",
            recipients = listOf(Recipient(name = "Test")),
            dates = emptyList(),
            body = "Hello"
        )
        val json = PersistenceManager.draftToJson(original)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals(1, restored.recipients.size)
        assertEquals(0, restored.dates.size)
    }

    @Test
    fun testCompleteJsonFormat() {
        val draft = makeDraft()
        val json = PersistenceManager.draftToJson(draft)

        assertTrue(json.has("id"))
        assertTrue(json.has("recipients"))
        assertTrue(json.has("dates"))
        assertTrue(json.has("sender"))
        assertTrue(json.has("body"))
        assertTrue(json.has("subject"))
        assertTrue(json.has("greeting"))
        assertTrue(json.has("createdTime"))
        assertTrue(json.has("modifiedTime"))
        assertTrue(json.has("isGenerated"))
    }

    @Test
    fun testDraftIdStableAcrossSerialization() {
        val draft = makeDraft(id = "stable-id-42")
        val json = PersistenceManager.draftToJson(draft)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals("stable-id-42", restored.id)
    }

    @Test
    fun testEmptyDraftIdGeneratesUuid() {
        val json = JSONObject()
        json.put("id", "")
        json.put("body", "test")
        json.put("recipients", org.json.JSONArray())
        json.put("dates", org.json.JSONArray())
        json.put("sender", JSONObject())

        val restored = PersistenceManager.jsonToDraft(json)
        assertNotNull("Generated ID should not be null", restored.id)
        assertTrue("Generated ID should not be blank", restored.id.isNotBlank())
    }

    @Test
    fun testMissingDraftIdGeneratesUuid() {
        val json = JSONObject()
        json.put("body", "test")
        json.put("recipients", org.json.JSONArray())
        json.put("dates", org.json.JSONArray())
        json.put("sender", JSONObject())

        val restored = PersistenceManager.jsonToDraft(json)
        assertNotNull("Generated ID should not be null", restored.id)
        assertTrue("Generated ID should not be blank", restored.id.isNotBlank())
    }

    @Test
    fun testBlankRecipientIdGeneratesUuid() {
        val json = JSONObject()
        json.put("id", "draft-ok")
        json.put("body", "test")
        val recipientsArray = org.json.JSONArray()
        val rObj = JSONObject()
        rObj.put("id", "   ")
        rObj.put("name", "Test")
        recipientsArray.put(rObj)
        json.put("recipients", recipientsArray)
        json.put("dates", org.json.JSONArray())
        json.put("sender", JSONObject())

        val restored = PersistenceManager.jsonToDraft(json)
        assertEquals(1, restored.recipients.size)
        assertNotEquals("Recipient ID should be generated, not blank", "   ", restored.recipients[0].id)
        assertTrue("Recipient ID should be non-blank", restored.recipients[0].id.isNotBlank())
    }

    @Test
    fun testValidIdsPreservedExactly() {
        val draft = makeDraft(id = "my-custom-id")
        val json = PersistenceManager.draftToJson(draft)
        val restored = PersistenceManager.jsonToDraft(json)

        assertEquals("my-custom-id", restored.id)
        assertEquals("r1", restored.recipients[0].id)
        assertEquals("r2", restored.recipients[1].id)
    }
}
