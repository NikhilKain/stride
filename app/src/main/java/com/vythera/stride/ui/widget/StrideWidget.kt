package com.vythera.stride.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.vythera.stride.Graph
import com.vythera.stride.MainActivity
import com.vythera.stride.R
import com.vythera.stride.data.db.DailySummaryEntity
import com.vythera.stride.model.StridePrefs
import com.vythera.stride.util.Formatters
import android.content.Context
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as JavaTextStyle

/**
 * Home-screen widget: today's steps, goal progress, distance and calories.
 *
 * One widget class serves every size — the launcher hands us the box it has and
 * [Content] picks the layout that fits it.
 * Colours come from whatever palette the user chose in the app, so the widget
 * matches it rather than being a fixed scheme that belongs to no part of it.
 */
class StrideWidget : GlanceAppWidget() {

    /**
     * Exact, not Responsive.
     *
     * Responsive maps the real cell to the nearest of a declared set and reports
     * *that* size, and its choice didn't match what the layouts needed — a 3x2
     * tile kept resolving to the 3x1 strip, leaving half the widget empty, and
     * no declared size made the week-bar layout reachable. Exact hands over the
     * true size and lets [Content] decide, which is the decision this widget
     * actually wants to make.
     */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Graph.ensureInit(context)
        val prefs = Graph.prefs.snapshot()
        val today = LocalDate.now()
        val row = Graph.database.summaryDao().getDay(today.toEpochDay())
        // Only the large layout draws the week, but the read is one indexed
        // query and deferring it would mean knowing the size before the
        // composition exists, which Glance doesn't offer.
        val week = Graph.database.summaryDao()
            .getRange(today.minusDays(6).toEpochDay(), today.toEpochDay())

        provideContent { Content(prefs, row, week) }
    }

    companion object {
        suspend fun requestUpdate(context: Context) {
            runCatching { StrideWidget().updateAll(context) }
        }
    }
}

class StrideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StrideWidget()
}

/* ----------------------------------------------------------------------- */

@Composable
private fun Content(
    prefs: StridePrefs,
    row: DailySummaryEntity?,
    week: List<DailySummaryEntity>
) {
    val context = LocalContext.current
    val look = WidgetLooks.resolve(context, prefs)
    val size = LocalSize.current

    val steps = row?.steps ?: 0L
    val goal = prefs.dailyGoal
    val data = WidgetData(
        steps = steps,
        goal = goal,
        progress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f,
        distance = Formatters.distance(row?.distanceMeters ?: 0.0, prefs.unit),
        calories = Formatters.caloriesShort(row?.calories ?: 0.0)
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(look.background)
            .cornerRadius(look.corner)
            // The whole widget is one tap target. It previously had none at all,
            // which reads as broken rather than as decoration.
            .clickable(actionStartActivity<MainActivity>())
            .padding(if (size.height < 60.dp) 10.dp else 14.dp)
    ) {
        // Height decides first: a one-row tile can only carry a strip, whatever
        // its width. Past that, a second row buys the week chart if there are
        // columns to draw it in, and a narrow-but-tall tile gets the ring.
        when {
            size.height < 60.dp -> CompactLayout(look, data)
            size.height >= 90.dp && size.width >= 170.dp -> LargeLayout(look, data, week)
            size.width < 150.dp -> SquareLayout(look, data)
            else -> WideLayout(look, data)
        }
    }
}

private data class WidgetData(
    val steps: Long,
    val goal: Int,
    val progress: Float,
    val distance: String,
    val calories: String
)

/* ---- 2x1 ---------------------------------------------------------------- */

