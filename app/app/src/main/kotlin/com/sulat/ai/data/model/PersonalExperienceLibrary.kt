package com.sulat.ai.data.model

import com.sulat.ai.data.persistence.PersistenceManager
import android.content.Context

object PersonalExperienceLibrary {
    const var EXPERIENCES_FILE = "personal_experiences.ser"

    data class ExperienceEntry(
        val id: String,
        val happened: String,
        val whatDidYouDo: String,
        val whatDidYouLearn: String,
        val whatChanged: String,
        val whatYouWantToExpress: String,
        val createdTime: Long = System.currentTimeMillis()
    )

    fun addExperience(context: Context, experience: ExperienceEntry) {
        // Load existing
        val existing = loadAll(context)
        // Add new (with new ID)
        val newId = "exp_${System.currentTimeMillis()}"
        val withId = experience.copy(id = newId)
        existing.add(withId)
        // Save
        saveAll(context, existing)
    }

    fun editExperience(context: Context, experienceId: String, updated: PersonalExperience.ExperienceEntry) {
        val existing = loadAll(context)
        val index = existing.indexWhere { it.id == experienceId }
        if (index >= 0) {
            val updatedWithId = updated.copy(id = experienceId)
            existing[index] = updatedWithId
            saveAll(context, existing)
        }
    }

    fun deleteExperience(context: Context, experienceId: String) {
        PersistenceManager.deleteExperience(context, experienceId)
        // Also remove from in-memory
        loadAll(context).removeAll { it.id == experienceId }
        saveAll(context, loadAll(context))
    }

    fun searchExperiences(context: Context, query: String): List<ExperienceEntry> {
        val all = loadAll(context)
        val lowerQuery = query.lowercase()
        return all.filter { it.happened.lowercase().contains(lowerQuery) ||
            it.whatDidYouDo.lowercase().contains(lowerQuery) ||
            it.whatDidYouLearn.lowercase().contains(lowerQuery) ||
            it.whatChanged.lowercase().contains(lowerQuery) ||
            it.whatYouWantToExpress.lowercase().contains(lowerQuery) }
    }

    fun getRelevantExperiences(context: Context, topicKeywords: List<String>): List<ExperienceEntry> {
        val all = loadAll(context)
        return all.filter { experience ->
            topicKeywords.any { keyword ->
                experience.happened.lowercase().contains(keyword.lowercase()) ||
                experience.whatDidYouDo.lowercase().contains(keyword.lowercase()) ||
                experience.whatDidYouLearn.lowercase().contains(keyword.lowercase()) ||
                experience.whatChanged.lowercase().contains(keyword.lowercase()) ||
                experience.whatYouWantToExpress.lowercase().contains(keyword.lowercase())
            }
        }
    }

    private fun loadAll(context: Context): List<ExperienceEntry> {
        try {
            val file = context.getFileStreamPath(EXPERIENCES_FILE)
            if (!file.exists()) return emptyList()
            val fis = FileInputStream(file)
            val ois = ObjectInputStream(fis)
            val result = ois.readObject() as List<ExperienceEntry>
            ois.close()
            fis.close()
            return result
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun saveAll(context: Context, experiences: List<ExperienceEntry>) {
        val fos = context.openFileOutput(EXPERIENCES_FILE, Context.MODE_PRIVATE)
        val oos = ObjectOutputStream(fos)
        oos.writeObject(experiences)
        oos.close()
        fos.close()
    }
}