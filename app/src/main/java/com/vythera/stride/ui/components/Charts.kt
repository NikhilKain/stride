package com.vythera.stride.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

data class DayBar(
    val date: LocalDate,
    val steps: Long,
    val goal: Int
) {
    val goalMet: Boolean get() = goal > 0 && steps >= goal
}

/**
 * Zenith-style weekly chart: rounded pill bars, seal-check badge riding the
 * top of every goal-met bar, springy staggered height animation.
 */
@Composable
fun WeeklyBarChart(
    days: List<DayBar>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 190.dp
) {
    val maxSteps = (days.maxOfOrNull { it.steps } ?: 0L)
        .coerceAtLeast(days.firstOrNull()?.goal?.toLong() ?: 1L)
        .coerceAtLeast(1L)
    val today = LocalDate.now()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight + 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = day.date == selected
            val isToday = day.date == today
            // Staggered spring: bars rise one after another on entry and re-spring on data change
            val barAnim = remember(day.date) { Animatable(0.045f) }
            val target = (day.steps.toFloat() / maxSteps).coerceIn(0.045f, 1f)
            LaunchedEffect(target) {
                delay(index * 45L)
                barAnim.animateTo(
                    target,
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            val fraction = barAnim.value
            val barColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                day.goalMet -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val interaction = remember { MutableInteractionSource() }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interaction,
                        indication = null
                    ) { onSelect(day.date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight * fraction)
                            .clip(RoundedCornerShape(50))
                            .background(barColor),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (day.goalMet) {
                            Spacer(Modifier.height(4.dp))
                            SealBadge(
                                icon = Icons.Rounded.Check,
                                size = 26.dp,
                                background = if (isSelected || isToday)
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                iconTint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = day.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(width = 14.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                )
            }
        }
    }
}

/** Month calendar heatmap: intensity = steps vs goal. */
@Composable
fun MonthHeatmap(
    month: YearMonth,
    stepsByDay: Map<LocalDate, DayBar>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstDay = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = (firstDay.dayOfWeek.value + 6) % 7 // Monday-first
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    text = dow.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7
        var dayCursor = 1
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    if (cellIndex < leadingBlanks || dayCursor > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayCursor)
                        val bar = stepsByDay[date]
                        val intensity = bar?.let {
                            (it.steps.toFloat() / it.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                        } ?: 0f
                        val isFuture = date.isAfter(today)
                        val isSelected = date == selected
                        val cellColor = when {
                            isFuture -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                            intensity <= 0.02f -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> lerp(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary,
                                intensity
                            )
                        }
                        val onCell = when {
                            isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            intensity > 0.55f -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(if (isSelected) 50 else 30))
                                .background(cellColor)
                                .then(
                                    if (isSelected) Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    ) else Modifier
                                )
                                .clickable(enabled = !isFuture) { onSelect(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayCursor.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = onCell
                            )
                        }
                        dayCursor++
                    }
                }
            }
        }
    }
}
