package com.vythera.stride.data.update

import com.vythera.stride.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?
)

/**
 * Checks the GitHub Releases API for a newer build. Uses HttpURLConnection so
 * the app needs no HTTP dependency.
 *
 * Point [REPO] at the public repository once it exists; until then the check
 * simply reports "no update" instead of failing loudly.
 */
object UpdateChecker {

    /**
     * owner/repo whose Releases feed is polled.
     *
     * Note for the open-core split: this points at the public repository, which
     * publishes the OSS edition. The premium build should either use Play
     * Store updates or its own channel, so paying users are never pointed at
     * the free APK.
     */
    const val REPO = "NikhilKain/stride"

    private const val TIMEOUT_MS = 10_000

    val releasesPage: String get() = "https://github.com/$REPO/releases/latest"

    suspend fun fetchLatest(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Stride-Android")
            }
            try {
                // 404 simply means "no releases published yet"
                if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
                if (conn.responseCode !in 200..299) {
                    error("HTTP ${conn.responseCode}")
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").trim()
                if (tag.isEmpty()) return@runCatching null

                val apk = json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                        ?.optString("browser_download_url")
                }

                UpdateInfo(
                    version = tag.removePrefix("v"),
                    notes = json.optString("body").trim(),
                    pageUrl = json.optString("html_url").ifEmpty { releasesPage },
                    apkUrl = apk
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    /** True when [latest] is a higher version than the running build. */
    fun isNewer(latest: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(v: String) = v.removePrefix("v")
            .split('.', '-')
            .mapNotNull { it.trim().toIntOrNull() }

        val l = parts(latest)
        val c = parts(current)
        if (l.isEmpty()) return false
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
