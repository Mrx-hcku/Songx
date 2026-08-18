package com.song.Song

import org.json.JSONArray
import org.json.JSONObject

/** Parses the JioSaavn-style API JSON shape into Track objects. Identical logic
 *  to what MainActivity.java used to have inline — extracted so the new
 *  MainViewModel (and MainActivity.java, via a static Java-callable method)
 *  share one source of truth. */
object TrackParser {

    @JvmStatic
    fun parseTracks(resultsJson: com.google.gson.JsonArray?, limit: Int = 10): List<Track> {
        val tracks = mutableListOf<Track>()
        if (resultsJson == null) return tracks
        val count = minOf(resultsJson.size(), limit)
        for (i in 0 until count) {
            try {
                val t = JSONObject(resultsJson.get(i).toString())
                val id = t.optString("id", "")
                val name = if (t.has("name")) t.optString("name") else t.optString("title", "Unknown Song")
                val hasLyrics = t.optBoolean("hasLyrics", false)
                val lyricsId = if (t.isNull("lyricsId")) null else t.optString("lyricsId", null)
                tracks.add(
                    Track(
                        id, name, extractArtist(t), extractImageUrl(t), extractDownloadUrls(t),
                        t.optString("url", ""), hasLyrics, lyricsId
                    )
                )
            } catch (ignored: Exception) {}
        }
        return tracks
    }

    @JvmStatic
    fun extractArtist(track: JSONObject): String {
        if (track.has("primaryArtists") && track.optString("primaryArtists").isNotEmpty()) return track.optString("primaryArtists")
        if (track.has("artist") && track.optString("artist").isNotEmpty()) return track.optString("artist")
        try {
            val artists = track.optJSONObject("artists")
            val primary = artists?.optJSONArray("primary")
            if (primary != null && primary.length() > 0) {
                val names = StringBuilder()
                for (i in 0 until primary.length()) {
                    if (i > 0) names.append(", ")
                    names.append(primary.getJSONObject(i).optString("name"))
                }
                return names.toString()
            }
        } catch (ignored: Exception) {}
        return "Unknown Artist"
    }

    @JvmStatic
    fun extractImageUrl(track: JSONObject): String {
        return try {
            val images: JSONArray? = track.optJSONArray("image")
            if (images != null && images.length() > 0) images.getJSONObject(images.length() - 1).optString("url", "") else ""
        } catch (ignored: Exception) { "" }
    }

    @JvmStatic
    fun extractDownloadUrls(track: JSONObject): List<String> {
        val urls = mutableListOf<String>()
        try {
            val arr = track.optJSONArray("downloadUrl")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i).optString("url", "")
                    if (u.isNotEmpty()) urls.add(u)
                }
            }
        } catch (ignored: Exception) {}
        return urls
    }
}
