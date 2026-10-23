package com.alarmx.smartalarm.domain.logic

object EditorLoadPolicy {
    fun keyFor(alarmId: Long): Long = alarmId.coerceAtLeast(0L)

    /**
     * Returning from sound/wallpaper/volume must not reload Room over an in-progress draft.
     */
    fun shouldReload(
        alreadyReady: Boolean,
        loadedKey: Long?,
        incomingAlarmId: Long,
        force: Boolean
    ): Boolean {
        if (force) return true
        if (!alreadyReady || loadedKey == null) return true
        return loadedKey != keyFor(incomingAlarmId)
    }
}
