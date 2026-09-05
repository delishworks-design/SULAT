package com.sulat.ai.preview

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.envelope.EnvelopeData
import com.sulat.ai.document.envelope.EnvelopeFilename
import com.sulat.ai.document.envelope.EnvelopeLayout
import com.sulat.ai.document.envelope.EnvelopeRenderer
import com.sulat.ai.document.renderer.DeterministicTextMeasurer
import com.sulat.ai.print.PrintHelper
import com.sulat.ai.share.ShareHelper
import java.io.File

/**
 * Activity that renders envelope labels from a letter draft's recipients.
 * Generates a real PDF via EnvelopeRenderer and provides Share/Print.
 * Shows error state for missing/invalid drafts — no demo fallback.
 */
class EnvelopePreviewActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT_ID = "extra_draft_id"
        const val EXTRA_PAPER_SIZE = "extra_paper_size"
    }

    private var currentDraft: LetterDraft? = null
    private var currentPaperSize: PaperSize = PaperSize.A4
    private var currentEnvelopePdf: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_envelope_preview)

        setupButtons()

        val draftId = intent.getStringExtra(EXTRA_DRAFT_ID)
        if (draftId.isNullOrBlank()) {
            showError("No letter selected. Please choose a letter for envelope labels.")
            return
        }

        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft == null) {
            showError("The letter data could not be loaded.")
            return
        }

        val paperSizeName = intent.getStringExtra(EXTRA_PAPER_SIZE)
        val paperSize = try {
            if (paperSizeName != null) PaperSize.valueOf(paperSizeName) else PaperSize.A4
        } catch (_: IllegalArgumentException) {
            PaperSize.A4
        }

        currentDraft = draft
        currentPaperSize = paperSize

        val envelopeDataList = EnvelopeData.fromDraft(draft)
        if (envelopeDataList.isEmpty()) {
            showError("No valid recipients found. Cannot generate envelope labels.")
            return
        }

        renderEnvelopePreviews(envelopeDataList)
        generateEnvelopePdf(draft, paperSize)
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val pdfFile = currentEnvelopePdf
            if (pdfFile == null || !pdfFile.exists()) {
                Toast.makeText(this, "No envelope PDF to share.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = ShareHelper.sharePdf(this, pdfFile)
            if (!result.success) {
                Toast.makeText(this, "Share failed: ${result.error}", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btnPrint).setOnClickListener {
            val pdfFile = currentEnvelopePdf
            if (pdfFile == null || !pdfFile.exists()) {
                Toast.makeText(this, "No envelope PDF to print.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val jobName = "Sulat-Envelope"
            val result = PrintHelper.printExistingPdf(this, pdfFile, currentPaperSize, jobName)
            if (!result.success) {
                Toast.makeText(this, "Print failed: ${result.error}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Print job sent", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderEnvelopePreviews(envelopeDataList: List<EnvelopeData>) {
        val container = findViewById<LinearLayout>(R.id.envelopeContainer)
        container.removeAllViews()

        for ((index, data) in envelopeDataList.withIndex()) {
            val pageView = createEnvelopePreviewView(data, index + 1, envelopeDataList.size)
            container.addView(pageView)
        }

        val info = findViewById<TextView>(R.id.tvPageInfo)
        info.text = "Envelope 1 of ${envelopeDataList.size}"
    }

    private fun createEnvelopePreviewView(data: EnvelopeData, pageNum: Int, totalPages: Int): LinearLayout {
        val layout = EnvelopeLayout.create(currentPaperSize)
        val h = data.nameHierarchy
        val r = data.recipient
        val styles = layout.styles

        val pageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(48, 48, 48, 48)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (16 * resources.displayMetrics.density).toInt()
            layoutParams = params
        }

        // Page header
        val header = TextView(this).apply {
            text = "Envelope $pageNum of $totalPages"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }
        pageLayout.addView(header)

        // Prefix
        if (h.prefix.isNotEmpty()) {
            val view = TextView(this).apply {
                text = h.prefix
                textSize = styles.prefixStyle.fontSizePt.toFloat()
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            pageLayout.addView(view)
        }

        // Main name
        if (h.mainName.isNotEmpty()) {
            val view = TextView(this).apply {
                text = h.mainName
                textSize = styles.nameStyle.fontSizePt.toFloat()
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            pageLayout.addView(view)
        }

        // Position
        if (r.position.isNotEmpty()) {
            val view = TextView(this).apply {
                text = r.position
                textSize = styles.positionStyle.fontSizePt.toFloat()
            }
            pageLayout.addView(view)
        }

        // Organization
        if (r.organization.isNotEmpty()) {
            val view = TextView(this).apply {
                text = r.organization
                textSize = styles.organizationStyle.fontSizePt.toFloat()
            }
            pageLayout.addView(view)
        }

        // Address — preserve line breaks
        if (r.address.isNotEmpty()) {
            val view = TextView(this).apply {
                text = r.address
                textSize = styles.addressStyle.fontSizePt.toFloat()
            }
            pageLayout.addView(view)
        }

        // Optional info
        if (r.optionalInfo.isNotEmpty()) {
            val view = TextView(this).apply {
                text = r.optionalInfo
                textSize = styles.optionalStyle.fontSizePt.toFloat()
                setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            pageLayout.addView(view)
        }

        return pageLayout
    }

    private fun generateEnvelopePdf(draft: LetterDraft, paperSize: PaperSize) {
        val envelopeDataList = EnvelopeData.fromDraft(draft)
        if (envelopeDataList.isEmpty()) return

        val firstRecipientName = envelopeDataList.first().recipient.name
        val filename = EnvelopeFilename.generate(firstRecipientName)
        val outputFile = File(cacheDir, filename)

        val renderer = EnvelopeRenderer(DeterministicTextMeasurer())
        val result = renderer.renderEnvelopePdf(envelopeDataList, outputFile, paperSize)

        if (!result.success) {
            Toast.makeText(this, "Failed to generate envelope PDF: ${result.error}", Toast.LENGTH_LONG).show()
            return
        }

        if (result.file == null || !com.sulat.ai.document.renderer.PdfRenderer.isValidPdfFile(result.file)) {
            Toast.makeText(this, "Generated envelope PDF is invalid", Toast.LENGTH_LONG).show()
            return
        }

        currentEnvelopePdf = result.file
    }

    private fun showError(message: String) {
        val container = findViewById<LinearLayout>(R.id.envelopeContainer)
        container.removeAllViews()

        val errorText = TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(32, 32, 32, 32)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        container.addView(errorText)

        findViewById<TextView>(R.id.tvPageInfo).text = "Unable to preview"
    }
}
