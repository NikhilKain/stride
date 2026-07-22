package com.vythera.stride.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vythera.stride.Graph
import com.vythera.stride.R
import com.vythera.stride.data.backup.BackupManager
import com.vythera.stride.data.update.UpdateChecker
import com.vythera.stride.data.update.UpdateInfo
import com.vythera.stride.model.AppFont
import com.vythera.stride.model.ColorStyle
import com.vythera.stride.model.HcState
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.model.ThemeMode
import com.vythera.stride.model.UnitSystem
import com.vythera.stride.model.UpdateFrequency
import com.vythera.stride.service.StepTrackingService
import com.vythera.stride.ui.components.GoalEditor
import com.vythera.stride.ui.components.StrideSlider
import com.vythera.stride.ui.components.UpdateDialog
import com.vythera.stride.ui.components.bouncyClickable
import com.vythera.stride.ui.components.entrance
import com.vythera.stride.ui.theme.StridePalettes
import com.vythera.stride.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val prefs = Graph.prefs
    private val hc = Graph.healthConnect

    val state: StateFlow<StridePrefs> =
        prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StridePrefs())

    val hcState = MutableStateFlow(HcState.UNAVAILABLE)

    init { refreshHc() }

    fun refreshHc() {
        viewModelScope.launch {
            hcState.value = if (Graph.repository.hcGranted()) HcState.GRANTED else hc.availability()
        }
    }

    fun hcPermissions() = hc.permissions
    fun hcContract() = hc.permissionContract()

    fun set(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    val p = prefs
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val hcState by viewModel.hcState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hcLauncher = rememberLauncherForActivityResult(viewModel.hcContract()) {
        viewModel.refreshHc()
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    // The health foreground-service type needs this granted at runtime,
    // otherwise startForeground throws and the service dies.
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runCatching { StepTrackingService.start(context) }
    }

    fun ensureActivityPermission(): Boolean {
        val granted = Build.VERSION.SDK_INT < 29 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return granted
    }

    val exportedMsg = stringResource(R.string.backup_exported)
    val backupFailedMsg = stringResource(R.string.backup_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            BackupManager.export(context, uri)
                .onSuccess { Toast.makeText(context, exportedMsg, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, backupFailedMsg, Toast.LENGTH_SHORT).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            BackupManager.import(context, uri)
                .onSuccess { days ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_imported, days),
                        Toast.LENGTH_SHORT
                    ).show()
                    Graph.appScope.launch { runCatching { Graph.repository.syncToday() } }
                }
                .onFailure { Toast.makeText(context, backupFailedMsg, Toast.LENGTH_SHORT).show() }
        }
    }

    var showGoalDialog by remember { mutableStateOf(false) }
    var sliderDialog by remember { mutableStateOf<SliderDialogSpec?>(null) }

    // Update checking
    var updateChecking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    val upToDateMsg = stringResource(R.string.update_up_to_date)
    val updateFailedMsg = stringResource(R.string.update_failed)

    fun checkForUpdates() {
        updateChecking = true
        updateStatus = null
        scope.launch {
            val result = UpdateChecker.fetchLatest()
            Graph.prefs.markUpdateChecked()
            updateChecking = false
            result.fold(
                onSuccess = { info ->
                    if (info != null && UpdateChecker.isNewer(info.version)) {
                        foundUpdate = info
                        updateStatus = null
                    } else {
                        updateStatus = upToDateMsg
                    }
                },
                onFailure = { updateStatus = updateFailedMsg }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.entrance(0)
        )

        SettingsCard(title = stringResource(R.string.appearance), modifier = Modifier.entrance(1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    ToggleButton(
                        checked = prefs.themeMode == mode,
                        onCheckedChange = { if (it) viewModel.set { viewModel.p.setThemeMode(mode) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingRow(
                    title = stringResource(R.string.wallpaper_colors),
                    subtitle = stringResource(R.string.wallpaper_colors_sub),
                    icon = Icons.Rounded.Wallpaper
                ) {
                    IconSwitch(
                        checked = prefs.dynamicColor,
                        icon = Icons.Rounded.Palette,
                        onCheckedChange = { on -> viewModel.set { viewModel.p.setDynamicColor(on) } }
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.amoled_black),
                subtitle = stringResource(R.string.amoled_black_sub),
                icon = Icons.Rounded.Contrast
            ) {
                IconSwitch(
                    checked = prefs.amoled,
                    icon = Icons.Rounded.Contrast,
                    onCheckedChange = { on -> viewModel.set { viewModel.p.setAmoled(on) } }
                )
            }
            if (!prefs.dynamicColor) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StridePalettes.forEach { palette ->
                        val selected = prefs.paletteId == palette.id
                        val dotScale by animateFloatAsState(
                            targetValue = if (selected) 1.18f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "palette${palette.id}"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = dotScale
                                    scaleY = dotScale
                                }
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(palette.seed)
                                .then(
                                    if (selected) Modifier.border(
                                        3.dp, MaterialTheme.colorScheme.onSurface, CircleShape
                                    ) else Modifier
                                )
                                .bouncyClickable { viewModel.set { viewModel.p.setPaletteId(palette.id) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = palette.label,
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.color_style),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                ColorStyle.entries.forEach { style ->
                    ToggleButton(
                        checked = prefs.colorStyle == style,
                        onCheckedChange = { if (it) viewModel.set { viewModel.p.setColorStyle(style) } }
                    ) {
                        Text(
                            when (style) {
                                ColorStyle.TONAL_SPOT -> stringResource(R.string.style_tonal_spot)
                                ColorStyle.NEUTRAL -> stringResource(R.string.style_neutral)
                                ColorStyle.MONOCHROME -> stringResource(R.string.style_monochrome)
                                ColorStyle.VIBRANT -> stringResource(R.string.style_vibrant)
                                ColorStyle.EXPRESSIVE -> stringResource(R.string.style_expressive)
                            }
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.font),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                AppFont.entries.forEach { font ->
                    ToggleButton(
                        checked = prefs.appFont == font,
                        onCheckedChange = { if (it) viewModel.set { viewModel.p.setAppFont(font) } }
                    ) {
                        Text(
                            when (font) {
                                AppFont.SYSTEM -> stringResource(R.string.font_system)
                                AppFont.NUNITO -> stringResource(R.string.font_nunito)
                                AppFont.INTER -> stringResource(R.string.font_inter)
                                AppFont.OUTFIT -> stringResource(R.string.font_outfit)
                                AppFont.LEXEND -> stringResource(R.string.font_lexend)
                                AppFont.MANROPE -> stringResource(R.string.font_manrope)
                                AppFont.GROTESK -> stringResource(R.string.font_grotesk)
                            }
                        )
                    }
                }
            }
        }

        SettingsCard(title = stringResource(R.string.tracking), modifier = Modifier.entrance(2)) {
            // Android 16+ Live Updates: promoted ongoing notification, shown as a
            // status-bar chip where the system UI supports it.
            if (Build.VERSION.SDK_INT >= 36) {
                SettingRow(
                    title = stringResource(R.string.live_updates),
                    subtitle = stringResource(R.string.live_updates_sub),
                    icon = Icons.Rounded.Splitscreen
                ) {
                    IconSwitch(
                        checked = prefs.liveUpdates,
                        icon = Icons.Rounded.Splitscreen,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.set { viewModel.p.setLiveUpdates(on) }
                            if (on && ensureActivityPermission()) {
                                runCatching { StepTrackingService.start(context) }
                            }
                        }
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.background_tracking),
                subtitle = stringResource(R.string.background_tracking_sub),
                icon = Icons.AutoMirrored.Rounded.DirectionsRun
            ) {
                IconSwitch(
                    checked = prefs.backgroundTracking,
                    icon = Icons.Rounded.DirectionsWalk,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.set { viewModel.p.setBackgroundTracking(on) }
                        if (on) {
                            if (ensureActivityPermission()) StepTrackingService.start(context)
                        } else {
                            StepTrackingService.stop(context)
                        }
                    }
                )
            }
        }

        SettingsCard(title = stringResource(R.string.goals_body), modifier = Modifier.entrance(3)) {
            SettingRow(
                title = stringResource(R.string.daily_goal),
                subtitle = stringResource(R.string.steps_value, Formatters.steps(prefs.dailyGoal.toLong())),
                icon = Icons.Rounded.TrackChanges,
                onClick = { showGoalDialog = true }
            )
            val offLabel = stringResource(R.string.off)
            SettingRow(
                title = stringResource(R.string.weekly_goal),
                icon = Icons.Rounded.CalendarMonth,
                subtitle = if (prefs.weeklyGoal > 0)
                    stringResource(R.string.steps_value, Formatters.steps(prefs.weeklyGoal.toLong()))
                else offLabel,
                onClick = {
                    sliderDialog = SliderDialogSpec(
                        titleRes = R.string.weekly_goal,
                        value = prefs.weeklyGoal.toFloat(),
                        range = 0f..200000f,
                        step = 5000f,
                        format = { v -> if (v <= 0f) offLabel else Formatters.steps(v.toLong()) },
                        onDone = { v -> viewModel.set { viewModel.p.setWeeklyGoal(v.toInt()) } }
                    )
                }
            )
            SettingRow(
                title = stringResource(R.string.height),
                subtitle = stringResource(R.string.height_sub, prefs.heightCm),
                icon = Icons.Rounded.Height,
                onClick = {
                    sliderDialog = SliderDialogSpec(
                        titleRes = R.string.height,
                        value = prefs.heightCm.toFloat(),
                        range = 120f..220f,
                        step = 1f,
                        format = { v -> "${v.toInt()} cm" },
                        onDone = { v -> viewModel.set { viewModel.p.setHeightCm(v.toInt()) } }
                    )
                }
            )
            SettingRow(
                title = stringResource(R.string.weight_label),
                subtitle = stringResource(R.string.weight_sub, prefs.weightKg),
                icon = Icons.Rounded.MonitorWeight,
                onClick = {
                    sliderDialog = SliderDialogSpec(
                        titleRes = R.string.weight_label,
                        value = prefs.weightKg.toFloat(),
                        range = 35f..180f,
                        step = 1f,
                        format = { v -> "${v.toInt()} kg" },
                        onDone = { v -> viewModel.set { viewModel.p.setWeightKg(v.toInt()) } }
                    )
                }
            )
            val autoLabel = stringResource(R.string.auto)
            SettingRow(
                title = stringResource(R.string.stride_length),
                icon = Icons.Rounded.Straighten,
                subtitle = if (prefs.strideOverrideCm > 0)
                    stringResource(R.string.stride_manual, prefs.strideOverrideCm)
                else stringResource(R.string.stride_auto, (prefs.strideMeters * 100).toInt()),
                onClick = {
                    sliderDialog = SliderDialogSpec(
                        titleRes = R.string.stride_length,
                        value = prefs.strideOverrideCm.toFloat(),
                        range = 0f..120f,
                        step = 1f,
                        format = { v -> if (v <= 0f) autoLabel else "${v.toInt()} cm" },
                        onDone = { v -> viewModel.set { viewModel.p.setStrideOverrideCm(v.toInt()) } }
                    )
                }
            )
        }

        SettingsCard(title = stringResource(R.string.units), modifier = Modifier.entrance(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToggleButton(
                    checked = prefs.unit == UnitSystem.METRIC,
                    onCheckedChange = { if (it) viewModel.set { viewModel.p.setUnit(UnitSystem.METRIC) } },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.kilometres)) }
                ToggleButton(
                    checked = prefs.unit == UnitSystem.IMPERIAL,
                    onCheckedChange = { if (it) viewModel.set { viewModel.p.setUnit(UnitSystem.IMPERIAL) } },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.miles)) }
            }
        }

        SettingsCard(title = stringResource(R.string.health_connect), modifier = Modifier.entrance(5)) {
            SettingRow(
                icon = Icons.Rounded.FavoriteBorder,
                title = when (hcState) {
                    HcState.GRANTED -> stringResource(R.string.hc_connected)
                    HcState.AVAILABLE -> stringResource(R.string.hc_not_connected)
                    HcState.NEEDS_UPDATE -> stringResource(R.string.hc_update_required)
                    HcState.UNAVAILABLE -> stringResource(R.string.hc_unavailable)
                },
                subtitle = when (hcState) {
                    HcState.GRANTED -> stringResource(R.string.hc_connected_sub)
                    HcState.AVAILABLE -> stringResource(R.string.hc_not_connected_sub)
                    HcState.NEEDS_UPDATE -> stringResource(R.string.hc_update_sub)
                    HcState.UNAVAILABLE -> stringResource(R.string.hc_unavailable_sub)
                },
                onClick = when (hcState) {
                    HcState.AVAILABLE -> ({ hcLauncher.launch(viewModel.hcPermissions()) })
                    HcState.GRANTED -> ({
                        runCatching {
                            context.startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
                        }
                        Unit
                    })
                    else -> null
                }
            )
        }

        SettingsCard(title = stringResource(R.string.notifications), modifier = Modifier.entrance(6)) {
            SettingRow(
                title = stringResource(R.string.goal_reached),
                subtitle = stringResource(R.string.goal_reached_sub),
                icon = Icons.Rounded.Celebration
            ) {
                IconSwitch(
                    checked = prefs.notifGoal,
                    icon = Icons.Rounded.Celebration,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.set { viewModel.p.setNotifGoal(on) }
                    }
                )
            }
            SettingRow(
                title = stringResource(R.string.evening_nudge),
                subtitle = stringResource(R.string.evening_nudge_sub),
                icon = Icons.Rounded.NotificationsActive
            ) {
                IconSwitch(
                    checked = prefs.notifNudge,
                    icon = Icons.Rounded.NotificationsActive,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.set { viewModel.p.setNotifNudge(on) }
                    }
                )
            }
        }

        SettingsCard(title = stringResource(R.string.data), modifier = Modifier.entrance(7)) {
            SettingRow(
                title = stringResource(R.string.export_backup),
                subtitle = stringResource(R.string.export_backup_sub),
                icon = Icons.Rounded.Upload,
                onClick = { exportLauncher.launch("stride-backup.json") }
            )
            SettingRow(
                title = stringResource(R.string.import_backup),
                subtitle = stringResource(R.string.import_backup_sub),
                icon = Icons.Rounded.Download,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            SettingsCard(title = stringResource(R.string.language), modifier = Modifier.entrance(8)) {
                SettingRow(
                    title = stringResource(R.string.app_language),
                    subtitle = stringResource(R.string.app_language_sub),
                    icon = Icons.Rounded.Language,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                            )
                        }
                        Unit
                    }
                )
            }
        }

        SettingsCard(title = stringResource(R.string.updates), modifier = Modifier.entrance(9)) {
            SettingRow(
                title = stringResource(R.string.check_updates),
                subtitle = when {
                    updateChecking -> stringResource(R.string.update_checking)
                    updateStatus != null -> updateStatus!!
                    else -> stringResource(R.string.check_updates_sub)
                },
                icon = Icons.Rounded.SystemUpdate,
                onClick = if (updateChecking) null else ({ checkForUpdates() })
            )
            Text(
                stringResource(R.string.update_frequency),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                UpdateFrequency.entries.forEach { freq ->
                    ToggleButton(
                        checked = prefs.updateFrequency == freq,
                        onCheckedChange = { if (it) viewModel.set { viewModel.p.setUpdateFrequency(freq) } }
                    ) {
                        Text(
                            when (freq) {
                                UpdateFrequency.NEVER -> stringResource(R.string.freq_never)
                                UpdateFrequency.DAILY -> stringResource(R.string.freq_daily)
                                UpdateFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
                                UpdateFrequency.MONTHLY -> stringResource(R.string.freq_monthly)
                            }
                        )
                    }
                }
            }
        }

        SettingsCard(title = stringResource(R.string.about), modifier = Modifier.entrance(10)) {
            SettingRow(
                title = "Stride",
                subtitle = stringResource(R.string.about_sub, com.vythera.stride.BuildConfig.VERSION_NAME),
                icon = Icons.Rounded.Info
            )
        }

        Spacer(Modifier.height(120.dp))
    }

    foundUpdate?.let { info ->
        UpdateDialog(info = info, onDismiss = { foundUpdate = null })
    }

    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            confirmButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text(stringResource(R.string.done)) }
            },
            title = { Text(stringResource(R.string.daily_goal)) },
            text = {
                GoalEditor(
                    goal = prefs.dailyGoal,
                    onGoalChange = { g -> viewModel.set { viewModel.p.setDailyGoal(g) } }
                )
            }
        )
    }

    sliderDialog?.let { spec ->
        SliderDialog(spec = spec, onDismiss = { sliderDialog = null })
    }
}

private data class SliderDialogSpec(
    val titleRes: Int,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val format: (Float) -> String,
    val onDone: (Float) -> Unit
)

@Composable
private fun SliderDialog(spec: SliderDialogSpec, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(spec.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                spec.onDone(value)
                onDismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(spec.titleRes)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = spec.format(value),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                StrideSlider(
                    value = value,
                    onValueChange = { v -> value = (v / spec.step).toInt() * spec.step },
                    valueRange = spec.range
                )
            }
        }
    )
}

@Composable
private fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .bouncyClickable(scaleDown = 0.97f, onClick = onClick)
                else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/** Switch with the feature's icon riding inside the thumb. */
@Composable
private fun IconSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}
