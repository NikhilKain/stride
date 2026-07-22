package com.vythera.stride.domain

import com.vythera.stride.data.db.DailySummaryEntity
import java.time.LocalDate

data class StreakInfo(val current: Int, val best: Int)

object Streaks {

    /**
     * A day counts toward a streak when its recorded steps met the goal stored
     * for that day. Today only extends the streak once its goal is met — an
     * unfinished today never breaks yesterday's streak.
     */
    fun compute(
        summaries: List<DailySummaryEntity>,
        today: LocalDate = LocalDate.now()
    ): StreakInfo {
        if (summaries.isEmpty()) return StreakInfo(0, 0)
        val byDay = summaries.associateBy { it.epochDay }

        fun met(epochDay: Long): Boolean {
            val s = byDay[epochDay] ?: return false
            val goal = if (s.goal > 0) s.goal else 8000
            return s.steps >= goal
        }

        val todayEpoch = today.toEpochDay()
        var current = 0
        var cursor = if (met(todayEpoch)) todayEpoch else todayEpoch - 1
        while (met(cursor)) {
            current++
            cursor--
        }

        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (day in summaries.map { it.epochDay }.distinct().sorted()) {
            val hit = met(day)
            run = if (hit) {
                if (day == prev + 1 && run > 0) run + 1 else 1
            } else 0
            if (hit) prev = day
            if (run > best) best = run
        }
        return StreakInfo(current, maxOf(best, current))
    }

    /** Sum of steps for the calendar week (Mon–Sun) containing [today]. */
    fun weekSteps(summaries: List<DailySummaryEntity>, today: LocalDate = LocalDate.now()): Long {
        val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val from = monday.toEpochDay()
        val to = from + 6
        return summaries.filter { it.epochDay in from..to }.sumOf { it.steps }
    }
}
