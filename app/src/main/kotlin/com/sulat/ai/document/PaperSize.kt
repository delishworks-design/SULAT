package com.sulat.ai.document

enum class PaperSize(
    val widthPt: Double,
    val heightPt: Double,
    val widthMm: Double,
    val heightMm: Double
) {
    A4(
        widthPt = 595.276,   // 210mm
        heightPt = 841.89,   // 297mm
        widthMm = 210.0,
        heightMm = 297.0
    ),
    ShortBond(
        widthPt = 612.0,     // 8.5in = 215.9mm
        heightPt = 792.0,    // 11in = 279.4mm
        widthMm = 215.9,
        heightMm = 279.4
    ),
    Legal(
        widthPt = 612.0,     // 8.5in = 215.9mm
        heightPt = 1008.0,   // 14in = 355.6mm
        widthMm = 215.9,
        heightMm = 355.6
    ),
    LongBond(
        widthPt = 612.0,     // 8.5in = 215.9mm
        heightPt = 936.0,    // 13in = 330.2mm
        widthMm = 215.9,
        heightMm = 330.2
    );

    companion object {
        private const val MM_PER_PT = 25.4 / 72.0

        fun fromMm(widthMm: Double, heightMm: Double): PaperSize? {
            return entries.find {
                kotlin.math.abs(it.widthMm - widthMm) < 0.1 &&
                kotlin.math.abs(it.heightMm - heightMm) < 0.1
            }
        }

        fun defaultMarginsPt(): Margins = Margins(
            top = 72.0,    // 1 inch
            bottom = 72.0,
            left = 72.0,
            right = 72.0
        )
    }
}

data class Margins(
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double
)
