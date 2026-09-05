package com.sulat.ai.document.layout

import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize

data class DocumentLayout(
    val page: PageGeometry,
    val sections: List<LayoutSection>,
    val validation: LayoutValidation
)

data class PageGeometry(
    val widthPt: Double,
    val heightPt: Double,
    val marginTopPt: Double,
    val marginBottomPt: Double,
    val marginLeftPt: Double,
    val marginRightPt: Double
) {
    val usableWidthPt: Double get() = widthPt - marginLeftPt - marginRightPt
    val usableHeightPt: Double get() = heightPt - marginTopPt - marginBottomPt
}

sealed class LayoutSection {
    data class DateSection(val label: String) : LayoutSection()

    data class RecipientBlock(val entries: List<RecipientEntry>) : LayoutSection()

    data class SubjectSection(val text: String) : LayoutSection()

    data class GreetingSection(val text: String) : LayoutSection()

    data class BodySection(val paragraphs: List<Paragraph>) : LayoutSection()

    data class ClosingSection(val sender: SenderProfile) : LayoutSection()
}

data class RecipientEntry(
    val recipient: Recipient,
    val nameHierarchy: RecipientNameHierarchy
)

data class RecipientNameHierarchy(
    val prefix: String,
    val mainName: String
)

data class Paragraph(
    val lines: List<String>
)

data class LayoutValidation(
    val hasRecipients: Boolean,
    val hasBody: Boolean,
    val hasSubject: Boolean,
    val hasGreeting: Boolean,
    val hasSender: Boolean,
    val errors: List<String>
)
