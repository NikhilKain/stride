package com.vythera.stride.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vythera.stride.model.AppFont
import com.vythera.stride.model.ColorStyle
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.model.ThemeMode
import com.vythera.stride.model.UnitSystem
import com.vythera.stride.model.UpdateFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "stride_prefs")

class UserPreferences(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val dailyGoal = intPreferencesKey("daily_goal")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val heightCm = intPreferencesKey("height_cm")
        val weightKg = intPreferencesKey("weight_kg")
        val strideOverrideCm = intPreferencesKey("stride_override_cm")
        val unit = stringPreferencesKey("unit")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val paletteId = stringPreferencesKey("palette_id")
        val notifGoal = booleanPreferencesKey("notif_goal")
        val notifNudge = booleanPreferencesKey("notif_nudge")
        val amoled = booleanPreferencesKey("amoled")
        val colorStyle = stringPreferencesKey("color_style")
        val appFont = stringPreferencesKey("app_font")
        val backgroundTracking = booleanPreferencesKey("background_tracking")
        val liveUpdates = booleanPreferencesKey("live_updates")
        val updateFrequency = stringPreferencesKey("update_frequency")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedVersion = stringPreferencesKey("skipped_version")

        // Step-sensor bookkeeping
        val sensorLastRaw = longPreferencesKey("sensor_last_raw")
        val sensorDayEpoch = longPreferencesKey("sensor_day_epoch")
        val sensorTodaySteps = longPreferencesKey("sensor_today_steps")

        // Notification bookkeeping
        val lastGoalNotifDay = longPreferencesKey("last_goal_notif_day")
        val lastNudgeDay = longPreferencesKey("last_nudge_day")
    }

    val prefs: Flow<StridePrefs> = context.dataStore.data.map { p ->
        StridePrefs(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            dailyGoal = p[Keys.dailyGoal] ?: 8000,
            weeklyGoal = p[Keys.weeklyGoal] ?: 56000,
            heightCm = p[Keys.heightCm] ?: 170,
            weightKg = p[Keys.weightKg] ?: 70,
            strideOverrideCm = p[Keys.strideOverrideCm] ?: 0,
            unit = runCatching { UnitSystem.valueOf(p[Keys.unit] ?: "METRIC") }.getOrDefault(UnitSystem.METRIC),
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.themeMode] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamicColor] ?: false,
            paletteId = p[Keys.paletteId] ?: "tide",
            notifGoal = p[Keys.notifGoal] ?: true,
            notifNudge = p[Keys.notifNudge] ?: true,
            amoled = p[Keys.amoled] ?: false,
            colorStyle = runCatching { ColorStyle.valueOf(p[Keys.colorStyle] ?: "TONAL_SPOT") }.getOrDefault(ColorStyle.TONAL_SPOT),
            appFont = runCatching { AppFont.valueOf(p[Keys.appFont] ?: "NUNITO") }.getOrDefault(AppFont.NUNITO),
            backgroundTracking = p[Keys.backgroundTracking] ?: false,
            liveUpdates = p[Keys.liveUpdates] ?: false,
            updateFrequency = runCatching {
                UpdateFrequency.valueOf(p[Keys.updateFrequency] ?: "WEEKLY")
            }.getOrDefault(UpdateFrequency.WEEKLY)
        )
    }

    suspend fun snapshot(): StridePrefs = prefs.first()

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setDailyGoal(goal: Int) = context.dataStore.edit { it[Keys.dailyGoal] = goal.coerceIn(1000, 50000) }
    suspend fun setWeeklyGoal(goal: Int) = context.dataStore.edit { it[Keys.weeklyGoal] = goal.coerceIn(0, 350000) }
    suspend fun setHeightCm(cm: Int) = context.dataStore.edit { it[Keys.heightCm] = cm.coerceIn(100, 230) }
    suspend fun setWeightKg(kg: Int) = context.dataStore.edit { it[Keys.weightKg] = kg.coerceIn(30, 250) }
    suspend fun setStrideOverrideCm(cm: Int) = context.dataStore.edit { it[Keys.strideOverrideCm] = cm.coerceIn(0, 150) }
    suspend fun setUnit(unit: UnitSystem) = context.dataStore.edit { it[Keys.unit] = unit.name }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setPaletteId(id: String) = context.dataStore.edit { it[Keys.paletteId] = id }
    suspend fun setNotifGoal(enabled: Boolean) = context.dataStore.edit { it[Keys.notifGoal] = enabled }
    suspend fun setNotifNudge(enabled: Boolean) = context.dataStore.edit { it[Keys.notifNudge] = enabled }
    suspend fun setAmoled(enabled: Boolean) = context.dataStore.edit { it[Keys.amoled] = enabled }
    suspend fun setColorStyle(style: ColorStyle) = context.dataStore.edit { it[Keys.colorStyle] = style.name }
    suspend fun setAppFont(font: AppFont) = context.dataStore.edit { it[Keys.appFont] = font.name }
    suspend fun setBackgroundTracking(enabled: Boolean) = context.dataStore.edit { it[Keys.backgroundTracking] = enabled }
    suspend fun setLiveUpdates(enabled: Boolean) = context.dataStore.edit { it[Keys.liveUpdates] = enabled }
    suspend fun setUpdateFrequency(freq: UpdateFrequency) =
        context.dataStore.edit { it[Keys.updateFrequency] = freq.name }

    suspend fun lastUpdateCheck(): Long = context.dataStore.data.first()[Keys.lastUpdateCheck] ?: 0L
    suspend fun markUpdateChecked() =
        context.dataStore.edit { it[Keys.lastUpdateCheck] = System.currentTimeMillis() }

    suspend fun skippedVersion(): String = context.dataStore.data.first()[Keys.skippedVersion] ?: ""
    suspend fun setSkippedVersion(v: String) = context.dataStore.edit { it[Keys.skippedVersion] = v }

    data class SensorState(val lastRaw: Long, val dayEpoch: Long, val todaySteps: Long)

    suspend fun sensorState(): SensorState {
        val p = context.dataStore.data.first()
        return SensorState(
            lastRaw = p[Keys.sensorLastRaw] ?: -1L,
            dayEpoch = p[Keys.sensorDayEpoch] ?: -1L,
            todaySteps = p[Keys.sensorTodaySteps] ?: 0L
        )
    }

    suspend fun setSensorState(state: SensorState) = context.dataStore.edit {
        it[Keys.sensorLastRaw] = state.lastRaw
        it[Keys.sensorDayEpoch] = state.dayEpoch
        it[Keys.sensorTodaySteps] = state.todaySteps
    }

    suspend fun lastGoalNotifDay(): Long = context.dataStore.data.first()[Keys.lastGoalNotifDay] ?: -1L
    suspend fun setLastGoalNotifDay(epochDay: Long) = context.dataStore.edit { it[Keys.lastGoalNotifDay] = epochDay }
    suspend fun lastNudgeDay(): Long = context.dataStore.data.first()[Keys.lastNudgeDay] ?: -1L
    suspend fun setLastNudgeDay(epochDay: Long) = context.dataStore.edit { it[Keys.lastNudgeDay] = epochDay }
}
