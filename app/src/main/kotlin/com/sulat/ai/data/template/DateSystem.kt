package com.sulat.ai.data.template

import com.sulat.ai.data.model.LetterDate
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

object DateSystem {
    private fun parseDateInput(userInput: String): List<LetterDate> {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        return userInput.split(",").mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                try {
                    val date = dateFormat.parse(trimmed)
                    if (date != null) LetterDate(date = date, label = trimmed) else null
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    // Mode A: Manual Dates - user selects one or more dates
    fun selectManualDates(userInput: String?): List<LetterDate> {
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
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        while (cal.get(Calendar.MONTH) == month - 1) {
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

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val dayOfWeekAtStart = cal.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = (dayOfWeekAtStart - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysToMonday)
        cal.add(Calendar.DAY_OF_WEEK, (week - 1) * 7)

        val weekStart = cal.clone() as Calendar

        for (i in 0 until 7) {
            val currentDay = weekStart.get(Calendar.DAY_OF_WEEK)
            if (weekdays.any { it == currentDay }) {
                val date = weekStart.time
                dates.add(LetterDate(
                    date = date,
                    label = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
                ))
            }
            weekStart.add(Calendar.DAY_OF_MONTH, 1)
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

    fun formatDate(date: Date): String {
        return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }

    fun deduplicateAndSort(dates: List<LetterDate>): List<LetterDate> {
        val unique = dates.groupBy { it.label }.values.map { it.first() }
        return unique.sortedBy { it.date }
    }

    fun calculateTotalLetters(recipientCount: Int, dateCount: Int): Int {
        return recipientCount * dateCount
    }

    fun calculateTotalEnvelopeLabels(recipientCount: Int, dateCount: Int): Int {
        return recipientCount * dateCount
    }
}
