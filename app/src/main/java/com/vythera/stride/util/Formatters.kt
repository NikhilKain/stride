package com.vythera.stride.util

import com.vythera.stride.model.UnitSystem
import java.util.Locale

object Formatters {

    fun steps(steps: Long): String = String.format(Locale.getDefault(), "%,d", steps)

    fun distance(meters: Double, unit: UnitSystem): String = when (unit) {
        UnitSystem.METRIC -> {
            val km = meters / 1000.0
            if (km >= 100) String.format(Locale.getDefault(), "%.0f km", km)
            else String.format(Locale.getDefault(), "%.2f km", km)
        }
        UnitSystem.IMPERIAL -> {
            val mi = meters / 1609.344
            if (mi >= 100) String.format(Locale.getDefault(), "%.0f mi", mi)
            else String.format(Locale.getDefault(), "%.2f mi", mi)
        }
    }

    fun calories(kcal: Double): String =
        String.format(Locale.getDefault(), "%,d kcal", kcal.toInt())

    fun caloriesShort(kcal: Double): String =
        String.format(Locale.getDefault(), "%,d", kcal.toInt())

    fun compactSteps(steps: Long): String = when {
        steps >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", steps / 1_000_000.0)
        steps >= 10_000 -> String.format(Locale.getDefault(), "%.0fk", steps / 1000.0)
        steps >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", steps / 1000.0)
        else -> steps.toString()
    }
}
