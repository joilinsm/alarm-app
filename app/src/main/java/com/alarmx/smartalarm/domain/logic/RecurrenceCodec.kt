package com.alarmx.smartalarm.domain.logic

import com.alarmx.smartalarm.domain.model.MissingDatePolicy
import com.alarmx.smartalarm.domain.model.MonthlyRecurrenceRule
import com.alarmx.smartalarm.domain.model.MonthlyRuleKind
import com.alarmx.smartalarm.domain.model.OccurrenceSpec
import com.alarmx.smartalarm.domain.model.RecurrenceMode
import com.alarmx.smartalarm.domain.model.RecurrenceSchedule
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.WeekOccurrence
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

object RecurrenceCodec {
    fun encode(schedule: RecurrenceSchedule): String {
        val root = JSONObject()
        root.put("mode", schedule.mode.name)
        root.put("weeklyMask", schedule.weeklyDays.mask)
        val rules = JSONArray()
        schedule.monthlyRules.forEach { rule ->
            rules.put(encodeRule(rule))
        }
        root.put("monthlyRules", rules)
        return root.toString()
    }

    fun decode(raw: String?, legacyMask: Int): RecurrenceSchedule {
        if (raw.isNullOrBlank()) {
            return RecurrenceSchedule.fromLegacyRepeatDays(RepeatDays(legacyMask))
        }
        return try {
            val root = JSONObject(raw)
            val mode = runCatching {
                RecurrenceMode.valueOf(root.optString("mode", RecurrenceMode.ONE_TIME.name))
            }.getOrDefault(RecurrenceMode.ONE_TIME)
            val weekly = RepeatDays(root.optInt("weeklyMask", legacyMask))
            val rulesJson = root.optJSONArray("monthlyRules") ?: JSONArray()
            val rules = buildList {
                for (i in 0 until rulesJson.length()) {
                    decodeRule(rulesJson.getJSONObject(i))?.let { add(it) }
                }
            }
            RecurrenceSchedule(mode, weekly, rules).sanitized()
        } catch (_: Exception) {
            RecurrenceSchedule.fromLegacyRepeatDays(RepeatDays(legacyMask))
        }
    }

    private fun encodeRule(rule: MonthlyRecurrenceRule): JSONObject {
        val obj = JSONObject()
        obj.put("id", rule.id)
        obj.put("kind", rule.kind.name)
        obj.put("enabled", rule.enabled)
        obj.put("intervalMonths", rule.intervalMonths)
        obj.put("missingDatePolicy", rule.missingDatePolicy.name)
        rule.anchorYear?.let { obj.put("anchorYear", it) }
        rule.anchorMonth?.let { obj.put("anchorMonth", it) }
        rule.startDate?.let { obj.put("startDate", it.toString()) }
        rule.endDate?.let { obj.put("endDate", it.toString()) }
        obj.put("specificDates", JSONArray(rule.specificDates))
        obj.put("everyWeekdays", JSONArray(rule.everyWeekdays.map { it.name }))
        obj.put("rangeWeekdays", JSONArray(rule.rangeWeekdays.map { it.name }))
        val occ = JSONArray()
        rule.occurrences.forEach { occ.put(encodeSpec(it)) }
        obj.put("occurrences", occ)
        rule.rangeStart?.let { obj.put("rangeStart", encodeSpec(it)) }
        rule.rangeEnd?.let { obj.put("rangeEnd", encodeSpec(it)) }
        return obj
    }

    private fun decodeRule(obj: JSONObject): MonthlyRecurrenceRule? {
        return try {
            val kind = MonthlyRuleKind.valueOf(obj.getString("kind"))
            MonthlyRecurrenceRule(
                id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                kind = kind,
                enabled = obj.optBoolean("enabled", true),
                occurrences = decodeSpecs(obj.optJSONArray("occurrences")),
                rangeStart = obj.optJSONObject("rangeStart")?.let { decodeSpec(it) },
                rangeEnd = obj.optJSONObject("rangeEnd")?.let { decodeSpec(it) },
                rangeWeekdays = decodeDays(obj.optJSONArray("rangeWeekdays")),
                everyWeekdays = decodeDays(obj.optJSONArray("everyWeekdays")),
                specificDates = decodeInts(obj.optJSONArray("specificDates")),
                missingDatePolicy = runCatching {
                    MissingDatePolicy.valueOf(obj.optString("missingDatePolicy", MissingDatePolicy.SKIP.name))
                }.getOrDefault(MissingDatePolicy.SKIP),
                intervalMonths = obj.optInt("intervalMonths", 1),
                anchorYear = if (obj.has("anchorYear")) obj.getInt("anchorYear") else null,
                anchorMonth = if (obj.has("anchorMonth")) obj.getInt("anchorMonth") else null,
                startDate = obj.optString("startDate").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                endDate = obj.optString("endDate").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
            ).sanitized()
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeSpec(spec: OccurrenceSpec): JSONObject {
        return JSONObject()
            .put("occurrence", spec.occurrence.name)
            .put("day", spec.day.name)
    }

    private fun decodeSpec(obj: JSONObject): OccurrenceSpec {
        return OccurrenceSpec(
            occurrence = WeekOccurrence.valueOf(obj.getString("occurrence")),
            day = DayOfWeek.valueOf(obj.getString("day"))
        )
    }

    private fun decodeSpecs(arr: JSONArray?): List<OccurrenceSpec> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                add(decodeSpec(arr.getJSONObject(i)))
            }
        }
    }

    private fun decodeDays(arr: JSONArray?): Set<DayOfWeek> {
        if (arr == null) return emptySet()
        return buildSet {
            for (i in 0 until arr.length()) {
                runCatching { DayOfWeek.valueOf(arr.getString(i)) }.getOrNull()?.let { add(it) }
            }
        }
    }

    private fun decodeInts(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }
}
