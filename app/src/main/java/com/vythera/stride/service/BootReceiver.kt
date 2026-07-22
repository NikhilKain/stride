package com.vythera.stride.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vythera.stride.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts background tracking after a reboot when the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        Graph.ensureInit(context)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (Graph.prefs.snapshot().backgroundTracking) {
                    runCatching { StepTrackingService.start(context) }
                }
            } finally {
                result.finish()
            }
        }
    }
}
