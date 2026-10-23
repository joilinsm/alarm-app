package com.alarmx.smartalarm.domain.logic

import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents the same alarm event from starting playback twice when the system
 * delivers a duplicate broadcast or the activity and service both observe it.
 */
class DeliveryGuard(
    private val windowMs: Long = 15_000L
) {
    private val recent = ConcurrentHashMap<String, Long>()

    fun eventKey(alarmId: Long, scheduledEpochMs: Long): String = "$alarmId:$scheduledEpochMs"

    @Synchronized
    fun shouldHandle(eventKey: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        prune(nowEpochMs)
        val previous = recent[eventKey]
        if (previous != null && nowEpochMs - previous <= windowMs) {
            return false
        }
        recent[eventKey] = nowEpochMs
        return true
    }

    @Synchronized
    fun clear(eventKey: String) {
        recent.remove(eventKey)
    }

    @Synchronized
    fun reset() {
        recent.clear()
    }

    private fun prune(nowEpochMs: Long) {
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowEpochMs - entry.value > windowMs * 4) {
                iterator.remove()
            }
        }
    }
}
