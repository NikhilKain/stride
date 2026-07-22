package com.vythera.stride.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.vythera.stride.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vythera.stride.Graph
import com.vythera.stride.model.HcState
import com.vythera.stride.ui.components.GoalEditor
import com.vythera.stride.ui.components.bob
import com.vythera.stride.ui.components.entrance
import com.vythera.stride.ui.components.pulse
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class OnboardingViewModel : ViewModel() {
    var goal by androidx.compose.runtime.mutableIntStateOf(8000)
    var heightCm by androidx.compose.runtime.mutableIntStateOf(170)

    fun hcAvailability(): HcState = Graph.healthConnect.availability()
    fun hcPermissions() = Graph.healthConnect.permissions
    fun hcContract() = Graph.healthConnect.permissionContract()

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            Graph.prefs.setDailyGoal(goal)
            Graph.prefs.setHeightCm(heightCm)
            Graph.prefs.setOnboardingDone(true)
            runCatching { Graph.repository.sync() }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val hcLauncher = rememberLauncherForActivityResult(viewModel.hcContract()) {
        scope.launch { pagerState.animateScrollToPage(2) }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true
        ) { page ->
            // Parallax: pages fade and shrink slightly while swiping
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue.coerceIn(0f, 1f)
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - pageOffset * 0.35f
                    scaleX = 1f - pageOffset * 0.06f
                    scaleY = 1f - pageOffset * 0.06f
                }
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> HealthPage(
                        hcState = viewModel.hcAvailability(),
                        onConnect = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (Build.VERSION.SDK_INT >= 29) {
                                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            hcLauncher.launch(viewModel.hcPermissions())
                        },
                        onSkip = {
                            if (Build.VERSION.SDK_INT >= 29) {
                                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                    )
                    2 -> GoalPage(viewModel)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val width by animateDpAsState(
                        targetValue = if (pagerState.currentPage == i) 28.dp else 8.dp,
                        label = "dot$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        viewModel.complete(onDone)
                    }
                },
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.animateContentSize()
            ) {
                if (pagerState.currentPage < 2) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.next_page)
                    )
                } else {
                    Text(stringResource(R.string.lets_stride))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The demo ring endlessly fills and relaxes while the walker bobs
        val demoProgress by rememberInfiniteTransition(label = "demoRing").animateFloat(
            initialValue = 0.15f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                tween(2600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "demoProgress"
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.entrance(0)) {
            CircularWavyProgressIndicator(
                progress = { demoProgress },
                modifier = Modifier.size(200.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Icon(
                Icons.Rounded.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp).bob(amplitude = 7.dp, durationMs = 1400)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            "Stride",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.entrance(1)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(2)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HealthPage(hcState: HcState, onConnect: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .pulse(from = 0.97f, to = 1.04f, durationMs = 1900)
                .size(140.dp)
                .clip(MaterialShapes.Clover8Leaf.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.steps_your_data),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when (hcState) {
                HcState.AVAILABLE -> stringResource(R.string.hc_rationale_available)
                HcState.NEEDS_UPDATE -> stringResource(R.string.hc_rationale_update)
                else -> stringResource(R.string.hc_rationale_unavailable)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        if (hcState == HcState.AVAILABLE) {
            Button(onClick = onConnect) { Text(stringResource(R.string.hc_connect_title)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.use_sensor_instead)) }
        } else {
            Button(onClick = onSkip) { Text(stringResource(R.string.use_builtin_sensor)) }
        }
    }
}

@Composable
private fun GoalPage(viewModel: OnboardingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.set_your_pace),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))
        GoalEditor(goal = viewModel.goal, onGoalChange = { viewModel.goal = it })
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.height_value, viewModel.heightCm),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        com.vythera.stride.ui.components.StrideSlider(
            value = viewModel.heightCm.toFloat(),
            onValueChange = { viewModel.heightCm = it.toInt() },
            valueRange = 120f..220f
        )
        Text(
            stringResource(R.string.height_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
