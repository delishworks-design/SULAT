package com.sulat.ai.data.template

import com.sulat.ai.data.model.Recipient

object RecipientTemplates {
    // Recipient Template 1: Executive Minister
    val executiveMinister = Recipient(
        name = "Bro. EDUARDO V. MANALO",
        position = "Executive Minister / Tagapamahalang Pangkalahatan",
        organization = "Iglesia Ni Cristo"
    )

    // Recipient Template 2: Deputy Executive Minister
    val deputyExecutiveMinister = Recipient(
        name = "Bro. ANGELO ERAÑO V. MANALO",
        position = "Deputy Executive Minister / Pangalawang Tagapamahalang Pangkalahatan",
        organization = "Iglesia Ni Cristo"
    )

    // Recipient Template 3: President – School For Ministers
    val presidentIglesia = Recipient(
        name = "Bro. ARNEL R. CANICOSA",
        position = "President – Iglesia Ni Cristo (Church of Christ)",
        organization = "School For Ministers"
    )

    // Get template by index (0=Executive Minister, 1=Deputy, 2=President)
    fun getTemplate(index: Int): Recipient {
        return when (index) {
            0 -> executiveMinister
            1 -> deputyExecutiveMinister
            2 -> presidentIglesia
            else -> executiveMinister
        }
    }

    // Get all available templates
    fun getAllTemplates(): List<Recipient> {
        return listOf(executiveMinister, deputyExecutiveMinister, presidentIglesia)
    }
}