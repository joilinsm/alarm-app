package com.alarmx.smartalarm.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class RecurrenceMode {
    ONE_TIME,
    WEEKLY,
    MONTHLY
}

enum class WeekOccurrence {
    FIRST,
    SECOND,
    THIRD,
    FOURTH,
    FIFTH,
    LAST
}

enum class MissingDatePolicy {
    SKIP,
    LAST_DAY
}

enum class MonthlyRuleKind {
    EXACT_OCCURRENCES,
    INCLUSIVE_RANGE,
    EVERY_WEEKDAY,
    SPECIFIC_DATES,
    LAST_CALENDAR_DAY
}

data class OccurrenceSpec(
    val occurrence: WeekOccurrence,
    val day: DayOfWeek
) {
    fun label(): String {
        val ord = occurrence.name.lowercase().replaceFirstChar(Char::uppercase)
        val wd = day.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
        return "$ord $wd"
    }
}

data class MonthlyRecurrenceRule(
    val id: String = UUID.randomUUID().toString(),
    val kind: MonthlyRuleKind,
    val enabled: Boolean = true,
    val occurrences: List<OccurrenceSpec> = emptyList(),
    val rangeStart: OccurrenceSpec? = null,
    val rangeEnd: OccurrenceSpec? = null,
    val rangeWeekdays: Set<DayOfWeek> = emptySet(),
    val everyWeekdays: Set<DayOfWeek> = emptySet(),
    val specificDates: List<Int> = emptyList(),
    val missingDatePolicy: MissingDatePolicy = MissingDatePolicy.SKIP,
    val intervalMonths: Int = 1,
    val anchorYear: Int? = null,
    val anchorMonth: Int? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    fun sanitized(): MonthlyRecurrenceRule {
        val interval = when (intervalMonths) {
            1, 2, 3, 6, 12 -> intervalMonths
            else -> 1
        }
        return copy(
            intervalMonths = interval,
            specificDates = specificDates.map { it.coerceIn(1, 31) }.distinct().sorted(),
            everyWeekdays = everyWeekdays,
            rangeWeekdays = rangeWeekdays
        )
    }

    fun summary(): String {
        val intervalLabel = when (intervalMonths) {
            1 -> "every month"
            2 -> "every 2 months"
            3 -> "every 3 months"
            6 -> "every 6 months"
            12 -> "every 12 months"
            else -> "every $intervalMonths months"
        }
        val body = when (kind) {
            MonthlyRuleKind.EXACT_OCCURRENCES ->
                occurrences.joinToString(", ") { it.label() }.ifBlank { "exact weekdays" }
            MonthlyRuleKind.INCLUSIVE_RANGE -> {
                val start = rangeStart?.label() ?: "?"
                val end = rangeEnd?.label() ?: "?"
                val days = rangeWeekdays.joinToString("/") { it.name.take(3) }
                "$start → $end ($days)"
            }
            MonthlyRuleKind.EVERY_WEEKDAY ->
                "every " + everyWeekdays.joinToString("/") { it.name.take(3) }
            MonthlyRuleKind.SPECIFIC_DATES ->
                "dates " + specificDates.joinToString(",") + " (${missingDatePolicy.name.lowercase()})"
            MonthlyRuleKind.LAST_CALENDAR_DAY -> "last calendar day"
        }
        return "$body · $intervalLabel"
    }
}

data class RecurrenceSchedule(
    val mode: RecurrenceMode = RecurrenceMode.ONE_TIME,
    val weeklyDays: RepeatDays = RepeatDays.NONE,
    val monthlyRules: List<MonthlyRecurrenceRule> = emptyList()
) {
    fun sanitized(): RecurrenceSchedule {
        return copy(
            weeklyDays = weeklyDays,
            monthlyRules = monthlyRules.map { it.sanitized() }.filter { it.enabled || true }
        )
    }

    fun isRepeating(): Boolean = when (mode) {
        RecurrenceMode.ONE_TIME -> false
        RecurrenceMode.WEEKLY -> weeklyDays.isRepeating
        RecurrenceMode.MONTHLY -> monthlyRules.any { it.enabled }
    }

    fun summary(): String = when (mode) {
        RecurrenceMode.ONE_TIME -> "One time"
        RecurrenceMode.WEEKLY -> weeklyDays.summary()
        RecurrenceMode.MONTHLY -> {
            val rules = monthlyRules.filter { it.enabled }
            if (rules.isEmpty()) "Monthly (no rules)"
            else rules.joinToString(" · ") { it.summary() }
        }
    }

    companion object {
        fun oneTime(): RecurrenceSchedule = RecurrenceSchedule(RecurrenceMode.ONE_TIME)

        fun fromLegacyRepeatDays(days: RepeatDays): RecurrenceSchedule {
            return if (days.isOneTime) {
                oneTime()
            } else {
                RecurrenceSchedule(RecurrenceMode.WEEKLY, weeklyDays = days)
            }
        }
    }
}

enum class VideoAudioMode {
    SEPARATE_RINGTONE,
    VIDEO_AUDIO,
    VIDEO_AND_RINGTONE
}
