package com.sulat.ai.document.renderer

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import java.io.File
import java.text.SimpleDateFormat

class EnvelopeLabelRenderer {

    fun renderEnvelopeLabel(
        sender: SenderProfile,
        recipient: Recipient,
        date: java.util.Date,
        paperSize: PaperSize
    ): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(date)

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
            sb.append("Contact: ${sender.contactNumber}\n")
        }

        sb.append("\n")
        sb.append("Bro. ${recipient.name}\n")
        sb.append("${recipient.position}\n")
        sb.append("${recipient.organization}\n")
        sb.append("Date: ${formattedDate}\n")

        return sb.toString()
    }

    fun renderBatchEnvelopeLabels(
        senders: List<SenderProfile>,
        recipients: List<Recipient>,
        dates: List<java.util.Date>
    ): List<String> {
        val results = mutableListOf<String>()
        senders.forEach { sender ->
            recipients.forEach { recipient ->
                dates.forEach { date ->
                    results.add(renderEnvelopeLabel(sender, recipient, date, PaperSize.A4))
                }
            }
        }
        return results
    }

    fun validateEnvelopeFormat(text: String): Boolean {
        return text.contains("Bro.") &&
               text.contains("Position") &&
               text.contains("Organization")
    }
}
