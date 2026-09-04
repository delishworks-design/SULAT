package com.sulat.ai.document.renderer

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import java.io.File

class EnvelopeLabelRenderer {

    // Render individual envelope label
    fun renderEnvelopeLabel(
        sender: SenderProfile,
        recipient: Recipient,
        date: java.util.Date,
        paperSize: PaperSize
    ): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(date)

        val sb = StringBuilder()

        // Sender block with proper hierarchy
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

        // Recipient block - visually stronger
        sb.append("\n")
        sb.append("Bro. ${recipient.name}\n")
        sb.append("${recipient.position}\n")
        sb.append("${recipient.organization}\n")
        sb.append("Date: ${formattedDate}\n")

        return sb.toString()
    }

    // Render batch of envelope labels
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

    // Validate envelope label format
    fun validateEnvelopeFormat(text: String): Boolean {
        // Check that envelope has required sections
        return text.contains("Bro.") && 
               text.contains("Position") && 
               text.contains("Organization")
    }
}