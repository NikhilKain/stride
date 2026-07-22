package com.vythera.stride.ui.screens.awards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vythera.stride.Graph
import com.vythera.stride.domain.AchievementDef
import com.vythera.stride.domain.Achievements
import com.vythera.stride.domain.StreakInfo
import com.vythera.stride.domain.Streaks
import com.vythera.stride.R
import com.vythera.stride.ui.components.bouncyClickable
import com.vythera.stride.ui.components.entrance
import com.vythera.stride.ui.components.pulse
import com.vythera.stride.util.achievementDescription
import com.vythera.stride.util.achievementTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AwardsViewModel : ViewModel() {
    data class AwardsState(
        val unlocked: Map<String, Long> = emptyMap(),
        val streak: StreakInfo = StreakInfo(0, 0)
    )

    val state: StateFlow<AwardsState> = combine(
        Graph.repository.observeAchievements(),
        Graph.repository.observeRange(LocalDate.now().minusDays(370), LocalDate.now())
    ) { unlocked, summaries ->
        AwardsState(
            unlocked = unlocked.associate { it.id to it.unlockedAtEpochDay },
            streak = Streaks.compute(summaries)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AwardsState())
}

@Composable
private fun badgeShape(id: String): Shape = when (id.hashCode().mod(6)) {
    0 -> MaterialShapes.Cookie9Sided.toShape()
    1 -> MaterialShapes.Clover8Leaf.toShape()
    2 -> MaterialShapes.Sunny.toShape()
    3 -> MaterialShapes.Cookie12Sided.toShape()
    4 -> MaterialShapes.Burst.toShape()
    else -> MaterialShapes.Flower.toShape()
}

private fun badgeIcon(id: String): ImageVector = when (id) {
    "first_steps" -> Icons.Rounded.DirectionsWalk
    "day_10k" -> Icons.Rounded.Star
    "day_15k" -> Icons.Rounded.Map
    "day_20k" -> Icons.Rounded.Bolt
    "day_30k" -> Icons.Rounded.WorkspacePremium
    "streak_3" -> Icons.Rounded.WbSunny
    "streak_7" -> Icons.Rounded.LocalFireDepartment
    "streak_14" -> Icons.Rounded.Whatshot
    "streak_30" -> Icons.Rounded.MilitaryTech
    "week_70k" -> Icons.Rounded.CalendarMonth
    "steady_5of7" -> Icons.Rounded.EmojiEvents
    "marathon" -> Icons.Rounded.Public
    "dist_100k" -> Icons.Rounded.Map
    "million" -> Icons.Rounded.Diamond
    else -> Icons.Rounded.Star
}

@Composable
fun AwardsScreen(viewModel: AwardsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<AchievementDef?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 130.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.nav_awards),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.entrance(0)
                )
                Spacer(Modifier.height(16.dp))
                StreakHero(streak = state.streak, modifier = Modifier.entrance(1))
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(Achievements.all, key = { _, def -> def.id }) { index, def ->
            BadgeTile(
                def = def,
                unlockedAt = state.unlocked[def.id],
                onClick = { detail = def },
                modifier = Modifier.entrance(2 + index)
            )
        }
    }

    detail?.let { def ->
        val unlockedAt = state.unlocked[def.id]
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text(stringResource(R.string.close)) }
            },
            icon = {
                BadgeMedallion(def = def, unlocked = unlockedAt != null, size = 72.dp)
            },
            title = { Text(context.achievementTitle(def), textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(context.achievementDescription(def), textAlign = TextAlign.Center)
                    unlockedAt?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.unlocked_on,
                                LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun StreakHero(streak: StreakInfo, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            BadgeCore(
                icon = Icons.Rounded.LocalFireDepartment,
                shape = MaterialShapes.Sunny.toShape(),
                unlocked = streak.current > 0,
                size = 84.dp,
                modifier = if (streak.current > 0) Modifier.pulse(from = 0.96f, to = 1.05f, durationMs = 1400)
                else Modifier
            )
            Column {
                Text(
                    text = if (streak.current == 1) stringResource(R.string.streak_day_one)
                    else stringResource(R.string.streak_days, streak.current),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.streak_current_best, streak.best),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(def: AchievementDef, unlockedAt: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.bouncyClickable(scaleDown = 0.88f, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BadgeMedallion(def = def, unlocked = unlockedAt != null, size = 64.dp)
            Text(
                text = LocalContext.current.achievementTitle(def),
                style = MaterialTheme.typography.labelMedium,
                color = if (unlockedAt != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BadgeMedallion(def: AchievementDef, unlocked: Boolean, size: androidx.compose.ui.unit.Dp) {
    BadgeCore(icon = badgeIcon(def.id), shape = badgeShape(def.id), unlocked = unlocked, size = size)
}

@Composable
private fun BadgeCore(
    icon: ImageVector,
    shape: Shape,
    unlocked: Boolean,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val bg = if (unlocked) Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    ) else Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (unlocked) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
