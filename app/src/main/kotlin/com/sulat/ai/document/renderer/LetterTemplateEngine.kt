package com.sulat.ai.document.renderer

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.data.template.DateSystem
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.layout.LayoutSection
import com.sulat.ai.document.layout.LayoutValidation
import com.sulat.ai.document.layout.PageGeometry
import com.sulat.ai.document.layout.Paragraph
import com.sulat.ai.document.layout.RecipientEntry
import com.sulat.ai.document.layout.RecipientNameHierarchy
import java.time.format.DateTimeFormatter
import java.util.Locale

class LetterTemplateEngine {

    private val displayFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    fun buildLayout(draft: LetterDraft, paperSize: PaperSize): DocumentLayout {
        val margins = PaperSize.defaultMarginsPt()
        val page = PageGeometry(
            widthPt = paperSize.widthPt,
            heightPt = paperSize.heightPt,
            marginTopPt = margins.top,
            marginBottomPt = margins.bottom,
            marginLeftPt = margins.left,
            marginRightPt = margins.right
        )

        val sections = mutableListOf<LayoutSection>()

        // Date section
        if (draft.dates.isNotEmpty()) {
            val dateLabels = draft.dates.joinToString(", ") { date ->
                DateSystem.formatDisplay(DateSystem.dateToLocalDate(date.date))
            }
            sections.add(LayoutSection.DateSection(label = dateLabels))
        }

        // Recipient block
        val recipientEntries = draft.recipients.map { recipient ->
            RecipientEntry(
                recipient = recipient,
                nameHierarchy = parseRecipientName(recipient.name)
            )
        }
        sections.add(LayoutSection.RecipientBlock(entries = recipientEntries))

        // Subject section
        if (draft.subject.isNotEmpty()) {
            sections.add(LayoutSection.SubjectSection(text = draft.subject))
        }

        // Greeting section
        if (draft.greeting.isNotEmpty()) {
            sections.add(LayoutSection.GreetingSection(text = draft.greeting))
        }

        // Body section
        val paragraphs = parseBodyParagraphs(draft.body)
        sections.add(LayoutSection.BodySection(paragraphs = paragraphs))

        // Closing section
        if (draft.sender.name.isNotEmpty() || draft.sender.signature.isNotEmpty()) {
            sections.add(LayoutSection.ClosingSection(sender = draft.sender))
        }

        // Validation
        val validation = validate(draft)

        return DocumentLayout(
            page = page,
            sections = sections,
            validation = validation
        )
    }

    fun parseRecipientName(name: String): RecipientNameHierarchy {
        val trimmed = name.trim()
        val prefixPattern = Regex("^(KA\\.?|Bro\\.?|Sister\\.?|Sr\\.?|Sra\\.?)\\s+", RegexOption.IGNORE_CASE)
        val match = prefixPattern.find(trimmed)
        return if (match != null) {
            RecipientNameHierarchy(
                prefix = match.groupValues[1],
                mainName = trimmed.substring(match.range.last + 1).trim()
            )
        } else {
            RecipientNameHierarchy(prefix = "", mainName = trimmed)
        }
    }

    fun parseBodyParagraphs(body: String): List<Paragraph> {
        if (body.isBlank()) return emptyList()
        return body.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { block ->
            Paragraph(lines = block.lines().filter { it.isNotBlank() })
        }
    }

    fun validate(draft: LetterDraft): LayoutValidation {
        val errors = mutableListOf<String>()
        if (draft.recipients.isEmpty()) errors.add("No recipients specified")
        if (draft.body.isBlank()) errors.add("Body is empty")
        return LayoutValidation(
            hasRecipients = draft.recipients.isNotEmpty(),
            hasBody = draft.body.isNotBlank(),
            hasSubject = draft.subject.isNotEmpty(),
            hasGreeting = draft.greeting.isNotEmpty(),
            hasSender = draft.sender.name.isNotEmpty(),
            errors = errors
        )
    }

    fun formatLetterText(draft: LetterDraft, paperSize: PaperSize): String {
        val layout = buildLayout(draft, paperSize)
        val sb = StringBuilder()

        for (section in layout.sections) {
            when (section) {
                is LayoutSection.DateSection -> {
                    sb.appendLine(section.label)
                    sb.appendLine()
                }
                is LayoutSection.RecipientBlock -> {
                    for (entry in section.entries) {
                        val name = if (entry.nameHierarchy.prefix.isNotEmpty()) {
                            "${entry.nameHierarchy.prefix} ${entry.nameHierarchy.mainName}"
                        } else {
                            entry.nameHierarchy.mainName
                        }
                        sb.appendLine(name)
                        if (entry.recipient.position.isNotEmpty()) sb.appendLine(entry.recipient.position)
                        if (entry.recipient.organization.isNotEmpty()) sb.appendLine(entry.recipient.organization)
                        if (entry.recipient.address.isNotEmpty()) sb.appendLine(entry.recipient.address)
                        if (entry.recipient.optionalInfo.isNotEmpty()) sb.appendLine(entry.recipient.optionalInfo)
                        sb.appendLine()
                    }
                }
                is LayoutSection.SubjectSection -> {
                    sb.appendLine("Re: ${section.text}")
                    sb.appendLine()
                }
                is LayoutSection.GreetingSection -> {
                    sb.appendLine(section.text)
                    sb.appendLine()
                }
                is LayoutSection.BodySection -> {
                    for (paragraph in section.paragraphs) {
                        for (line in paragraph.lines) {
                            sb.appendLine(line)
                        }
                        sb.appendLine()
                    }
                }
                is LayoutSection.ClosingSection -> {
                    if (section.sender.signature.isNotEmpty()) {
                        sb.appendLine(section.sender.signature)
                    }
                    sb.appendLine()
                    if (section.sender.name.isNotEmpty()) sb.appendLine(section.sender.name)
                    if (section.sender.address.isNotEmpty()) sb.appendLine(section.sender.address)
                    if (section.sender.lokal.isNotEmpty()) sb.appendLine(section.sender.lokal)
                    if (section.sender.distrito.isNotEmpty()) sb.appendLine(section.sender.distrito)
                    if (section.sender.contactNumber.isNotEmpty()) sb.appendLine(section.sender.contactNumber)
                }
            }
        }

        return sb.toString().trimEnd()
    }
}
