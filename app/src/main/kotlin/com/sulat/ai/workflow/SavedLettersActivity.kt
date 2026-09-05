package com.sulat.ai.workflow

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.preview.EnvelopePreviewActivity
import com.sulat.ai.preview.PreviewActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedLettersActivity : Activity() {

    private lateinit var lettersContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_letters)

        lettersContainer = findViewById(R.id.lettersContainer)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        loadLetters()
    }

    override fun onResume() {
        super.onResume()
        loadLetters()
    }

    private fun loadLetters() {
        lettersContainer.removeAllViews()
        val drafts = PersistenceManager.loadDrafts(this)

        if (drafts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No saved letters yet."
                textSize = 16f
                setTextColor(resources.getColor(R.color.muted_text))
                setPadding(32, 64, 32, 32)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            lettersContainer.addView(emptyView)

            val createButton = Button(this).apply {
                text = "Create New Letter"
                setTextColor(resources.getColor(R.color.warm_white))
                setBackgroundColor(resources.getColor(R.color.deep_green))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(32, 16, 32, 0) }
                setOnClickListener {
                    val draft = PersistenceManager.createDraft(this@SavedLettersActivity)
                    val intent = Intent(this@SavedLettersActivity, CreateLetterActivity::class.java)
                    intent.putExtra(CreateLetterActivity.EXTRA_DRAFT_ID, draft.id)
                    startActivity(intent)
                }
            }
            lettersContainer.addView(createButton)
            return
        }

        for (draft in drafts) {
            val itemView = createLetterItem(draft)
            lettersContainer.addView(itemView)
        }
    }

    private fun createLetterItem(draft: LetterDraft): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.item_saved_letter, lettersContainer, false)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvRecipients = view.findViewById<TextView>(R.id.tvRecipients)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)

        val displayTitle = try {
            if (draft.subject.isNotBlank()) draft.subject else "Untitled Letter"
        } catch (e: Exception) {
            "Untitled Letter"
        }

        tvTitle.text = displayTitle

        val recipientSummary = try {
            if (draft.recipients.isNotEmpty()) {
                val validRecipients = draft.recipients.filter { it.name.isNotBlank() }
                if (validRecipients.isEmpty()) {
                    "No recipient"
                } else {
                    val names = validRecipients.take(3).map { it.name }
                    val suffix = if (validRecipients.size > 3) " +${validRecipients.size - 3} more" else ""
                    names.joinToString(", ") + suffix
                }
            } else {
                "No recipients"
            }
        } catch (e: Exception) {
            "No recipients"
        }
        tvRecipients.text = recipientSummary

        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
        tvDate.text = try {
            dateFormat.format(Date(draft.modifiedTime))
        } catch (e: Exception) {
            ""
        }

        view.findViewById<Button>(R.id.btnOpen).setOnClickListener {
            openForEditing(draft.id)
        }

        view.findViewById<Button>(R.id.btnEnvelope).setOnClickListener {
            openEnvelopePreview(draft.id)
        }

        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            confirmDelete(draft.id)
        }

        return view
    }

    private fun openForEditing(draftId: String) {
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft == null) {
            Toast.makeText(this, "Letter not found.", Toast.LENGTH_SHORT).show()
            loadLetters()
            return
        }
        val intent = Intent(this, CreateLetterActivity::class.java)
        intent.putExtra(CreateLetterActivity.EXTRA_DRAFT_ID, draftId)
        startActivity(intent)
    }

    private fun openEnvelopePreview(draftId: String) {
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft == null) {
            Toast.makeText(this, "Letter not found.", Toast.LENGTH_SHORT).show()
            loadLetters()
            return
        }
        if (draft.recipients.isEmpty()) {
            Toast.makeText(this, "No recipients for envelope labels.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, EnvelopePreviewActivity::class.java)
        intent.putExtra(EnvelopePreviewActivity.EXTRA_DRAFT_ID, draftId)
        startActivity(intent)
    }

    private fun confirmDelete(draftId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Letter")
            .setMessage("Delete this letter? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    PersistenceManager.deleteDraft(this, draftId)
                    loadLetters()
                    Toast.makeText(this, "Letter deleted.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
