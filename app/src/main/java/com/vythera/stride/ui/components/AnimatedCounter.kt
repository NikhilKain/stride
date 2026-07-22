package com.vythera.stride.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import com.vythera.stride.util.Formatters

/**
 * Odometer-style rolling numerals: each digit springs vertically when it
 * changes, new digits slide in from below.
 */
@Composable
fun AnimatedCounter(
    value: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val text = Formatters.steps(value)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            // Key on digits-from-the-right so a length change doesn't remount every slot
            val slot = text.length - index
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val springSpec = spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    (slideInVertically(springSpec) { it } + fadeIn()) togetherWith
                        (slideOutVertically(springSpec) { -it } + fadeOut())
                },
                label = "digit$slot"
            ) { c ->
                Text(text = c.toString(), style = style, color = color)
            }
        }
    }
}
