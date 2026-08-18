package com.song.Song

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Track(
    @JvmField var id: String,
    @JvmField var title: String,
    @JvmField var artist: String,
    @JvmField var imageUrl: String,
    downloadUrls: List<String>?,
    @JvmField var pageUrl: String,
    @JvmField var hasLyrics: Boolean,
    @JvmField var lyricsId: String?
) {
    // Ascending quality: [12kbps, 48kbps, 96kbps, 160kbps, 320kbps]
    @JvmField
    var downloadUrls: MutableList<String> = downloadUrls?.toMutableList() ?: mutableListOf()

    fun bestUrl(): String {
        if (downloadUrls.isEmpty()) return ""
        return downloadUrls[downloadUrls.size - 1]
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        try {
            o.put("id", id)
            o.put("title", title)
            o.put("artist", artist)
            o.put("imageUrl", imageUrl)
            val urls = JSONArray()
            for (u in downloadUrls) urls.put(u)
            o.put("downloadUrls", urls)
            o.put("pageUrl", pageUrl)
            o.put("hasLyrics", hasLyrics)
            o.put("lyricsId", lyricsId ?: "")
        } catch (ignored: JSONException) {}
        return o
    }

    companion object {
        @JvmStatic
        fun fromJson(o: JSONObject): Track {
            val urls = mutableListOf<String>()
            val arr = o.optJSONArray("downloadUrls")
            if (arr != null) {
                for (i in 0 until arr.length()) urls.add(arr.optString(i, ""))
            } else if (o.has("downloadUrl")) {
                val single = o.optString("downloadUrl", "")
                if (single.isNotEmpty()) urls.add(single)
            }
            val lyricsId = o.optString("lyricsId", "")
            return Track(
                o.optString("id", ""),
                o.optString("title", "Unknown Song"),
                o.optString("artist", "Unknown Artist"),
                o.optString("imageUrl", ""),
                urls,
                o.optString("pageUrl", ""),
                o.optBoolean("hasLyrics", false),
                if (lyricsId.isNotEmpty()) lyricsId else null
            )
        }
    }
}