/** Icon, count, goal progress. Distance and calories don't fit legibly here. */
@Composable
private fun CompactLayout(look: WidgetLook, data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatIcon(R.drawable.ic_widget_steps, look.accent, 20.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column {
            StepCount(look, data.steps, 20.sp)
            StepsLabel(look, 10.sp)
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.End) {
            Text(
                percent(data.progress),
                style = TextStyle(
                    color = accentOf(look),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            ProgressBar(look, data.progress, GlanceModifier.width(64.dp))
        }
    }
}

/* ---- 3x1 ---------------------------------------------------------------- */

/** The default. Count on the left; goal bar and the two stats on the right. */
@Composable
private fun WideLayout(look: WidgetLook, data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatIcon(R.drawable.ic_widget_steps, look.accent, 22.dp)
        Spacer(GlanceModifier.width(8.dp))
        Column {
            StepCount(look, data.steps, 24.sp)
            StepsLabel(look, 11.sp)
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            GoalLine(look, data)
            Spacer(GlanceModifier.height(5.dp))
            ProgressBar(look, data.progress, GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(8.dp))
            StatsRow(look, data, compact = true)
        }
    }
}

/* ---- 2x2 ---------------------------------------------------------------- */

/** Square: the goal ring carries the count, with the stats underneath. */
@Composable
private fun SquareLayout(look: WidgetLook, data: WidgetData) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    WidgetGraphics.progressRing(
                        context = context,
                        sizeDp = RING_SIZE_DP,
                        strokeDp = 6f,
                        progress = data.progress,
                        accent = look.accent,
                        track = look.track,
                        segments = null
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(RING_SIZE_DP.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Formatters.compactSteps(data.steps),
                    style = TextStyle(
                        color = accentOf(look),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
                StepsLabel(look, 10.sp)
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        StatsRow(look, data)
    }
}

/* ---- 4x2 ---------------------------------------------------------------- */

/**
 * Everything, plus the week's bars so today has something to sit against.
 *
 * Stacked, not side by side. The chart originally sat in a second column, which
 * only works from about 250dp — and a 3x2 tile is nearer 190dp, where it left
 * the step count wrapping onto two lines and clipped the calories entirely.
 * Going vertical spends the second row of cells on the chart, which is the thing
 * that row was added for, and gives both halves the full width.
 */
@Composable
private fun LargeLayout(look: WidgetLook, data: WidgetData, week: List<DailySummaryEntity>) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            // Without this the row wraps its content and the weighted spacer has
            // nothing to push against, so the percentage sits against the count.
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatIcon(R.drawable.ic_widget_steps, look.accent, 24.dp)
            Spacer(GlanceModifier.width(9.dp))
            Column {
                StepCount(look, data.steps, 26.sp)
                StepsLabel(look, 11.sp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                percent(data.progress),
                style = TextStyle(
                    color = accentOf(look),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(7.dp))
        ProgressBar(look, data.progress, GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.height(8.dp))
        StatsRow(look, data)
        Spacer(GlanceModifier.height(10.dp))
        WeekBars(look, week, GlanceModifier.fillMaxWidth().defaultWeight(), barMaxDp = 44)
    }
}

/** The last seven days, today lit in the accent colour. */
@Composable
private fun WeekBars(
    look: WidgetLook,
    week: List<DailySummaryEntity>,
    modifier: GlanceModifier,
    barMaxDp: Int = BAR_MAX_DP
) {
    val today = LocalDate.now()
    val days = (6L downTo 0L).map { back ->
        val date = today.minusDays(back)
        date to (week.firstOrNull { it.epochDay == date.toEpochDay() }?.steps ?: 0L)
    }
    val peak = days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        days.forEach { (date, steps) ->
            val isToday = date == today
            // Floored at a visible minimum: a bar of no height reads as missing
            // data rather than as a quiet day.
            val barHeight =
                (BAR_MIN_DP + (steps.toFloat() / peak) * (barMaxDp - BAR_MIN_DP)).roundToInt()
            val fill = if (isToday) look.accent else look.track
            val labelColor = if (isToday) look.accent else look.muted
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier.height(barMaxDp.dp).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(BAR_WIDTH_DP.dp)
                            .height(barHeight.dp)
                            .cornerRadius(3.dp)
                            .background(fill)
                    ) {}
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    // Narrow ("M"), not short ("Mon"): seven three-letter labels
                    // don't fit across a 3-cell tile without clipping.
                    look.label(
                        date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                    ),
                    style = TextStyle(
                        color = ColorProvider(labelColor, labelColor),
                        fontSize = 9.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

/* ---- shared pieces ------------------------------------------------------ */

@Composable
private fun StepCount(look: WidgetLook, steps: Long, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        Formatters.steps(steps),
        style = TextStyle(color = accentOf(look), fontSize = size, fontWeight = FontWeight.Bold),
        maxLines = 1
    )
}

@Composable
private fun StepsLabel(look: WidgetLook, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        look.label(LocalContext.current.getString(R.string.widget_steps_label)),
        style = TextStyle(
            color = ColorProvider(look.muted, look.muted),
            fontSize = size,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

@Composable
private fun GoalLine(look: WidgetLook, data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            look.label(
                LocalContext.current.getString(
                    R.string.widget_goal_label,
                    Formatters.steps(data.goal.toLong())
                )
            ),
            style = TextStyle(color = ColorProvider(look.muted, look.muted), fontSize = 11.sp),
            maxLines = 1
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            percent(data.progress),
            style = TextStyle(
                color = accentOf(look),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

/**
 * Distance and calories.
 *
 * Every label is pinned to one line — Glance wraps by default, and on a 3-cell
 * widget the calories text wrapped and spilled out of the card.
 *
 * [compact] then drops the "kcal" suffix, because one line still wasn't enough:
 * three cells can't hold both stats with both unit words, and clipping the
 * number to "0…" is worse than leaning on the flame icon to say what it is.
 * The distance keeps its unit — km and mi are not interchangeable.
 */
@Composable
private fun StatsRow(look: WidgetLook, data: WidgetData, compact: Boolean = false) {
    val onBg = ColorProvider(look.onBackground, look.onBackground)
    val style = TextStyle(color = onBg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val calories =
        if (compact) data.calories
        else LocalContext.current.getString(R.string.widget_kcal, data.calories)
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatIcon(R.drawable.ic_widget_distance, look.muted, 12.dp)
        Spacer(GlanceModifier.width(4.dp))
        Text(look.label(data.distance), style = style, maxLines = 1)
        Spacer(GlanceModifier.width(10.dp))
        StatIcon(R.drawable.ic_widget_calories, look.muted, 12.dp)
        Spacer(GlanceModifier.width(4.dp))
        Text(look.label(calories), style = style, maxLines = 1)
    }
}

@Composable
private fun StatIcon(resId: Int, tint: Color, size: Dp) {
    Image(
        provider = ImageProvider(resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ColorProvider(tint, tint)),
        modifier = GlanceModifier.size(size)
    )
}

/**
 * Goal progress.
 *
 * Glance has no fractional width modifier, so the bar is assembled from
 * weighted boxes rather than a progress view.
 */
@Composable
private fun ProgressBar(look: WidgetLook, progress: Float, modifier: GlanceModifier) {
    // Filled cells against empty ones. Weights are integers, so the bar is
    // quantised to 1/PROGRESS_STEPS — a fraction of a pixel at widget scale.
    val filled = (progress.coerceIn(0f, 1f) * PROGRESS_STEPS).roundToInt()
    Box(
        modifier = modifier.height(6.dp).cornerRadius(3.dp).background(look.track),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            if (filled > 0) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(3.dp)
                        .background(look.accent)
                ) {}
                repeat(filled - 1) {
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {}
                }
            }
            repeat(PROGRESS_STEPS - filled) {
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {}
            }
        }
    }
}

private fun accentOf(look: WidgetLook) = ColorProvider(look.accent, look.accent)

private fun percent(progress: Float): String =
    String.format(Locale.getDefault(), "%d%%", (progress * 100).roundToInt())

private const val RING_SIZE_DP = 84
private const val PROGRESS_STEPS = 40
private const val BAR_WIDTH_DP = 8
private const val BAR_MIN_DP = 4f
private const val BAR_MAX_DP = 40
