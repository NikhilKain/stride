package com.vythera.stride.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.vythera.stride.model.HcState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period

data class HcDay(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double,
    val calories: Double
)

class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    fun availability(): HcState = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcState.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.NEEDS_UPDATE
        else -> HcState.UNAVAILABLE
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean {
        if (availability() != HcState.AVAILABLE) return false
        return runCatching {
            client().permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
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
                        start.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                        end.atZone(java.time.ZoneId.systemDefault()).toInstant()
                    ),
                    timeRangeSlicer = java.time.Duration.ofHours(1)
                )
            )
            groups.forEach { g ->
                val hour = g.startTime.atZone(java.time.ZoneId.systemDefault()).hour
                buckets[hour] = buckets[hour] + (g.result[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
        }
        return buckets.toList()
    }

    suspend fun readRange(from: LocalDate, to: LocalDate): List<HcDay> {
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
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        return groups.map { g ->
            HcDay(
                date = g.startTime.toLocalDate(),
                steps = g.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                distanceMeters = g.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
                calories = g.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
            )
        }
    }
}
