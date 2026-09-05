package com.sulat.ai.document.renderer

import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.layout.LayoutSection
import com.sulat.ai.document.layout.Paragraph
import com.sulat.ai.document.layout.RecipientEntry

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
    private val layout: DocumentLayout,
    private val textMeasurer: TextMeasurer = DeterministicTextMeasurer()
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
            val wrapped = wrapTextWidthAware(text, role, maxWidthPt)
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
            addSpacer(PdfTextRole.CLOSING.style.fontSizePt)
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

    /**
     * Whitespace-aware token: represents either a word or a run of spaces.
     * Preserves consecutive spaces exactly as they appear in the source text.
     */
    private data class Token(val text: String, val isSpace: Boolean)

    /**
     * Tokenize [text] into words and space runs, preserving consecutive spaces.
     * "Hello  world" → [Token("Hello", false), Token("  ", true), Token("world", false)]
     */
    private fun tokenizePreservingSpaces(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            if (text[i] == ' ') {
                val start = i
                while (i < text.length && text[i] == ' ') i++
                tokens.add(Token(text.substring(start, i), true))
            } else {
                val start = i
                while (i < text.length && text[i] != ' ') i++
                tokens.add(Token(text.substring(start, i), false))
            }
        }
        return tokens
    }

    /**
     * Width-aware text wrapping that preserves consecutive spaces.
     *
     * Deterministic whitespace policy:
     * - Source characters are never deleted during wrapping.
     * - Space tokens handle all inter-word spacing.
     * - When a space token fits on the current line, it is appended.
     * - When a space token does not fit, the current line is flushed
     *   and the space becomes leading content on the next line.
     * - Leading spaces at the start of the text are preserved on the first line.
     * - The invariant: lines.joinToString("") produces a string containing
     *   all source characters in their original order.
     *
     * Falls back to character-level splitting for tokens wider than [maxWidthPt].
     */
    private fun wrapTextWidthAware(text: String, role: PdfTextRole, maxWidthPt: Double): List<String> {
        if (text.isEmpty()) return listOf(text)

        val style = role.style
        val totalWidth = textMeasurer.measureTextWidth(text, style)
        if (totalWidth <= maxWidthPt) return listOf(text)

        val tokens = tokenizePreservingSpaces(text)
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        fun currentLineWidth(): Double {
            if (currentLine.isEmpty()) return 0.0
            return textMeasurer.measureTextWidth(currentLine.toString(), style)
        }

        fun flushLine() {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
            }
        }

        for (token in tokens) {
            if (token.isSpace) {
                val spaceWidth = textMeasurer.measureTextWidth(token.text, style)
                if (spaceWidth > maxWidthPt) {
                    flushLine()
                    val splitChunks = splitLongTokenUnicodeSafe(token.text, role, maxWidthPt)
                    for (chunk in splitChunks) {
                        lines.add(chunk)
                    }
                } else if (currentLineWidth() + spaceWidth <= maxWidthPt) {
                    currentLine.append(token.text)
                } else {
                    flushLine()
                    currentLine.append(token.text)
                }
            } else {
                val tokenWidth = textMeasurer.measureTextWidth(token.text, style)
                if (tokenWidth > maxWidthPt) {
                    flushLine()
                    val splitChunks = splitLongTokenUnicodeSafe(token.text, role, maxWidthPt)
                    for (chunk in splitChunks) {
                        lines.add(chunk)
                    }
                } else {
                    val candidate = if (currentLine.isEmpty()) {
                        token.text
                    } else {
                        currentLine.toString() + token.text
                    }
                    val candidateWidth = textMeasurer.measureTextWidth(candidate, style)
                    if (candidateWidth <= maxWidthPt) {
                        currentLine.append(token.text)
                    } else {
                        flushLine()
                        currentLine.append(token.text)
                    }
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    /**
     * Unicode-safe splitting of a long unbreakable token.
     * Uses code-point iteration to avoid splitting UTF-16 surrogate pairs.
     * All original characters are preserved in order.
     */
    private fun splitLongTokenUnicodeSafe(token: String, role: PdfTextRole, maxWidthPt: Double): List<String> {
        if (token.isEmpty()) return emptyList()
        val style = role.style
        val maxChars = textMeasurer.estimateMaxCharsForWidth(token, role, maxWidthPt)
        if (maxChars >= token.length) return listOf(token)

        val codePoints = mutableListOf<Int>()
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            codePoints.add(cp)
            i += Character.charCount(cp)
        }

        val result = mutableListOf<String>()
        var start = 0
        while (start < codePoints.size) {
            var end = (start + maxChars).coerceAtMost(codePoints.size)
            while (end > start + 1) {
                val chunkCodePoints = codePoints.subList(start, end)
                val chunk = buildString {
                    for (cp in chunkCodePoints) {
                        appendCodePoint(cp)
                    }
                }
                val chunkWidth = textMeasurer.measureTextWidth(chunk, style)
                if (chunkWidth <= maxWidthPt) break
                end--
            }
            if (start < codePoints.size) {
                val chunkCodePoints = codePoints.subList(start, end)
                val chunk = buildString {
                    for (cp in chunkCodePoints) {
                        appendCodePoint(cp)
                    }
                }
                result.add(chunk)
                start = end
            }
        }
        return result
    }
}
