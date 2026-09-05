package com.sulat.ai.workflow

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.sulat.ai.R
import com.sulat.ai.data.model.LetterDate
import com.sulat.ai.data.persistence.PersistenceManager
import com.sulat.ai.data.template.DateSystem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.Year

class DateSelectionActivity : Activity() {

    companion object {
        const val EXTRA_DRAFT_ID = "extra_draft_id"
        private const val MODE_NONE = 0
        private const val MODE_SPECIFIC = 1
        private const val MODE_RECURRING = 2
        private const val MODE_MANUAL = 3
    }

    private var draftId: String = ""
    private val selectedDates = mutableListOf<LetterDate>()

    private lateinit var spinnerOccurrence: Spinner
    private lateinit var spinnerWeekday: Spinner
    private lateinit var spinnerMonth: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var specificDateContainer: LinearLayout
    private lateinit var recurringContainer: LinearLayout
    private lateinit var tvSelectedDates: TextView

    private val occurrenceLabels = listOf("First", "Second", "Third", "Fourth", "Fifth", "Last")
    private val weekdayLabels = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val monthLabels = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_date_selection)

        draftId = intent.getStringExtra(EXTRA_DRAFT_ID) ?: ""
        if (draftId.isBlank()) {
            Toast.makeText(this, "Error: No draft ID.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        specificDateContainer = findViewById(R.id.specificDateContainer)
        recurringContainer = findViewById(R.id.recurringContainer)
        tvSelectedDates = findViewById(R.id.tvSelectedDates)
        spinnerOccurrence = findViewById(R.id.spinnerOccurrence)
        spinnerWeekday = findViewById(R.id.spinnerWeekday)
        spinnerMonth = findViewById(R.id.spinnerMonth)
        spinnerYear = findViewById(R.id.spinnerYear)

        setupSpinners()
        loadExistingDates()

        val spinnerDateMode = findViewById<Spinner>(R.id.spinnerDateMode)
        val dateModes = listOf("No dates", "Specific Date", "Recurring Weekday", "Manual ISO Date")
        spinnerDateMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dateModes)
        spinnerDateMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                onDateModeChanged(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (selectedDates.isNotEmpty()) {
            spinnerDateMode.setSelection(1)
            onDateModeChanged(1)
        }

        findViewById<Button>(R.id.btnPickDate).setOnClickListener {
            showDatePicker()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            saveAndFinish()
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            onContinue()
        }

        updateSelectedDatesDisplay()
    }

    private fun setupSpinners() {
        spinnerOccurrence.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, occurrenceLabels)
        spinnerWeekday.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekdayLabels)
        spinnerMonth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthLabels)

        val currentYear = Year.now().value
        val years = (currentYear - 1..currentYear + 2).map { it.toString() }
        spinnerYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spinnerYear.setSelection(1)
    }

    private fun onDateModeChanged(mode: Int) {
        specificDateContainer.visibility = if (mode == MODE_SPECIFIC) View.VISIBLE else View.GONE
        recurringContainer.visibility = if (mode == MODE_RECURRING) View.VISIBLE else View.GONE

        if (mode == MODE_NONE) {
            selectedDates.clear()
            updateSelectedDatesDisplay()
        } else if (mode == MODE_MANUAL) {
            showManualDateDialog()
        } else if (mode == MODE_RECURRING) {
            selectedDates.clear()
            applyRecurringDates()
            updateSelectedDatesDisplay()
        }
    }

    private fun showDatePicker() {
        val now = LocalDate.now()
        val dialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val date = DateSystem.specificDate(year, month + 1, dayOfMonth)
            if (date != null) {
                val letterDate = LetterDate(date = DateSystem.localDateToDate(date), label = DateSystem.formatDisplay(date))
                selectedDates.add(letterDate)
                updateSelectedDatesDisplay()
            } else {
                Toast.makeText(this, "Invalid date.", Toast.LENGTH_SHORT).show()
            }
        }, now.year, now.monthValue - 1, now.dayOfMonth)
        dialog.show()
    }

    private fun showManualDateDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "yyyy-MM-dd"
            setPadding(32, 32, 32, 32)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Enter Date (yyyy-MM-dd)")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val dates = DateSystem.parseDateInput(input.text.toString())
                if (dates.isNotEmpty()) {
                    selectedDates.addAll(dates)
                    updateSelectedDatesDisplay()
                } else {
                    Toast.makeText(this, "Invalid date format. Use yyyy-MM-dd.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyRecurringDates() {
        val occurrenceIndex = spinnerOccurrence.selectedItemPosition
        val weekdayIndex = spinnerWeekday.selectedItemPosition
        val monthIndex = spinnerMonth.selectedItemPosition
        val yearIndex = spinnerYear.selectedItemPosition

        val currentYear = Year.now().value
        val year = (currentYear - 1) + yearIndex
        val month = monthIndex + 1
        val weekday = DayOfWeek.of(weekdayIndex + 1)

        val dates = if (occurrenceIndex == 5) {
            listOf(DateSystem.lastWeekday(year, month, weekday))
        } else {
            val date = DateSystem.ordinalWeekday(year, month, weekday, occurrenceIndex + 1)
            if (date != null) listOf(date) else emptyList()
        }

        selectedDates.clear()
        for (date in dates) {
            selectedDates.add(LetterDate(date = DateSystem.localDateToDate(date), label = DateSystem.formatDisplay(date)))
        }
    }

    private fun loadExistingDates() {
        val draft = PersistenceManager.getDraft(this, draftId) ?: return
        selectedDates.addAll(draft.dates)
    }

    private fun onContinue() {
        val draft = PersistenceManager.getDraft(this, draftId)
        if (draft == null) {
            Toast.makeText(this, "Draft not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = draft.copy(
            dates = DateSystem.deduplicateAndSort(selectedDates),
            modifiedTime = System.currentTimeMillis()
        )
        PersistenceManager.saveDraft(this, updated)

        val intent = Intent(this, WriteLetterActivity::class.java)
        intent.putExtra(EXTRA_DRAFT_ID, draftId)
        startActivity(intent)
    }

    private fun updateSelectedDatesDisplay() {
        if (selectedDates.isEmpty()) {
            tvSelectedDates.text = "No dates selected"
        } else {
            val sorted = DateSystem.deduplicateAndSort(selectedDates)
            val labels = sorted.map { it.label }
            tvSelectedDates.text = "Selected (${sorted.size}):\n" + labels.joinToString("\n")
        }
    }

    private fun saveCurrentState() {
        val draft = PersistenceManager.getDraft(this, draftId) ?: return
        val updated = draft.copy(
            dates = DateSystem.deduplicateAndSort(selectedDates),
            modifiedTime = System.currentTimeMillis()
        )
        PersistenceManager.saveDraft(this, updated)
    }

    private fun saveAndFinish() {
        saveCurrentState()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draftId", draftId)
        outState.putStringArrayList("dateLabels", ArrayList(selectedDates.map { it.label }))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredId = savedInstanceState.getString("draftId", "")
        if (restoredId.isNotBlank()) draftId = restoredId
    }

    override fun onPause() {
        super.onPause()
        saveCurrentState()
    }
}
