package com.vythera.stride.data.health

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.vythera.stride.model.HcState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId

data class HcDay(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double,
    val calories: Double
)

/**
 * Who wrote steps into Health Connect for a given day, Stride itself excluded.
 *
 * [wearableSteps] is the part that came from a watch, band, ring or strap —
 * the signal that Health Connect knows about walking the phone slept through.
 */
data class HcContributors(
    val steps: Long = 0L,
    val writers: List<String> = emptyList(),
    val wearableSteps: Long = 0L
) {
    val hasWearable: Boolean get() = wearableSteps > 0L
}

class HealthConnectManager(private val context: Context) {

    /** Read access — requested during onboarding and from the Health Connect card. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    /**
     * Write access — requested separately, only when the user turns write-back on.
     * Sharing a step count into a store other apps read is a decision worth asking
     * for on its own rather than bundling into the initial connect prompt.
     */
    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    )

    fun availability(): HcState = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcState.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.NEEDS_UPDATE
        else -> HcState.UNAVAILABLE
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    private suspend fun granted(): Set<String> =
        client().permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean {
        if (availability() != HcState.AVAILABLE) return false
        return runCatching { granted().containsAll(permissions) }.getOrDefault(false)
    }

    suspend fun hasWritePermissions(): Boolean {
        if (availability() != HcState.AVAILABLE) return false
        return runCatching { granted().containsAll(writePermissions) }.getOrDefault(false)
    }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun readDay(date: LocalDate): HcDay {
        val start = date.atStartOfDay()
        val end = if (date == LocalDate.now()) LocalDateTime.now() else date.atTime(LocalTime.MAX)
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return HcDay(
            date = date,
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
            distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
            calories = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        )
    }

    /** Steps per hour for [date]; 24 buckets, missing hours are zero. */
    suspend fun readHourly(date: LocalDate): List<Long> {
        val start = date.atStartOfDay()
        val end = if (date == LocalDate.now()) LocalDateTime.now() else date.atTime(LocalTime.MAX)
        val buckets = LongArray(24)
        runCatching {
            val groups = client().aggregateGroupByDuration(
                androidx.health.connect.client.request.AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        start.atZone(ZoneId.systemDefault()).toInstant(),
                        end.atZone(ZoneId.systemDefault()).toInstant()
                    ),
                    timeRangeSlicer = java.time.Duration.ofHours(1)
                )
            )
            groups.forEach { g ->
                val hour = g.startTime.atZone(ZoneId.systemDefault()).hour
                buckets[hour] = buckets[hour] + (g.result[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
        }
        return buckets.toList()
    }

    /**
     * Daily aggregates for the range.
     *
     * [excludeOwnWrites] subtracts Stride's own contribution. That matters the
     * moment write-back is on: Health Connect aggregates every writer, so reading
     * back a day we just wrote would count our steps twice, we would store the
     * doubled figure, write *that* back, and the number would climb on every sync.
     * Health Connect has no "everything except me" filter, so we aggregate twice —
     * once unfiltered, once filtered to our own package — and subtract.
     */
    suspend fun readRange(
        from: LocalDate,
        to: LocalDate,
        excludeOwnWrites: Boolean = false
    ): List<HcDay> {
        val all = aggregateDays(from, to, emptySet())
        if (!excludeOwnWrites) return all.values.sortedBy { it.date }

        val own = aggregateDays(from, to, setOf(DataOrigin(context.packageName)))
        if (own.isEmpty()) return all.values.sortedBy { it.date }

        return all.values.map { day ->
            val mine = own[day.date] ?: return@map day
            HcDay(
                date = day.date,
                steps = (day.steps - mine.steps).coerceAtLeast(0L),
                distanceMeters = (day.distanceMeters - mine.distanceMeters).coerceAtLeast(0.0),
                calories = (day.calories - mine.calories).coerceAtLeast(0.0)
            )
        }.sortedBy { it.date }
    }

    private suspend fun aggregateDays(
        from: LocalDate,
        to: LocalDate,
        origins: Set<DataOrigin>
    ): Map<LocalDate, HcDay> {
        val groups = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL
                ),
                timeRangeFilter = TimeRangeFilter.between(
                    from.atStartOfDay(),
                    to.atTime(LocalTime.MAX)
                ),
                timeRangeSlicer = Period.ofDays(1),
                dataOriginFilter = origins
            )
        )
        return groups.associate { g ->
            val date = g.startTime.toLocalDate()
            date to HcDay(
                date = date,
                steps = g.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                distanceMeters = g.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
                calories = g.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
            )
        }
    }

    private val device: Device
        get() = Device(
            type = Device.TYPE_PHONE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL
        )

    /**
     * Publishes one day's totals to Health Connect as a single record per type.
     *
     * Replace, never append: our own records for the day are deleted first, so
     * re-syncing the same day cannot stack duplicates. Health Connect only ever
     * permits an app to delete records it wrote itself, so this can't touch data
     * belonging to Fit, Samsung Health or anything else.
     *
     * Returns true when the write landed.
     */
    suspend fun writeDay(
        date: LocalDate,
        steps: Long,
        distanceMeters: Double,
        calories: Double
    ): Boolean {
        if (!hasWritePermissions()) return false

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val start = date.atStartOfDay(zone).toInstant()
        // Stop 1ms short of midnight rather than landing exactly on it. Health
        // Connect apportions a record that only partly overlaps the query window,
        // and reads run to LocalTime.MAX (23:59:59.999…) — a record ending at
        // 24:00:00.000 sticks out past that and gets scaled down, so a day written
        // as 4,321 steps read back as 4,320. Ending inside the window keeps the
        // number exact. The live day ends "now" instead: Health Connect rejects
        // records that end in the future.
        val end = minOf(
            date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1),
            now
        )
        if (!end.isAfter(start)) return false

        val startOffset = zone.rules.getOffset(start)
        val endOffset = zone.rules.getOffset(end)
        val range = TimeRangeFilter.between(start, end)

        return runCatching {
            val c = client()
            c.deleteRecords(StepsRecord::class, range)
            c.deleteRecords(DistanceRecord::class, range)
            c.deleteRecords(ActiveCaloriesBurnedRecord::class, range)

            // A zero-step day is a delete, not an insert: Health Connect won't
            // accept a StepsRecord with a count of zero.
            if (steps <= 0L) return@runCatching true

            val records = buildList {
                add(
                    StepsRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        count = steps,
                        metadata = Metadata.autoRecorded(device)
                    )
                )
                if (distanceMeters > 0.0) add(
                    DistanceRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        distance = Length.meters(distanceMeters),
                        metadata = Metadata.autoRecorded(device)
                    )
                )
                if (calories > 0.0) add(
                    ActiveCaloriesBurnedRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        energy = Energy.kilocalories(calories),
                        metadata = Metadata.autoRecorded(device)
                    )
                )
            }
            c.insertRecords(records)
            true
        }.getOrDefault(false)
    }

    /** Removes everything Stride has ever written, leaving other apps' data alone. */
    suspend fun deleteOwnWrites(since: LocalDate): Boolean = runCatching {
        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(since.atStartOfDay(zone).toInstant(), Instant.now())
        val c = client()
        c.deleteRecords(StepsRecord::class, range)
        c.deleteRecords(DistanceRecord::class, range)
        c.deleteRecords(ActiveCaloriesBurnedRecord::class, range)
        true
    }.getOrDefault(false)

    /**
     * What Health Connect holds for [date], broken down by who wrote it.
     *
     * Used for two things: deciding automatically whether Health Connect or the
     * phone's own pedometer should win for the day, and showing the user the
     * actual numbers side by side in Settings so the choice isn't a guess.
     *
     * Stride's own records are excluded throughout — they're an echo of the very
     * count we're trying to choose against.
     */
    suspend fun inspectDay(date: LocalDate): HcContributors {
        if (availability() != HcState.AVAILABLE) return HcContributors()
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = minOf(
                date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1),
                Instant.now()
            )
            if (!end.isAfter(start)) return@runCatching HcContributors()

            val records = mutableListOf<StepsRecord>()
            var token: String? = null
            // Paged deliberately: a day with a chatty writer can hold hundreds of
            // short records, and the cap stops a pathological day from stalling
            // the sync. Five pages is far more than any real day produces.
            var page = 0
            do {
                val response = client().readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageSize = 1000,
                        pageToken = token
                    )
                )
                records += response.records
                token = response.pageToken
                page++
            } while (token != null && page < 5)

            val mine = context.packageName
            val external = records.filter { it.metadata.dataOrigin.packageName != mine }

            HcContributors(
                steps = external.sumOf { it.count },
                writers = external.map { it.metadata.dataOrigin.packageName }.distinct().sorted(),
                wearableSteps = external
                    .filter { it.metadata.device?.type in WEARABLE_DEVICE_TYPES }
                    .sumOf { it.count }
            )
        }.getOrDefault(HcContributors())
    }

    private companion object {
        /**
         * Device types that see steps a pocketed or desk-bound phone cannot.
         * A watch reporting 17k against the phone's 3k isn't a discrepancy —
         * it's the watch being on the wrist all day.
         */
        val WEARABLE_DEVICE_TYPES = setOf(
            Device.TYPE_WATCH,
            Device.TYPE_FITNESS_BAND,
            Device.TYPE_RING,
            Device.TYPE_CHEST_STRAP
        )
    }
}
