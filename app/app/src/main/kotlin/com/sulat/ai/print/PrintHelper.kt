package com.sulat.ai.print

import android.content.Context
import android.print.PrintManager
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile

object PrintHelper {
    // Print document using Android Print Framework
    fun printDocument(
        context: Context,
        draft: LetterDraft,
        recipient: Recipient,
        sender: SenderProfile,
        paperSize: PrintHelper.PaperSize
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Sulat-Letter-${recipient.name}"

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                cancel: Boolean?,
                receivedWidth: Int,
                oldParams: android.print.PrintAttributes?
            ) {
                // Create print document
                val pdfBuilder = PdfDocument.Builder()

                // Add page with letter content
                val page = pdfBuilder.addPage(
                    android.print.PrintAttributes.PAGE_SIZE_LETTER,
                    android.print.PrintAttributes.ORIENTATION_PORTRAIT
                )

                // Write letter content to PDF
                val canvas = page.beginPage(android.graphics.Canvas())
                val paint = android.graphics.Paint()

                // Draw recipient block
                val recipientText = "Kapatid na ${recipient.name}"
                canvas.drawText(recipientText, 50f, 100f, paint)

                // Draw date
                val dateText = "March 29, 2026"
                canvas.drawText(dateText, 50f, 140f, paint)

                // Draw body
                canvas.drawText("Letter body content...", 50f, 200f, paint)

                page.endPage()

                // Write PDF document
                pdfBuilder.writeToParcel(
                    android.os.Parcel.obtain().apply {
                        writeString("Sulat Letter Print")
                    }
                )

                // Commit print job
                printManager.print(
                    jobName,
                    printAdapter,
                    android.print.PrintAttributes.Builder().apply {
                        addMediaSize(android.print.PrintAttributes.MediaSize.STANDARD_LETTER)
                        apply {
                            colorMode = android.print.PrintAttributes.COLOR_MODE_COLOR
                            duplexMode = android.print.PrintAttributes.DUPLEX_NONE
                        }
                    }.apply { cancel = false }
                )
            }
        }

        printManager.print(jobName, printAdapter, null)
    }

    enum class PaperSize { A4, Legal, LongBond, Letter }
}