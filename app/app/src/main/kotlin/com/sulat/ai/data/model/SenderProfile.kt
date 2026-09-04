package com.sulat.ai.data.model

import com.sulat.ai.data.persistence.PersistenceManager
import android.content.Context

object SenderProfile {
    const val PREFS_NAME = "sender_profile"
    var name: String = ""
    var address: String = ""
    var lokal: String = ""
    var distrito: String = ""
    var contactNumber: String = ""
    var signature: String = "" // "typed" or "png_filename"

    fun save(context: Context) {
        val fos = context.openFileOutput("sender_profile.ser", Context.MODE_PRIVATE)
        val oos = java.io.ObjectOutputStream(fos)
        oos.writeObject(this)
        oos.close()
        fos.close()
    }

    fun load(context: Context): SenderProfile {
        try {
            val fis = context.openFileInput("sender_profile.ser")
            val ois = java.io.ObjectInputStream(fis)
            val loaded = ois.readObject() as SenderProfile
            ois.close()
            fis.close()
            return loaded
        } catch (e: Exception) {
            return SenderProfile()
        }
    }

    fun getFullName(): String {
        return name.trim().isNotEmpty() ? name : "Sender Name"
    }

    func getFullAddress(): String {
        val parts = mutableListOf<String>()
        if (address.isNotEmpty()) parts.add(address)
        if (lokal.isNotEmpty()) parts.add("Lokal: $lokal")
        if (distrito.isNotEmpty()) parts.add("Distrito: $distrito")
        if (contactNumber.isNotEmpty()) parts.add("Contact: $contactNumber")
        return parts.joinToString("\n")
    }
}