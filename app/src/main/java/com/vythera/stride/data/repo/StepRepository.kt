package com.vythera.stride.data.repo

import com.vythera.stride.data.db.AchievementEntity
import com.vythera.stride.data.db.DailySummaryEntity
import com.vythera.stride.data.db.StrideDatabase
import com.vythera.stride.data.health.HealthConnectManager
import com.vythera.stride.data.health.StepSensorManager
import com.vythera.stride.data.prefs.UserPreferences
import com.vythera.stride.domain.AchievementDef
import com.vythera.stride.domain.Achievements
import com.vythera.stride.domain.StreakInfo
import com.vythera.stride.domain.Streaks
import com.vythera.stride.model.DailyStats
import com.vythera.stride.model.StatSource
import com.vythera.stride.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max

class StepRepository(
    private val hc: HealthConnectManager,
    private val sensor: StepSensorManager,
    private val db: StrideDatabase,
    private val prefs: UserPreferences,
    private val notifier: Notifier,
    val scope: CoroutineScope
) {
    /** Fired when the daily goal is crossed while the app is live. */
    val goalCelebration = MutableSharedFlow<Long>(extraBufferCapacity = 4)

    /** Fired for each newly unlocked achievement. */
    val newAchievements = MutableSharedFlow<AchievementDef>(extraBufferCapacity = 16)

    private val syncMutex = Mutex()

    // The activity and the tracking service both stream the hardware sensor;
    // without this lock two coroutines could read the same baseline and apply
    // the same delta twice, inflating the count.
    private val sensorMutex = Mutex()

    fun observeToday(): Flow<DailyStats> {
        val today = LocalDate.now()
        return combine(db.summaryDao().observeDay(today.toEpochDay()), prefs.prefs) { row, p ->
            row?.let {
                DailyStats(
                    date = today,
                    steps = it.steps,
                    distanceMeters = it.distanceMeters,
                    calories = it.calories,
                    goal = p.dailyGoal,
                    source = runCatching { StatSource.valueOf(it.source) }.getOrDefault(StatSource.NONE)
                )
            } ?: DailyStats.empty(today, p.dailyGoal)
        }
    }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailySummaryEntity>> =
        db.summaryDao().observeRange(from.toEpochDay(), to.toEpochDay())

    fun observeAchievements(): Flow<List<AchievementEntity>> = db.achievementDao().observeAll()

    suspend fun hcGranted(): Boolean = hc.hasAllPermissions()

    /** Cheap refresh of just the last couple of days — safe to call often. */
    suspend fun syncToday() = sync(historyDays = 2)

    /**
     * Pulls fresh data from the best available source into Room, then
     * re-evaluates streaks, achievements and goal notifications.
     *
     * Today's row is a max-merge of Health Connect and the hardware sensor:
     * HC only has data when some app writes to it, and the sensor only counts
     * while we're listening — whichever saw more steps wins, never both added.
     */
    suspend fun sync(historyDays: Long = 90) {
        syncMutex.withLock {
            val p = prefs.snapshot()
            val today = LocalDate.now()
            val before = db.summaryDao().getDay(today.toEpochDay())?.steps ?: 0L

            if (hc.hasAllPermissions()) {
                runCatching {
                    val sensorState = prefs.sensorState()
                    val sensorToday =
                        if (sensorState.dayEpoch == today.toEpochDay()) sensorState.todaySteps else 0L
                    val days = hc.readRange(today.minusDays(historyDays), today)
                        .filter { it.steps > 0 || it.date == today }
                    val existingGoals = db.summaryDao()
                        .getRange(today.minusDays(historyDays).toEpochDay(), today.toEpochDay())
                        .associate { it.epochDay to it.goal }
                    db.summaryDao().upsertAll(days.map { d ->
                        val isToday = d.date == today
                        val mergedSteps = if (isToday) max(d.steps, sensorToday) else d.steps
                        val hcWins = mergedSteps == d.steps && d.steps > 0
                        DailySummaryEntity(
                            epochDay = d.date.toEpochDay(),
                            steps = mergedSteps,
                            distanceMeters = if (hcWins && d.distanceMeters > 0) d.distanceMeters
                            else mergedSteps * p.strideMeters,
                            calories = if (hcWins && d.calories > 0) d.calories
                            else estimateCalories(mergedSteps, p.weightKg),
                            goal = if (isToday) p.dailyGoal
                            else existingGoals[d.date.toEpochDay()] ?: p.dailyGoal,
                            source = if (hcWins) StatSource.HEALTH_CONNECT.name else StatSource.SENSOR.name
                        )
                    })
                }
            } else if (sensor.isAvailable) {
                val raw = withTimeoutOrNull(3000) { sensor.rawSteps().firstOrNull() }
                if (raw != null) onSensorRaw(raw)
            }

            afterDataChanged(before)
        }
    }

    /** Feeds one raw cumulative sensor reading through the daily-delta bookkeeping. */
    suspend fun onSensorRaw(raw: Long) = sensorMutex.withLock {
        val p = prefs.snapshot()
        val today = LocalDate.now().toEpochDay()
        var st = prefs.sensorState()

        if (st.dayEpoch != today) {
            st = UserPreferences.SensorState(
                lastRaw = if (st.lastRaw < 0) raw else st.lastRaw,
                dayEpoch = today,
                todaySteps = 0
            )
        }
        var delta = raw - st.lastRaw
        if (st.lastRaw < 0) delta = 0                  // first reading ever
        if (delta < 0) delta = raw                     // device rebooted; counter restarted
        val newState = UserPreferences.SensorState(raw, today, st.todaySteps + max(delta, 0L))
        prefs.setSensorState(newState)

        // Max-merge with whatever is already recorded (possibly from HC):
        // only write when the sensor has seen more than the current row.
        val steps = newState.todaySteps
        val existing = db.summaryDao().getDay(today)
        if (existing == null || steps > existing.steps) {
            db.summaryDao().upsert(
                DailySummaryEntity(
                    epochDay = today,
                    steps = steps,
                    distanceMeters = steps * p.strideMeters,
                    calories = estimateCalories(steps, p.weightKg),
                    goal = p.dailyGoal,
                    source = StatSource.SENSOR.name
                )
            )
        }
    }

    /**
     * Live foreground stream: every hardware step event updates today's row
     * instantly, and (when Health Connect is granted) periodically pulls HC
     * so richer distance/calorie data replaces the estimates.
     */
    suspend fun collectSensorLive() {
        var before = db.summaryDao().getDay(LocalDate.now().toEpochDay())?.steps ?: 0L
        var lastHcPull = 0L
        sensor.rawSteps().collect { raw ->
            onSensorRaw(raw)
            val now = System.currentTimeMillis()
            if (now - lastHcPull > 8000 && hc.hasAllPermissions()) {
                lastHcPull = now
                runCatching { syncToday() }
            } else {
                afterDataChanged(before)
            }
            before = db.summaryDao().getDay(LocalDate.now().toEpochDay())?.steps ?: before
        }
    }

    private suspend fun afterDataChanged(stepsBefore: Long) {
        val p = prefs.snapshot()
        val today = LocalDate.now()
        val all = db.summaryDao().getAll()
        val streak = Streaks.compute(all, today)
        val todayRow = all.firstOrNull { it.epochDay == today.toEpochDay() }
        val steps = todayRow?.steps ?: 0L

        // Goal crossing
        if (steps >= p.dailyGoal && stepsBefore < p.dailyGoal && p.dailyGoal > 0) {
            goalCelebration.tryEmit(steps)
            if (p.notifGoal && prefs.lastGoalNotifDay() != today.toEpochDay()) {
                notifier.goalReached(steps)
                prefs.setLastGoalNotifDay(today.toEpochDay())
            }
        }

        // Achievements
        val unlocked = db.achievementDao().getAll().map { it.id }.toSet()
        for (def in Achievements.all) {
            if (def.id !in unlocked && def.condition(all, streak)) {
                db.achievementDao().unlock(AchievementEntity(def.id, today.toEpochDay()))
                newAchievements.tryEmit(def)
                notifier.achievementUnlocked(def)
            }
        }
    }

    suspend fun currentStreak(): StreakInfo = Streaks.compute(db.summaryDao().getAll())

    /** Evening reminder when the day's count is lagging behind the goal. */
    suspend fun maybeNudge() {
        val p = prefs.snapshot()
        if (!p.notifNudge) return
        val today = LocalDate.now()
        if (LocalTime.now() < LocalTime.of(18, 0)) return
        if (prefs.lastNudgeDay() == today.toEpochDay()) return
        val steps = db.summaryDao().getDay(today.toEpochDay())?.steps ?: 0L
        if (steps < p.dailyGoal * 0.5) {
            notifier.nudge(steps, p.dailyGoal)
            prefs.setLastNudgeDay(today.toEpochDay())
        }
    }

    companion object {
        fun estimateCalories(steps: Long, weightKg: Int): Double =
            steps * 0.04 * (weightKg / 70.0)
    }
}
