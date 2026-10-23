package com.alarmx.smartalarm.audio

import androidx.compose.ui.graphics.Color
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.WallpaperRef

data class BundledTone(
    val rawName: String,
    val title: String,
    val description: String
) {
    val ref: SoundRef get() = SoundRef.Bundled(rawName)
}

data class BundledWallpaper(
    val id: String,
    val title: String,
    val description: String,
    val colors: List<Color>
) {
    val ref: WallpaperRef get() = WallpaperRef.Bundled(id)
}

object BundledCatalog {
    val tones = listOf(
        BundledTone("tone_alert_beacon", "Alert Beacon", "Classic dual-tone wake-up pulse"),
        BundledTone("tone_dawn_pulse", "Dawn Pulse", "Rising gentle beeps"),
        BundledTone("tone_crystal_chime", "Crystal Chime", "Two-note chime with soft decay"),
        BundledTone("tone_radar_ping", "Radar Ping", "Distant ping with echo"),
        BundledTone("tone_gentle_wave", "Gentle Wave", "Soft triangle-wave swell"),
        BundledTone("tone_night_watch", "Night Watch", "Low repeating pulse"),
        BundledTone("tone_sunrise_bell", "Sunrise Bell", "Bell-like harmonic strike"),
        BundledTone("tone_digital_tick", "Digital Tick", "Short digital metronome")
    )

    val wallpapers = listOf(
        BundledWallpaper(
            "midnight_navy", "Midnight Navy", "Deep navy night",
            listOf(Color(0xFF050816), Color(0xFF152048), Color(0xFF1F3B73))
        ),
        BundledWallpaper(
            "night_sky", "Night Sky", "Indigo to black sky",
            listOf(Color(0xFF05060F), Color(0xFF1B2156), Color(0xFF4C63C7))
        ),
        BundledWallpaper(
            "sunrise_amber", "Sunrise", "Warm amber dawn",
            listOf(Color(0xFF1A0B08), Color(0xFFB45309), Color(0xFFFBBF24))
        ),
        BundledWallpaper(
            "mountain_dusk", "Mountain Dusk", "Cool dusk ridges",
            listOf(Color(0xFF0B1320), Color(0xFF334155), Color(0xFF94A3B8))
        ),
        BundledWallpaper(
            "forest_mist", "Forest Mist", "Evergreen haze",
            listOf(Color(0xFF052E16), Color(0xFF14532D), Color(0xFF86EFAC))
        ),
        BundledWallpaper(
            "abstract_aurora", "Aurora", "Teal and violet glow",
            listOf(Color(0xFF022C22), Color(0xFF0F766E), Color(0xFFC084FC))
        ),
        BundledWallpaper(
            "ember_gradient", "Ember", "Charcoal to ember",
            listOf(Color(0xFF1C0707), Color(0xFF7F1D1D), Color(0xFFF97316))
        ),
        BundledWallpaper(
            "ocean_depth", "Ocean Depth", "Deep sea teal",
            listOf(Color(0xFF042F2E), Color(0xFF0E7490), Color(0xFF67E8F9))
        ),
        BundledWallpaper(
            "minimal_charcoal", "Minimal Dark", "Quiet charcoal",
            listOf(Color(0xFF0A0A0A), Color(0xFF171717), Color(0xFF404040))
        ),
        BundledWallpaper(
            "violet_dusk", "Violet Dusk", "Twilight violet",
            listOf(Color(0xFF1E1033), Color(0xFF5B21B6), Color(0xFFF0ABFC))
        )
    )

    fun wallpaper(id: String): BundledWallpaper {
        return wallpapers.find { it.id == id } ?: wallpapers.first()
    }

    fun tone(rawName: String): BundledTone {
        return tones.find { it.rawName == rawName } ?: tones.first()
    }
}
