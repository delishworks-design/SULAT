package com.sulat.ai.workflow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient
import com.sulat.ai.data.persistence.PersistenceManager

class CreateLetterActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT_ID = "extra_draft_id"
    }

    private var draftId: String = ""
    private val recipientViews = mutableListOf<View>()
    private val recipientIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_letter)

        draftId = intent.getStringExtra(EXTRA_DRAFT_ID) ?: ""
        if (draftId.isBlank()) {
            Toast.makeText(this, "Error: No draft ID.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            saveAndFinish()
        }

        findViewById<Button>(R.id.btnAddRecipient).setOnClickListener {
            addRecipientView()
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            if (validateAndSave()) {
                val intent = Intent(this, LetterInfoActivity::class.java)
                intent.putExtra(EXTRA_DRAFT_ID, draftId)
                startActivity(intent)
            }
        }

        loadExistingRecipients()
    }

    private fun loadExistingRecipients() {
        val draft = PersistenceManager.getDraft(this, draftId) ?: return
        if (draft.recipients.isNotEmpty()) {
            for (recipient in draft.recipients) {
                addRecipientView(recipient)
            }
        } else {
            addRecipientView()
        }
    }

    private fun addRecipientView(recipient: Recipient? = null) {
        val container = findViewById<LinearLayout>(R.id.recipientsContainer)
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.item_recipient, container, false)

        val recipientId = recipient?.id ?: java.util.UUID.randomUUID().toString()
        recipientIds.add(recipientId)

        val number = recipientViews.size + 1
        view.findViewById<TextView>(R.id.tvRecipientNumber).text = "Recipient $number"

        val etName = view.findViewById<EditText>(R.id.etName)
        val etPosition = view.findViewById<EditText>(R.id.etPosition)
        val etOrganization = view.findViewById<EditText>(R.id.etOrganization)
        val etAddress = view.findViewById<EditText>(R.id.etAddress)
        val etOptional = view.findViewById<EditText>(R.id.etOptional)

        if (recipient != null) {
            etName.setText(recipient.name)
            etPosition.setText(recipient.position)
            etOrganization.setText(recipient.organization)
            etAddress.setText(recipient.address)
            etOptional.setText(recipient.optionalInfo)
        }

        view.findViewById<Button>(R.id.btnDeleteRecipient).setOnClickListener {
            container.removeView(view)
            recipientViews.remove(view)
            recipientIds.remove(recipientId)
            renumberRecipients()
        }

        recipientViews.add(view)
        container.addView(view)
    }

    private fun renumberRecipients() {
        for ((index, view) in recipientViews.withIndex()) {
            view.findViewById<TextView>(R.id.tvRecipientNumber).text = "Recipient ${index + 1}"
        }
    }

    private fun collectRecipients(): List<Recipient> {
        val recipients = mutableListOf<Recipient>()
        for (view in recipientViews) {
            val name = view.findViewById<EditText>(R.id.etName).text.toString().trim()
            if (name.isNotEmpty()) {
                recipients.add(
                    Recipient(
                        id = recipientIds[recipientViews.indexOf(view)],
                        name = name,
                        position = view.findViewById<EditText>(R.id.etPosition).text.toString().trim(),
                        organization = view.findViewById<EditText>(R.id.etOrganization).text.toString().trim(),
                        address = view.findViewById<EditText>(R.id.etAddress).text.toString(),
                        optionalInfo = view.findViewById<EditText>(R.id.etOptional).text.toString()
                    )
                )
            }
        }
        return recipients
    }

    private fun validateAndSave(): Boolean {
        val recipients = collectRecipients()
        if (recipients.isEmpty()) {
            Toast.makeText(this, "Please add at least one recipient with a name.", Toast.LENGTH_SHORT).show()
            return false
        }
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft == null) {
            Toast.makeText(this, "Draft not found.", Toast.LENGTH_SHORT).show()
            return false
        }
        val updated = draft.copy(
            recipients = recipients,
            modifiedTime = System.currentTimeMillis()
        )
        PersistenceManager.saveDraft(this, updated)
        return true
    }

    private fun saveAndFinish() {
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft != null) {
            val recipients = collectRecipients()
            val updated = draft.copy(
                recipients = recipients,
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
            val recipients = collectRecipients()
            val updated = draft.copy(
                recipients = recipients,
                modifiedTime = System.currentTimeMillis()
            )
            PersistenceManager.saveDraft(this, updated)
        }
    }
}
