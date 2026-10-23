package com.alarmx.smartalarm.data.repo

import com.alarmx.smartalarm.data.local.AlarmDao
import com.alarmx.smartalarm.data.local.toDomain
import com.alarmx.smartalarm.data.local.toEntity
import com.alarmx.smartalarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(
    private val dao: AlarmDao
) {
    fun observeAlarms(): Flow<List<Alarm>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Alarm> = dao.getAll().map { it.toDomain() }

    suspend fun getEnabled(): List<Alarm> = dao.getEnabled().map { it.toDomain() }

    suspend fun getById(id: Long): Alarm? = dao.getById(id)?.toDomain()

    suspend fun upsert(alarm: Alarm): Alarm {
        val clean = alarm.sanitized().copy(updatedAt = System.currentTimeMillis())
        return if (clean.id == 0L) {
            val newId = dao.insert(clean.copy(id = 0).toEntity())
            clean.copy(id = newId)
        } else {
            dao.update(clean.toEntity())
            clean
        }
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Alarm? {
        dao.setEnabled(id, enabled, System.currentTimeMillis())
        return getById(id)
    }

    suspend fun duplicate(id: Long): Alarm? {
        val source = getById(id) ?: return null
        val copy = source.copy(
            id = 0L,
            label = if (source.label.endsWith(" copy")) source.label else "${source.label} copy",
            enabled = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastScheduledEpochMs = null
        )
        return upsert(copy)
    }

    suspend fun setLastScheduled(id: Long, epochMs: Long?) {
        dao.setLastScheduled(id, epochMs, System.currentTimeMillis())
    }

    suspend fun disableOneTimeAfterFire(alarm: Alarm) {
        if (!alarm.isRepeating()) {
            setEnabled(alarm.id, false)
        }
    }
}
