package com.sulat.ai.preview

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.model.SenderProfile
import com.sulat.ai.document.PaperSize
import com.sulat.ai.document.layout.DocumentLayout
import com.sulat.ai.document.renderer.LetterTemplateEngine
import com.sulat.ai.document.renderer.PdfContentCalculator

/**
 * Activity that renders a letter preview using the same deterministic pipeline as PDF.
 * Accepts a [LetterDraft] via intent extra EXTRA_DRAFT.
 * If no draft is provided, a demo letter is rendered.
 */
class PreviewActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT = "extra_draft"
    }

    private var calculator: PreviewCalculator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val draft = intent.getSerializableExtra(EXTRA_DRAFT) as? LetterDraft
            ?: createDemoDraft()

        val layout = buildLayout(draft)
        val renderPlan = PdfContentCalculator(layout).plan()

        val display = resources.displayMetrics
        val screenWidthPx = display.widthPixels
        val horizontalPaddingPx = (32 * resources.displayMetrics.density).toInt()
        val availableWidthPx = screenWidthPx - horizontalPaddingPx

        calculator = PreviewCalculator(renderPlan, availableWidthPx)

        setupButtons()
        renderPages()
        updatePageInfo()
    }

    private fun buildLayout(draft: LetterDraft): DocumentLayout {
        val engine = LetterTemplateEngine()
        return engine.buildLayout(draft, PaperSize.A4)
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
            Toast.makeText(this, "Print — coming in FIX 6D", Toast.LENGTH_SHORT).show()
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

    private fun createDemoDraft(): LetterDraft {
        return LetterDraft(
            id = "demo-preview",
            recipients = listOf(
                Recipient(
                    id = "r1",
                    name = "KA. JUAN DELA CRUZ",
                    position = "Minister",
                    organization = "Local Congregation of Example",
                    address = "123 Example Street, Calamba, Laguna"
                )
            ),
            dates = listOf(LetterDate(date = java.util.Date(1700000000000L), label = "Jan 1")),
            body = "Peace be with you, brother.\n\n" +
                "This is a formal request regarding the upcoming activity " +
                "scheduled for next week. We would like to confirm the details " +
                "and ensure that all necessary arrangements are in place.\n\n" +
                "Maraming salamat po sa inyong pagkakataon. We look forward " +
                "to your kind response at your earliest convenience.",
            subject = "Request for Confirmation of Activity",
            greeting = "Dear Brother,",
            sender = SenderProfile(
                name = "Lloyd Malto",
                signature = "Faithfully yours,",
                address = "456 Sender Street"
            )
        )
    }
}
