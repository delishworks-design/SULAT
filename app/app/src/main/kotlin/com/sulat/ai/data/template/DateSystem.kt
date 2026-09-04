package com.sulat.ai.data.template

import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

object DateSystem {
    // Mode A: Manual Dates - user selects one or more dates
    fun selectManualDates(userInput: String?): List<LetterDate> {
        // Parse user-selected dates from input
        return if (userInput?.isNotEmpty() == true) {
            parseDateInput(userInput)
        } else {
            emptyList()
        }
    }

    // Mode B: Weekdays - user selects month, year, weekdays (Mon-Fri default)
    fun selectWeekdaysDates(
        month: Int,
        year: Int,
        weekdays: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
    ): List<LetterDate> {
        val dates = mutableListOf<LetterDate>()
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1) // Calendar months are 0-based
        cal.set(Calendar.DAY_OF_MONTH, 1)

        while (cal.get(Calendar.MONTH) + 1 - 1 == month - 1) { // Still in target month
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (weekdays.any { it == dayOfWeek }) {
                val date = cal.time
                dates.add(LetterDate(
                    date = date,
                    label = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
                ))
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return dates
    }

    // Mode C: Specific Week - 1st week, 2nd week, 3rd week, 4th week, 5th week + weekdays
    fun selectSpecificWeekDates(
        week: Int,
        weekdays: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
    ): List<LetterDate> {
        val dates = mutableListOf<LetterDate>()
        val cal = Calendar.getInstance(Locale.getDefault())
        val firstOfMonth = Calendar.getInstance(Locale.getDefault())
        firstOfMonth.set(Calendar.DAY_OF_MONTH, 1)

        // Find the start of the target week
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.roll(Calendar.DAY_OF_WEEK, -((cal[Calendar.DAY_OF_WEEK] - Calendar.MONDAY.id + 7) % 7))
        cal.add(Calendar.DAY_OF_WEEK, (week - 1) * 7)

        // Process dates in that week
        val weekStart = cal.copy()
        cal.add(Calendar.DAY_OF_MONTH, (week - 1) * 7)

        for (i in 0 until 7) {
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            if (weekdays.any { it == currentDay }) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val date = cal.time
                // Check if we're still in the same calendar week
                dates.add(LetterDate(
                    date = date,
                    label = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
                ))
            } else {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return dates
    }

    // Mode D: Multiple Dates from calendar
    fun selectMultiDateDates(selectedDates: List<Date>): List<LetterDate> {
        return selectedDates.map { LetterDate(date = it, label = formatDate(it)) }
    }

    // Mode E: Whole Month
    fun selectWholeMonthDates(
        month: Int,
        year: Int,
        weekdays: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
    ): List<LetterDate> = selectWeekdaysDates(month, year, weekdays)

    // Format date for display
    fun formatDate(date: Date): String {
        return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }

    // Deduplicate and sort dates chronologically
    fun deduplicateAndSort(dates: List<LetterDate>): List<LetterDate> {
        // Parse dates and sort
        val unique = dates.groupBy { it.label }.values.map { it.first() }
        return unique.sortedBy { it.date }
    }

    // Calculate total letters: recipients × dates
    fun calculateTotalLetters(recipientCount: Int, dateCount: Int): Int {
        return recipientCount * dateCount
    }

    // Calculate total envelope labels (same as letters)
    fun calculateTotalEnvelopeLabels(recipientCount: Int, dateCount: Int): Int {
        return recipientCount * dateCount
    }
}