package com.vythera.stride.domain

import com.vythera.stride.data.db.DailySummaryEntity

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    /** Evaluates against the full ordered history. */
    val condition: (List<DailySummaryEntity>, StreakInfo) -> Boolean
)

object Achievements {

    val all: List<AchievementDef> = listOf(
        AchievementDef("first_steps", "First Steps", "Record your very first day of steps") { days, _ ->
            days.any { it.steps > 0 }
        },
        AchievementDef("day_10k", "10K Day", "Walk 10,000 steps in a single day") { days, _ ->
            days.any { it.steps >= 10_000 }
        },
        AchievementDef("day_15k", "Pathfinder", "Walk 15,000 steps in a single day") { days, _ ->
            days.any { it.steps >= 15_000 }
        },
        AchievementDef("day_20k", "Trailblazer", "Walk 20,000 steps in a single day") { days, _ ->
            days.any { it.steps >= 20_000 }
        },
        AchievementDef("day_30k", "Ultra Walker", "Walk 30,000 steps in a single day") { days, _ ->
            days.any { it.steps >= 30_000 }
        },
        AchievementDef("streak_3", "Warming Up", "Hit your goal 3 days in a row") { _, streak ->
            streak.best >= 3
        },
        AchievementDef("streak_7", "On Fire", "Hit your goal 7 days in a row") { _, streak ->
            streak.best >= 7
        },
        AchievementDef("streak_14", "Unstoppable", "Hit your goal 14 days in a row") { _, streak ->
            streak.best >= 14
        },
        AchievementDef("streak_30", "Habit Forged", "Hit your goal 30 days in a row") { _, streak ->
            streak.best >= 30
        },
        AchievementDef("week_70k", "Big Week", "Walk 70,000 steps within one week") { days, _ ->
            rollingWindowMax(days, 7) >= 70_000
        },
        AchievementDef("steady_5of7", "Steady Strider", "Hit your goal on 5 days of any week") { days, _ ->
            var count = 0
            val sorted = days.sortedBy { it.epochDay }
            for (i in sorted.indices) {
                val from = sorted[i].epochDay
                count = sorted.count { it.epochDay in from until from + 7 && it.steps >= maxOf(it.goal, 1) }
                if (count >= 5) return@AchievementDef true
            }
            false
        },
        AchievementDef("marathon", "Marathon Club", "Cover 42.2 km of total distance") { days, _ ->
            days.sumOf { it.distanceMeters } >= 42_195.0
        },
        AchievementDef("dist_100k", "Century Rambler", "Cover 100 km of total distance") { days, _ ->
            days.sumOf { it.distanceMeters } >= 100_000.0
        },
        AchievementDef("million", "Millionaire", "Reach 1,000,000 lifetime steps") { days, _ ->
            days.sumOf { it.steps } >= 1_000_000L
        }
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    private fun rollingWindowMax(days: List<DailySummaryEntity>, window: Int): Long {
        val sorted = days.sortedBy { it.epochDay }
        var best = 0L
        for (i in sorted.indices) {
            val from = sorted[i].epochDay
            val sum = sorted.filter { it.epochDay in from until from + window }.sumOf { it.steps }
            if (sum > best) best = sum
        }
        return best
    }
}
