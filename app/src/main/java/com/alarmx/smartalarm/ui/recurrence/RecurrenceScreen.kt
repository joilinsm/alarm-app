package com.alarmx.smartalarm.ui.recurrence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.domain.logic.MonthlyRecurrenceCalculator
import com.alarmx.smartalarm.domain.logic.NextAlarmCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.MissingDatePolicy
import com.alarmx.smartalarm.domain.model.MonthlyRecurrenceRule
import com.alarmx.smartalarm.domain.model.MonthlyRuleKind
import com.alarmx.smartalarm.domain.model.OccurrenceSpec
import com.alarmx.smartalarm.domain.model.RecurrenceMode
import com.alarmx.smartalarm.domain.model.RecurrenceSchedule
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.WeekOccurrence
import com.alarmx.smartalarm.ui.components.SectionCard
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurrenceScreen(
    alarm: Alarm,
    onSave: (RecurrenceSchedule) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember(alarm.id, alarm.updatedAt) { mutableStateOf(alarm.recurrence.sanitized()) }
    var editingRule by remember { mutableStateOf<MonthlyRecurrenceRule?>(null) }
    val previewAlarm = alarm.copy(recurrence = draft, enabled = true)
    val upcoming = remember(draft, alarm.hour, alarm.minute) {
        NextAlarmCalculator.upcoming(previewAlarm, ZonedDateTime.now(), limit = 15)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurrence") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                Text("Repeat type", style = MaterialTheme.typography.titleMedium)
                RecurrenceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draft.mode == mode,
                        onClick = {
                            draft = when (mode) {
                                RecurrenceMode.ONE_TIME -> RecurrenceSchedule.oneTime()
                                RecurrenceMode.WEEKLY -> RecurrenceSchedule(
                                    RecurrenceMode.WEEKLY,
                                    weeklyDays = if (draft.weeklyDays.isRepeating) draft.weeklyDays else RepeatDays.EVERY_DAY
                                )
                                RecurrenceMode.MONTHLY -> RecurrenceSchedule(
                                    RecurrenceMode.MONTHLY,
                                    monthlyRules = draft.monthlyRules.ifEmpty {
                                        listOf(
                                            MonthlyRecurrenceRule(
                                                kind = MonthlyRuleKind.EXACT_OCCURRENCES,
                                                occurrences = listOf(
                                                    OccurrenceSpec(WeekOccurrence.FIRST, DayOfWeek.MONDAY)
                                                ),
                                                anchorYear = YearMonth.now().year,
                                                anchorMonth = YearMonth.now().monthValue
                                            )
                                        )
                                    }
                                )
                            }
                        },
                        label = {
                            Text(
                                when (mode) {
                                    RecurrenceMode.ONE_TIME -> "One time"
                                    RecurrenceMode.WEEKLY -> "Weekly"
                                    RecurrenceMode.MONTHLY -> "Monthly"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                Text(draft.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (draft.mode == RecurrenceMode.WEEKLY) {
                SectionCard {
                    Text("Weekdays")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = draft.weeklyDays.contains(day),
                                onClick = {
                                    val next = draft.weeklyDays.toggle(day)
                                    draft = draft.copy(
                                        weeklyDays = next,
                                        mode = if (next.isOneTime) RecurrenceMode.ONE_TIME else RecurrenceMode.WEEKLY
                                    )
                                },
                                label = { Text(day.name.take(3)) }
                            )
                        }
                    }
                }
            }

            if (draft.mode == RecurrenceMode.MONTHLY) {
                SectionCard {
                    Text("Monthly rules", style = MaterialTheme.typography.titleMedium)
                    draft.monthlyRules.forEach { rule ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(rule.summary(), style = MaterialTheme.typography.bodyLarge)
                            MonthlyRecurrenceCalculator.previewIssues(rule).forEach {
                                Text(it.message, color = MaterialTheme.colorScheme.error)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editingRule = rule }) { Text("Edit") }
                                OutlinedButton(onClick = {
                                    draft = draft.copy(monthlyRules = draft.monthlyRules.filterNot { it.id == rule.id })
                                }) { Text("Delete") }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            editingRule = MonthlyRecurrenceRule(
                                kind = MonthlyRuleKind.EXACT_OCCURRENCES,
                                occurrences = listOf(OccurrenceSpec(WeekOccurrence.FIRST, DayOfWeek.MONDAY)),
                                anchorYear = YearMonth.now().year,
                                anchorMonth = YearMonth.now().monthValue
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add monthly rule") }
                }
            }

            editingRule?.let { rule ->
                MonthlyRuleEditor(
                    rule = rule,
                    onChange = { editingRule = it },
                    onDone = {
                        val updated = editingRule ?: return@MonthlyRuleEditor
                        val exists = draft.monthlyRules.any { it.id == updated.id }
                        draft = draft.copy(
                            mode = RecurrenceMode.MONTHLY,
                            monthlyRules = if (exists) {
                                draft.monthlyRules.map { if (it.id == updated.id) updated else it }
                            } else {
                                draft.monthlyRules + updated
                            }
                        )
                        editingRule = null
                    },
                    onCancel = { editingRule = null }
                )
            }

            SectionCard {
                Text("Next occurrences", style = MaterialTheme.typography.titleMedium)
                if (upcoming.isEmpty()) {
                    Text("No upcoming dates for this configuration.", color = MaterialTheme.colorScheme.error)
                } else {
                    upcoming.forEach { hit ->
                        Text(hit.format(formatter))
                    }
                }
            }

            Button(
                onClick = { onSave(draft.sanitized()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save recurrence") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlyRuleEditor(
    rule: MonthlyRecurrenceRule,
    onChange: (MonthlyRecurrenceRule) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    SectionCard {
        Text("Edit rule", style = MaterialTheme.typography.titleMedium)
        Text("Kind")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthlyRuleKind.entries.forEach { kind ->
                FilterChip(
                    selected = rule.kind == kind,
                    onClick = { onChange(rule.copy(kind = kind)) },
                    label = { Text(kindLabel(kind)) }
                )
            }
        }
        Text("Interval")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 6, 12).forEach { months ->
                FilterChip(
                    selected = rule.intervalMonths == months,
                    onClick = { onChange(rule.copy(intervalMonths = months)) },
                    label = { Text(if (months == 1) "Every month" else "Every $months mo") }
                )
            }
        }
        when (rule.kind) {
            MonthlyRuleKind.EXACT_OCCURRENCES -> {
                Text("Occurrences (multi-select)")
                OccurrencePicker(rule.occurrences) { onChange(rule.copy(occurrences = it)) }
            }
            MonthlyRuleKind.INCLUSIVE_RANGE -> {
                Text("Range start")
                SingleOccurrencePicker(rule.rangeStart) { onChange(rule.copy(rangeStart = it)) }
                Text("Range end")
                SingleOccurrencePicker(rule.rangeEnd) { onChange(rule.copy(rangeEnd = it)) }
                Text("Weekdays inside range")
                WeekdayPicker(rule.rangeWeekdays) { onChange(rule.copy(rangeWeekdays = it)) }
                MonthlyRecurrenceCalculator.previewIssues(rule).forEach {
                    Text(it.message, color = MaterialTheme.colorScheme.error)
                }
            }
            MonthlyRuleKind.EVERY_WEEKDAY -> {
                Text("Every selected weekday in eligible months")
                WeekdayPicker(rule.everyWeekdays) { onChange(rule.copy(everyWeekdays = it)) }
            }
            MonthlyRuleKind.SPECIFIC_DATES -> {
                Text("Calendar dates")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 10, 15, 28, 29, 30, 31).forEach { day ->
                        FilterChip(
                            selected = day in rule.specificDates,
                            onClick = {
                                val next = if (day in rule.specificDates) {
                                    rule.specificDates - day
                                } else {
                                    (rule.specificDates + day).sorted()
                                }
                                onChange(rule.copy(specificDates = next))
                            },
                            label = { Text(day.toString()) }
                        )
                    }
                }
                Text("Missing-date policy")
                MissingDatePolicy.entries.forEach { policy ->
                    FilterChip(
                        selected = rule.missingDatePolicy == policy,
                        onClick = { onChange(rule.copy(missingDatePolicy = policy)) },
                        label = {
                            Text(
                                when (policy) {
                                    MissingDatePolicy.SKIP -> "Skip missing dates"
                                    MissingDatePolicy.LAST_DAY -> "Use last day of month"
                                }
                            )
                        }
                    )
                }
            }
            MonthlyRuleKind.LAST_CALENDAR_DAY -> {
                Text("Fires on the final calendar day of each eligible month (leap-year aware).")
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel edit") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccurrencePicker(
    selected: List<OccurrenceSpec>,
    onChange: (List<OccurrenceSpec>) -> Unit
) {
    WeekOccurrence.entries.forEach { occ ->
        Text(occ.name.lowercase().replaceFirstChar(Char::uppercase))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val spec = OccurrenceSpec(occ, day)
                FilterChip(
                    selected = selected.any { it == spec },
                    onClick = {
                        onChange(
                            if (selected.any { it == spec }) selected.filterNot { it == spec }
                            else selected + spec
                        )
                    },
                    label = { Text(day.name.take(3)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleOccurrencePicker(
    selected: OccurrenceSpec?,
    onChange: (OccurrenceSpec) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeekOccurrence.entries.forEach { occ ->
            FilterChip(
                selected = selected?.occurrence == occ,
                onClick = {
                    onChange(OccurrenceSpec(occ, selected?.day ?: DayOfWeek.MONDAY))
                },
                label = { Text(occ.name.take(3)) }
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = selected?.day == day,
                onClick = {
                    onChange(OccurrenceSpec(selected?.occurrence ?: WeekOccurrence.FIRST, day))
                },
                label = { Text(day.name.take(3)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayPicker(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = {
                    onChange(if (day in selected) selected - day else selected + day)
                },
                label = { Text(day.name.take(3)) }
            )
        }
    }
}

private fun kindLabel(kind: MonthlyRuleKind): String = when (kind) {
    MonthlyRuleKind.EXACT_OCCURRENCES -> "Exact"
    MonthlyRuleKind.INCLUSIVE_RANGE -> "Range"
    MonthlyRuleKind.EVERY_WEEKDAY -> "Every weekday"
    MonthlyRuleKind.SPECIFIC_DATES -> "Dates"
    MonthlyRuleKind.LAST_CALENDAR_DAY -> "Last day"
}
