package com.vythera.stride.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.vythera.stride.R
import com.vythera.stride.model.DailyStats
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class ShareColors(
    val background: Int,
    val backgroundEnd: Int,
    val onBackground: Int,
    val primary: Int,
    val tertiary: Int,
    val chip: Int,
    val onChip: Int
)

object ShareCardRenderer {

    private const val CARD_W = 1080
    private const val CARD_H = 1350

    fun render(
        context: Context,
        stats: DailyStats,
        streak: Int,
        distanceLabel: String,
        caloriesLabel: String,
        activeLabel: String,
        colors: ShareColors
    ): File {
        val W = CARD_W
        val H = CARD_H
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val nunito = ResourcesCompat.getFont(context, R.font.nunito_variable)

        fun textPaint(sizePx: Float, color: Int, weight: Int): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = nunito
                fontVariationSettings = "'wght' $weight"
                textSize = sizePx
                this.color = color
                textAlign = Paint.Align.CENTER
            }

        fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

        // ---- Background: diagonal gradient + soft radial glows ----
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                colors.background, colors.backgroundEnd, Shader.TileMode.CLAMP
            )
        })
        canvas.drawCircle(W * 0.92f, H * 0.06f, W * 0.42f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W * 0.92f, H * 0.06f, W * 0.42f,
                withAlpha(colors.primary, 0x30), withAlpha(colors.primary, 0), Shader.TileMode.CLAMP
            )
        })
        canvas.drawCircle(W * 0.06f, H * 0.92f, W * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W * 0.06f, H * 0.92f, W * 0.5f,
                withAlpha(colors.tertiary, 0x2A), withAlpha(colors.tertiary, 0), Shader.TileMode.CLAMP
            )
        })

        val cx = W / 2f

        // ---- Top: date pill ----
        val dateStr = stats.date
            .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
        val datePaint = textPaint(38f, colors.onBackground, 750)
        val dateWidth = datePaint.measureText(dateStr)
        val pillTop = H * 0.055f
        canvas.drawRoundRect(
            RectF(cx - dateWidth / 2 - 36f, pillTop, cx + dateWidth / 2 + 36f, pillTop + 76f),
            38f, 38f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(colors.chip, 0xCC) }
        )
        canvas.drawText(dateStr, cx, pillTop + 51f, datePaint)

        // Streak line under the date
        if (streak > 1) {
            canvas.drawText(
                "🔥 $streak-day streak", cx, pillTop + 140f,
                textPaint(40f, colors.tertiary, 850)
            )
        }

        // ---- Hero ring: always-wavy gradient arc ----
        val cy = H * 0.37f
        val radius = min(W, H) * 0.26f
        val strokeW = radius * 0.16f
        val progress = min(stats.progress, 1f)

        // Track
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = withAlpha(colors.onBackground, 0x1E)
        })

        if (progress > 0.01f) {
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeW
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = SweepGradient(
                    cx, cy,
                    intArrayOf(colors.primary, colors.tertiary, colors.primary),
                    floatArrayOf(0f, 0.55f, 1f)
                ).also { sg ->
                    val m = android.graphics.Matrix()
                    m.postRotate(-90f, cx, cy)
                    sg.setLocalMatrix(m)
                }
            }
            val path = Path()
            val sweepDeg = 360f * progress
            val waves = 14f
            val amp = strokeW * 0.42f
            var first = true
            var deg = 0f
            while (deg <= sweepDeg) {
                val rad = Math.toRadians((deg - 90).toDouble())
                val wobble = (amp * sin(Math.toRadians((deg * waves).toDouble()))).toFloat()
                val r = radius + wobble
                val x = cx + r * cos(rad).toFloat()
                val y = cy + r * sin(rad).toFloat()
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                deg += 1.5f
            }
            canvas.drawPath(path, arcPaint)

            // Glowing head dot
            val headRad = Math.toRadians((sweepDeg - 90).toDouble())
            val hx = cx + radius * cos(headRad).toFloat()
            val hy = cy + radius * sin(headRad).toFloat()
            canvas.drawCircle(hx, hy, strokeW * 0.85f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    hx, hy, strokeW * 0.85f,
                    withAlpha(colors.tertiary, 0xFF), withAlpha(colors.tertiary, 0x30),
                    Shader.TileMode.CLAMP
                )
            })
        }

        // Center: big number + label
        canvas.drawText(
            String.format(Locale.US, "%,d", stats.steps),
            cx, cy + radius * 0.04f, textPaint(radius * 0.42f, colors.onBackground, 1000)
        )
        canvas.drawText(
            "STEPS", cx, cy + radius * 0.32f,
            textPaint(radius * 0.13f, colors.primary, 850).apply { letterSpacing = 0.32f }
        )

        // ---- Under the ring: percent pill ----
        val pct = (stats.progress * 100).toInt().coerceAtMost(999)
        val pctText = if (stats.goal > 0) "$pct% of ${String.format(Locale.US, "%,d", stats.goal)} goal" else ""
        if (pctText.isNotEmpty()) {
            val pctPaint = textPaint(40f, if (pct >= 100) colors.tertiary else colors.primary, 850)
            val pctW = pctPaint.measureText(pctText)
            val pctTop = cy + radius + strokeW + H * 0.028f
            canvas.drawRoundRect(
                RectF(cx - pctW / 2 - 40f, pctTop, cx + pctW / 2 + 40f, pctTop + 82f),
                41f, 41f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(colors.chip, 0xCC) }
            )
            canvas.drawText(pctText, cx, pctTop + 55f, pctPaint)
        }

        // ---- Stat chips ----
        run {
            val chipY = H * 0.70f
            val chipH = 168f
            val labels = listOf(distanceLabel to "DISTANCE", caloriesLabel to "CALORIES", activeLabel to "ACTIVE MIN")
            val chipW = 306f
            val gap = 26f
            var x = (W - (chipW * 3 + gap * 2)) / 2f
            val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(colors.chip, 0xE6) }
            for ((value, label) in labels) {
                val rect = RectF(x, chipY, x + chipW, chipY + chipH)
                canvas.drawRoundRect(rect, 52f, 52f, chipPaint)
                canvas.drawText(value, rect.centerX(), chipY + 74f, textPaint(48f, colors.onChip, 950))
                canvas.drawText(
                    label, rect.centerX(), chipY + 128f,
                    textPaint(25f, colors.onChip, 750).apply { alpha = 165; letterSpacing = 0.14f }
                )
                x += chipW + gap
            }
        }

        // ---- Footer wordmark pill ----
        val markPaint = textPaint(42f, colors.onBackground, 1000).apply { letterSpacing = 0.3f }
        val markW = markPaint.measureText("STRIDE")
        val markTop = H - H * 0.075f
        canvas.drawRoundRect(
            RectF(cx - markW / 2 - 48f, markTop, cx + markW / 2 + 48f, markTop + 86f),
            43f, 43f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(colors.chip, 0xB3) }
        )
        canvas.drawText("STRIDE", cx, markTop + 58f, markPaint)
        canvas.drawCircle(
            cx + markW / 2 + 22f, markTop + 30f, 9f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.tertiary }
        )

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "stride_card.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "com.vythera.stride.fileprovider", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            context.getString(R.string.share_card_title)
        )
    }
}
