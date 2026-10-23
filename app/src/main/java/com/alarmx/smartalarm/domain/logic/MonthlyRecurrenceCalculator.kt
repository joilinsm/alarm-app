package com.alarmx.smartalarm.domain.logic

import com.alarmx.smartalarm.domain.model.MissingDatePolicy
import com.alarmx.smartalarm.domain.model.MonthlyRecurrenceRule
import com.alarmx.smartalarm.domain.model.MonthlyRuleKind
import com.alarmx.smartalarm.domain.model.OccurrenceSpec
import com.alarmx.smartalarm.domain.model.RecurrenceMode
import com.alarmx.smartalarm.domain.model.RecurrenceSchedule
import com.alarmx.smartalarm.domain.model.WeekOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic monthly recurrence engine using java.time calendar math.
 * Never advances by a fixed number of days.
 */
object MonthlyRecurrenceCalculator {
    data class PreviewIssue(val message: String)

    fun nextTrigger(
        schedule: RecurrenceSchedule,
        hour: Int,
        minute: Int,
        from: ZonedDateTime,
        searchMonths: Int = 36
    ): ZonedDateTime? {
        return upcoming(schedule, hour, minute, from, limit = 1, searchMonths = searchMonths).firstOrNull()
    }

    fun upcoming(
        schedule: RecurrenceSchedule,
        hour: Int,
        minute: Int,
        from: ZonedDateTime,
        limit: Int = 20,
        searchMonths: Int = 48
    ): List<ZonedDateTime> {
        return when (schedule.mode) {
            RecurrenceMode.ONE_TIME -> {
                val next = NextAlarmCalculator.firstFutureClockTime(hour, minute, from)
                listOf(next).take(limit)
            }
            RecurrenceMode.WEEKLY -> {
                buildList {
                    var cursor = from
                    repeat(limit * 2) {
                        val hit = NextAlarmCalculator.nextTrigger(hour, minute, schedule.weeklyDays, cursor)
                            ?: return@buildList
                        add(hit)
                        cursor = hit
                        if (size >= limit) return@buildList
                    }
                }.take(limit)
            }
            RecurrenceMode.MONTHLY -> upcomingMonthly(schedule, hour, minute, from, limit, searchMonths)
        }
    }

