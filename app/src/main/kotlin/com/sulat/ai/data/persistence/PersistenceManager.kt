package com.sulat.ai.data.persistence

import android.content.Context
import android.util.Log
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.PersonalExperience
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.data.model.LetterDate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*

object PersistenceManager {
    private const val FILE_NAME = "sulat_data.json"
    private var cache: Map<String, Any>? = null

    fun getDataDir(context: Context): File {
        return context.getFilesDir()
    }

    fun saveDraft(context: Context, draft: LetterDraft) {
        val fos = FileOutputStream(getDataDir(context), true)
        val oos = ObjectOutputStream(fos)
        oos.writeObject(draft)
        oos.close()
        fos.close()
        Log.d("Persistence", "Saved draft: ${draft.id}")
    }

    fun loadDrafts(context: Context): List<LetterDraft> {
        val file = File(getDataDir(context), FILE_NAME)
        if (!file.exists()) return emptyList()
        try {
            val fis = FileInputStream(file)
            val ois = ObjectInputStream(fis)
            val result = ois.readObject() as List<LetterDraft>
            ois.close()
            fis.close()
            return result
        } catch (e: Exception) {
            Log.e("Persistence", "Error loading drafts: ${e.message}")
            return emptyList()
        }
    }

    fun saveExperience(context: Context, experience: PersonalExperience) {
        val file = File(getDataDir(context), "experience_${experience.id}.ser")
        val fos = FileOutputStream(file)
        val oos = ObjectOutputStream(fos)
        oos.writeObject(experience)
        oos.close()
        fos.close()
    }

    fun loadExperiences(context: Context): List<PersonalExperience> {
        val dir = getDataDir(context)
        var results = mutableListOf<PersonalExperience>()
        if (!dir.exists()) return results
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("experience_") && file.name.endsWith(".ser")) {
                try {
                    val fis = FileInputStream(file)
                    val ois = ObjectInputStream(fis)
                    val exp = ois.readObject() as PersonalExperience
                    results.add(exp)
                    ois.close()
                    fis.close()
                } catch (e: Exception) {
                    Log.e("Persistence", "Error loading experience: ${e.message}")
                }
            }
        }
        return results
    }

    fun deleteExperience(context: Context, experienceId: String) {
        val file = File(getDataDir(context), "experience_$experienceId.ser")
        file.delete()
    }
}