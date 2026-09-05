package com.sulat.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.workflow.CreateLetterActivity
import com.sulat.ai.workflow.SavedLettersActivity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCreateNew).setOnClickListener {
            val draft = PersistenceManager.createDraft(this)
            val intent = Intent(this, CreateLetterActivity::class.java)
            intent.putExtra(CreateLetterActivity.EXTRA_DRAFT_ID, draft.id)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnSavedLetters).setOnClickListener {
            startActivity(Intent(this, SavedLettersActivity::class.java))
        }

        findViewById<Button>(R.id.btnEnvelopes).setOnClickListener {
            val drafts = PersistenceManager.loadDrafts(this)
            if (drafts.isEmpty()) {
                Toast.makeText(this, "No letters available. Create a letter first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, SavedLettersActivity::class.java))
        }
    }
}