    fun previewIssues(rule: MonthlyRecurrenceRule, sampleMonth: YearMonth = YearMonth.now()): List<PreviewIssue> {
        val issues = mutableListOf<PreviewIssue>()
        if (rule.kind == MonthlyRuleKind.INCLUSIVE_RANGE) {
            val start = rule.rangeStart
            val end = rule.rangeEnd
            if (start == null || end == null) {
                issues += PreviewIssue("Inclusive range needs both a start and an end occurrence.")
            } else {
                val startDate = weekdayOccurrence(sampleMonth, start)
                val endDate = weekdayOccurrence(sampleMonth, end)
                if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                    issues += PreviewIssue(
                        "Invalid range in ${sampleMonth}: ${start.label()} (${startDate}) is after " +
                            "${end.label()} (${endDate}). That month contributes no dates " +
                            "(ranges never wrap into the next month)."
                    )
                }
            }
            if (rule.rangeWeekdays.isEmpty()) {
                issues += PreviewIssue("Inclusive range needs at least one weekday to include.")
            }
        }
        if (rule.kind == MonthlyRuleKind.EXACT_OCCURRENCES && rule.occurrences.isEmpty()) {
            issues += PreviewIssue("Exact selection needs at least one occurrence/weekday.")
        }
        if (rule.kind == MonthlyRuleKind.EVERY_WEEKDAY && rule.everyWeekdays.isEmpty()) {
            issues += PreviewIssue("Select at least one weekday.")
        }
        if (rule.kind == MonthlyRuleKind.SPECIFIC_DATES && rule.specificDates.isEmpty()) {
            issues += PreviewIssue("Select at least one calendar date.")
        }
        return issues
    }

    fun datesInMonth(rule: MonthlyRecurrenceRule, month: YearMonth): Set<LocalDate> {
        if (!rule.enabled) return emptySet()
        if (!monthEligible(rule, month)) return emptySet()
        val dates = when (rule.kind) {
            MonthlyRuleKind.EXACT_OCCURRENCES -> rule.occurrences.mapNotNull { weekdayOccurrence(month, it) }.toSet()
            MonthlyRuleKind.INCLUSIVE_RANGE -> inclusiveRangeDates(rule, month)
            MonthlyRuleKind.EVERY_WEEKDAY -> everyWeekdayDates(month, rule.everyWeekdays)
            MonthlyRuleKind.SPECIFIC_DATES -> specificDates(month, rule.specificDates, rule.missingDatePolicy)
            MonthlyRuleKind.LAST_CALENDAR_DAY -> setOf(month.atEndOfMonth())
        }
        return dates.filter { dateAllowed(rule, it) }.toSet()
    }

    fun weekdayOccurrence(month: YearMonth, spec: OccurrenceSpec): LocalDate? {
        val candidates = (1..month.lengthOfMonth()).map { month.atDay(it) }
            .filter { it.dayOfWeek == spec.day }
        if (candidates.isEmpty()) return null
        return when (spec.occurrence) {
            WeekOccurrence.FIRST -> candidates.getOrNull(0)
            WeekOccurrence.SECOND -> candidates.getOrNull(1)
            WeekOccurrence.THIRD -> candidates.getOrNull(2)
            WeekOccurrence.FOURTH -> candidates.getOrNull(3)
            WeekOccurrence.FIFTH -> candidates.getOrNull(4)
            WeekOccurrence.LAST -> candidates.lastOrNull()
        }
    }

    private fun upcomingMonthly(
        schedule: RecurrenceSchedule,
        hour: Int,
        minute: Int,
        from: ZonedDateTime,
        limit: Int,
        searchMonths: Int
    ): List<ZonedDateTime> {
        val zone = from.zone
        val enabled = schedule.monthlyRules.filter { it.enabled }
        if (enabled.isEmpty()) return emptyList()
        val results = sortedSetOf<LocalDate>()
        var month = YearMonth.from(from.toLocalDate())
        repeat(searchMonths) {
            enabled.forEach { rule ->
                results += datesInMonth(rule, month)
            }
            month = month.plusMonths(1)
        }
        return results
            .mapNotNull { date -> atAlarmTime(date, hour, minute, zone) }
            .filter { it.isAfter(from) }
            .take(limit)
    }

    private fun inclusiveRangeDates(rule: MonthlyRecurrenceRule, month: YearMonth): Set<LocalDate> {
        val startSpec = rule.rangeStart ?: return emptySet()
        val endSpec = rule.rangeEnd ?: return emptySet()
        val start = weekdayOccurrence(month, startSpec) ?: return emptySet()
        val end = weekdayOccurrence(month, endSpec) ?: return emptySet()
        if (end.isBefore(start)) {
            // Deterministic invalid-range policy: contribute nothing for this month.
            return emptySet()
        }
        if (rule.rangeWeekdays.isEmpty()) return emptySet()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .filter { it.dayOfWeek in rule.rangeWeekdays }
            .toSet()
    }

    private fun everyWeekdayDates(month: YearMonth, days: Set<DayOfWeek>): Set<LocalDate> {
        if (days.isEmpty()) return emptySet()
        return (1..month.lengthOfMonth()).map { month.atDay(it) }
            .filter { it.dayOfWeek in days }
            .toSet()
    }

    private fun specificDates(
        month: YearMonth,
        days: List<Int>,
        policy: MissingDatePolicy
    ): Set<LocalDate> {
        val length = month.lengthOfMonth()
        return days.mapNotNull { day ->
            when {
                day in 1..length -> month.atDay(day)
                policy == MissingDatePolicy.LAST_DAY -> month.atEndOfMonth()
                else -> null
            }
        }.toSet()
    }

    private fun monthEligible(rule: MonthlyRecurrenceRule, month: YearMonth): Boolean {
        val interval = rule.intervalMonths.coerceAtLeast(1)
        val anchor = when {
            rule.anchorYear != null && rule.anchorMonth != null ->
                YearMonth.of(rule.anchorYear, rule.anchorMonth.coerceIn(1, 12))
            rule.startDate != null -> YearMonth.from(rule.startDate)
            else -> month
        }
        val monthsBetween = (month.year - anchor.year) * 12 + (month.monthValue - anchor.monthValue)
        if (monthsBetween < 0) return false
        if (monthsBetween % interval != 0) return false
        return true
    }

    private fun dateAllowed(rule: MonthlyRecurrenceRule, date: LocalDate): Boolean {
        rule.startDate?.let { if (date.isBefore(it)) return false }
        rule.endDate?.let { if (date.isAfter(it)) return false }
        return true
    }

    private fun atAlarmTime(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime? {
        return try {
            date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone)
        } catch (_: Exception) {
            // DST gap: skip forward one hour.
            date.atTime((hour + 1).coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone)
        }
    }
}
