package com.alarmx.smartalarm.util

import android.content.Context
import android.text.format.DateFormat
import com.alarmx.smartalarm.domain.model.TimeFormatMode
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    fun is24Hour(context: Context, mode: TimeFormatMode): Boolean {
        return when (mode) {
            TimeFormatMode.FOLLOW_SYSTEM -> DateFormat.is24HourFormat(context)
            TimeFormatMode.HOURS_24 -> true
            TimeFormatMode.HOURS_12 -> false
        }
    }

    fun clock(now: ZonedDateTime, is24Hour: Boolean): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm"
        return now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun clockWithSeconds(now: ZonedDateTime, is24Hour: Boolean): String {
        val pattern = if (is24Hour) "HH:mm:ss" else "h:mm:ss"
        return now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun dateLine(now: ZonedDateTime): String {
        return now.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }

    fun countdown(from: ZonedDateTime, to: ZonedDateTime): String {
        val duration = Duration.between(from, to)
        if (duration.isNegative) return "now"
        val totalMin = duration.toMinutes()
        val days = totalMin / (60 * 24)
        val hours = (totalMin % (60 * 24)) / 60
        val minutes = totalMin % 60
        val seconds = duration.seconds % 60
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
