package com.sulat.ai.workflow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.persistence.PersistenceManager

class LetterInfoActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT_ID = "extra_draft_id"
    }

    private var draftId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_letter_info)

        draftId = intent.getStringExtra(EXTRA_DRAFT_ID) ?: ""
        if (draftId.isBlank()) {
            Toast.makeText(this, "Error: No draft ID.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val etSubject = findViewById<EditText>(R.id.etSubject)
        val etGreeting = findViewById<EditText>(R.id.etGreeting)

        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft != null) {
            etSubject.setText(draft.subject)
            etGreeting.setText(draft.greeting)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            saveAndFinish()
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            val subject = etSubject.text.toString()
            val greeting = etGreeting.text.toString()

            if (subject.isBlank() && greeting.isBlank()) {
                Toast.makeText(this, "Please enter a subject or greeting.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentDraft = PersistenceManager.getDraft(this, draftId)
            if (currentDraft == null) {
                Toast.makeText(this, "Draft not found.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = currentDraft.copy(
                subject = subject,
                greeting = greeting,
                modifiedTime = System.currentTimeMillis()
            )
            PersistenceManager.saveDraft(this, updated)

            val intent = Intent(this, DateSelectionActivity::class.java)
            intent.putExtra(EXTRA_DRAFT_ID, draftId)
            startActivity(intent)
        }
    }

    private fun saveAndFinish() {
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft != null) {
            val etSubject = findViewById<EditText>(R.id.etSubject)
            val etGreeting = findViewById<EditText>(R.id.etGreeting)
            val updated = draft.copy(
                subject = etSubject.text.toString(),
                greeting = etGreeting.text.toString(),
                modifiedTime = System.currentTimeMillis()
            )
            PersistenceManager.saveDraft(this, updated)
        }
        finish()
    }

    override fun onPause() {
        super.onPause()
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft != null) {
            val etSubject = findViewById<EditText>(R.id.etSubject)
            val etGreeting = findViewById<EditText>(R.id.etGreeting)
            val updated = draft.copy(
                subject = etSubject.text.toString(),
                greeting = etGreeting.text.toString(),
                modifiedTime = System.currentTimeMillis()
            )
            PersistenceManager.saveDraft(this, updated)
        }
    }
}
