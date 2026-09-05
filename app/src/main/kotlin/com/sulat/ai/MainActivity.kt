package com.sulat.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.preview.EnvelopePreviewActivity
import com.sulat.ai.preview.PreviewActivity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = findViewById<LinearLayout>(R.id.rootContainer)

        val previewButton = container.findViewById<Button>(R.id.btnPreview)
        if (previewButton != null) {
            previewButton.setOnClickListener { openPreview() }
        }

        val envelopeButton = container.findViewById<Button>(R.id.btnEnvelope)
        if (envelopeButton != null) {
            envelopeButton.setOnClickListener { openEnvelope() }
        }
    }

    private fun openPreview() {
        val drafts = PersistenceManager.loadDrafts(this)
        val draft = drafts.firstOrNull()
        if (draft == null) {
            Toast.makeText(this, "No letters to preview. Create a letter first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PreviewActivity::class.java)
        intent.putExtra(PreviewActivity.EXTRA_DRAFT_ID, draft.id)
        startActivity(intent)
    }

    private fun openEnvelope() {
        val drafts = PersistenceManager.loadDrafts(this)
        val draft = drafts.firstOrNull()
        if (draft == null) {
            Toast.makeText(this, "No letters available. Create a letter first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, EnvelopePreviewActivity::class.java)
        intent.putExtra(EnvelopePreviewActivity.EXTRA_DRAFT_ID, draft.id)
        startActivity(intent)
    }
}
