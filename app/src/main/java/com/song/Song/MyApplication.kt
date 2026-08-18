package com.song.Song

import android.app.Application
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))

                val file = File(Environment.getExternalStorageDirectory(), "song_crash_log.txt")
                FileWriter(file, false).use { it.write(sw.toString()) }
            } catch (ignored: Exception) {
            }

            defaultHandler?.uncaughtException(thread, ex)
        }
    }
}
