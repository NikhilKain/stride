package com.vythera.stride.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Springy staggered entrance: fade + rise + gentle overshoot scale.
 * Give sequential [index]es to cascade a screen's cards.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val alphaAnim = remember { Animatable(0f) }
    val offsetAnim = remember { Animatable(34f) }
    val scaleAnim = remember { Animatable(0.95f) }
    LaunchedEffect(Unit) {
        delay(index * 42L)
        launch { alphaAnim.animateTo(1f, tween(210, easing = LinearOutSlowInEasing)) }
        launch {
            offsetAnim.animateTo(
                0f,
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
            )
        }
        launch {
            scaleAnim.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    return graphicsLayer {
        alpha = alphaAnim.value
        translationY = offsetAnim.value.dp.toPx()
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
    }
}

/**
 * Tap feedback the expressive way: the element itself squashes on press
 * and springs back on release. Replaces the ripple.
 */
@Composable
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.9f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyPress"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/** Gentle infinite breathing scale — for streak flames and live elements. */
@Composable
fun Modifier.pulse(from: Float = 1f, to: Float = 1.1f, durationMs: Int = 1500): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Infinite vertical bob — for onboarding art. */
@Composable
fun Modifier.bob(amplitude: Dp = 5.dp, durationMs: Int = 1800): Modifier {
    val transition = rememberInfiniteTransition(label = "bob")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bobOffset"
    )
    return graphicsLayer { translationY = offset * amplitude.toPx() }
}
