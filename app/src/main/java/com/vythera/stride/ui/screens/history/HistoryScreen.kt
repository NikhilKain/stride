package com.vythera.stride.ui.screens.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vythera.stride.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vythera.stride.Graph
import com.vythera.stride.data.db.DailySummaryEntity
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.ui.components.DayBar
import com.vythera.stride.ui.components.MonthHeatmap
import com.vythera.stride.ui.components.WeeklyBarChart
import com.vythera.stride.ui.components.entrance
import com.vythera.stride.util.Formatters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryViewModel : ViewModel() {
    private val repo = Graph.repository

    data class HistoryData(
        val summaries: List<DailySummaryEntity> = emptyList(),
        val prefs: StridePrefs = StridePrefs()
    )

    val data: StateFlow<HistoryData> = combine(
        repo.observeRange(LocalDate.now().minusDays(370), LocalDate.now()),
        Graph.prefs.prefs
    ) { summaries, prefs -> HistoryData(summaries, prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryData())
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var weekOffset by rememberSaveable { mutableStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }

    val byDay = remember(data.summaries, data.prefs.dailyGoal) {
        data.summaries.associate { s ->
            val date = LocalDate.ofEpochDay(s.epochDay)
            date to DayBar(date, s.steps, if (s.goal > 0) s.goal else data.prefs.dailyGoal)
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
            stringResource(R.string.nav_history),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.entrance(0)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.entrance(1)) {
            ToggleButton(checked = !monthMode, onCheckedChange = { if (it) monthMode = false }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.week))
            }
            ToggleButton(checked = monthMode, onCheckedChange = { if (it) monthMode = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.month))
            }
        }

        AnimatedContent(
            targetState = monthMode,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f) +
                    slideInVertically { it / 20 }) togetherWith fadeOut(tween(140))
            },
            modifier = Modifier.entrance(2),
            label = "historyMode"
        ) { isMonth ->
            if (!isMonth) {
                WeekSection(
                    byDay = byDay,
                    weekOffset = weekOffset,
                    goal = data.prefs.dailyGoal,
                    selected = selected,
                    onSelect = { selected = it },
                    onOffsetChange = { weekOffset = it }
                )
            } else {
                MonthSection(
                    byDay = byDay,
                    month = month,
                    selected = selected,
                    onSelect = { selected = it },
                    onMonthChange = { month = it }
                )
            }
        }

        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 12 }) togetherWith fadeOut(tween(120))
            },
            modifier = Modifier.entrance(3),
            label = "dayDetail"
        ) { date ->
            if (date != null) {
                DayDetailCard(date = date, bar = byDay[date], prefs = data.prefs)
            }
        }

        TotalsCard(
            byDay = byDay, monthMode = monthMode, month = month, weekOffset = weekOffset,
            modifier = Modifier.entrance(4)
        )

        Spacer(Modifier.height(120.dp))
    }

}

private fun weekStart(offset: Int): LocalDate {
    val today = LocalDate.now()
    val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
    return monday.plusWeeks(offset.toLong())
}

@Composable
private fun WeekSection(
    byDay: Map<LocalDate, DayBar>,
    weekOffset: Int,
    goal: Int,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onOffsetChange: (Int) -> Unit
) {
    val start = weekStart(weekOffset)
    val days = (0..6).map { i ->
        val date = start.plusDays(i.toLong())
        byDay[date] ?: DayBar(date, 0, goal)
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onOffsetChange(weekOffset - 1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    text = if (weekOffset == 0) stringResource(R.string.this_week)
                    else "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${
                        start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))
                    }",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { onOffsetChange(weekOffset + 1) }, enabled = weekOffset < 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next))
                }
            }
            WeeklyBarChart(days = days, selected = selected, onSelect = onSelect)
        }
    }
}

@Composable
private fun MonthSection(
    byDay: Map<LocalDate, DayBar>,
    month: YearMonth,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = { onMonthChange(month.plusMonths(1)) },
                    enabled = month < YearMonth.now()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next))
                }
            }
            MonthHeatmap(month = month, stepsByDay = byDay, selected = selected, onSelect = onSelect)
        }
    }
}

@Composable
private fun DayDetailCard(date: LocalDate, bar: DayBar?, prefs: StridePrefs) {
    val steps = bar?.steps ?: 0L
    val distance = steps * prefs.strideMeters
    val calories = steps * 0.04 * (prefs.weightKg / 70.0)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.day_steps, Formatters.steps(steps)),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailStat(stringResource(R.string.distance), Formatters.distance(distance, prefs.unit))
                DetailStat(stringResource(R.string.calories), Formatters.calories(calories))
                DetailStat(
                    stringResource(R.string.active_min),
                    stringResource(R.string.active_short, (steps / 110).toInt())
                )
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun TotalsCard(
    byDay: Map<LocalDate, DayBar>,
    monthMode: Boolean,
    month: YearMonth,
    weekOffset: Int,
    modifier: Modifier = Modifier
) {
    val days = if (monthMode) {
        byDay.values.filter { YearMonth.from(it.date) == month }
    } else {
        val start = weekStart(weekOffset)
        byDay.values.filter { !it.date.isBefore(start) && !it.date.isAfter(start.plusDays(6)) }
    }
    val total = days.sumOf { it.steps }
    val best = days.maxByOrNull { it.steps }
    val goalsMet = days.count { it.goalMet }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TotalStat(stringResource(R.string.total), Formatters.compactSteps(total))
            TotalStat(stringResource(R.string.best_day), best?.let { Formatters.compactSteps(it.steps) } ?: "—")
            TotalStat(stringResource(R.string.goals_met), "$goalsMet")
        }
    }
}

@Composable
private fun TotalStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
