package com.sulat.ai.workflow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.preview.PreviewActivity

class WriteLetterActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT_ID = "extra_draft_id"
    }

    private var draftId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_letter)

        draftId = intent.getStringExtra(EXTRA_DRAFT_ID) ?: ""
        if (draftId.isBlank()) {
            Toast.makeText(this, "Error: No draft ID.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val tvSubjectPreview = findViewById<TextView>(R.id.tvSubjectPreview)
        val tvGreetingPreview = findViewById<TextView>(R.id.tvGreetingPreview)
        val etBody = findViewById<EditText>(R.id.etBody)

        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft != null) {
            if (draft.subject.isNotBlank()) {
                tvSubjectPreview.text = draft.subject
            } else {
                tvSubjectPreview.text = "(No subject)"
            }
            if (draft.greeting.isNotBlank()) {
                tvGreetingPreview.text = draft.greeting
            } else {
                tvGreetingPreview.text = "(No greeting)"
            }
            etBody.setText(draft.body)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            saveAndFinish()
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            val body = etBody.text.toString()
            if (body.isBlank()) {
                Toast.makeText(this, "Please write the letter body.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentDraft = PersistenceManager.getDraft(this, draftId)
            if (currentDraft == null) {
                Toast.makeText(this, "Draft not found.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = currentDraft.copy(
                body = body,
                modifiedTime = System.currentTimeMillis()
            )
            PersistenceManager.saveDraft(this, updated)

            val intent = Intent(this, PreviewActivity::class.java)
            intent.putExtra(PreviewActivity.EXTRA_DRAFT_ID, draftId)
            startActivity(intent)
        }
    }

    private fun saveAndFinish() {
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft != null) {
            val etBody = findViewById<EditText>(R.id.etBody)
            val updated = draft.copy(
                body = etBody.text.toString(),
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
            val etBody = findViewById<EditText>(R.id.etBody)
            val updated = draft.copy(
                body = etBody.text.toString(),
                modifiedTime = System.currentTimeMillis()
            )
            PersistenceManager.saveDraft(this, updated)
        }
    }
}
