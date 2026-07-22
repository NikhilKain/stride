package com.vythera.stride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vythera.stride.domain.AchievementDef
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.ui.components.ConfettiBurst
import com.vythera.stride.ui.components.bouncyClickable
import com.vythera.stride.util.achievementTitle
import com.vythera.stride.ui.screens.awards.AwardsScreen
import com.vythera.stride.ui.screens.awards.BadgeMedallion
import com.vythera.stride.ui.screens.dashboard.DashboardScreen
import com.vythera.stride.ui.screens.history.HistoryScreen
import com.vythera.stride.ui.screens.onboarding.OnboardingScreen
import com.vythera.stride.ui.screens.settings.SettingsScreen
import com.vythera.stride.ui.theme.StrideTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        Graph.ensureInit(this)
        enableEdgeToEdge()

        var prefsLoaded = false
        splash.setKeepOnScreenCondition { !prefsLoaded }

        setContent {
            val prefsState by Graph.prefs.prefs
                .map { it as StridePrefs? }
                .collectAsState(initial = null)

            val prefs = prefsState
            if (prefs != null) prefsLoaded = true

            StrideTheme(prefs = prefs ?: StridePrefs()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (prefs != null) {
                        var onboarded by remember(prefs.onboardingDone) {
                            mutableStateOf(prefs.onboardingDone)
                        }
                        if (!onboarded) {
                            Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                                OnboardingScreen(onDone = { onboarded = true })
                            }
                        } else {
                            StrideMain()
                        }
                    }
                }
            }
        }

        // Foreground data: sync on start, stream the hardware sensor while
        // visible when Health Connect isn't the active source.
        val repo = Graph.repository
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { runCatching { repo.sync() } }
                // Fallback poll for live numbers even when the sensor is quiet
                launch {
                    while (true) {
                        kotlinx.coroutines.delay(15_000)
                        runCatching { repo.syncToday() }
                    }
                }
                runCatching { repo.collectSensorLive() }
            }
        }
    }
}

data class NavDest(val route: String, val icon: ImageVector, val labelRes: Int)

private val destinations = listOf(
    NavDest("today", Icons.Rounded.Home, R.string.nav_today),
    NavDest("history", Icons.Rounded.BarChart, R.string.nav_history),
    NavDest("awards", Icons.Rounded.EmojiEvents, R.string.nav_awards),
    NavDest("settings", Icons.Rounded.Settings, R.string.nav_settings)
)

@Composable
fun StrideMain() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "today"

    var achievementToast by remember { mutableStateOf<AchievementDef?>(null) }
    var achievementBurst by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        Graph.repository.newAchievements.collect { def ->
            achievementToast = def
            achievementBurst += 1
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = "today",
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(tween(150)) + slideInVertically(tween(210)) { it / 16 } +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(210))
                },
                exitTransition = { fadeOut(tween(80)) }
            ) {
                composable("today") {
                    DashboardScreen(onOpenAwards = { navController.navigateSingleTop("awards") })
                }
                composable("history") { HistoryScreen() }
                composable("awards") { AwardsScreen() }
                composable("settings") { SettingsScreen() }
            }

            StrideFloatingNav(
                currentRoute = currentRoute,
                onNavigate = { route -> navController.navigateSingleTop(route) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
            )

            // Looks for a new release when one is due for the chosen frequency
            com.vythera.stride.ui.components.UpdateGate()

            ConfettiBurst(
                burstKey = achievementBurst,
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = achievementToast != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
            ) {
                achievementToast?.let { def ->
                    AchievementToast(def = def, onDismiss = { achievementToast = null })
                }
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AchievementToast(def: AchievementDef, onDismiss: () -> Unit) {
    LaunchedEffect(def.id) {
        kotlinx.coroutines.delay(4200)
        onDismiss()
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.clickable(onClick = onDismiss)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BadgeMedallion(def = def, unlocked = true, size = 34.dp)
            Column {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.badge_unlocked),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f)
                )
                Text(
                    androidx.compose.ui.platform.LocalContext.current.achievementTitle(def),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

@Composable
fun StrideFloatingNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            destinations.forEach { dest ->
                val selected = currentRoute == dest.route
                val label = androidx.compose.ui.res.stringResource(dest.labelRes)
                // Icon pops with an overshoot spring whenever this tab becomes active
                val pop = remember { Animatable(1f) }
                LaunchedEffect(selected) {
                    if (selected) {
                        pop.snapTo(0.65f)
                        pop.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier
                        .bouncyClickable(scaleDown = 0.88f) { onNavigate(dest.route) }
                        .clip(CircleShape)
                        .animateContentSize()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (selected) 18.dp else 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            dest.icon,
                            contentDescription = label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = pop.value
                                    scaleY = pop.value
                                }
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut()
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
