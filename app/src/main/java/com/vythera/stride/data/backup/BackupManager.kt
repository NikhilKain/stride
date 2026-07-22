package com.vythera.stride.data.backup

import android.content.Context
import android.net.Uri
import com.vythera.stride.Graph
import com.vythera.stride.data.db.AchievementEntity
import com.vythera.stride.data.db.DailySummaryEntity
import com.vythera.stride.model.AppFont
import com.vythera.stride.model.ColorStyle
import com.vythera.stride.model.ThemeMode
import com.vythera.stride.model.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-app backup as a single JSON document: settings, daily history and
 * unlocked achievements. Written/read through SAF so it survives device
 * switches and factory resets.
 */
object BackupManager {

    suspend fun export(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = Graph.prefs.snapshot()
            val days = Graph.database.summaryDao().getAll()
            val achievements = Graph.database.achievementDao().getAll()

            val root = JSONObject().apply {
                put("app", "stride")
                put("version", 1)
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("prefs", JSONObject().apply {
                    put("dailyGoal", prefs.dailyGoal)
                    put("weeklyGoal", prefs.weeklyGoal)
                    put("heightCm", prefs.heightCm)
                    put("weightKg", prefs.weightKg)
                    put("strideOverrideCm", prefs.strideOverrideCm)
                    put("unit", prefs.unit.name)
                    put("themeMode", prefs.themeMode.name)
                    put("dynamicColor", prefs.dynamicColor)
                    put("paletteId", prefs.paletteId)
                    put("notifGoal", prefs.notifGoal)
                    put("notifNudge", prefs.notifNudge)
                    put("amoled", prefs.amoled)
                    put("colorStyle", prefs.colorStyle.name)
                    put("appFont", prefs.appFont.name)
                })
                put("days", JSONArray().apply {
                    days.forEach { d ->
                        put(JSONObject().apply {
                            put("epochDay", d.epochDay)
                            put("steps", d.steps)
                            put("distanceMeters", d.distanceMeters)
                            put("calories", d.calories)
                            put("goal", d.goal)
                            put("source", d.source)
                        })
                    }
                })
                put("achievements", JSONArray().apply {
                    achievements.forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id)
                            put("unlockedAtEpochDay", a.unlockedAtEpochDay)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output")
        }
    }

    /** Returns the number of day-rows restored. */
    suspend fun import(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Cannot open input")
            val root = JSONObject(text)
            require(root.optString("app") == "stride") { "Not a Stride backup" }

            val p = root.optJSONObject("prefs")
            if (p != null) {
                val prefs = Graph.prefs
                prefs.setDailyGoal(p.optInt("dailyGoal", 8000))
                prefs.setWeeklyGoal(p.optInt("weeklyGoal", 56000))
                prefs.setHeightCm(p.optInt("heightCm", 170))
                prefs.setWeightKg(p.optInt("weightKg", 70))
                prefs.setStrideOverrideCm(p.optInt("strideOverrideCm", 0))
                runCatching { prefs.setUnit(UnitSystem.valueOf(p.optString("unit", "METRIC"))) }
                runCatching { prefs.setThemeMode(ThemeMode.valueOf(p.optString("themeMode", "SYSTEM"))) }
                prefs.setDynamicColor(p.optBoolean("dynamicColor", false))
                prefs.setPaletteId(p.optString("paletteId", "tide"))
                prefs.setNotifGoal(p.optBoolean("notifGoal", true))
                prefs.setNotifNudge(p.optBoolean("notifNudge", true))
                prefs.setAmoled(p.optBoolean("amoled", false))
                runCatching { prefs.setColorStyle(ColorStyle.valueOf(p.optString("colorStyle", "TONAL_SPOT"))) }
                runCatching { prefs.setAppFont(AppFont.valueOf(p.optString("appFont", "NUNITO"))) }
            }

            val days = root.optJSONArray("days") ?: JSONArray()
            val entities = buildList {
                for (i in 0 until days.length()) {
                    val d = days.getJSONObject(i)
                    add(
                        DailySummaryEntity(
                            epochDay = d.getLong("epochDay"),
                            steps = d.getLong("steps"),
                            distanceMeters = d.optDouble("distanceMeters", 0.0),
                            calories = d.optDouble("calories", 0.0),
                            goal = d.optInt("goal", 8000),
                            source = d.optString("source", "SENSOR")
                        )
                    )
                }
            }
            // Merge: never lose local data that's ahead of the backup
            val dao = Graph.database.summaryDao()
            val existing = dao.getAll().associateBy { it.epochDay }
            dao.upsertAll(entities.filter { e ->
                (existing[e.epochDay]?.steps ?: -1L) < e.steps
            })

            val achievements = root.optJSONArray("achievements") ?: JSONArray()
            for (i in 0 until achievements.length()) {
                val a = achievements.getJSONObject(i)
                Graph.database.achievementDao().unlock(
                    AchievementEntity(a.getString("id"), a.optLong("unlockedAtEpochDay", 0))
                )
            }
            entities.size
        }
    }
}
