package com.song.Song

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

/**
 * Owns all network-fetch operations for the Home/Search screen.
 * MainActivity.java (still Java) creates this via ViewModelProvider and
 * observes the returned LiveData — standard, fully-supported Java/Kotlin
 * Jetpack interop, no changes needed to how MainActivity is structured.
 */
class MainViewModel : ViewModel() {

    /** Search/category/recommendation track fetch — same endpoint, same parsing, now via coroutines. */
    fun fetchTracks(query: String): LiveData<List<Track>> = liveData(Dispatchers.IO) {
        emit(fetchTracksBlocking(query))
    }

    /** Single best-match artist lookup, used by the Top Artists row. */
    fun fetchArtist(name: String): LiveData<JSONObject?> = liveData(Dispatchers.IO) {
        emit(fetchArtistBlocking(name))
    }

    private fun fetchTracksBlocking(query: String): List<Track> {
        return try {
            val response = ApiClient.getApiService().searchSongs(query).execute()
            if (response.isSuccessful && response.body() != null) {
                val root = response.body()!!.data
                TrackParser.parseTracks(root.getAsJsonArray("results"))
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchArtistBlocking(name: String): JSONObject? {
        return try {
            val response = ApiClient.getApiService().searchArtists(name, 1).execute()
            if (response.isSuccessful && response.body() != null) {
                val root = response.body()!!.data
                val results = root.getAsJsonArray("results")
                if (results != null && results.size() > 0) JSONObject(results[0].toString()) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
