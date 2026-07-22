package com.vythera.stride.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vythera.stride.Graph
import com.vythera.stride.R
import com.vythera.stride.data.update.UpdateChecker
import com.vythera.stride.data.update.UpdateInfo
import com.vythera.stride.model.UpdateFrequency
import kotlinx.coroutines.launch

/**
 * Runs a release check when one is due for the chosen frequency, and shows the
 * result. Silent when there's nothing new or the network is unavailable.
 */
@Composable
fun UpdateGate() {
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        val prefs = Graph.prefs
        val freq = prefs.snapshot().updateFrequency
        if (freq == UpdateFrequency.NEVER) return@LaunchedEffect

        val elapsed = System.currentTimeMillis() - prefs.lastUpdateCheck()
        if (elapsed < freq.intervalMillis) return@LaunchedEffect

        prefs.markUpdateChecked()
        val latest = UpdateChecker.fetchLatest().getOrNull() ?: return@LaunchedEffect
        if (!UpdateChecker.isNewer(latest.version)) return@LaunchedEffect
        if (latest.version == prefs.skippedVersion()) return@LaunchedEffect
        update = latest
    }

    update?.let { info ->
        UpdateDialog(
            info = info,
            onSkip = {
                Graph.appScope.launch { Graph.prefs.setSkippedVersion(info.version) }
                update = null
            },
            onDismiss = { update = null }
        )
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onSkip: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.update_available, info.version)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = info.notes.ifBlank { stringResource(R.string.update_no_notes) },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl ?: info.pageUrl))
                    )
                }
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            TextButton(onClick = { onSkip?.invoke() ?: onDismiss() }) {
                Text(stringResource(if (onSkip != null) R.string.update_skip else R.string.close))
            }
        }
    )
}
