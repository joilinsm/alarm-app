package com.alarmx.smartalarm.domain

import com.alarmx.smartalarm.domain.logic.NextAlarmCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.SnoozeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextAlarmCalculatorTest {
    private val zone = ZoneId.of("UTC")

    private fun at(hour: Int, minute: Int, day: Int = 10): ZonedDateTime {
        return ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, zone)
    }

    @Test
    fun oneTimeUsesTodayWhenStillInFuture() {
        val from = at(6, 0)
        val next = NextAlarmCalculator.nextTrigger(7, 30, RepeatDays.NONE, from)
        assertEquals(at(7, 30), next)
    }

    @Test
    fun oneTimeRollsToTomorrowAfterMidnightWindow() {
        val from = at(23, 30)
        val next = NextAlarmCalculator.nextTrigger(0, 15, RepeatDays.NONE, from)
        assertEquals(at(0, 15, day = 11), next)
    }

    @Test
    fun oneTimeSkipsSameMinuteAlreadyPassed() {
        val from = at(8, 0)
        val next = NextAlarmCalculator.nextTrigger(8, 0, RepeatDays.NONE, from)
        assertEquals(at(8, 0, day = 11), next)
    }

    @Test
    fun repeatingSelectsNextMatchingWeekday() {
        val from = at(9, 0, day = 11) // Wednesday 11 Mar 2026
        val days = RepeatDays.of(listOf(DayOfWeek.FRIDAY))
        val next = NextAlarmCalculator.nextTrigger(7, 0, days, from)
        assertEquals(DayOfWeek.FRIDAY, next?.dayOfWeek)
        assertEquals(7, next?.hour)
        assertEquals(13, next?.dayOfMonth)
    }

    @Test
    fun repeatingSameDayStillAheadUsesToday() {
        val from = at(6, 0, day = 11)
        val days = RepeatDays.of(listOf(DayOfWeek.WEDNESDAY))
        val next = NextAlarmCalculator.nextTrigger(8, 15, days, from)
        assertEquals(at(8, 15, day = 11), next)
    }

    @Test
    fun disabledAlarmHasNoOccurrence() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, enabled = false)
        assertNull(NextAlarmCalculator.nextTrigger(alarm, at(5, 0)))
    }

    @Test
    fun nextOccurrencePrefersSoonestSnooze() {
        val alarm = Alarm(id = 4, hour = 9, minute = 0, enabled = true)
        val from = at(8, 0)
        val snooze = SnoozeState(4, 1, from.plusMinutes(5).toInstant().toEpochMilli())
        val next = NextAlarmCalculator.nextOccurrence(listOf(alarm), listOf(snooze), from)
        assertNotNull(next)
        assertTrue(next!!.isSnooze)
        assertEquals(1, next.snoozeCount)
    }

    @Test
    fun weekdayBitmaskRoundTrip() {
        val days = RepeatDays.of(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(days.contains(DayOfWeek.MONDAY))
        assertTrue(days.contains(DayOfWeek.FRIDAY))
        assertTrue(!days.contains(DayOfWeek.SUNDAY))
        assertEquals("Mon Fri", days.summary())
    }
}
