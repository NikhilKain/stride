package com.vythera.stride.ui.screens.dashboard

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.vythera.stride.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vythera.stride.Graph
import com.vythera.stride.domain.StreakInfo
import com.vythera.stride.domain.Streaks
import com.vythera.stride.model.DailyStats
import com.vythera.stride.model.HcState
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.share.ShareCardRenderer
import com.vythera.stride.share.ShareColors
import com.vythera.stride.ui.components.AnimatedCounter
import com.vythera.stride.ui.components.ConfettiBurst
import com.vythera.stride.ui.components.GoalEditor
import com.vythera.stride.ui.components.StatChip
import com.vythera.stride.ui.components.bouncyClickable
import com.vythera.stride.ui.components.entrance
import com.vythera.stride.ui.components.pulse
import com.vythera.stride.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DashboardUiState(
    val stats: DailyStats = DailyStats.empty(),
    val prefs: StridePrefs = StridePrefs(),
    val streak: StreakInfo = StreakInfo(0, 0),
    val weekSteps: Long = 0,
    val yesterdaySteps: Long = 0,
    val sevenDayAvg: Long = 0,
    val hcState: HcState = HcState.UNAVAILABLE
)

class DashboardViewModel : ViewModel() {
    private val repo = Graph.repository
    private val prefs = Graph.prefs
    private val hc = Graph.healthConnect

    private val hcStateFlow = MutableStateFlow(HcState.UNAVAILABLE)

