package com.sulat.ai.data.persistence

import android.content.Context
import android.util.Log
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.share.PdfArtifactManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

object PersistenceManager {
    private const val TAG = "PersistenceManager"
    private const val FILE_NAME = "sulat_data.json"
    private const val TEMP_FILE_NAME = "sulat_data.json.tmp"

    private fun getDataFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    private fun getTempFile(context: Context): File {
        return File(context.filesDir, TEMP_FILE_NAME)
    }

    // ── Create new draft ──────────────────────────────────────────────────

    fun createDraft(context: Context): LetterDraft {
        val draft = LetterDraft(
            createdTime = System.currentTimeMillis(),
            modifiedTime = System.currentTimeMillis()
        )
        saveDraft(context, draft)
        return draft
    }

    // ── Save (create or update) ──────────────────────────────────────────

    fun saveDraft(context: Context, draft: LetterDraft) {
        val drafts = loadDrafts(context).toMutableList()
        val index = drafts.indexOfFirst { it.id == draft.id }
        if (index >= 0) {
            drafts[index] = draft
        } else {
            drafts.add(draft)
        }
        writeDrafts(context, drafts)
        invalidateCachedArtifacts(context, draft)
    }

    // ── Load all ──────────────────────────────────────────────────────────

    fun loadDrafts(context: Context): List<LetterDraft> {
        val file = getDataFile(context)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) return emptyList()
            parseDrafts(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load drafts from ${file.absolutePath}: ${e.message}", e)
            emptyList()
        }
    }

    // ── Get single ────────────────────────────────────────────────────────

    fun getDraft(context: Context, id: String): LetterDraft? {
        return loadDrafts(context).find { it.id == id }
    }

    // ── Update ────────────────────────────────────────────────────────────

    fun updateDraft(context: Context, draft: LetterDraft) {
        saveDraft(context, draft)
    }

    // ── Delete ────────────────────────────────────────────────────────────

    fun deleteDraft(context: Context, id: String) {
        // Locate the draft before removing it so we can invalidate its cached
        // artifacts (PDF + fingerprint sidecar) for every paper size. This
        // prevents sensitive content from persisting in cacheDir/shared/ after
        // a user-initiated delete.
        val target = loadDrafts(context).firstOrNull { it.id == id }
        val drafts = loadDrafts(context).filter { it.id != id }
        writeDrafts(context, drafts)
        if (target != null) {
            invalidateCachedArtifacts(context, target)
        }
    }

    // ── Clear all ─────────────────────────────────────────────────────────

    fun clearDrafts(context: Context) {
        writeDrafts(context, emptyList())
    }

    // ── Cached artifact invalidation ──────────────────────────────────────

    /**
     * Invalidate cached PDF artifacts (and their fingerprint sidecars) for the
     * supplied draft across all paper sizes. Called from [saveDraft] and
     * [deleteDraft] so that any subsequent [PdfArtifactManager.ensurePdfArtifact]
     * is forced to regenerate. PersistenceManager does NOT generate PDFs — that
     * remains the responsibility of [PdfArtifactManager].
     */
    private fun invalidateCachedArtifacts(context: Context, draft: LetterDraft) {
        for (paperSize in PaperSize.entries) {
            try {
                PdfArtifactManager.deleteArtifact(context, draft, paperSize)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to invalidate cached artifact for draft=${draft.id} size=${paperSize.name}: ${e.message}")
            }
        }
    }

    // ── JSON write (safe replacement) ─────────────────────────────────────

    private fun writeDrafts(context: Context, drafts: List<LetterDraft>) {
        val root = JSONObject()
        val array = JSONArray()
        for (draft in drafts) {
            array.put(draftToJson(draft))
        }
        root.put("drafts", array)

        val tempFile = getTempFile(context)
        val dataFile = getDataFile(context)
        try {
            // Step 1: Write complete JSON to temp file atomically
            val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
            java.io.FileOutputStream(tempFile).use { output ->
                output.write(jsonBytes)
                output.flush()
            }

            // Step 2: Verify temp file is non-empty before replacing destination
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Temp file missing or empty after write to ${tempFile.absolutePath}")
            }

            // Step 3: Delete destination if it exists
            if (dataFile.exists()) {
                dataFile.delete()
            }

            // Step 4: Rename temp to destination
            val renamed = tempFile.renameTo(dataFile)

            if (!renamed) {
                // Fallback: copy content then delete temp
                Log.w(TAG, "renameTo failed, using copy fallback")
                tempFile.copyTo(dataFile, overwrite = true)
                tempFile.delete()
            }

            // Step 5: Verify destination exists and has content
            if (!dataFile.exists() || dataFile.length() == 0L) {
                Log.e(TAG, "Data file missing or empty after write")
                throw IllegalStateException("Failed to persist data to ${dataFile.absolutePath}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write drafts: ${e.message}", e)
            tempFile.delete()
            throw e
        }
    }

    // ── JSON serialization ────────────────────────────────────────────────

    internal fun draftToJson(draft: LetterDraft): JSONObject {
        val json = JSONObject()
        json.put("id", draft.id)
        json.put("body", draft.body)
        json.put("subject", draft.subject)
        json.put("greeting", draft.greeting)
        json.put("createdTime", draft.createdTime)
        json.put("modifiedTime", draft.modifiedTime)
        json.put("isGenerated", draft.isGenerated)

        val recipientsArray = JSONArray()
        for (r in draft.recipients) {
            val rj = JSONObject()
            rj.put("id", r.id)
            rj.put("name", r.name)
            rj.put("position", r.position)
            rj.put("organization", r.organization)
            rj.put("address", r.address)
            rj.put("optionalInfo", r.optionalInfo)
            recipientsArray.put(rj)
        }
        json.put("recipients", recipientsArray)

        val datesArray = JSONArray()
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        for (d in draft.dates) {
            val dj = JSONObject()
            dj.put("date", fmt.format(d.date))
            dj.put("label", d.label)
            datesArray.put(dj)
        }
        json.put("dates", datesArray)

        val sj = JSONObject()
        sj.put("name", draft.sender.name)
        sj.put("address", draft.sender.address)
        sj.put("lokal", draft.sender.lokal)
        sj.put("distrito", draft.sender.distrito)
        sj.put("contactNumber", draft.sender.contactNumber)
        sj.put("signature", draft.sender.signature)
        json.put("sender", sj)

        return json
    }

    // ── JSON deserialization ───────────────────────────────────────────────

    private fun parseDrafts(json: String): List<LetterDraft> {
        val root = JSONObject(json)
        val array = root.optJSONArray("drafts") ?: return emptyList()
        val drafts = mutableListOf<LetterDraft>()
        for (i in 0 until array.length()) {
            try {
                drafts.add(jsonToDraft(array.getJSONObject(i)))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed draft at index $i: ${e.message}")
            }
        }
        return drafts
    }

    internal fun jsonToDraft(json: JSONObject): LetterDraft {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        val draftId = json.optString("id", "")
        val safeDraftId = if (draftId.isNotBlank()) draftId else UUID.randomUUID().toString()

        val recipientsArray = json.optJSONArray("recipients") ?: JSONArray()
        val recipients = (0 until recipientsArray.length()).map { i ->
            val r = recipientsArray.getJSONObject(i)
            val rid = r.optString("id", "")
            val safeRid = if (rid.isNotBlank()) rid else UUID.randomUUID().toString()
            com.sulat.ai.data.model.Recipient(
                id = safeRid,
                name = r.optString("name", ""),
                position = r.optString("position", ""),
                organization = r.optString("organization", ""),
                address = r.optString("address", ""),
                optionalInfo = r.optString("optionalInfo", "")
            )
        }

        val datesArray = json.optJSONArray("dates") ?: JSONArray()
        val dates = (0 until datesArray.length()).mapNotNull { i ->
            try {
                val d = datesArray.getJSONObject(i)
                val dateStr = d.optString("date", "")
                val date = if (dateStr.isNotEmpty()) fmt.parse(dateStr) else null
                if (date != null) LetterDate(date = date, label = d.optString("label", "")) else null
            } catch (e: Exception) {
                null
            }
        }

        val senderJson = json.optJSONObject("sender") ?: JSONObject()
        val sender = SenderProfile(
            name = senderJson.optString("name", ""),
            address = senderJson.optString("address", ""),
            lokal = senderJson.optString("lokal", ""),
            distrito = senderJson.optString("distrito", ""),
            contactNumber = senderJson.optString("contactNumber", ""),
            signature = senderJson.optString("signature", "")
        )

        return LetterDraft(
            id = safeDraftId,
            recipients = recipients,
            dates = dates,
            sender = sender,
            body = json.optString("body", ""),
            subject = json.optString("subject", ""),
            greeting = json.optString("greeting", ""),
            createdTime = json.optLong("createdTime", 0L),
            modifiedTime = json.optLong("modifiedTime", 0L),
            isGenerated = json.optBoolean("isGenerated", false)
        )
    }
}
