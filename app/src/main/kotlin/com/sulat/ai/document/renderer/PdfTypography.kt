package com.sulat.ai.document.renderer

data class PdfTextStyle(
    val fontSizePt: Double,
    val isBold: Boolean,
    val isItalic: Boolean = false,
    val lineSpacingMultiplier: Double = 1.4
)

enum class PdfTextRole(
    val style: PdfTextStyle
) {
    DATE(PdfTextStyle(fontSizePt = 11.0, isBold = false)),
    RECIPIENT_PREFIX(PdfTextStyle(fontSizePt = 13.0, isBold = true)),
    RECIPIENT_NAME(PdfTextStyle(fontSizePt = 12.0, isBold = true)),
    RECIPIENT_POSITION(PdfTextStyle(fontSizePt = 11.0, isBold = false)),
    RECIPIENT_ORGANIZATION(PdfTextStyle(fontSizePt = 11.0, isBold = false)),
    RECIPIENT_ADDRESS(PdfTextStyle(fontSizePt = 10.0, isBold = false)),
    RECIPIENT_OPTIONAL(PdfTextStyle(fontSizePt = 10.0, isBold = false, isItalic = true)),
    SUBJECT(PdfTextStyle(fontSizePt = 11.0, isBold = true)),
    GREETING(PdfTextStyle(fontSizePt = 11.0, isBold = false)),
    BODY(PdfTextStyle(fontSizePt = 11.0, isBold = false)),
    BODY_FIRST_LINE(PdfTextStyle(fontSizePt = 11.0, isBold = false)),
    CLOSING(PdfTextStyle(fontSizePt = 11.0, isBold = false, isItalic = true)),
    SENDER_NAME(PdfTextStyle(fontSizePt = 11.0, isBold = true)),
    SENDER_ADDRESS(PdfTextStyle(fontSizePt = 10.0, isBold = false)),
    SENDER_ORG(PdfTextStyle(fontSizePt = 10.0, isBold = false)),
    SENDER_CONTACT(PdfTextStyle(fontSizePt = 10.0, isBold = false)),
    SPACER(PdfTextStyle(fontSizePt = 6.0, isBold = false))
}
