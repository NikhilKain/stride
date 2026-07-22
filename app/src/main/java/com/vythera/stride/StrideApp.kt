package com.vythera.stride

import android.app.Application
import com.vythera.stride.data.work.SyncWorker
import com.vythera.stride.service.StepTrackingService
import kotlinx.coroutines.launch

class StrideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.ensureInit(this)
        Graph.notifier.ensureChannels()
        SyncWorker.schedule(this)
        // Resurrect background tracking / live island if the user has them enabled
        Graph.appScope.launch {
            val p = Graph.prefs.snapshot()
            if (p.backgroundTracking || p.liveUpdates) {
                runCatching { StepTrackingService.start(this@StrideApp) }
            }
        }
    }
}
