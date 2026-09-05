package com.sulat.ai.data.template

import com.sulat.ai.data.model.LetterDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

object DateSystem {

    private val DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    private val ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val SHORT_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    // ── Specific date ─────────────────────────────────────────────────────

    fun specificDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            val date = LocalDate.of(year, month, day)
            if (date.monthValue == month && date.dayOfMonth == day) date else null
        } catch (e: Exception) {
            null
        }
    }

    // ── Manual date parsing (strict yyyy-MM-dd) ───────────────────────────

    fun parseDateInput(userInput: String): List<LetterDate> {
        return userInput.split(",").mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parsed = parseSingleDate(trimmed)
            if (parsed != null) LetterDate(date = localDateToDate(parsed), label = formatDisplay(parsed))
            else null
        }
    }

    fun parseSingleDate(input: String): LocalDate? {
        return try {
            val date = LocalDate.parse(input.trim(), ISO_FORMAT)
            if (date.format(ISO_FORMAT) == input.trim()) date else null
        } catch (e: DateTimeParseException) {
            null
        }
    }

    // ── Ordinal weekday (nth weekday of month) ────────────────────────────

    fun ordinalWeekday(
        year: Int,
        month: Int,
        weekday: DayOfWeek,
        occurrence: Int
    ): LocalDate? {
        if (occurrence < 1 || occurrence > 5) return null
        val ym = YearMonth.of(year, month)
        val firstOfMonth = ym.atDay(1)
        val firstTarget = with(firstOfMonth) {
            val daysUntil = (weekday.value - this.dayOfWeek.value + 7) % 7
            this.plusDays(daysUntil.toLong())
        }
        val result = firstTarget.plusDays(((occurrence - 1) * 7).toLong())
        return if (result.monthValue == month && result.year == year) result else null
    }

    // ── Last weekday of month ─────────────────────────────────────────────

    fun lastWeekday(year: Int, month: Int, weekday: DayOfWeek): LocalDate {
        val ym = YearMonth.of(year, month)
        val lastOfMonth = ym.atEndOfMonth()
        val daysBack = (lastOfMonth.dayOfWeek.value - weekday.value + 7) % 7
        return lastOfMonth.minusDays(daysBack.toLong())
    }

    // ── All occurrences of a weekday in a month ───────────────────────────

    fun allWeekdaysInMonth(year: Int, month: Int, weekday: DayOfWeek): List<LocalDate> {
        val results = mutableListOf<LocalDate>()
        var occurrence = 1
        while (true) {
            val date = ordinalWeekday(year, month, weekday, occurrence) ?: break
            results.add(date)
            occurrence++
        }
        return results
    }

    // ── Mode B: All weekdays (Mon–Fri) in a month ─────────────────────────

    fun selectWeekdaysDates(
        year: Int,
        month: Int,
        weekdays: List<DayOfWeek> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
    ): List<LetterDate> {
        val ym = YearMonth.of(year, month)
        val results = mutableListOf<LetterDate>()
        var current = ym.atDay(1)
        val end = ym.atEndOfMonth()
        while (!current.isAfter(end)) {
            if (current.dayOfWeek in weekdays) {
                results.add(LetterDate(date = localDateToDate(current), label = formatDisplay(current)))
            }
            current = current.plusDays(1)
        }
        return results
    }

    // ── Mode C: Specific week + weekdays ──────────────────────────────────

    fun selectSpecificWeekDates(
        week: Int,
        year: Int,
        month: Int,
        weekdays: List<DayOfWeek> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
    ): List<LetterDate> {
        val ym = YearMonth.of(year, month)
        val firstOfMonth = ym.atDay(1)
        val firstMonday = with(firstOfMonth) {
            val daysUntil = (DayOfWeek.MONDAY.value - this.dayOfWeek.value + 7) % 7
            this.plusDays(daysUntil.toLong())
        }
        val weekStart = firstMonday.plusDays(((week - 1) * 7).toLong())
        val results = mutableListOf<LetterDate>()
        for (i in 0 until 7) {
            val current = weekStart.plusDays(i.toLong())
            if (current.monthValue == month && current.dayOfWeek in weekdays) {
                results.add(LetterDate(date = localDateToDate(current), label = formatShort(current)))
            }
        }
        return results
    }

    // ── Mode D: Multiple selected dates ───────────────────────────────────

    fun selectMultiDateDates(selectedDates: List<LocalDate>): List<LetterDate> {
        return selectedDates.map { LetterDate(date = localDateToDate(it), label = formatDisplay(it)) }
    }

    // ── Mode E: Whole month (alias for weekdays) ──────────────────────────

    fun selectWholeMonthDates(
        year: Int,
        month: Int,
        weekdays: List<DayOfWeek> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
    ): List<LetterDate> = selectWeekdaysDates(year, month, weekdays)

    // ── Formatting ────────────────────────────────────────────────────────

    fun formatDisplay(date: LocalDate): String = date.format(DISPLAY_FORMAT)

    fun formatIso(date: LocalDate): String = date.format(ISO_FORMAT)

    fun formatShort(date: LocalDate): String = date.format(SHORT_FORMAT)

    // ── Deduplication and sorting ─────────────────────────────────────────

    fun deduplicateAndSort(dates: List<LetterDate>): List<LetterDate> {
        val seen = mutableSetOf<String>()
        return dates.filter { seen.add(it.label) }.sortedBy { it.date }
    }

    // ── Calculations ──────────────────────────────────────────────────────

    fun calculateTotalLetters(recipientCount: Int, dateCount: Int): Int =
        recipientCount * dateCount

    fun calculateTotalEnvelopeLabels(recipientCount: Int, dateCount: Int): Int =
        recipientCount * dateCount

    // ── Conversion helpers ────────────────────────────────────────────────

    fun localDateToDate(ld: LocalDate): Date {
        return Date.from(ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
    }

    fun dateToLocalDate(date: Date): LocalDate {
        return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate()
    }
}
