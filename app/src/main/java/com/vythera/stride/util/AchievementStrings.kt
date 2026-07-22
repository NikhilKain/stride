package com.vythera.stride.util

import android.annotation.SuppressLint
import android.content.Context
import com.vythera.stride.domain.AchievementDef

/*
 * Achievement titles/descriptions live in string resources for localization,
 * keyed by convention: ach_<id>_title / ach_<id>_desc. The English text in
 * the domain definitions is the fallback.
 */

@SuppressLint("DiscouragedApi")
private fun Context.resolve(name: String, fallback: String): String {
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id != 0) getString(id) else fallback
}

fun Context.achievementTitle(def: AchievementDef): String =
    resolve("ach_${def.id}_title", def.title)

fun Context.achievementDescription(def: AchievementDef): String =
    resolve("ach_${def.id}_desc", def.description)
