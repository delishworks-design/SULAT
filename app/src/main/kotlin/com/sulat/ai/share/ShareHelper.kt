package com.sulat.ai.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.sulat.ai.data.model.LetterDraft
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ShareHelper {
    // Share PDF through Android's native Share Sheet
    fun sharePdf(context: Context, pdfFile: File, recipientName: String): Boolean {
        try {
            // Create content URI through FileProvider
            val contentUri = FileProvider.getUriForFile(
                context,
                "com.sulat.ai.fileprovider",
                pdfFile
            )

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setType("application/pdf")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Start chooser
            context.startActivity(Intent.createChooser(shareIntent, "Share letter via"))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Generate filename for letter PDF
    fun generateLetterFilename(recipientName: String, date: Date): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val safeName = recipientName.replace(" ", "").replace(".", "")
        return "Sulat_${safeName}_${dateFormat.format(date)}.pdf"
    }

    // Generate filename for envelope label PDF
    fun generateEnvelopeFilename(recipientName: String, date: Date): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val safeName = recipientName.replace(" ", "").replace(".", "")
        return "Envelope_${safeName}_${dateFormat.format(date)}.pdf"
    }

    // Generate batch filenames
    fun generateBatchFilename(prefix: String, date: Date): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return "$${prefix}_${dateFormat.format(date)}.pdf"
    }
}