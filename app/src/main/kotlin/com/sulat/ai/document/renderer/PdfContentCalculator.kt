package com.sulat.ai.document.renderer

import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.layout.LayoutSection
import com.sulat.ai.document.layout.Paragraph
import com.sulat.ai.document.layout.RecipientEntry
import kotlin.math.ceil

data class RenderLine(
    val text: String,
    val role: PdfTextRole,
    val yPt: Double
)

data class RenderPage(
    val pageNumber: Int,
    val lines: List<RenderLine>
)

data class RenderPlan(
    val pages: List<RenderPage>,
    val totalPages: Int
)

class PdfContentCalculator(
    private val layout: DocumentLayout
) {
    private val page get() = layout.page

    fun plan(): RenderPlan {
        val pages = mutableListOf<RenderPage>()
        var currentPageLines = mutableListOf<RenderLine>()
        var cursorY = page.marginTopPt
        var pageNumber = 1

        fun checkSpace(neededPt: Double): Boolean {
            return cursorY + neededPt <= page.heightPt - page.marginBottomPt
        }

        fun newPage() {
            if (currentPageLines.isNotEmpty()) {
                pages.add(RenderPage(pageNumber, currentPageLines.toList()))
            }
            pageNumber++
            currentPageLines = mutableListOf()
            cursorY = page.marginTopPt
        }

        fun addSpacer(spacerPt: Double = PdfTextRole.SPACER.style.fontSizePt) {
            if (!checkSpace(spacerPt)) newPage()
            cursorY += spacerPt
        }

        fun addLine(text: String, role: PdfTextRole) {
            val style = role.style
            val lineHeight = style.fontSizePt * style.lineSpacingMultiplier
            if (!checkSpace(lineHeight)) newPage()
            currentPageLines.add(RenderLine(text, role, cursorY))
            cursorY += lineHeight
        }

        fun addWrappedLines(text: String, role: PdfTextRole, maxWidthPt: Double) {
            val style = role.style
            val lineHeight = style.fontSizePt * style.lineSpacingMultiplier
            val charsPerLine = estimateCharsPerLine(maxWidthPt, style.fontSizePt)
            val wrapped = wrapText(text, charsPerLine)
            for (line in wrapped) {
                if (!checkSpace(lineHeight)) newPage()
                currentPageLines.add(RenderLine(line, role, cursorY))
                cursorY += lineHeight
            }
        }

        for (section in layout.sections) {
            when (section) {
                is LayoutSection.DateSection -> {
                    addSpacer(PdfTextRole.DATE.style.fontSizePt * 2)
                    addLine(section.label, PdfTextRole.DATE)
                    addSpacer()
                }

                is LayoutSection.RecipientBlock -> {
                    for (entry in section.entries) {
                        renderRecipient(entry, ::addLine, ::addSpacer, ::checkSpace, ::newPage)
                    }
                    addSpacer()
                }

                is LayoutSection.SubjectSection -> {
                    addLine("Re: ${section.text}", PdfTextRole.SUBJECT)
                    addSpacer()
                }

                is LayoutSection.GreetingSection -> {
                    addLine(section.text, PdfTextRole.GREETING)
                    addSpacer()
                }

                is LayoutSection.BodySection -> {
                    for ((idx, paragraph) in section.paragraphs.withIndex()) {
                        for (line in paragraph.lines) {
                            addWrappedLines(line, PdfTextRole.BODY, page.usableWidthPt)
                        }
                        if (idx < section.paragraphs.size - 1) {
                            addSpacer(PdfTextRole.BODY.style.fontSizePt * 1.5)
                        }
                    }
                    addSpacer()
                }

                is LayoutSection.ClosingSection -> {
                    renderClosing(section.sender, ::addLine, ::addSpacer, ::checkSpace, ::newPage)
                }
            }
        }

        if (currentPageLines.isNotEmpty()) {
            pages.add(RenderPage(pageNumber, currentPageLines.toList()))
        }

        return RenderPlan(
            pages = pages,
            totalPages = pages.size.coerceAtLeast(1)
        )
    }

    private fun renderRecipient(
        entry: RecipientEntry,
        addLine: (String, PdfTextRole) -> Unit,
        addSpacer: (Double) -> Unit,
        checkSpace: (Double) -> Boolean,
        newPage: () -> Unit
    ) {
        val r = entry.recipient
        val h = entry.nameHierarchy

        if (h.prefix.isNotEmpty()) {
            addLine(h.prefix, PdfTextRole.RECIPIENT_PREFIX)
        }
        if (h.mainName.isNotEmpty()) {
            addLine(h.mainName, PdfTextRole.RECIPIENT_NAME)
        }
        if (r.position.isNotEmpty()) {
            addLine(r.position, PdfTextRole.RECIPIENT_POSITION)
        }
        if (r.organization.isNotEmpty()) {
            addLine(r.organization, PdfTextRole.RECIPIENT_ORGANIZATION)
        }
        if (r.address.isNotEmpty()) {
            addLine(r.address, PdfTextRole.RECIPIENT_ADDRESS)
        }
        if (r.optionalInfo.isNotEmpty()) {
            addLine(r.optionalInfo, PdfTextRole.RECIPIENT_OPTIONAL)
        }
    }

    private fun renderClosing(
        sender: com.sulat.ai.data.model.SenderProfile,
        addLine: (String, PdfTextRole) -> Unit,
        addSpacer: (Double) -> Unit,
        checkSpace: (Double) -> Boolean,
        newPage: () -> Unit
    ) {
        addSpacer(PdfTextRole.CLOSING.style.fontSizePt * 2)
        if (sender.signature.isNotEmpty()) {
            addLine(sender.signature, PdfTextRole.CLOSING)
            addSpacer()
        }
        if (sender.name.isNotEmpty()) {
            addLine(sender.name, PdfTextRole.SENDER_NAME)
        }
        if (sender.address.isNotEmpty()) {
            addLine(sender.address, PdfTextRole.SENDER_ADDRESS)
        }
        if (sender.lokal.isNotEmpty()) {
            addLine(sender.lokal, PdfTextRole.SENDER_ORG)
        }
        if (sender.distrito.isNotEmpty()) {
            addLine(sender.distrito, PdfTextRole.SENDER_ORG)
        }
        if (sender.contactNumber.isNotEmpty()) {
            addLine("Contact: ${sender.contactNumber}", PdfTextRole.SENDER_CONTACT)
        }
    }

    private fun estimateCharsPerLine(availableWidthPt: Double, fontSizePt: Float): Int {
        val avgCharWidth = fontSizePt * 0.55
        return (availableWidthPt / avgCharWidth).toInt().coerceAtLeast(20)
    }

    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        if (text.length <= maxCharsPerLine) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= maxCharsPerLine) {
                currentLine.append(" ").append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }
}
