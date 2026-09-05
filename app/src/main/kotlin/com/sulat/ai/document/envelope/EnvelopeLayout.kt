package com.sulat.ai.document.envelope

import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.PageGeometry
import com.sulat.ai.document.renderer.PdfTextStyle

/**
 * Physical layout configuration for envelope labels on a given paper size.
 * The envelope is a printable template on the selected document size,
 * NOT a physical envelope with separate dimensions.
 */
data class EnvelopeLayout(
    val page: PageGeometry,
    val labelOriginXPt: Double,
    val labelOriginYPt: Double,
    val labelMaxWidthPt: Double,
    val styles: EnvelopeStyles
) {
    companion object {
        /**
         * Default margins for envelope labels (1 inch = 72pt).
         */
        private const val DEFAULT_MARGIN_PT = 72.0

        /**
         * Label origin offset from top-left corner of the page.
         * 1.5 inches from left, 1.5 inches from top — standard return-address area.
         */
        private const val LABEL_OFFSET_X_PT = 108.0  // 1.5 inches
        private const val LABEL_OFFSET_Y_PT = 108.0  // 1.5 inches

        /**
         * Create an [EnvelopeLayout] for the given paper size.
         * Uses default 1-inch margins on all sides.
         */
        fun create(paperSize: PaperSize): EnvelopeLayout {
            val margins = PaperSize.defaultMarginsPt()
            val page = PageGeometry(
                widthPt = paperSize.widthPt,
                heightPt = paperSize.heightPt,
                marginTopPt = margins.top,
                marginBottomPt = margins.bottom,
                marginLeftPt = margins.left,
                marginRightPt = margins.right
            )
            return create(page, EnvelopeStyles())
        }

        /**
         * Create an [EnvelopeLayout] with custom page geometry and styles.
         */
        fun create(page: PageGeometry, styles: EnvelopeStyles): EnvelopeLayout {
            val labelMaxWidthPt = page.usableWidthPt - (LABEL_OFFSET_X_PT - page.marginLeftPt)
            return EnvelopeLayout(
                page = page,
                labelOriginXPt = LABEL_OFFSET_X_PT,
                labelOriginYPt = LABEL_OFFSET_Y_PT,
                labelMaxWidthPt = labelMaxWidthPt.coerceAtLeast(200.0),
                styles = styles
            )
        }
    }
}

/**
 * Typography styles for envelope label elements.
 * Reuses [PdfTextStyle] for consistency with the PDF rendering system.
 */
data class EnvelopeStyles(
    val prefixStyle: PdfTextStyle = PdfTextStyle(
        fontSizePt = 12.0,
        isBold = true,
        lineSpacingMultiplier = 1.3
    ),
    val nameStyle: PdfTextStyle = PdfTextStyle(
        fontSizePt = 14.0,
        isBold = true,
        lineSpacingMultiplier = 1.3
    ),
    val positionStyle: PdfTextStyle = PdfTextStyle(
        fontSizePt = 11.0,
        isBold = false,
        lineSpacingMultiplier = 1.3
    ),
    val organizationStyle: PdfTextStyle = PdfTextStyle(
        fontSizePt = 11.0,
        isBold = false,
        lineSpacingMultiplier = 1.3
    ),
    val addressStyle: PdfTextStyle = PdfTextStyle(
        fontSizePt = 10.0,
        isBold = false,
        lineSpacingMultiplier = 1.4
    ),
    val optionalStyle: PdfTextStyle = PdfTextStyle(
        fontSizePt = 10.0,
        isBold = false,
        isItalic = true,
        lineSpacingMultiplier = 1.4
    )
)
