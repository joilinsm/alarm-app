package com.alarmx.smartalarm.domain.logic

import com.alarmx.smartalarm.domain.model.SoundRef

object SoundPlaybackMessages {
    fun missingSound(sound: SoundRef): String = when (sound) {
        is SoundRef.UserFile -> "The imported sound “${sound.fileName}” is missing. Choose another sound."
        is SoundRef.Bundled -> "Bundled tone ${sound.rawName} is missing from the app."
        is SoundRef.SystemDefault -> "The system alarm sound is unavailable."
    }
}
