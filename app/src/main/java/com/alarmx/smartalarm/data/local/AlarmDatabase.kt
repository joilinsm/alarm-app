package com.alarmx.smartalarm.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alarmx.smartalarm.domain.logic.RecurrenceCodec
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.DismissMethod
import com.alarmx.smartalarm.domain.model.RecurrenceSchedule
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.VibrationPattern
import com.alarmx.smartalarm.domain.model.VideoAudioMode
import com.alarmx.smartalarm.domain.model.VolumeSettings
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    val repeatMask: Int,
    val recurrenceJson: String = "",
    val soundKey: String,
    val vibrate: Boolean,
    val vibrationPattern: String,
    val volumePercent: Int,
    val gradualEnabled: Boolean,
    val rampStartPercent: Int,
    val rampTargetPercent: Int,
    val rampDurationMs: Int,
    val softwareAmpEnabled: Boolean,
    val softwareGainDb: Float,
    val videoVolumePercent: Int = 80,
    val ringtoneVolumePercent: Int = 90,
    val snoozeMinutes: Int,
    val snoozeMaxCount: Int,
    val dismissMethod: String,
    val keepRingingUntilDismissed: Boolean,
    val maxRingDurationMinutes: Int,
    val flashlightEnabled: Boolean,
    val screenFlashEnabled: Boolean,
    val wallpaperKey: String,
    val wallpaperFit: String = WallpaperFit.CROP.name,
    val overlayDarkness: Float,
    val videoAudioMode: String = VideoAudioMode.SEPARATE_RINGTONE.name,
    val fallbackWallpaperKey: String = WallpaperRef.Default.storageKey,
    val createdAt: Long,
    val updatedAt: Long,
    val lastScheduledEpochMs: Long?
)

fun AlarmEntity.toDomain(): Alarm {
    val recurrence = RecurrenceCodec.decode(recurrenceJson, repeatMask)
    return Alarm(
        id = id,
        hour = hour,
        minute = minute,
        enabled = enabled,
        label = label,
        repeatDays = when (recurrence.mode) {
            com.alarmx.smartalarm.domain.model.RecurrenceMode.WEEKLY -> recurrence.weeklyDays
            else -> RepeatDays(repeatMask)
        },
        recurrence = recurrence,
        sound = SoundRef.fromStorage(soundKey),
        vibrate = vibrate,
        vibrationPattern = runCatching { VibrationPattern.valueOf(vibrationPattern) }
            .getOrDefault(VibrationPattern.HEARTBEAT),
        volume = VolumeSettings(
            volumePercent = volumePercent,
            gradualEnabled = gradualEnabled,
            rampStartPercent = rampStartPercent,
            rampTargetPercent = rampTargetPercent,
            rampDurationMs = rampDurationMs,
            softwareAmpEnabled = softwareAmpEnabled,
            softwareGainDb = softwareGainDb,
            videoVolumePercent = videoVolumePercent,
            ringtoneVolumePercent = ringtoneVolumePercent
        ),
        snoozeMinutes = snoozeMinutes,
        snoozeMaxCount = snoozeMaxCount,
        dismissMethod = runCatching { DismissMethod.valueOf(dismissMethod) }
            .getOrDefault(DismissMethod.TAP),
        keepRingingUntilDismissed = keepRingingUntilDismissed,
        maxRingDurationMinutes = maxRingDurationMinutes,
        flashlightEnabled = flashlightEnabled,
        screenFlashEnabled = screenFlashEnabled,
        wallpaper = WallpaperRef.fromStorage(wallpaperKey),
        wallpaperFit = runCatching { WallpaperFit.valueOf(wallpaperFit) }
            .getOrDefault(WallpaperFit.CROP),
        overlayDarkness = overlayDarkness,
        videoAudioMode = runCatching { VideoAudioMode.valueOf(videoAudioMode) }
            .getOrDefault(VideoAudioMode.SEPARATE_RINGTONE),
        fallbackWallpaper = WallpaperRef.fromStorage(fallbackWallpaperKey),
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastScheduledEpochMs = lastScheduledEpochMs
    )
}

fun Alarm.toEntity(): AlarmEntity {
    val clean = sanitized()
    return AlarmEntity(
        id = clean.id,
        hour = clean.hour,
        minute = clean.minute,
        enabled = clean.enabled,
        label = clean.label,
        repeatMask = clean.repeatDays.mask,
        recurrenceJson = RecurrenceCodec.encode(clean.recurrence),
        soundKey = clean.sound.storageKey,
        vibrate = clean.vibrate,
        vibrationPattern = clean.vibrationPattern.name,
        volumePercent = clean.volume.volumePercent,
        gradualEnabled = clean.volume.gradualEnabled,
        rampStartPercent = clean.volume.rampStartPercent,
        rampTargetPercent = clean.volume.rampTargetPercent,
        rampDurationMs = clean.volume.rampDurationMs,
        softwareAmpEnabled = clean.volume.softwareAmpEnabled,
        softwareGainDb = clean.volume.softwareGainDb,
        videoVolumePercent = clean.volume.videoVolumePercent,
        ringtoneVolumePercent = clean.volume.ringtoneVolumePercent,
        snoozeMinutes = clean.snoozeMinutes,
        snoozeMaxCount = clean.snoozeMaxCount,
        dismissMethod = clean.dismissMethod.name,
        keepRingingUntilDismissed = clean.keepRingingUntilDismissed,
        maxRingDurationMinutes = clean.maxRingDurationMinutes,
        flashlightEnabled = clean.flashlightEnabled,
        screenFlashEnabled = clean.screenFlashEnabled,
        wallpaperKey = clean.wallpaper.storageKey,
        wallpaperFit = clean.wallpaperFit.name,
        overlayDarkness = clean.overlayDarkness,
        videoAudioMode = clean.videoAudioMode.name,
        fallbackWallpaperKey = clean.fallbackWallpaper.storageKey,
        createdAt = clean.createdAt,
        updatedAt = clean.updatedAt,
        lastScheduledEpochMs = clean.lastScheduledEpochMs
    )
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC, id ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC, id ASC")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AlarmEntity): Long

    @Update
    suspend fun update(entity: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE alarms SET lastScheduledEpochMs = :epochMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLastScheduled(id: Long, epochMs: Long?, updatedAt: Long)
}

@Database(
    entities = [AlarmEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN wallpaperFit TEXT NOT NULL DEFAULT 'CROP'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN recurrenceJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE alarms ADD COLUMN videoVolumePercent INTEGER NOT NULL DEFAULT 80")
                database.execSQL("ALTER TABLE alarms ADD COLUMN ringtoneVolumePercent INTEGER NOT NULL DEFAULT 90")
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN videoAudioMode TEXT NOT NULL DEFAULT 'SEPARATE_RINGTONE'"
                )
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN fallbackWallpaperKey TEXT NOT NULL DEFAULT 'bundled:midnight_navy'"
                )
            }
        }

        fun create(context: Context): AlarmDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AlarmDatabase::class.java,
                "alarmx.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        }
    }
}
