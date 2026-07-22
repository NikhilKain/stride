package com.vythera.stride.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vythera.stride.R
import com.vythera.stride.model.AppFont
import com.vythera.stride.model.StridePrefs

/*
 * Font engine. Every bundled family is a single-axis (wght) variable font under
 * the SIL Open Font License, instanced per typography tier.
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int): FontFamily = FontFamily(
    Font(
        resId = resId,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        weight = FontWeight(weight.coerceIn(100, 900))
    )
)

private fun nunito(weight: Int): FontFamily = variable(R.font.nunito_variable, weight)

private data class Tiers(
    val display: FontFamily,
    val headline: FontFamily,
    val title: FontFamily,
    val body: FontFamily,
    val label: FontFamily
)

private fun buildTypography(t: Tiers): Typography = Typography(
    displayLarge = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)

/** Display/headline/title/body/label weights for a single-axis family. */
private fun weightedTiers(resId: Int, top: Int = 900): Tiers = Tiers(
    display = variable(resId, top),
    headline = variable(resId, (top - 100).coerceAtLeast(100)),
    title = variable(resId, (top - 200).coerceAtLeast(100)),
    body = variable(resId, 450),
    label = variable(resId, 600)
)

fun strideTypography(prefs: StridePrefs): Typography = when (prefs.appFont) {
    AppFont.SYSTEM -> buildTypography(
        Tiers(FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default)
    )
    AppFont.NUNITO -> buildTypography(
        Tiers(nunito(1000), nunito(850), nunito(800), nunito(600), nunito(750))
    )
    AppFont.INTER -> buildTypography(weightedTiers(R.font.inter_variable))
    AppFont.OUTFIT -> buildTypography(weightedTiers(R.font.outfit_variable))
    AppFont.LEXEND -> buildTypography(weightedTiers(R.font.lexend_variable))
    AppFont.MANROPE -> buildTypography(weightedTiers(R.font.manrope_variable, top = 800))
    AppFont.GROTESK -> buildTypography(weightedTiers(R.font.spacegrotesk_variable, top = 700))
}
