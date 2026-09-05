package com.sulat.ai.document.renderer

data class PdfTextStyle(
    val fontSizePt: Float,
    val isBold: Boolean,
    val isItalic: Boolean = false,
    val lineSpacingMultiplier: Float = 1.4f
)

enum class PdfTextRole(
    val style: PdfTextStyle
) {
    DATE(PdfTextStyle(fontSizePt = 11f, isBold = false)),
    RECIPIENT_PREFIX(PdfTextStyle(fontSizePt = 13f, isBold = true)),
    RECIPIENT_NAME(PdfTextStyle(fontSizePt = 12f, isBold = true)),
    RECIPIENT_POSITION(PdfTextStyle(fontSizePt = 11f, isBold = false)),
    RECIPIENT_ORGANIZATION(PdfTextStyle(fontSizePt = 11f, isBold = false)),
    RECIPIENT_ADDRESS(PdfTextStyle(fontSizePt = 10f, isBold = false)),
    RECIPIENT_OPTIONAL(PdfTextStyle(fontSizePt = 10f, isBold = false, isItalic = true)),
    SUBJECT(PdfTextStyle(fontSizePt = 11f, isBold = true)),
    GREETING(PdfTextStyle(fontSizePt = 11f, isBold = false)),
    BODY(PdfTextStyle(fontSizePt = 11f, isBold = false)),
    BODY_FIRST_LINE(PdfTextStyle(fontSizePt = 11f, isBold = false)),
    CLOSING(PdfTextStyle(fontSizePt = 11f, isBold = false, isItalic = true)),
    SENDER_NAME(PdfTextStyle(fontSizePt = 11f, isBold = true)),
    SENDER_ADDRESS(PdfTextStyle(fontSizePt = 10f, isBold = false)),
    SENDER_ORG(PdfTextStyle(fontSizePt = 10f, isBold = false)),
    SENDER_CONTACT(PdfTextStyle(fontSizePt = 10f, isBold = false)),
    SPACER(PdfTextStyle(fontSizePt = 6f, isBold = false))
}