    val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.observeToday(),
        prefs.prefs,
        repo.observeRange(LocalDate.now().minusDays(8), LocalDate.now()),
        hcStateFlow
    ) { stats, p, recent, hcState ->
        val yesterday = LocalDate.now().minusDays(1).toEpochDay()
        val prev7 = recent.filter { it.epochDay < LocalDate.now().toEpochDay() }
        val avg = if (prev7.isEmpty()) 0L else prev7.sumOf { it.steps } / prev7.size
        DashboardUiState(
            stats = stats,
            prefs = p,
            streak = Streaks.compute(recent),
            weekSteps = Streaks.weekSteps(recent),
            yesterdaySteps = recent.firstOrNull { it.epochDay == yesterday }?.steps ?: 0L,
            sevenDayAvg = avg,
            hcState = hcState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val celebration = MutableStateFlow(0)

    init {
        refreshHcState()
        viewModelScope.launch {
            repo.goalCelebration.collect { celebration.value += 1 }
        }
        refresh()
    }

    fun refreshHcState() {
        viewModelScope.launch {
            hcStateFlow.value = if (repo.hcGranted()) HcState.GRANTED else hc.availability()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            runCatching { repo.sync() }
            // let the springs breathe so the refresh doesn't flicker
            delay(350)
            isRefreshing.value = false
        }
    }

    fun setGoal(goal: Int) {
        viewModelScope.launch { prefs.setDailyGoal(goal) }
    }

    fun stopBackgroundTracking(context: Context) {
        com.vythera.stride.service.StepTrackingService.stop(context)
        viewModelScope.launch { prefs.setBackgroundTracking(false) }
    }

    fun hcPermissions() = hc.permissions
    fun hcContract() = hc.permissionContract()

    suspend fun buildShareIntent(
        context: Context,
        colors: ShareColors
    ): android.content.Intent {
        val state = uiState.value
        val file = withContext(Dispatchers.Default) {
            ShareCardRenderer.render(
                context = context,
                stats = state.stats,
                streak = state.streak.current,
                distanceLabel = Formatters.distance(state.stats.distanceMeters, state.prefs.unit),
                caloriesLabel = Formatters.caloriesShort(state.stats.calories),
                activeLabel = state.stats.activeMinutes.toString(),
                colors = colors
            )
        }
        return ShareCardRenderer.shareIntent(context, file)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    onOpenAwards: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showGoalSheet by remember { mutableStateOf(false) }
    val goalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val permissionLauncher = rememberLauncherForActivityResult(viewModel.hcContract()) {
        viewModel.refreshHcState()
        viewModel.refresh()
    }

    // Haptic tick every 1,000 steps
    var lastBucket by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.stats.steps) {
        val bucket = (state.stats.steps / 1000).toInt()
        if (lastBucket in 0 until bucket) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastBucket = bucket
    }

    val shareColors = shareColorsFromTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                DashboardHeader(streak = state.streak.current, modifier = Modifier.entrance(0))

                if (state.prefs.backgroundTracking) {
                    TrackingBanner(
                        onStop = { viewModel.stopBackgroundTracking(context) },
                        modifier = Modifier.entrance(0)
                    )
                }

                HeroCard(
                    stats = state.stats,
                    celebrationKey = celebration,
                    onEditGoal = { showGoalSheet = true },
                    modifier = Modifier.entrance(1)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip(
                        icon = Icons.Rounded.Route,
                        value = Formatters.distance(state.stats.distanceMeters, state.prefs.unit),
                        label = stringResource(R.string.distance),
                        tint = MaterialTheme.colorScheme.primary,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f).entrance(2)
                    )
                    StatChip(
                        icon = Icons.Rounded.LocalFireDepartment,
                        value = Formatters.caloriesShort(state.stats.calories),
                        label = stringResource(R.string.calories),
                        tint = MaterialTheme.colorScheme.tertiary,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f).entrance(3)
                    )
                    StatChip(
                        icon = Icons.Rounded.Timer,
                        value = "${state.stats.activeMinutes}",
                        label = stringResource(R.string.active_min),
                        tint = MaterialTheme.colorScheme.secondary,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f).entrance(4)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard(
                        title = stringResource(R.string.yesterday),
                        value = Formatters.steps(state.yesterdaySteps),
                        modifier = Modifier.weight(1f).entrance(5)
                    )
                    SmallInfoCard(
                        title = stringResource(R.string.seven_day_avg),
                        value = Formatters.steps(state.sevenDayAvg),
                        modifier = Modifier.weight(1f).entrance(6)
                    )
                }

                if (state.prefs.weeklyGoal > 0) {
                    WeeklyGoalCard(
                        weekSteps = state.weekSteps,
                        weeklyGoal = state.prefs.weeklyGoal,
                        modifier = Modifier.entrance(7)
                    )
                }

                if (state.hcState != HcState.GRANTED) {
                    ConnectCard(
                        hcState = state.hcState,
                        onConnect = { permissionLauncher.launch(viewModel.hcPermissions()) },
                        modifier = Modifier.entrance(8)
                    )
                }

                QuickActions(
                    modifier = Modifier.entrance(9),
                    onShare = {
                        scope.launch {
                            val intent = viewModel.buildShareIntent(context, shareColors)
                            context.startActivity(intent)
                        }
                    },
                    onGoal = { showGoalSheet = true },
                    onSync = { viewModel.refresh() },
                    onAwards = onOpenAwards
                )

                Spacer(Modifier.height(120.dp)) // room for floating nav
            }
        }

        ConfettiBurst(
            burstKey = celebration,
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showGoalSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGoalSheet = false },
            sheetState = goalSheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.daily_goal),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                GoalEditor(goal = state.prefs.dailyGoal, onGoalChange = { viewModel.setGoal(it) })
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun shareColorsFromTheme(): ShareColors {
    val scheme = MaterialTheme.colorScheme
    return ShareColors(
        background = scheme.surfaceContainerLowest.toArgb(),
        backgroundEnd = scheme.primaryContainer.toArgb(),
        onBackground = scheme.onSurface.toArgb(),
        primary = scheme.primary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        chip = scheme.surfaceContainerHigh.toArgb(),
        onChip = scheme.onSurface.toArgb()
    )
}

