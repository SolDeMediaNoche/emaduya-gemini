
package com.wave.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class LyricsResult(
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val instrumental: Boolean
)

object LyricsRepository {

    suspend fun find(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext null

        val durationSeconds = (durationMs / 1000L).toInt()
        if (durationSeconds !in 1..3600) return@withContext null

        val params = listOf(
            "track_name" to title,
            "artist_name" to artist,
            "album_name" to album,
            "duration" to durationSeconds.toString()
        ).joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }

        val url = URL("https://lrclib.net/api/get?$params")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty(
                "User-Agent",
                "WAVEMusic/1.0 (Android music player)"
            )

            if (connection.responseCode != 200) {
                return@withContext null
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val json = JSONObject(body)

            LyricsResult(
                syncedLyrics = json.optString("syncedLyrics")
                    .takeUnless { it == "null" || it.isBlank() },
                plainLyrics = json.optString("plainLyrics")
                    .takeUnless { it == "null" || it.isBlank() },
                instrumental = json.optBoolean("instrumental", false)
            )
        } finally {
            connection.disconnect()
        }
    }
}
