package com.sulat.ai.document.envelope

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/**
 * Safe filename generation for envelope PDFs.
 * Reuses the sanitization principles from ShareHelper.
 */
object EnvelopeFilename {

    private const val MAX_FILENAME_LENGTH = 100
    private const val DANGEROUS_CHARS_REGEX = "[/\\\\:*?\"<>|.]"
    private const val CONTROL_CHARS_REGEX = "[\\x00-\\x1f]"

    /**
     * Generate a safe filename for an envelope PDF.
     * Format: "Sulat-Envelope-{RECIPIENT_NAME}-{DATE}.pdf"
     *
     * @param recipientName The primary recipient's full name.
     * @param date The date for the filename (defaults to now).
     * @return A safe filename ending in ".pdf".
     */
    fun generate(
        recipientName: String,
        date: Long = System.currentTimeMillis()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(Date(date))

        val baseName = sanitizeNamePart(recipientName)
        val namePart = if (baseName.isNotEmpty()) baseName else "Recipient"

        val full = "Sulat-Envelope-$namePart-$dateStr"
        val truncated = if (full.length > MAX_FILENAME_LENGTH) {
            full.substring(0, MAX_FILENAME_LENGTH)
        } else {
            full
        }

        return "$truncated.pdf"
    }

    /**
     * Sanitize a name component for use in a filename.
     * Removes dangerous characters, control characters, and collapses whitespace.
     */
    private fun sanitizeNamePart(name: String): String {
        return name
            .replace(Regex(DANGEROUS_CHARS_REGEX), "")
            .replace(Regex(CONTROL_CHARS_REGEX), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
