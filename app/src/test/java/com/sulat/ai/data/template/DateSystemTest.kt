package com.sulat.ai.data.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DateSystemTest {

    // ── Specific dates ────────────────────────────────────────────────────

    @Test
    fun testSpecificDate_valid() {
        val result = DateSystem.specificDate(2026, 9, 13)
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 9, 13), result)
    }

    @Test
    fun testSpecificDate_leapYear() {
        val result = DateSystem.specificDate(2024, 2, 29)
        assertNotNull(result)
        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    @Test
    fun testSpecificDate_invalidDay() {
        assertNull(DateSystem.specificDate(2026, 2, 30))
    }

    @Test
    fun testSpecificDate_invalidMonth() {
        assertNull(DateSystem.specificDate(2026, 13, 1))
    }

    @Test
    fun testSpecificDate_dayZero() {
        assertNull(DateSystem.specificDate(2026, 1, 0))
    }

    @Test
    fun testSpecificDate_monthZero() {
        assertNull(DateSystem.specificDate(2026, 0, 1))
    }

    @Test
    fun testSpecificDate_feb30Rejected() {
        assertNull(DateSystem.specificDate(2026, 2, 30))
    }

    // ── Manual date parsing ───────────────────────────────────────────────

    @Test
    fun testParseDateInput_valid() {
        val results = DateSystem.parseDateInput("2026-09-05")
        assertEquals(1, results.size)
        assertEquals(LocalDate.of(2026, 9, 5), DateSystem.dateToLocalDate(results[0].date))
    }

    @Test
    fun testParseDateInput_invalidFormat() {
        val results = DateSystem.parseDateInput("09/05/2026")
        assertEquals(0, results.size)
    }

    @Test
    fun testParseDateInput_impossibleDate() {
        val results = DateSystem.parseDateInput("2026-02-30")
        assertEquals(0, results.size)
    }

    @Test
    fun testParseDateInput_multiple() {
        val results = DateSystem.parseDateInput("2026-09-05, 2026-10-15")
        assertEquals(2, results.size)
        assertEquals(LocalDate.of(2026, 9, 5), DateSystem.dateToLocalDate(results[0].date))
        assertEquals(LocalDate.of(2026, 10, 15), DateSystem.dateToLocalDate(results[1].date))
    }

    @Test
    fun testParseSingleDate_valid() {
        assertEquals(LocalDate.of(2026, 9, 5), DateSystem.parseSingleDate("2026-09-05"))
    }

    @Test
    fun testParseSingleDate_invalid() {
        assertNull(DateSystem.parseSingleDate("not-a-date"))
    }

    // ── Ordinal weekday — September 2026 (Sep 1 = Tue) ────────────────────

    @Test
    fun testOrdinal_firstSunday_sept2026() {
        // Sep 6 = first Sunday
        assertEquals(LocalDate.of(2026, 9, 6),
            DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 1))
    }

    @Test
    fun testOrdinal_secondSunday_sept2026() {
        // Sep 13 = second Sunday
        assertEquals(LocalDate.of(2026, 9, 13),
            DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 2))
    }

    @Test
    fun testOrdinal_thirdSunday_sept2026() {
        // Sep 20 = third Sunday
        assertEquals(LocalDate.of(2026, 9, 20),
            DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 3))
    }

    @Test
    fun testOrdinal_fourthSunday_sept2026() {
        // Sep 27 = fourth Sunday
        assertEquals(LocalDate.of(2026, 9, 27),
            DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 4))
    }

    @Test
    fun testOrdinal_fifthSunday_sept2026() {
        // Sep has only 4 Sundays (6, 13, 20, 27) → 5th does not exist
        assertNull(DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 5))
    }

    @Test
    fun testOrdinal_fifthSunday_whenExists() {
        // October 2026: Oct 1=Thu → Sundays: 4, 11, 18, 25 → only 4
        // July 2026: Jul 1=Wed → Sundays: 5, 12, 19, 26 → only 4
        // March 2026: Mar 1=Sun → Sundays: 1, 8, 15, 22, 29 → 5 exists!
        assertEquals(LocalDate.of(2026, 3, 29),
            DateSystem.ordinalWeekday(2026, 3, DayOfWeek.SUNDAY, 5))
    }

    @Test
    fun testOrdinal_secondSaturday_sept2026() {
        // Sep 5 = first Saturday, Sep 12 = second Saturday
        assertEquals(LocalDate.of(2026, 9, 12),
            DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SATURDAY, 2))
    }

    @Test
    fun testOrdinal_invalidOccurrence_zero() {
        assertNull(DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 0))
    }

    @Test
    fun testOrdinal_invalidOccurrence_six() {
        assertNull(DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 6))
    }

    // ── Last weekday ──────────────────────────────────────────────────────

    @Test
    fun testLastSunday_sept2026() {
        // Sep has Sundays: 6, 13, 20, 27 → last = Sep 27
        assertEquals(LocalDate.of(2026, 9, 27),
            DateSystem.lastWeekday(2026, 9, DayOfWeek.SUNDAY))
    }

    @Test
    fun testLastFriday_sept2026() {
        // Sep has Fridays: 4, 11, 18, 25 → last = Sep 25
        assertEquals(LocalDate.of(2026, 9, 25),
            DateSystem.lastWeekday(2026, 9, DayOfWeek.FRIDAY))
    }

    @Test
    fun testLastMonday_sept2026() {
        // Sep has Mondays: 7, 14, 21, 28 → last = Sep 28
        assertEquals(LocalDate.of(2026, 9, 28),
            DateSystem.lastWeekday(2026, 9, DayOfWeek.MONDAY))
    }

    // ── Boundary cases ────────────────────────────────────────────────────

    @Test
    fun testOrdinal_january() {
        // Jan 2026: Jan 1=Thu → Fridays: 2, 9, 16, 23, 30
        assertEquals(LocalDate.of(2026, 1, 2),
            DateSystem.ordinalWeekday(2026, 1, DayOfWeek.FRIDAY, 1))
        assertEquals(LocalDate.of(2026, 1, 30),
            DateSystem.ordinalWeekday(2026, 1, DayOfWeek.FRIDAY, 5))
    }

    @Test
    fun testOrdinal_december() {
        // Dec 2026: Dec 1=Tue → Fridays: 4, 11, 18, 25
        assertEquals(LocalDate.of(2026, 12, 4),
            DateSystem.ordinalWeekday(2026, 12, DayOfWeek.FRIDAY, 1))
        assertEquals(LocalDate.of(2026, 12, 25),
            DateSystem.ordinalWeekday(2026, 12, DayOfWeek.FRIDAY, 4))
    }

    @Test
    fun testLastWeekday_decemberToJanuary() {
        // Dec 2026 last Friday = Dec 25
        assertEquals(LocalDate.of(2026, 12, 25),
            DateSystem.lastWeekday(2026, 12, DayOfWeek.FRIDAY))
    }

    @Test
    fun testOrdinal_leapYearFebruary() {
        // Feb 2024 (leap year): Feb 1=Thu → Sundays: 4, 11, 18, 25
        assertEquals(LocalDate.of(2024, 2, 4),
            DateSystem.ordinalWeekday(2024, 2, DayOfWeek.SUNDAY, 1))
        assertEquals(LocalDate.of(2024, 2, 25),
            DateSystem.ordinalWeekday(2024, 2, DayOfWeek.SUNDAY, 4))
        // 5th Sunday does not exist in Feb 2024
        assertNull(DateSystem.ordinalWeekday(2024, 2, DayOfWeek.SUNDAY, 5))
    }

    // ── Determinism ───────────────────────────────────────────────────────

    @Test
    fun testDeterminism_sameInputSameOutput() {
        val r1 = DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 2)
        val r2 = DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 2)
        assertEquals(r1, r2)
    }

    // ── Locale independence ───────────────────────────────────────────────

    @Test
    fun testLocaleIndependence() {
        val result = DateSystem.ordinalWeekday(2026, 9, DayOfWeek.SUNDAY, 2)
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 9, 13), result)
    }

    // ── selectWeekdaysDates ───────────────────────────────────────────────

    @Test
    fun testSelectWeekdaysDates_sept2026() {
        // Sep 2026 weekdays (Mon-Fri): 1,2,3,4,7,8,9,10,11,14,15,16,17,18,21,22,23,24,25,28,29,30
        val dates = DateSystem.selectWeekdaysDates(2026, 9)
        assertEquals(22, dates.size)
    }

    @Test
    fun testSelectWeekdaysDates_customWeekdays() {
        // Only Sundays and Mondays in Sep 2026
        val dates = DateSystem.selectWeekdaysDates(2026, 9, listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
        // Sundays: 6,13,20,27 (4) + Mondays: 7,14,21,28 (4) = 8
        assertEquals(8, dates.size)
    }

    // ── selectSpecificWeekDates ───────────────────────────────────────────

    @Test
    fun testSelectSpecificWeekDates_firstWeek_sept2026() {
        // First Monday of Sep 2026 = Sep 7
        // Week 1 = Sep 7 (Mon) through Sep 13 (Sun), weekdays only: Sep 7,8,9,10,11
        val dates = DateSystem.selectSpecificWeekDates(1, 2026, 9)
        assertEquals(5, dates.size) // Mon, Tue, Wed, Thu, Fri of that week
    }

    // ── Utility functions ─────────────────────────────────────────────────

    @Test
    fun testFormatDisplay() {
        val date = LocalDate.of(2026, 9, 13)
        assertEquals("September 13, 2026", DateSystem.formatDisplay(date))
    }

    @Test
    fun testFormatIso() {
        val date = LocalDate.of(2026, 9, 13)
        assertEquals("2026-09-13", DateSystem.formatIso(date))
    }

    @Test
    fun testCalculateTotalLetters() {
        assertEquals(10, DateSystem.calculateTotalLetters(2, 5))
    }

    @Test
    fun testCalculateTotalEnvelopeLabels() {
        assertEquals(6, DateSystem.calculateTotalEnvelopeLabels(2, 3))
    }

    // ── Conversion helpers ────────────────────────────────────────────────

    @Test
    fun testLocalDateToDate_roundTrip() {
        val ld = LocalDate.of(2026, 9, 13)
        val date = DateSystem.localDateToDate(ld)
        val back = DateSystem.dateToLocalDate(date)
        assertEquals(ld, back)
    }
}
