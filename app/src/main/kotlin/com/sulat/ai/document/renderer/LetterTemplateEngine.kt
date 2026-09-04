package com.sulat.ai.document.renderer

import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LetterTemplateEngine {

    fun generateLetterContent(
        draft: LetterDraft,
        recipient: Recipient,
        date: LetterDate,
        paperSize: PaperSize
    ): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(date.date)

        val sb = StringBuilder()

        sb.append("Kapatid na ${recipient.name}\n")
        sb.append("${recipient.position}\n")
        sb.append("${recipient.organization}\n\n")

        sb.append("$formattedDate\n\n")

        sb.append("Re: Liham po ukol sa paghingi ng kapatawaran at pagkakataon na makabalik sa banal na ministerio.\n\n")

        sb.append("Pinakamamahal na Kapatid,\n\n")

        sb.append("${draft.body}\n\n")

        sb.append("\n")

        return sb.toString()
    }

    fun generateEnvelopeContent(
        sender: SenderProfile,
        recipient: Recipient,
        date: LetterDate
    ): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(date.date)

        val sb = StringBuilder()

        sb.append("${sender.name}\n")
        if (sender.address.isNotEmpty()) {
            sb.append("${sender.address}\n")
        }
        if (sender.lokal.isNotEmpty()) {
            sb.append("Lokal\n")
        }
        if (sender.distrito.isNotEmpty()) {
            sb.append("Distrito\n")
        }
        if (sender.contactNumber.isNotEmpty()) {
            sb.append("Contact Number: ${sender.contactNumber}\n")
        }
        sb.append("\n")

        sb.append("Bro. ${recipient.name}\n")
        sb.append("${recipient.position}\n")
        sb.append("${recipient.organization}\n")

        return sb.toString()
    }

    fun applyTypography(text: String): String {
        return text
    }
}
