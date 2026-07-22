package com.vythera.stride.model

import java.time.LocalDate

enum class StatSource { HEALTH_CONNECT, SENSOR, NONE }

data class DailyStats(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double,
    val calories: Double,
    val goal: Int,
    val source: StatSource
) {
    val progress: Float get() = if (goal <= 0) 0f else (steps.toFloat() / goal.toFloat())
    val activeMinutes: Int get() = (steps / 110.0).toInt()

    companion object {
        fun empty(date: LocalDate = LocalDate.now(), goal: Int = 8000) =
            DailyStats(date, 0L, 0.0, 0.0, goal, StatSource.NONE)
    }
}

enum class UnitSystem { METRIC, IMPERIAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class HcState { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE, GRANTED }

/** Monet-style palette treatments, ColorBlendr-style. */
enum class ColorStyle { TONAL_SPOT, NEUTRAL, MONOCHROME, VIBRANT, EXPRESSIVE }

enum class AppFont { SYSTEM, NUNITO, INTER, OUTFIT, LEXEND, MANROPE, GROTESK }

/** How often Stride looks for a new release. */
enum class UpdateFrequency(val intervalMillis: Long) {
    NEVER(Long.MAX_VALUE),
    DAILY(24L * 60 * 60 * 1000),
    WEEKLY(7L * 24 * 60 * 60 * 1000),
    MONTHLY(30L * 24 * 60 * 60 * 1000)
}

data class StridePrefs(
    val onboardingDone: Boolean = false,
    val dailyGoal: Int = 8000,
    val weeklyGoal: Int = 56000,
    val heightCm: Int = 170,
    val weightKg: Int = 70,
    val strideOverrideCm: Int = 0,
    val unit: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val paletteId: String = "tide",
    val notifGoal: Boolean = true,
    val notifNudge: Boolean = true,
    val amoled: Boolean = false,
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val appFont: AppFont = AppFont.NUNITO,
    val backgroundTracking: Boolean = false,
    val liveUpdates: Boolean = false,
    val updateFrequency: UpdateFrequency = UpdateFrequency.WEEKLY
) {
    /** Stride length in meters; auto-derived from height unless overridden. */
    val strideMeters: Double
        get() = if (strideOverrideCm > 0) strideOverrideCm / 100.0 else heightCm * 0.414 / 100.0
}
