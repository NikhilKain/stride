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

/**
 * Which source wins for the current day when both the phone pedometer and
 * Health Connect have data for it.
 *
 * [AUTO] prefers Health Connect when a wearable wrote today's steps, or when
 * Health Connect already holds a day this phone never saw any part of. A watch
 * or band sees walking a pocketed phone misses; another phone-side app writing
 * the same steps the phone already counted does not.
 */
enum class StepSource { AUTO, PHONE, HEALTH_CONNECT }

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
    /**
     * Opt-in: publish each day's totals back to Health Connect so other apps can
     * read them. Off by default — writing into a store shared with every other
     * health app is the user's call, not a default.
     */
    val hcWrite: Boolean = false,
    /**
     * Which source wins for today when both the phone and Health Connect have
     * data. Defaults to [StepSource.AUTO]; see the enum for why.
     */
    val stepSource: StepSource = StepSource.AUTO,
    val updateFrequency: UpdateFrequency = UpdateFrequency.WEEKLY
) {
    /** Stride length in meters; auto-derived from height unless overridden. */
    val strideMeters: Double
        get() = if (strideOverrideCm > 0) strideOverrideCm / 100.0 else heightCm * 0.414 / 100.0
}