@Composable
private fun DashboardHeader(streak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Stride",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        AnimatedVisibility(visible = streak > 0, enter = fadeIn() + scaleIn()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = stringResource(R.string.streak),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp).pulse(from = 0.92f, to = 1.12f, durationMs = 1100)
                    )
                    Text(
                        text = "$streak",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(
    stats: DailyStats,
    celebrationKey: Int,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = stats.progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "heroProgress"
    )
    val goalHit = stats.goal > 0 && stats.steps >= stats.goal

    // Odometer count-up: every change rolls through intermediate values
    val shownSteps by animateIntAsState(
        targetValue = stats.steps.toInt(),
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "stepCount"
    )

    // Wave breathes gently; goal-hit pumps it to full amplitude
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breatheAmp"
    )

    // Celebration pulse: quick squash-and-overshoot on goal crossing
    val ringPulse = remember { Animatable(1f) }
    LaunchedEffect(celebrationKey) {
        if (celebrationKey > 0) {
            ringPulse.animateTo(1.08f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessHigh))
            ringPulse.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    val ringScale = ringPulse.value

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Box {
            IconButton(onClick = onEditGoal, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.edit_goal),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val ringStroke = remember(density) {
                        with(density) { Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round) }
                    }
                    val trackStroke = remember(density) {
                        with(density) { Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round) }
                    }
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .size(258.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                            },
                        color = if (goalHit) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        stroke = ringStroke,
                        trackStroke = trackStroke,
                        wavelength = 38.dp,
                        amplitude = { if (goalHit) 1f else breathe },
                        waveSpeed = if (goalHit) 26.dp else 10.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedCounter(
                            value = shownSteps.toLong(),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.of_steps_goal, Formatters.steps(stats.goal.toLong())),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = when {
                        goalHit -> stringResource(R.string.goal_crushed)
                        stats.steps == 0L -> stringResource(R.string.first_step_quote)
                        else -> stringResource(
                            R.string.steps_to_go,
                            Formatters.steps((stats.goal - stats.steps).coerceAtLeast(0))
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (goalHit) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SmallInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeeklyGoalCard(weekSteps: Long, weeklyGoal: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.this_week),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${Formatters.compactSteps(weekSteps)} / ${Formatters.compactSteps(weeklyGoal.toLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val density = LocalDensity.current
            val barStroke = remember(density) {
                with(density) { Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round) }
            }
            LinearWavyProgressIndicator(
                progress = { (weekSteps.toFloat() / weeklyGoal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(26.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                stroke = barStroke,
                trackStroke = barStroke,
                amplitude = { 1f },
                wavelength = 32.dp
            )
        }
    }
}

@Composable
private fun ConnectCard(hcState: HcState, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when (hcState) {
                        HcState.AVAILABLE -> stringResource(R.string.hc_connect_title)
                        HcState.NEEDS_UPDATE -> stringResource(R.string.hc_update_title)
                        else -> stringResource(R.string.hc_sensor_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = when (hcState) {
                    HcState.AVAILABLE -> stringResource(R.string.hc_connect_body)
                    HcState.NEEDS_UPDATE -> stringResource(R.string.hc_update_body)
                    else -> stringResource(R.string.hc_sensor_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            if (hcState == HcState.AVAILABLE) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onConnect)
                ) {
                    Text(
                        text = stringResource(R.string.connect),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingBanner(onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pulse(from = 0.8f, to = 1.25f, durationMs = 900)
            )
            Text(
                text = stringResource(R.string.tracking_active),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.clip(CircleShape).clickable(onClick = onStop)
            ) {
                Text(
                    text = stringResource(R.string.stop),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    onShare: () -> Unit,
    onGoal: () -> Unit,
    onSync: () -> Unit,
    onAwards: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickAction(Icons.Rounded.IosShare, stringResource(R.string.action_share), onShare)
        QuickAction(Icons.Rounded.TrackChanges, stringResource(R.string.action_goal), onGoal)
        QuickAction(Icons.Rounded.Sync, stringResource(R.string.action_sync), onSync)
        QuickAction(Icons.Rounded.EmojiEvents, stringResource(R.string.nav_awards), onAwards)
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .bouncyClickable(scaleDown = 0.85f, onClick = onClick)
                .size(64.dp)
                .clip(CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
