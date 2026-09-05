package com.sulat.ai.document.envelope

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.document.layout.RecipientNameHierarchy

/**
 * Domain model for envelope label content.
 * Wraps a single [Recipient] with parsed name hierarchy.
 * Each EnvelopeData produces one envelope page.
 */
data class EnvelopeData(
    val recipient: Recipient,
    val nameHierarchy: RecipientNameHierarchy
) {
    companion object {
        /**
         * Known prefixes/acronyms used in recipient names.
         * Matched case-insensitively at the start of the name.
         */
        private val KNOWN_PREFIXES = listOf(
            "KA.", "KAB.", "BRO.", "BROTHER",
            "SIS.", "SISTER", "MIN.", "MINISTER",
            "MTR.", "PASTOR", "PRS.", "PRESIDENT",
            "SEC.", "SECRETARY", "TREAS.", "TREASURER",
            "CLK.", "CLERK", "DIR.", "DIRECTOR"
        )

        /**
         * Parse a recipient name into a hierarchy of prefix and main name.
         * Examples:
         *   "KA. JUAN DELA CRUZ" → prefix="KA.", mainName="JUAN DELA CRUZ"
         *   "BRO. PEDRO SANTOS" → prefix="BRO.", mainName="PEDRO SANTOS"
         *   "JUAN DELA CRUZ" → prefix="", mainName="JUAN DELA CRUZ"
         */
        fun parseNameHierarchy(fullName: String): RecipientNameHierarchy {
            val trimmed = fullName.trim()
            if (trimmed.isEmpty()) return RecipientNameHierarchy(prefix = "", mainName = "")

            for (prefix in KNOWN_PREFIXES) {
                if (trimmed.uppercase().startsWith(prefix.uppercase())) {
                    val mainName = trimmed.removeRange(0, prefix.length).trim()
                    return RecipientNameHierarchy(prefix = prefix, mainName = mainName)
                }
            }

            return RecipientNameHierarchy(prefix = "", mainName = trimmed)
        }

        /**
         * Create an [EnvelopeData] from a [Recipient].
         * Returns null if the recipient has no usable name.
         */
        fun fromRecipient(recipient: Recipient): EnvelopeData? {
            val name = recipient.name.trim()
            if (name.isEmpty()) return null

            val hierarchy = parseNameHierarchy(name)
            return EnvelopeData(recipient = recipient, nameHierarchy = hierarchy)
        }

        /**
         * Create a list of [EnvelopeData] from a [LetterDraft]'s recipients.
         * Skips recipients with empty names.
         * Preserves recipient ordering.
         * Returns empty list if draft has zero recipients.
         */
        fun fromDraft(draft: LetterDraft): List<EnvelopeData> {
            return draft.recipients.mapNotNull { fromRecipient(it) }
        }
    }
}
