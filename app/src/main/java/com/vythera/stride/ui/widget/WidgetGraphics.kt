package com.vythera.stride.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bitmaps for the parts of the widget Glance can't draw.
 *
 * Glance lays out `RemoteViews`, which have boxes, text and images and nothing
 * else — no canvas, no arcs, no custom shapes. Anything curved has to be
 * rasterised here and handed over as an image.
 */
object WidgetGraphics {

    /**
     * The goal ring: a track circle with [progress] of it painted in [accent],
     * starting at twelve o'clock and running clockwise.
     *
     * [segments] switches from a continuous arc to that many discrete blocks,
     * an alternative rendering for skins that draw progress as discrete pips.
     */
    fun progressRing(
        context: Context,
        sizeDp: Int,
        strokeDp: Float,
        progress: Float,
        accent: Color,
        track: Color,
        segments: Int? = null
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).roundToInt().coerceAtLeast(1)
        val stroke = strokeDp * density
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val sweep = 360f * progress.coerceIn(0f, 1f)
        val inset = stroke / 2f
        val box = RectF(inset, inset, px - inset, px - inset)

        if (segments == null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = track.toArgb()
            canvas.drawArc(box, 0f, 360f, false, paint)
            if (sweep > 0f) {
                paint.color = accent.toArgb()
                canvas.drawArc(box, START_ANGLE, sweep, false, paint)
            }
            return bitmap
        }

        // Blocks, not an arc: square pips laid around the circle, lit up to the
        // proportion reached. Squares (not circles) so it reads as pixels.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val radius = (px - stroke) / 2f
        val centre = px / 2f
        val half = min(stroke, radius / 2f) / 2f
        val lit = (segments * progress.coerceIn(0f, 1f)).roundToInt()
        for (i in 0 until segments) {
            val angle = Math.toRadians((START_ANGLE + 360.0 * i / segments))
            val cx = centre + radius * cos(angle).toFloat()
            val cy = centre + radius * sin(angle).toFloat()
            paint.color = (if (i < lit) accent else track).toArgb()
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        }
        return bitmap
    }

    /** Twelve o'clock. Android's arc angles start at three. */
    private const val START_ANGLE = -90f
}
