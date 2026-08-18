package com.song.Song

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the ExoPlayer instance and keeps playing even when the app is backgrounded,
 * with a MediaSession-backed notification (play/pause/next/prev + album art).
 * Kotlin + coroutines version — same public API as the old Java MusicService,
 * so MainActivity (still Java) binds and calls it exactly the same way.
 */
class MusicService : Service() {

    companion object {
        private const val CHANNEL_ID = "song_playback_channel_v2"
        private const val NOTIF_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.song.Song.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.song.Song.ACTION_NEXT"
        const val ACTION_PREV = "com.song.Song.ACTION_PREV"
        const val ACTION_STOP = "com.song.Song.ACTION_STOP"

        private const val FREE_SKIP_LIMIT = 7
        private const val SKIP_WINDOW_MS = 60 * 60 * 1000L
    }

    interface PlaybackListener {
        fun onTrackChanged(track: Track)
        fun onPlayStateChanged(playing: Boolean)
        fun onProgress(positionMs: Int, durationMs: Int)
        fun onBuffering(buffering: Boolean)
    }

    inner class MusicBinder : Binder() {
        val service: MusicService get() = this@MusicService
    }

    private val binder = MusicBinder()
    private var listener: PlaybackListener? = null

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManager? = null

    private var queue: MutableList<Track> = mutableListOf()
    private var currentIndex = -1
    private var isPlaying = false
    private var isPreparing = false
    private var shuffleOn = false
    private var repeatOn = false
    private var fallbackQualityLevel = 0
    private var currentArt: Bitmap? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            if (isPreparing) {
                                isPreparing = false
                                listener?.onBuffering(false)
                                exo.play()
                            }
                        }
                        Player.STATE_BUFFERING -> listener?.onBuffering(true)
                        Player.STATE_ENDED -> if (repeatOn) playCurrent() else next()
                    }
                }

                override fun onIsPlayingChanged(playingNow: Boolean) {
                    isPlaying = playingNow
                    listener?.onPlayStateChanged(playingNow)
                    updatePlaybackState()
                    getCurrentTrack()?.let { postNotification(it, playingNow) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isPreparing = false
                    val track = getCurrentTrack()
                    if (track != null && fallbackQualityLevel < track.downloadUrls.size - 1) {
                        fallbackQualityLevel++
                        Toast.makeText(this@MusicService, "Connection slow hai — quality kam karke try kar rahe hai", Toast.LENGTH_SHORT).show()
                        startPlayback(track)
                    } else {
                        listener?.onBuffering(false)
                        Toast.makeText(this@MusicService, "Play nahi ho paaya — agla gaana try karo", Toast.LENGTH_SHORT).show()
                        next()
                    }
                }
            })
        }

        mediaSession = MediaSessionCompat(this, "SongPlaybackSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { skipToNext() }
                override fun onSkipToPrevious() { prev() }
                override fun onSeekTo(pos: Long) { seekToMs(pos.toInt()) }
            })
            isActive = true
        }

        startProgressTicker()
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (true) {
                if (player != null && isPlaying && !isPreparing) {
                    try {
                        listener?.onProgress(getCurrentPositionMs(), getDurationMs())
                        updatePlaybackState()
                    } catch (ignored: Exception) {}
                }
                delay(500)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> skipToNext()
            ACTION_PREV -> prev()
            ACTION_STOP -> { pause(); stopForeground(false) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun setListener(l: PlaybackListener?) { listener = l }

    // ============================================================
    //  PLAYBACK CONTROL
    // ============================================================

    fun playQueue(newQueue: List<Track>, startTrack: Track) {
        queue = newQueue.toMutableList()
        currentIndex = queue.indexOfFirst { it.id == startTrack.id }
        if (currentIndex == -1) { queue.add(startTrack); currentIndex = queue.size - 1 }
        playCurrent()
    }

    private fun playCurrent() {
        if (currentIndex < 0 || currentIndex >= queue.size) return
        val track = queue[currentIndex]
        fallbackQualityLevel = 0
        currentArt = null
        listener?.onTrackChanged(track)
        loadAlbumArtForNotification(track)
        startPlayback(track)
    }

    private fun startPlayback(track: Track) {
        val url = pickStreamUrl(track, fallbackQualityLevel)
        if (url.isEmpty()) {
            Toast.makeText(this, "Is gaane ka audio link nahi mila", Toast.LENGTH_SHORT).show()
            return
        }

        isPreparing = true
        listener?.onBuffering(true)

        try {
            player?.apply {
                stop()
                clearMediaItems()
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
            }
        } catch (e: Exception) {
            isPreparing = false
            listener?.onBuffering(false)
            Toast.makeText(this, "Play nahi ho paaya", Toast.LENGTH_SHORT).show()
        }
    }

    fun togglePlayPause() { if (isPlaying) pause() else resume() }

    private fun pause() {
        if (player == null || isPreparing) return
        player?.pause()
        stopForeground(false)
    }

    private fun resume() {
        if (player == null || isPreparing) return
        player?.play()
    }

    private fun postNotification(track: Track, asForeground: Boolean) {
        try {
            val n = buildNotification(track)
            if (asForeground) {
                startForeground(NOTIF_ID, n)
            } else {
                notificationManager?.notify(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Notification error: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        currentIndex = if (shuffleOn) (Math.random() * queue.size).toInt()
        else (currentIndex + 1).let { if (it >= queue.size) 0 else it }
        playCurrent()
    }

    fun skipToNext() {
        if (!isSubscribed() && !canSkip()) {
            Toast.makeText(this, "Free plan mein har ghante sirf $FREE_SKIP_LIMIT skips milte hain. Upgrade karo unlimited skips ke liye!", Toast.LENGTH_LONG).show()
            return
        }
        if (!isSubscribed()) recordSkip()
        next()
    }

    fun prev() {
        if (queue.isEmpty()) return
        currentIndex = (currentIndex - 1).let { if (it < 0) queue.size - 1 else it }
        playCurrent()
    }

    fun seekToProgress1000(progress1000: Int) {
        if (player == null || isPreparing) return
        seekToMs((getDurationMs() * (progress1000 / 1000f)).toInt())
    }

    private fun seekToMs(ms: Int) {
        if (player == null || isPreparing) return
        player?.seekTo(ms.toLong())
        updatePlaybackState()
    }

    fun setShuffle(on: Boolean) { shuffleOn = on }
    fun setRepeat(on: Boolean) { repeatOn = on }
    fun isShuffleOn() = shuffleOn
    fun isRepeatOn() = repeatOn
    fun isPlaying() = isPlaying
    fun getCurrentTrack(): Track? = queue.getOrNull(currentIndex)

    fun getDurationMs(): Int {
        return try {
            val dur = player?.duration ?: 0L
            if (dur == C.TIME_UNSET) 0 else dur.toInt()
        } catch (e: Exception) { 0 }
    }

    fun getCurrentPositionMs(): Int {
        return try { player?.currentPosition?.toInt() ?: 0 } catch (e: Exception) { 0 }
    }

    fun getQueue(): List<Track> = queue
    fun getCurrentQueueIndex(): Int = currentIndex

    fun playAtIndex(index: Int) {
        if (index < 0 || index >= queue.size) return
        currentIndex = index
        playCurrent()
    }

    // ============================================================
    //  SUBSCRIPTION HELPERS
    // ============================================================

    private fun isSubscribed(): Boolean =
        getSharedPreferences("song_user_data", MODE_PRIVATE).getBoolean("subscribed", false)

    private fun canSkip(): Boolean {
        val prefs = getSharedPreferences("song_skip_data", MODE_PRIVATE)
        val windowStart = prefs.getLong("window_start", 0)
        val count = prefs.getInt("skip_count", 0)
        val now = System.currentTimeMillis()
        if (now - windowStart > SKIP_WINDOW_MS) return true
        return count < FREE_SKIP_LIMIT
    }

    private fun recordSkip() {
        val prefs = getSharedPreferences("song_skip_data", MODE_PRIVATE)
        val windowStart = prefs.getLong("window_start", 0)
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        if (now - windowStart > SKIP_WINDOW_MS) {
            editor.putLong("window_start", now)
            editor.putInt("skip_count", 1)
        } else {
            editor.putInt("skip_count", prefs.getInt("skip_count", 0) + 1)
        }
        editor.apply()
    }

    // ============================================================
    //  QUALITY SELECTION
    // ============================================================

    private fun pickStreamUrl(track: Track, fallbackLevel: Int): String {
        val urls = track.downloadUrls
        if (urls.isEmpty()) return ""
        val size = urls.size

        val preferredIndex = if (isSubscribed()) {
            size - 1
        } else {
            when (getNetworkQualityHint()) {
                2 -> maxOf(0, minOf(size - 1, size - 2))
                1 -> maxOf(0, size - 3)
                else -> 0
            }
        }

        var index = preferredIndex - fallbackLevel
        if (index < 0) index = 0
        if (index >= size) index = size - 1
        return urls[index]
    }

    private fun getNetworkQualityHint(): Int {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = cm.activeNetworkInfo
            if (info == null || !info.isConnected) 0
            else if (info.type == ConnectivityManager.TYPE_WIFI) 2 else 1
        } catch (e: Exception) { 1 }
    }

    // ============================================================
    //  MEDIA SESSION + NOTIFICATION
    // ============================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Controls for the currently playing song"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updatePlaybackState() {
        val session = mediaSession ?: return
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_PLAY_PAUSE
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, getCurrentPositionMs().toLong(), 1f)
                .build()
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun buildNotification(track: Track): Notification {
        var contentFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) contentFlags = contentFlags or PendingIntent.FLAG_IMMUTABLE
        val openApp = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, openApp, contentFlags)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_music)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setLargeIcon(currentArt)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setStyle(mediaStyle)
            .addAction(NotificationCompat.Action(R.drawable.ic_prev, "Previous", actionIntent(ACTION_PREV)))
            .addAction(NotificationCompat.Action(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play", actionIntent(ACTION_PLAY_PAUSE)))
            .addAction(NotificationCompat.Action(R.drawable.ic_next, "Next", actionIntent(ACTION_NEXT)))
            .build()
    }

    private fun loadAlbumArtForNotification(track: Track) {
        serviceScope.launch(Dispatchers.IO) {
            val bmp = downloadBitmap(track.imageUrl)
            if (bmp != null) {
                currentArt = bmp
                val nowPlaying = getCurrentTrack()
                if (nowPlaying != null && nowPlaying.id == track.id) {
                    launch(Dispatchers.Main) {
                        notificationManager?.notify(NOTIF_ID, buildNotification(track))
                    }
                }
            }
        }
    }

    private fun downloadBitmap(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        player?.release(); player = null
        mediaSession?.release(); mediaSession = null
    }
}
