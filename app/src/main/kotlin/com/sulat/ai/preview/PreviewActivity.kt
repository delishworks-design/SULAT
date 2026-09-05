package com.sulat.ai.preview

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfContentCalculator
import com.sulat.ai.print.PrintHelper

/**
 * Activity that renders a letter preview using the same deterministic pipeline as PDF.
 * Loads a draft by ID from PersistenceManager.
 * Shows error state for missing/invalid drafts — no demo fallback.
 */
class PreviewActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT_ID = "extra_draft_id"
        const val EXTRA_PAPER_SIZE = "extra_paper_size"
    }

    private var calculator: PreviewCalculator? = null
    private var currentDraft: LetterDraft? = null
    private var currentPaperSize: PaperSize = PaperSize.A4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        setupButtons()

        val draftId = intent.getStringExtra(EXTRA_DRAFT_ID)
        if (draftId.isNullOrBlank()) {
            showError("No letter selected. Please choose a letter to preview.")
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

        val layout = try {
            val engine = LetterTemplateEngine()
            engine.buildLayout(draft, paperSize)
        } catch (e: Exception) {
            showError("Failed to generate letter layout.")
            return
        }

        val renderPlan = try {
            PdfContentCalculator(layout).plan()
        } catch (e: Exception) {
            showError("Failed to compute letter preview.")
            return
        }

        if (renderPlan.pages.isEmpty()) {
            showError("The letter has no content to preview.")
            return
        }

        val display = resources.displayMetrics
        val screenWidthPx = display.widthPixels
        val horizontalPaddingPx = (32 * resources.displayMetrics.density).toInt()
        val availableWidthPx = screenWidthPx - horizontalPaddingPx

        calculator = PreviewCalculator(renderPlan, layout.page, availableWidthPx)

        renderPages()
        updatePageInfo()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Toast.makeText(this, "Save PDF — coming in FIX 6B", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener {
            Toast.makeText(this, "Share — coming in FIX 6C", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPrint).setOnClickListener {
            val draft = currentDraft
            if (draft == null) {
                Toast.makeText(this, "No letter loaded to print.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = PrintHelper.printDocument(this, draft, currentPaperSize)
            if (!result.success) {
                Toast.makeText(this, "Print failed: ${result.error}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Print job sent", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPages() {
        val container = findViewById<LinearLayout>(R.id.pageContainer)
        container.removeAllViews()

        val calc = calculator ?: return

        for (i in 0 until calc.totalPages) {
            val pageView = LetterPreviewView(this)
            pageView.setPreviewData(calc, i)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = (16 * resources.displayMetrics.density).toInt()
            pageView.layoutParams = layoutParams

            container.addView(pageView)
        }
    }

    private fun updatePageInfo() {
        val calc = calculator ?: return
        val info = findViewById<TextView>(R.id.tvPageInfo)
        info.text = "Page 1 of ${calc.totalPages}"
    }

    private fun showError(message: String) {
        val container = findViewById<LinearLayout>(R.id.pageContainer)
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
