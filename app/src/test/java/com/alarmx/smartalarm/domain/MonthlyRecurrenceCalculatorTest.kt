package com.alarmx.smartalarm.domain

import com.alarmx.smartalarm.domain.logic.MonthlyRecurrenceCalculator
import com.alarmx.smartalarm.domain.logic.RecurrenceCodec
import com.alarmx.smartalarm.domain.model.MissingDatePolicy
import com.alarmx.smartalarm.domain.model.MonthlyRecurrenceRule
import com.alarmx.smartalarm.domain.model.MonthlyRuleKind
import com.alarmx.smartalarm.domain.model.OccurrenceSpec
import com.alarmx.smartalarm.domain.model.RecurrenceMode
import com.alarmx.smartalarm.domain.model.RecurrenceSchedule
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.WeekOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class MonthlyRecurrenceCalculatorTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun firstMondayOfMarch2026() {
        val month = YearMonth.of(2026, 3)
        val date = MonthlyRecurrenceCalculator.weekdayOccurrence(
            month,
            OccurrenceSpec(WeekOccurrence.FIRST, DayOfWeek.MONDAY)
        )
        assertEquals(LocalDate.of(2026, 3, 2), date)
    }

    @Test
    fun lastFridayIndependentOfFourth() {
        val month = YearMonth.of(2026, 3)
        val fourth = MonthlyRecurrenceCalculator.weekdayOccurrence(
            month, OccurrenceSpec(WeekOccurrence.FOURTH, DayOfWeek.FRIDAY)
        )
        val last = MonthlyRecurrenceCalculator.weekdayOccurrence(
            month, OccurrenceSpec(WeekOccurrence.LAST, DayOfWeek.FRIDAY)
        )
        assertEquals(LocalDate.of(2026, 3, 27), fourth)
        assertEquals(LocalDate.of(2026, 3, 27), last)
    }

    @Test
    fun fifthFridayMissingInShortMonth() {
        val feb = YearMonth.of(2026, 2)
        val fifth = MonthlyRecurrenceCalculator.weekdayOccurrence(
            feb, OccurrenceSpec(WeekOccurrence.FIFTH, DayOfWeek.FRIDAY)
        )
        assertNull(fifth)
    }

    @Test
    fun leapFebruaryTwentyNinth() {
        val leap = YearMonth.of(2024, 2)
        assertEquals(29, leap.lengthOfMonth())
        val rule = MonthlyRecurrenceRule(
            kind = MonthlyRuleKind.SPECIFIC_DATES,
            specificDates = listOf(29),
            missingDatePolicy = MissingDatePolicy.SKIP,
            anchorYear = 2024,
            anchorMonth = 2
        )
        assertTrue(LocalDate.of(2024, 2, 29) in MonthlyRecurrenceCalculator.datesInMonth(rule, leap))
        val nonLeap = YearMonth.of(2025, 2)
        assertTrue(MonthlyRecurrenceCalculator.datesInMonth(rule.copy(anchorYear = 2025), nonLeap).isEmpty())
        val lastDay = rule.copy(missingDatePolicy = MissingDatePolicy.LAST_DAY, anchorYear = 2025)
        assertEquals(
            setOf(LocalDate.of(2025, 2, 28)),
            MonthlyRecurrenceCalculator.datesInMonth(lastDay, nonLeap)
        )
    }

    @Test
    fun inclusiveRangeFirstMondayThroughFourthSaturday() {
        val rule = MonthlyRecurrenceRule(
            kind = MonthlyRuleKind.INCLUSIVE_RANGE,
            rangeStart = OccurrenceSpec(WeekOccurrence.FIRST, DayOfWeek.MONDAY),
            rangeEnd = OccurrenceSpec(WeekOccurrence.FOURTH, DayOfWeek.SATURDAY),
            rangeWeekdays = setOf(
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            ),
            anchorYear = 2026,
            anchorMonth = 3
        )
        val dates = MonthlyRecurrenceCalculator.datesInMonth(rule, YearMonth.of(2026, 3))
        assertTrue(LocalDate.of(2026, 3, 2) in dates) // first Monday
        assertTrue(LocalDate.of(2026, 3, 28) in dates) // fourth Saturday
        assertTrue(LocalDate.of(2026, 3, 4) in dates) // Wednesday inside
        assertFalse(LocalDate.of(2026, 3, 1) in dates) // before start
        assertFalse(LocalDate.of(2026, 3, 29) in dates) // after end (Sunday)
    }

    @Test
    fun invalidRangeDoesNotWrapMonths() {
        val rule = MonthlyRecurrenceRule(
            kind = MonthlyRuleKind.INCLUSIVE_RANGE,
            rangeStart = OccurrenceSpec(WeekOccurrence.FOURTH, DayOfWeek.SATURDAY),
            rangeEnd = OccurrenceSpec(WeekOccurrence.FIRST, DayOfWeek.MONDAY),
            rangeWeekdays = setOf(DayOfWeek.MONDAY),
            anchorYear = 2026,
            anchorMonth = 3
        )
        assertTrue(MonthlyRecurrenceCalculator.datesInMonth(rule, YearMonth.of(2026, 3)).isEmpty())
        assertTrue(MonthlyRecurrenceCalculator.previewIssues(rule, YearMonth.of(2026, 3)).isNotEmpty())
    }

    @Test
    fun everyWeekdayAndLastCalendarDay() {
        val every = MonthlyRecurrenceRule(
            kind = MonthlyRuleKind.EVERY_WEEKDAY,
            everyWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            anchorYear = 2026,
            anchorMonth = 3
        )
        val dates = MonthlyRecurrenceCalculator.datesInMonth(every, YearMonth.of(2026, 3))
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.WEDNESDAY })
        val lastDay = MonthlyRecurrenceRule(
            kind = MonthlyRuleKind.LAST_CALENDAR_DAY,
            anchorYear = 2026,
            anchorMonth = 3
        )
        assertEquals(
            setOf(LocalDate.of(2026, 3, 31)),
            MonthlyRecurrenceCalculator.datesInMonth(lastDay, YearMonth.of(2026, 3))
        )
    }

    @Test
    fun intervalAnchoringEveryTwoMonths() {
        val rule = MonthlyRecurrenceRule(
            kind = MonthlyRuleKind.LAST_CALENDAR_DAY,
            intervalMonths = 2,
            anchorYear = 2026,
            anchorMonth = 1
        )
        assertTrue(MonthlyRecurrenceCalculator.datesInMonth(rule, YearMonth.of(2026, 1)).isNotEmpty())
        assertTrue(MonthlyRecurrenceCalculator.datesInMonth(rule, YearMonth.of(2026, 2)).isEmpty())
        assertTrue(MonthlyRecurrenceCalculator.datesInMonth(rule, YearMonth.of(2026, 3)).isNotEmpty())
    }

    @Test
    fun overlappingRulesDeduplicate() {
        val schedule = RecurrenceSchedule(
            mode = RecurrenceMode.MONTHLY,
            monthlyRules = listOf(
                MonthlyRecurrenceRule(
                    kind = MonthlyRuleKind.SPECIFIC_DATES,
                    specificDates = listOf(15),
                    anchorYear = 2026,
                    anchorMonth = 3
                ),
                MonthlyRecurrenceRule(
                    kind = MonthlyRuleKind.SPECIFIC_DATES,
                    specificDates = listOf(15, 20),
                    anchorYear = 2026,
                    anchorMonth = 3
                )
            )
        )
        val from = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone)
        val upcoming = MonthlyRecurrenceCalculator.upcoming(schedule, 7, 0, from, limit = 5)
        val midMarch = upcoming.filter { it.monthValue == 3 && it.dayOfMonth == 15 }
        assertEquals(1, midMarch.size)
    }

    @Test
    fun codecRoundTripPreservesMonthlyRules() {
        val schedule = RecurrenceSchedule(
            mode = RecurrenceMode.MONTHLY,
            monthlyRules = listOf(
                MonthlyRecurrenceRule(
                    kind = MonthlyRuleKind.EXACT_OCCURRENCES,
                    occurrences = listOf(
                        OccurrenceSpec(WeekOccurrence.FIRST, DayOfWeek.MONDAY),
                        OccurrenceSpec(WeekOccurrence.FOURTH, DayOfWeek.SATURDAY)
                    ),
                    intervalMonths = 3,
                    anchorYear = 2026,
                    anchorMonth = 3,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2027, 3, 1)
                )
            )
        )
        val restored = RecurrenceCodec.decode(RecurrenceCodec.encode(schedule), 0)
        assertEquals(RecurrenceMode.MONTHLY, restored.mode)
        assertEquals(1, restored.monthlyRules.size)
        assertEquals(2, restored.monthlyRules[0].occurrences.size)
        assertEquals(3, restored.monthlyRules[0].intervalMonths)
    }

    @Test
    fun legacyWeeklyMaskDecodesWhenJsonBlank() {
        val days = RepeatDays.of(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val schedule = RecurrenceCodec.decode(null, days.mask)
        assertEquals(RecurrenceMode.WEEKLY, schedule.mode)
        assertEquals(days.mask, schedule.weeklyDays.mask)
    }
}
