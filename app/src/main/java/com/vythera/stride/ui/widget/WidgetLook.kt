package com.vythera.stride.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.model.ThemeMode
import com.vythera.stride.ui.theme.paletteById

/**
 * The widget's resolved appearance for one render.
 *
 * A home-screen widget can't read composition locals or MaterialTheme, so the
 * app's palette and light/dark choice are flattened into plain values here and
 * handed to the layouts. The point is that the widget looks like the app the
 * user actually configured rather than a fixed colour scheme that belongs to no
 * part of it.
 */
data class WidgetLook(
    val background: Color,
    val accent: Color,
    val onBackground: Color,
    val muted: Color,
    val track: Color,
    val corner: Dp
) {
    /**
     * Hook for skins that restyle label text. The free build renders labels as
     * written; kept so the layouts don't have to know whether any transform is
     * in force.
     */
    fun label(text: String): String = text
}

object WidgetLooks {

    fun resolve(context: Context, prefs: StridePrefs): WidgetLook {
        val dark = isDark(context, prefs)
        val scheme = paletteById(prefs.paletteId).let { if (dark) it.dark else it.light }

        // surfaceContainer rather than background: a widget sits on the user's
        // wallpaper, where the app's window background is often too close to it
        // to read as a distinct card.
        val surface = when {
            dark && prefs.amoled -> Color(0xFF000000)
            dark -> scheme.surfaceContainer
            else -> scheme.surfaceContainerLowest
        }

        return WidgetLook(
            background = surface,
            accent = scheme.primary,
            onBackground = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            track = scheme.surfaceVariant.copy(alpha = if (dark) 1f else 0.55f),
            corner = 24.dp
        )
    }

    private fun isDark(context: Context, prefs: StridePrefs): Boolean = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        // No composition to ask, so read the configuration the launcher handed us.
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}
