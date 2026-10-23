package com.alarmx.smartalarm.audio

import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.WallpaperRef

object SoundLabels {
    fun title(sound: SoundRef): String {
        return when (sound) {
            is SoundRef.SystemDefault -> "System default alarm"
            is SoundRef.Bundled -> BundledCatalog.tone(sound.rawName).title
            is SoundRef.UserFile -> sound.fileName
        }
    }

    fun detail(sound: SoundRef): String {
        return when (sound) {
            is SoundRef.SystemDefault -> "Device alarm ringtone"
            is SoundRef.Bundled -> BundledCatalog.tone(sound.rawName).description
            is SoundRef.UserFile -> "Imported audio"
        }
    }
}

object WallpaperLabels {
    fun title(wallpaper: WallpaperRef): String {
        return when (wallpaper) {
            is WallpaperRef.Bundled -> BundledCatalog.wallpaper(wallpaper.id).title
            is WallpaperRef.UserFile -> wallpaper.fileName
            is WallpaperRef.VideoFile -> "Video · ${wallpaper.fileName}"
        }
    }
}
