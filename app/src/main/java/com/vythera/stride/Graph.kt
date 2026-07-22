package com.vythera.stride

import android.content.Context
import com.vythera.stride.data.db.StrideDatabase
import com.vythera.stride.data.health.HealthConnectManager
import com.vythera.stride.data.health.StepSensorManager
import com.vythera.stride.data.prefs.UserPreferences
import com.vythera.stride.data.repo.StepRepository
import com.vythera.stride.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny hand-rolled service locator — the app is small enough not to need DI. */
object Graph {
    lateinit var prefs: UserPreferences
        private set
    lateinit var database: StrideDatabase
        private set
    lateinit var healthConnect: HealthConnectManager
        private set
    lateinit var stepSensor: StepSensorManager
        private set
    lateinit var notifier: Notifier
        private set
    lateinit var repository: StepRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            prefs = UserPreferences(app)
            database = StrideDatabase.build(app)
            healthConnect = HealthConnectManager(app)
            stepSensor = StepSensorManager(app)
            notifier = Notifier(app)
            repository = StepRepository(healthConnect, stepSensor, database, prefs, notifier, appScope)
            initialized = true
        }
    }
}
