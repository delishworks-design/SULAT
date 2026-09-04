package com.sulat.ai.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import java.io.File
import java.io.FileOutputStream

object PrintHelper {

    enum class PaperSize { A4, Legal, LongBond, Letter }

    fun printDocument(
        context: Context,
        draft: LetterDraft,
        recipient: Recipient,
        sender: SenderProfile,
        paperSize: PaperSize
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Sulat-Letter-${recipient.name}"

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onWrite(
                pages: android.graphics.pdf.PdfDocument.PageRange[],
                destination: ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
                val page = pdfDocument.startPage(pageInfo)

                val canvas: Canvas = page.canvas
                val paint = Paint()

                paint.textSize = 14f
                canvas.drawText("Kapatid na ${recipient.name}", 50f, 80f, paint)
                canvas.drawText(recipient.position, 50f, 100f, paint)
                canvas.drawText(recipient.organization, 50f, 120f, paint)

                paint.textSize = 12f
                canvas.drawText("March 29, 2026", 50f, 160f, paint)

                paint.textSize = 11f
                val bodyLines = draft.body.split("\n")
                var y = 200f
                for (line in bodyLines) {
                    canvas.drawText(line, 50f, y, paint)
                    y += 16f
                }

                pdfDocument.finishPage(page)

                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                } finally {
                    pdfDocument.close()
                }
            }

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                callback?.onLayoutFinished(
                    PrintDocumentInfo.Builder(jobName)
                        .setPageCount(1)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                )
            }
        }

        printManager.print(jobName, printAdapter, null)
    }
}
