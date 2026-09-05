package com.vythera.stride.data.health

import java.time.LocalDate

/**
 * A destination Stride can publish its daily totals to.
 *
 * Health Connect is the only implementation that ships today. The interface
 * exists because it is not the only one users ask for: Huawei phones and watches
 * (GT series, Band series) have no Google Mobile Services, so Health Connect is
 * absent and their data lives in Huawei Health instead. A Huawei Health Service
 * Kit sink implements this and registers alongside — the repository does not
 * change. See docs/HUAWEI_HEALTH.md for what that integration still needs.
 */
interface HealthExporter {

    /** Stable identifier, used in logs and settings copy. */
    val id: String

    /** True when the underlying provider exists on this device at all. */
    suspend fun isAvailable(): Boolean

    /** True when the user has granted this sink the permissions it needs. */
    suspend fun isConnected(): Boolean

    /**
     * Publishes one day's totals, replacing anything this app wrote for that day.
     * Implementations must be idempotent — this is called repeatedly as the day
     * accumulates. Returns true when the write landed.
     */
    suspend fun write(
        day: LocalDate,
        steps: Long,
        distanceMeters: Double,
        calories: Double
    ): Boolean
}

/** Health Connect sink — thin adapter over [HealthConnectManager]. */
class HealthConnectExporter(private val hc: HealthConnectManager) : HealthExporter {

    override val id: String = "health_connect"

    override suspend fun isAvailable(): Boolean =
        hc.availability() == com.vythera.stride.model.HcState.AVAILABLE

    override suspend fun isConnected(): Boolean = hc.hasWritePermissions()

    override suspend fun write(
        day: LocalDate,
        steps: Long,
        distanceMeters: Double,
        calories: Double
    ): Boolean = hc.writeDay(day, steps, distanceMeters, calories)
}
