package com.vythera.stride.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vythera.stride.util.Formatters

private val presets = listOf(6000, 8000, 10000, 15000)

/** Daily-goal picker: huge live number, preset toggle group, fine-grained slider. */
@Composable
fun GoalEditor(
    goal: Int,
    onGoalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Number bumps with a spring every time the goal changes
        val bump = remember { Animatable(1f) }
        LaunchedEffect(goal) {
            bump.snapTo(1.07f)
            bump.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Formatters.steps(goal.toLong()),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                }
            )
            Text(
                text = androidx.compose.ui.res.stringResource(com.vythera.stride.R.string.steps_per_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val checked = goal == preset
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (it) onGoalChange(preset) },
                    modifier = Modifier.weight(1f),
                    colors = ToggleButtonDefaults.toggleButtonColors()
                ) {
                    Text(Formatters.compactSteps(preset.toLong()))
                }
            }
        }

        StrideSlider(
            value = goal.toFloat(),
            onValueChange = { onGoalChange((it / 500).toInt() * 500) },
            valueRange = 2000f..30000f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}
