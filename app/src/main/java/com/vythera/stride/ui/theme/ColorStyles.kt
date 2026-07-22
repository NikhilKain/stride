package com.vythera.stride.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vythera.stride.model.ColorStyle

/*
 * ColorBlendr-style palette treatments applied on top of any scheme
 * (static palette or Material You dynamic): saturation/hue remaps of the
 * accent roles, full desaturation for monochrome, plus a pure-black AMOLED
 * surface ladder.
 */

private fun Color.mapHsv(block: (FloatArray) -> Unit): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    block(hsv)
    hsv[1] = hsv[1].coerceIn(0f, 1f)
    hsv[2] = hsv[2].coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
}

private fun Color.saturate(factor: Float) = mapHsv { it[1] = it[1] * factor }
private fun Color.hueShift(degrees: Float) = mapHsv { it[0] = (it[0] + degrees + 360f) % 360f }

private fun ColorScheme.mapAccents(transform: (Color) -> Color): ColorScheme = copy(
    primary = transform(primary),
    onPrimary = transform(onPrimary),
    primaryContainer = transform(primaryContainer),
    onPrimaryContainer = transform(onPrimaryContainer),
    secondary = transform(secondary),
    onSecondary = transform(onSecondary),
    secondaryContainer = transform(secondaryContainer),
    onSecondaryContainer = transform(onSecondaryContainer),
    tertiary = transform(tertiary),
    onTertiary = transform(onTertiary),
    tertiaryContainer = transform(tertiaryContainer),
    onTertiaryContainer = transform(onTertiaryContainer),
    inversePrimary = transform(inversePrimary)
)

private fun ColorScheme.mapTertiary(transform: (Color) -> Color): ColorScheme = copy(
    tertiary = transform(tertiary),
    tertiaryContainer = transform(tertiaryContainer)
)

private fun ColorScheme.mapAll(transform: (Color) -> Color): ColorScheme =
    mapAccents(transform).copy(
        background = transform(background),
        onBackground = transform(onBackground),
        surface = transform(surface),
        onSurface = transform(onSurface),
        surfaceVariant = transform(surfaceVariant),
        onSurfaceVariant = transform(onSurfaceVariant),
        surfaceContainerLowest = transform(surfaceContainerLowest),
        surfaceContainerLow = transform(surfaceContainerLow),
        surfaceContainer = transform(surfaceContainer),
        surfaceContainerHigh = transform(surfaceContainerHigh),
        surfaceContainerHighest = transform(surfaceContainerHighest),
        outline = transform(outline),
        outlineVariant = transform(outlineVariant),
        inverseSurface = transform(inverseSurface),
        inverseOnSurface = transform(inverseOnSurface)
    )

fun ColorScheme.applyStyle(style: ColorStyle): ColorScheme = when (style) {
    ColorStyle.TONAL_SPOT -> this
    ColorStyle.NEUTRAL -> mapAccents { it.saturate(0.35f) }
    ColorStyle.MONOCHROME -> mapAll { it.saturate(0f) }
    ColorStyle.VIBRANT -> mapAccents { it.saturate(1.45f) }
    ColorStyle.EXPRESSIVE -> mapAccents { it.saturate(1.2f) }
        .mapTertiary { it.hueShift(-45f) }
        .copy(
            secondary = secondary.hueShift(30f).saturate(1.25f),
            secondaryContainer = secondaryContainer.hueShift(30f).saturate(1.2f)
        )
}

/** Pure-black window with a near-black elevation ladder for AMOLED panels. */
fun ColorScheme.applyAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF1C1C1C)
)
