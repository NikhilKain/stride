package com.vythera.stride.data.repo

import com.vythera.stride.data.db.AchievementEntity
import com.vythera.stride.data.db.DailySummaryEntity
import com.vythera.stride.data.db.StrideDatabase
import com.vythera.stride.data.health.HealthConnectManager
import com.vythera.stride.data.health.HealthExporter
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

class StepRepository(
    private val appContext: android.content.Context,
    private val hc: HealthConnectManager,
    private val sensor: StepSensorManager,
    private val db: StrideDatabase,
    private val prefs: UserPreferences,
    private val notifier: Notifier,
    val scope: CoroutineScope,
    /** Where daily totals get published; see [HealthExporter]. */
    private val exporters: List<HealthExporter> = emptyList()
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
     * Today's row takes one source or the other, never both added — see
     * [todayPrefersHealthConnect] for how that choice is made and why summing
     * them would be wrong.
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
                    // Once we write our own totals back, Health Connect's aggregate
                    // includes them — reading that in unfiltered would count our
                    // steps twice and the number would climb on every sync.
                    val days = hc.readRange(
                        today.minusDays(historyDays), today,
                        excludeOwnWrites = p.hcWrite
                    ).filter { it.steps > 0 || it.date == today }
                    val existingGoals = db.summaryDao()
                        .getRange(today.minusDays(historyDays).toEpochDay(), today.toEpochDay())
                        .associate { it.epochDay to it.goal }
                    val preferHc = todayPrefersHealthConnect(p.stepSource, today)
                    // "This phone" is a deliberate instruction to show only what
                    // this pedometer saw, so it gets the raw number. The floor
                    // below is a guard for the cases where we are the ones
                    // guessing, not an override of what the user asked for.
                    val phoneIsExplicit = p.stepSource == com.vythera.stride.model.StepSource.PHONE
                    db.summaryDao().upsertAll(days.map { d ->
                        val isToday = d.date == today
                        // Past days predate our local history, so they always come
                        // straight from Health Connect. Today is a choice between two
                        // sources that both have a claim, and [todayPrefersHealthConnect]
                        // makes it. Whichever loses is discarded outright rather than
                        // added: these are two counts of the same walking, so summing or
                        // taking the larger of the two would double-count it.
                        val mergedSteps = when {
                            !isToday -> d.steps
                            preferHc && d.steps > 0 -> d.steps
                            // The phone's pedometer is a *floor*, never a
                            // correction downwards: it only counts from the moment
                            // we started listening, so it knows nothing about steps
                            // taken before install, before permission, or while the
                            // process was dead. Writing it in flat is what made a
                            // user's count drop to zero and climb again every time
                            // they reopened the app.
                            sensorToday > 0 ->
                                if (phoneIsExplicit) sensorToday else maxOf(sensorToday, before)
                            else -> d.steps
                        }
                        val hcWins = mergedSteps == d.steps && d.steps > 0
                        DailySummaryEntity(
                            epochDay = d.date.toEpochDay(),
                            steps = mergedSteps,
                            distanceMeters = if (hcWins && d.distanceMeters > 0) d.distanceMeters
                            else mergedSteps * p.strideMeters,
                            // Health Connect reports TOTAL calories, which includes basal
                            // metabolism — roughly 1,500 kcal of just being alive. Shown
                            // beside a step count that reads as "burned by walking", so we
                            // always use the movement-only estimate and keep the two screens
                            // telling the same story.
                            calories = estimateCalories(mergedSteps, p.weightKg),
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

    // ---- Choosing today's source ------------------------------------------

    @Volatile private var wearableProbeDay = Long.MIN_VALUE
    @Volatile private var wearableProbeAt = 0L
    @Volatile private var wearableProbeResult = false

    /**
     * Whether Health Connect wins for today, rather than the phone's pedometer.
     *
     * The two disagree for opposite reasons and there's no single right answer:
     *
     * - A phone in a pocket under-counts badly against a watch on the wrist. One
     *   user's band reported 17k while Stride showed the phone's 3k, which reads
     *   as the app being broken.
     * - Health Connect's total sums *every* app that writes steps, so when Fit or
     *   Samsung Health also logs the walk the phone just took, the total is close
     *   to double. That's the bug that made us prefer the sensor in 1.0.1 — a
     *   tester saw 3,831 against a true 2,906.
     *
     * [StepSource.AUTO] splits them on two signals:
     *
     * 1. A *wearable* wrote today's steps. A watch or band sees walking the phone
     *    missed; another phone-side app writing the same steps the phone already
     *    counted does not.
     * 2. Health Connect already holds a substantial day that our own sensor never
     *    saw any part of. That's the connect-mid-day case: a user grants access at
     *    3pm with 8,000 steps already banked elsewhere and nothing of our own to
     *    weigh against it. Requiring our sensor to be at *zero* is what keeps this
     *    from re-opening the 1.0.1 double-count — if we have been counting, we
     *    have our own honest number and we keep it.
     *
     * Users whose setup defeats both can pin the source outright in Settings.
     */
    private suspend fun todayPrefersHealthConnect(
        source: com.vythera.stride.model.StepSource,
        today: LocalDate
    ): Boolean = when (source) {
        com.vythera.stride.model.StepSource.PHONE -> false
        com.vythera.stride.model.StepSource.HEALTH_CONNECT -> true
        com.vythera.stride.model.StepSource.AUTO -> autoPrefersHealthConnect(today)
    }

    /**
     * The automatic decision, decided at most once per day in the HC direction.
     *
     * Deliberately one-way: it may flip from phone to Health Connect (a watch
     * syncs for the first time this morning) but never back within the same day.
     * A source that flips both ways makes the displayed count jump up and down,
     * which is precisely the complaint this whole setting exists to answer — one
     * user watched their total reset to zero and climb again every time they
     * reopened the app.
     *
     * Cached otherwise: the probe reads every step record for the day, and [sync]
     * runs every 15 seconds while the app is open.
     */
    private suspend fun autoPrefersHealthConnect(today: LocalDate): Boolean {
        val epochDay = today.toEpochDay()
        val now = System.currentTimeMillis()
        if (epochDay == wearableProbeDay) {
            // Already settled on Health Connect, or asked recently enough.
            if (wearableProbeResult) return true
            if (now - wearableProbeAt < WEARABLE_PROBE_TTL_MS) return false
        }
        // A decision taken earlier today outlives the process. Without this the
        // cold-start rule below would re-run after a restart, find our sensor no
        // longer at zero, and quietly hand the day back to the phone.
        if (prefs.autoHcDay() == epochDay) {
            wearableProbeDay = epochDay
            wearableProbeAt = now
            wearableProbeResult = true
            return true
        }

        val contributors = hc.inspectDay(today)
        val sensorState = prefs.sensorState()
        val sensorToday = if (sensorState.dayEpoch == epochDay) sensorState.todaySteps else 0L

        val decision = contributors.hasWearable ||
            (sensorToday == 0L && contributors.steps >= AUTO_HC_COLD_START_STEPS)

        wearableProbeDay = epochDay
        wearableProbeAt = now
        wearableProbeResult = decision
        if (decision) prefs.setAutoHcDay(epochDay)
        return decision
    }

    /**
     * Forgets the automatic decision so [StepSource.AUTO] re-evaluates from
     * scratch. Called when the user picks a source by hand: having chosen once,
     * they may well come back to AUTO, and it should look at today afresh rather
     * than honour a conclusion reached before they intervened.
     */
    suspend fun resetAutoSourceDecision() {
        wearableProbeDay = Long.MIN_VALUE
        wearableProbeAt = 0L
        wearableProbeResult = false
        prefs.setAutoHcDay(Long.MIN_VALUE)
    }

    /**
     * Today's Health Connect breakdown beside our own sensor count, for the
     * source picker in Settings. Uncached — the user is looking at it.
     */
    suspend fun inspectTodaySources(): SourceComparison {
        val today = LocalDate.now()
        val contributors = hc.inspectDay(today)
        val sensorState = prefs.sensorState()
        val sensorToday =
            if (sensorState.dayEpoch == today.toEpochDay()) sensorState.todaySteps else 0L
        // Refresh the probe cache while we're here; the user may be about to
        // switch to AUTO and expect it to take effect immediately. Only ever
        // upgrades the answer — [autoPrefersHealthConnect] is one-way for the
        // day, and reading a screen must not be able to undo that.
        val epochDay = today.toEpochDay()
        if (contributors.hasWearable) {
            wearableProbeDay = epochDay
            wearableProbeAt = System.currentTimeMillis()
            wearableProbeResult = true
            prefs.setAutoHcDay(epochDay)
        }
        return SourceComparison(
            phoneSteps = sensorToday,
            healthConnectSteps = contributors.steps,
            wearableSteps = contributors.wearableSteps,
            writers = contributors.writers
        )
    }

    /** What the two sources say right now; see [inspectTodaySources]. */
    data class SourceComparison(
        val phoneSteps: Long,
        val healthConnectSteps: Long,
        val wearableSteps: Long,
        val writers: List<String>
    ) {
        val hasWearable: Boolean get() = wearableSteps > 0L
    }

    /** Feeds one raw cumulative sensor reading through the daily-delta bookkeeping. */
    suspend fun onSensorRaw(raw: Long) = sensorMutex.withLock {
        val p = prefs.snapshot()
        val today = LocalDate.now().toEpochDay()
        var st = prefs.sensorState()

        // A new day starts counting from the reading in hand. Carrying yesterday's
        // lastRaw forward would charge today for every step the counter accumulated
        // since we last looked — overnight, or a whole evening if the app was killed —
        // which showed up as the count lurching upward on its own. Steps taken between
        // midnight and this first reading are recovered by the Health Connect merge.
        if (st.dayEpoch != today) {
            st = UserPreferences.SensorState(
                lastRaw = raw,
                dayEpoch = today,
                todaySteps = 0
            )
        }
        // TYPE_STEP_COUNTER reports cumulative steps since boot, so today's steps
        // are the growth between readings. Two readings must never be trusted as a
        // delta: the first one ever (no baseline yet) and the first after a reboot
        // (the counter restarted from zero, so raw < lastRaw). In both cases we only
        // resync the baseline and count nothing — adding `raw` would inject the whole
        // post-boot count as phantom steps.
        val delta = when {
            st.lastRaw < 0 -> 0L              // first reading ever
            raw < st.lastRaw -> 0L            // device rebooted; counter restarted
            else -> raw - st.lastRaw
        }
        val newState = UserPreferences.SensorState(raw, today, st.todaySteps + delta)
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

        maybeExport(p, todayRow)
        maybeRefreshWidget(steps)
    }

    @Volatile private var lastWidgetSteps = -1L

    /**
     * Nudges the home-screen widget when the number on it has actually changed.
     *
     * The widget's own `updatePeriodMillis` is hourly, which meant it could sit
     * an hour behind the app it sits next to. This runs on every sensor event
     * while the app is live, so it only pushes on a real change — a RemoteViews
     * update per footstep would be pure waste.
     */
    private suspend fun maybeRefreshWidget(steps: Long) {
        if (steps == lastWidgetSteps) return
        lastWidgetSteps = steps
        runCatching { com.vythera.stride.ui.widget.StrideWidget.requestUpdate(appContext) }
    }

    // ---- Publishing to Health Connect (and, later, other sinks) -------------

    private val exportMutex = Mutex()

    @Volatile private var lastExportAt = 0L
    @Volatile private var lastExportedSteps = -1L
    @Volatile private var lastExportedDay = Long.MIN_VALUE

    /**
     * Pushes today's running total out to every enabled sink.
     *
     * Throttled deliberately: [afterDataChanged] runs on every hardware step
     * event while the app is in the foreground, and a Health Connect write per
     * footstep would be both wasteful and pointless. We publish when the count
     * has moved meaningfully, when the last write has gone stale, or when the
     * date rolls — in which case yesterday is flushed one final time so its
     * record ends on the real total rather than whatever it held at the last tick.
     */
    private suspend fun maybeExport(p: com.vythera.stride.model.StridePrefs, todayRow: DailySummaryEntity?) {
        if (!p.hcWrite || exporters.isEmpty()) return
        val row = todayRow ?: return

        val now = System.currentTimeMillis()
        val dayRolled = lastExportedDay != Long.MIN_VALUE && lastExportedDay != row.epochDay
        val moved = kotlin.math.abs(row.steps - lastExportedSteps) >= EXPORT_MIN_STEP_DELTA
        val stale = now - lastExportAt >= EXPORT_MIN_INTERVAL_MS
        if (!dayRolled && !moved && !stale) return

        exportMutex.withLock {
            val sinks = connectedSinks()
            if (sinks.isEmpty()) return@withLock
            if (dayRolled) {
                db.summaryDao().getDay(lastExportedDay)?.let { publish(it, sinks) }
            }
            publish(row, sinks)
            lastExportAt = now
            lastExportedSteps = row.steps
            lastExportedDay = row.epochDay
        }
    }

    /** Resolved once per batch — each check is an IPC into the provider. */
    private suspend fun connectedSinks(): List<HealthExporter> =
        exporters.filter { runCatching { it.isConnected() }.getOrDefault(false) }

    private suspend fun publish(row: DailySummaryEntity, sinks: List<HealthExporter>) {
        val date = LocalDate.ofEpochDay(row.epochDay)
        for (sink in sinks) {
            runCatching { sink.write(date, row.steps, row.distanceMeters, row.calories) }
        }
    }

    /**
     * One-shot backfill, run when the user first turns write-back on so their
     * existing history appears in Health Connect rather than only days from here
     * onward. Returns how many days were written.
     */
    suspend fun exportHistory(days: Long = 30): Int = exportMutex.withLock {
        val sinks = connectedSinks()
        if (sinks.isEmpty()) return@withLock 0
        val today = LocalDate.now()
        val rows = db.summaryDao()
            .getRange(today.minusDays(days).toEpochDay(), today.toEpochDay())
            .filter { it.steps > 0 }
        rows.forEach { publish(it, sinks) }
        lastExportAt = System.currentTimeMillis()
        db.summaryDao().getDay(today.toEpochDay())?.let {
            lastExportedSteps = it.steps
            lastExportedDay = it.epochDay
        }
        rows.size
    }

    /** Removes everything Stride has written to Health Connect. */
    suspend fun withdrawFromHealthConnect(days: Long = 365): Boolean {
        lastExportedSteps = -1L
        lastExportAt = 0L
        return hc.deleteOwnWrites(LocalDate.now().minusDays(days))
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

        /** Health Connect write-back throttle; see [maybeExport]. */
        private const val EXPORT_MIN_STEP_DELTA = 50L
        private const val EXPORT_MIN_INTERVAL_MS = 5 * 60 * 1000L

        /** How long a wearable-detection probe stays good; see [wearableWroteToday]. */
        private const val WEARABLE_PROBE_TTL_MS = 10 * 60 * 1000L

        /**
         * How much Health Connect must already hold, with our own sensor still at
         * zero, before AUTO hands today over to it. High enough that a phone we
         * simply have not started counting on yet does not trip it at 00:05.
         */
        private const val AUTO_HC_COLD_START_STEPS = 500L
    }
}
